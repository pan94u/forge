package com.forge.webide.service

import com.forge.webide.model.McpContent
import com.forge.webide.model.McpTool
import com.forge.webide.model.McpToolCallResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Proxies MCP (Model Context Protocol) tool calls to configured MCP servers.
 *
 * When no external MCP servers are configured (Trial mode), delegates to
 * specialized handlers:
 * - BuiltinToolHandler: search_knowledge, read_file, get_service_info, run_baseline, query_schema, list_baselines
 * - WorkspaceToolHandler: workspace_write_file, workspace_read_file, workspace_list_files, workspace_compile, workspace_test
 * - SkillToolHandler: read_skill, run_skill_script, list_skills
 * - MemoryToolHandler: update_workspace_memory, get_session_history, analyze_codebase
 */
@Service
class McpProxyService(
    private val builtinToolHandler: BuiltinToolHandler,
    private val workspaceToolHandler: WorkspaceToolHandler,
    private val skillToolHandler: SkillToolHandler,
    private val memoryToolHandler: MemoryToolHandler
) {

    private val logger = LoggerFactory.getLogger(McpProxyService::class.java)

    @Value("\${forge.mcp.servers:}")
    private var mcpServerUrls: String = ""

    private val toolCache = ConcurrentHashMap<String, List<McpTool>>()
    private val serverClients = ConcurrentHashMap<String, WebClient>()

    /**
     * List all available tools from all connected MCP servers.
     */
    fun listTools(): List<McpTool> {
        val servers = parseMcpServers()
        if (servers.isEmpty()) {
            return getDefaultTools()
        }

        val allTools = mutableListOf<McpTool>()

        servers.forEach { serverUrl ->
            try {
                val cached = toolCache[serverUrl]
                if (cached != null) {
                    allTools.addAll(cached)
                    return@forEach
                }

                val client = getOrCreateClient(serverUrl)
                val response = client.get()
                    .uri("/tools")
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block()

                @Suppress("UNCHECKED_CAST")
                val tools = (response?.get("tools") as? List<Map<String, Any>>)?.map { toolMap ->
                    McpTool(
                        name = toolMap["name"] as? String ?: "",
                        description = toolMap["description"] as? String ?: "",
                        inputSchema = toolMap["inputSchema"] as? Map<String, Any?> ?: emptyMap()
                    )
                } ?: emptyList()

                toolCache[serverUrl] = tools
                allTools.addAll(tools)

                logger.info("Discovered {} tools from {}", tools.size, serverUrl)
            } catch (e: Exception) {
                logger.warn("Failed to list tools from $serverUrl: ${e.message}")
            }
        }

        // Merge: external tools + default tools (external take precedence by name)
        val externalToolNames = allTools.map { it.name }.toSet()
        val defaults = getDefaultTools().filter { it.name !in externalToolNames }
        return (allTools + defaults).ifEmpty { getDefaultTools() }
    }

    /**
     * Call an MCP tool by name with the given arguments, with workspace context.
     * Workspace tools (workspace_*) require a workspaceId to operate on.
     */
    fun callTool(toolName: String, arguments: Map<String, Any?>, workspaceId: String?): McpToolCallResponse {
        val resolvedWsId = workspaceId ?: arguments["workspaceId"] as? String

        // Route workspace tools
        if (toolName.startsWith("workspace_") && resolvedWsId != null) {
            return workspaceToolHandler.handle(toolName, arguments, resolvedWsId)
        }

        // Route memory tools that have workspace context
        if (toolName in MEMORY_TOOLS && resolvedWsId != null) {
            return memoryToolHandler.handle(toolName, arguments, resolvedWsId)
        }

        return callTool(toolName, arguments)
    }

    /**
     * Call an MCP tool by name with the given arguments.
     */
    fun callTool(toolName: String, arguments: Map<String, Any?>): McpToolCallResponse {
        val servers = parseMcpServers()

        // Find which server has this tool (cache-based lookup)
        for (serverUrl in servers) {
            val tools = toolCache[serverUrl]
            if (tools != null && tools.any { it.name == toolName }) {
                return callToolOnServer(serverUrl, toolName, arguments)
            }
        }

        // If tool cache is populated for all servers and tool not found,
        // skip "try all servers" and go directly to built-in fallback.
        val allCached = servers.all { toolCache.containsKey(it) }
        if (!allCached) {
            // Cache not fully populated yet -- try each server
            for (serverUrl in servers) {
                val result = callToolOnServer(serverUrl, toolName, arguments)
                if (!result.isError) return result
            }
        }

        // Fallback to handler-based dispatch
        return handleBuiltinTool(toolName, arguments)
    }

    /**
     * Invalidate the tool cache for all servers.
     */
    fun invalidateCache() {
        toolCache.clear()
        logger.info("MCP tool cache invalidated")
    }

    // ---- Remote MCP server calls ----

    private fun callToolOnServer(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): McpToolCallResponse {
        val client = getOrCreateClient(serverUrl)

        try {
            logger.info("Calling MCP server: {} tool={}", serverUrl, toolName)
            val response = client.post()
                .uri("/tools/$toolName")
                .bodyValue(mapOf(
                    "name" to toolName,
                    "arguments" to arguments
                ))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            @Suppress("UNCHECKED_CAST")
            val content = (response?.get("content") as? List<Map<String, Any?>>)?.map { contentMap ->
                McpContent(
                    type = contentMap["type"] as? String ?: "text",
                    text = contentMap["text"] as? String,
                    data = contentMap["data"] as? String,
                    mimeType = contentMap["mimeType"] as? String,
                    uri = contentMap["uri"] as? String
                )
            } ?: emptyList()

            val isError = response?.get("isError") as? Boolean ?: false

            return McpToolCallResponse(content = content, isError = isError)
        } catch (e: Exception) {
            logger.error("MCP tool call failed: server=$serverUrl, tool=$toolName: ${e.message}")
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Error calling tool: ${e.message}")),
                isError = true
            )
        }
    }

    // ---- Built-in tool dispatch ----

    private fun handleBuiltinTool(
        toolName: String,
        arguments: Map<String, Any?>
    ): McpToolCallResponse {
        return try {
            when {
                toolName in BUILTIN_TOOLS -> builtinToolHandler.handle(toolName, arguments, null)
                toolName in SKILL_TOOLS -> skillToolHandler.handle(toolName, arguments, null)
                toolName in MEMORY_TOOLS -> memoryToolHandler.handle(toolName, arguments, null)
                toolName.startsWith("workspace_") -> workspaceToolHandler.handle(toolName, arguments, null)
                else -> McpToolCallResponse(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Unknown tool: $toolName. Available tools: ${ALL_TOOLS.joinToString(", ")}"
                    )),
                    isError = true
                )
            }
        } catch (e: Exception) {
            logger.error("Built-in tool execution failed: tool=$toolName: ${e.message}", e)
            McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Tool execution error: ${e.message}")),
                isError = true
            )
        }
    }

    // ---- Tool definitions ----

    private fun getDefaultTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "workspace_write_file",
                description = "Create or overwrite a file in the current workspace. Always use this tool when generating code, configuration, or documentation — write files instead of only showing code in the chat response.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "File path relative to workspace root (e.g. 'src/hello.ts', 'README.md')"),
                        "content" to mapOf("type" to "string", "description" to "Complete file content to write")
                    ),
                    "required" to listOf("path", "content")
                )
            ),
            McpTool(
                name = "workspace_read_file",
                description = "Read a file from the current workspace. Use this to understand existing code before modifying it.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "File path relative to workspace root")
                    ),
                    "required" to listOf("path")
                )
            ),
            McpTool(
                name = "workspace_list_files",
                description = "List all files in the current workspace. Use this to understand the project structure before creating or modifying files.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf<String, Any>()
                )
            ),
            McpTool(
                name = "search_knowledge",
                description = "Search the knowledge base for documentation, ADRs, runbooks, conventions, and API docs. Supports scope-based filtering: global (platform-wide), workspace (project-specific), personal (user's own). When no scope is specified, uses cascade search (workspace > personal > global priority).",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Search query (keywords to find in document titles and content)"),
                        "type" to mapOf("type" to "string", "description" to "Document type filter", "enum" to listOf("adr", "runbook", "convention", "api-doc")),
                        "scope" to mapOf("type" to "string", "description" to "Knowledge scope filter: global, workspace, personal. Omit for cascade search.", "enum" to listOf("global", "workspace", "personal"))
                    ),
                    "required" to listOf("query")
                )
            ),
            McpTool(
                name = "read_file",
                description = "Read a file from the knowledge base, workspace, or plugins directory. Returns the file content as text.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "File path (relative to knowledge-base/, workspace, or plugins directory)")
                    ),
                    "required" to listOf("path")
                )
            ),
            McpTool(
                name = "get_service_info",
                description = "Get information about a platform service including type, port, dependencies, and endpoints. Pass empty service name to get an overview of all services.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "service" to mapOf("type" to "string", "description" to "Service name (backend, frontend, nginx) or empty for overview")
                    ),
                    "required" to listOf("service")
                )
            ),
            McpTool(
                name = "run_baseline",
                description = "Execute baseline quality gate scripts to check code quality, security, test coverage, API contracts, and architecture constraints. Returns a pass/fail report. Use this after generating or modifying code to verify quality.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "baselines" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "string"),
                            "description" to "List of baseline names to run (e.g. ['code-style-baseline', 'security-baseline']). Leave empty to run all available baselines."
                        ),
                        "project_root" to mapOf("type" to "string", "description" to "Project root directory (optional, defaults to workspace)")
                    )
                )
            ),
            McpTool(
                name = "query_schema",
                description = "Query the database schema. Without a table name, lists all tables. With a table name, shows column details (name, type, size, nullable).",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "table" to mapOf("type" to "string", "description" to "Table name to describe (optional, omit to list all tables)")
                    )
                )
            ),
            McpTool(
                name = "list_baselines",
                description = "List all available baseline quality gate scripts that can be run with run_baseline.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf<String, Any>()
                )
            ),
            McpTool(
                name = "read_skill",
                description = "Read a skill's SKILL.md guide or sub-file content. Use this to load detailed skill instructions before applying them. Pass only skill_name to read the main guide, or specify a file path (e.g. 'examples/pattern.md') to read a sub-file.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "skill_name" to mapOf("type" to "string", "description" to "Name of the skill to read (e.g. 'code-generation', 'kotlin-conventions')"),
                        "file" to mapOf("type" to "string", "description" to "Sub-file path relative to skill directory (default: 'SKILL.md'). Examples: 'examples/entity-pattern.md', 'reference/rules.md'")
                    ),
                    "required" to listOf("skill_name")
                )
            ),
            McpTool(
                name = "run_skill_script",
                description = "Execute a skill's script and return the output. Scripts provide deterministic validation and generation operations. The script code does NOT enter your context — only stdout/stderr and exit code are returned.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "skill_name" to mapOf("type" to "string", "description" to "Name of the skill containing the script"),
                        "script" to mapOf("type" to "string", "description" to "Script path relative to skill directory (e.g. 'scripts/validate.py')"),
                        "args" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Optional arguments to pass to the script")
                    ),
                    "required" to listOf("skill_name", "script")
                )
            ),
            McpTool(
                name = "list_skills",
                description = "List all available skills with their metadata (name, description, scope, category, tags, sub-files, scripts). Use this to discover what skills are available before reading them.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "profile" to mapOf("type" to "string", "description" to "Filter by profile name (e.g. 'development-profile')"),
                        "category" to mapOf("type" to "string", "description" to "Filter by category", "enum" to listOf("SYSTEM", "FOUNDATION", "DELIVERY", "KNOWLEDGE", "CUSTOM")),
                        "scope" to mapOf("type" to "string", "description" to "Filter by scope (ownership)", "enum" to listOf("PLATFORM", "WORKSPACE", "CUSTOM"))
                    )
                )
            ),
            McpTool(
                name = "update_workspace_memory",
                description = "Update the workspace-level persistent memory. Use this to save key project facts, tech stack, constraints, decisions, and current progress. This memory persists across sessions and is automatically injected into every future session's system prompt. Keep content concise (max 4000 chars) and focused on durable facts.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "content" to mapOf("type" to "string", "description" to "Markdown content for workspace memory. Should include: project overview, tech stack, key decisions, current stage, and important constraints.")
                    ),
                    "required" to listOf("content")
                )
            ),
            McpTool(
                name = "get_session_history",
                description = "Retrieve structured summaries of recent sessions in the current workspace. Use this to understand what has been accomplished in previous sessions, what decisions were made, and what issues remain unresolved.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf("type" to "integer", "description" to "Number of recent sessions to retrieve (default: 5)")
                    )
                )
            ),
            McpTool(
                name = "workspace_compile",
                description = "Compile/build the project in the current workspace. Detects project type (TypeScript, Kotlin/Java, Python) and runs the appropriate build command. Returns compilation status, errors, and warnings.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "projectType" to mapOf("type" to "string", "description" to "Project type hint: 'typescript', 'kotlin', 'java', 'python'. Auto-detected if not provided.")
                    )
                )
            ),
            McpTool(
                name = "workspace_test",
                description = "Run tests in the current workspace. Detects test framework (Jest, JUnit, pytest) and executes tests. Returns pass/fail counts and error details.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "testPattern" to mapOf("type" to "string", "description" to "Optional test file pattern to run specific tests.")
                    )
                )
            ),
            McpTool(
                name = "analyze_codebase",
                description = "Analyze the workspace codebase structure. Returns a JSON summary including: project type, languages, framework, file/LOC statistics, entity/controller/service inventory, dependencies, and configuration files. Use this before generating a design baseline document.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf<String, Any?>()
                )
            )
        )
    }

    // ---- Utility ----

    private fun parseMcpServers(): List<String> {
        return mcpServerUrls
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun getOrCreateClient(serverUrl: String): WebClient {
        return serverClients.getOrPut(serverUrl) {
            WebClient.builder()
                .baseUrl(serverUrl)
                .defaultHeader("Content-Type", "application/json")
                .build()
        }
    }

    companion object {

        // Tool name sets for routing
        private val BUILTIN_TOOLS = setOf(
            "search_knowledge", "read_file", "get_service_info",
            "run_baseline", "query_schema", "list_baselines"
        )

        private val SKILL_TOOLS = setOf(
            "read_skill", "run_skill_script", "list_skills"
        )

        private val MEMORY_TOOLS = setOf(
            "update_workspace_memory", "get_session_history", "analyze_codebase"
        )

        private val ALL_TOOLS = BUILTIN_TOOLS + SKILL_TOOLS + MEMORY_TOOLS + setOf(
            "workspace_write_file", "workspace_read_file", "workspace_list_files",
            "workspace_compile", "workspace_test"
        )

        /**
         * Create a standard error response. Used by all handlers.
         */
        fun errorResponse(message: String): McpToolCallResponse {
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Error: $message")),
                isError = true
            )
        }

        /**
         * Format an MCP tool call result into a readable string.
         */
        fun formatResult(result: McpToolCallResponse): String {
            if (result.isError) {
                val errorText = result.content
                    .filter { it.type == "text" }
                    .mapNotNull { it.text }
                    .joinToString("\n")
                return "Error: ${errorText.ifEmpty { "Unknown error" }}"
            }

            return result.content.joinToString("\n") { content ->
                when (content.type) {
                    "text" -> content.text ?: ""
                    "image" -> "[Image: ${content.mimeType ?: "image"}]"
                    "resource" -> "[Resource: ${content.uri ?: "unknown"}]"
                    else -> ""
                }
            }
        }
    }
}
