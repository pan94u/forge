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
    private val memoryToolHandler: MemoryToolHandler,
    private val planToolHandler: PlanToolHandler
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
     * sessionId and onEvent are forwarded to WorkspaceToolHandler for git confirmation flow.
     */
    fun callTool(
        toolName: String,
        arguments: Map<String, Any?>,
        workspaceId: String?,
        sessionId: String = "",
        onEvent: ((Map<String, Any?>) -> Unit)? = null
    ): McpToolCallResponse {
        val resolvedWsId = workspaceId ?: arguments["workspaceId"] as? String

        // Route workspace tools
        if (toolName.startsWith("workspace_") && resolvedWsId != null) {
            return workspaceToolHandler.handle(toolName, arguments, resolvedWsId, sessionId, onEvent)
        }

        // Route memory tools that have workspace context
        if (toolName in MEMORY_TOOLS && resolvedWsId != null) {
            return memoryToolHandler.handle(toolName, arguments, resolvedWsId)
        }

        // Route plan tools (need sessionId and onEvent for blocking confirmation flows)
        if (toolName.startsWith("plan_")) {
            return planToolHandler.handle(toolName, arguments, sessionId, onEvent)
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
                toolName.startsWith("plan_") -> planToolHandler.handle(toolName, arguments, "", null)
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
                description = "Create or overwrite a file in the current workspace. Always use this tool when generating code, configuration, or documentation — write files instead of only showing code in the chat response. IMPORTANT: Before writing, you MUST first: 1) Use workspace_read_file to read the existing file (if updating), 2) Use workspace_list_files to understand project structure (if creating new files), 3) If a previous workspace_compile returned errors, read related files first. Never write files blindly — always gather context first.",
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
            ),
            McpTool(
                name = "trigger_knowledge_extraction",
                description = "Trigger AI-driven knowledge extraction for a workspace. Analyzes the codebase and generates standard documentation (UI/UX, API contracts, data models, architecture decisions, etc.). Returns a job ID to track progress.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "workspaceId" to mapOf("type" to "string", "description" to "Workspace ID to analyze"),
                        "tagId" to mapOf("type" to "string", "description" to "Optional: specific tag ID to extract (omit for all tags)")
                    ),
                    "required" to listOf("workspaceId")
                )
            ),
            McpTool(
                name = "workspace_start_service",
                description = "Start a service process in the workspace (e.g. HTTP server, Node.js app). The service runs in the background and is accessible via the reverse proxy URL at /api/workspaces/{workspaceId}/proxy/{port}/. Use this to let users preview generated web applications. IMPORTANT: Ports 8080-8082 are reserved by the platform. Recommended ports: 8888 for Python HTTP servers, 3000 for Node.js apps.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "command" to mapOf("type" to "string", "description" to "Shell command to start the service (e.g. 'python3 -m http.server 8888', 'node server.js')"),
                        "port" to mapOf("type" to "integer", "description" to "Port the service will listen on (3000-9999, excluding 8080-8082 which are reserved)")
                    ),
                    "required" to listOf("command", "port")
                )
            ),
            McpTool(
                name = "workspace_stop_service",
                description = "Stop a running service in the workspace by port number.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "port" to mapOf("type" to "integer", "description" to "Port of the service to stop")
                    ),
                    "required" to listOf("port")
                )
            ),
            McpTool(
                name = "workspace_delete_file",
                description = "Delete a file or directory from the current workspace. Use this to remove files that are no longer needed. SECURITY: path traversal (.. ) is forbidden.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "File or directory path relative to workspace root to delete")
                    ),
                    "required" to listOf("path")
                )
            ),
            McpTool(
                name = "workspace_git_status",
                description = "Show git status of the workspace: current branch name and list of modified/staged/untracked files.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf<String, Any>()
                )
            ),
            McpTool(
                name = "workspace_git_diff",
                description = "Show git diff (staged + unstaged changes) in the workspace. Use this before committing to review what will be committed.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf<String, Any>()
                )
            ),
            McpTool(
                name = "workspace_git_add",
                description = "Stage files for the next git commit. Use 'paths' to stage specific files, or 'all=true' to stage all changes.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "paths" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "List of file paths to stage (relative to workspace root)"),
                        "all" to mapOf("type" to "boolean", "description" to "If true, stage all changes (git add -A). Overrides paths.")
                    )
                )
            ),
            McpTool(
                name = "workspace_git_commit",
                description = "Commit staged changes with a message. The message will be tagged with [Forge-Agent] automatically. Always run workspace_git_diff and workspace_git_add before committing.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "message" to mapOf("type" to "string", "description" to "Commit message (format: 'type: description', e.g. 'feat: add login feature')")
                    ),
                    "required" to listOf("message")
                )
            ),
            McpTool(
                name = "workspace_git_push",
                description = "Push the current branch to a remote repository. SAFETY: pushing to main/master returns a warning instead of executing. Use feature branches and PRs.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "remote" to mapOf("type" to "string", "description" to "Remote name (default: origin)"),
                        "branch" to mapOf("type" to "string", "description" to "Branch name to push (default: current branch)")
                    )
                )
            ),
            McpTool(
                name = "workspace_git_pull",
                description = "Pull latest changes from the remote repository. Uses --rebase by default to maintain a clean history.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "remote" to mapOf("type" to "string", "description" to "Remote name (default: origin)"),
                        "rebase" to mapOf("type" to "boolean", "description" to "Use --rebase instead of merge (default: true)")
                    )
                )
            ),
            McpTool(
                name = "workspace_git_branch",
                description = "Create a new git branch or list existing branches. Provide 'name' to create a new branch, or 'list=true' to list all branches.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "New branch name to create (e.g. 'feature/user-login')"),
                        "list" to mapOf("type" to "boolean", "description" to "If true, list all branches instead of creating")
                    )
                )
            ),
            McpTool(
                name = "plan_create",
                description = "Submit an ordered task list for the current large task and wait for user confirmation before executing. Use this when the task involves >100 lines of changes or 3+ files. The user will see a plan card and click '开始执行' to approve. Do NOT call any code-writing tools until plan_create returns 'Plan approved'.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "tasks" to mapOf(
                            "type" to "array",
                            "description" to "Ordered list of tasks to execute",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "id" to mapOf("type" to "string", "description" to "Unique task ID (e.g. 'task-001')"),
                                    "title" to mapOf("type" to "string", "description" to "Task title (≤20 characters)"),
                                    "files" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Main files to be modified"),
                                    "successCriteria" to mapOf("type" to "string", "description" to "Verifiable success criteria (e.g. 'workspace_compile returns zero errors')"),
                                    "estimatedLines" to mapOf("type" to "integer", "description" to "Estimated lines of code to add/modify"),
                                    "dependsOn" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Task IDs this task depends on (optional)")
                                ),
                                "required" to listOf("id", "title", "files", "successCriteria", "estimatedLines")
                            )
                        )
                    ),
                    "required" to listOf("tasks")
                )
            ),
            McpTool(
                name = "plan_update_task",
                description = "Update the status of a single task in the current plan. Call this before and after executing each task to keep the plan card in sync.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "taskId" to mapOf("type" to "string", "description" to "Task ID from the plan (e.g. 'task-001')"),
                        "status" to mapOf(
                            "type" to "string",
                            "enum" to listOf("pending", "in_progress", "done", "failed", "blocked"),
                            "description" to "New task status"
                        ),
                        "detail" to mapOf("type" to "string", "description" to "Optional detail message (e.g. retry count, error summary)")
                    ),
                    "required" to listOf("taskId", "status")
                )
            ),
            McpTool(
                name = "plan_ask_user",
                description = "Ask the user clarifying questions (choice or free-text) and wait for their answers before proceeding. Use this at the start when intent is unclear, or when a blocked task needs user guidance.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "questions" to mapOf(
                            "type" to "array",
                            "description" to "List of questions (max 3)",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "type" to mapOf("type" to "string", "enum" to listOf("choice", "text"), "description" to "Question type"),
                                    "question" to mapOf("type" to "string", "description" to "The question text"),
                                    "options" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Answer options (required when type=choice)")
                                ),
                                "required" to listOf("type", "question")
                            )
                        )
                    ),
                    "required" to listOf("questions")
                )
            ),
            McpTool(
                name = "plan_complete",
                description = "Submit the final execution summary after all plan tasks are done. Renders a summary card in the UI. Always call this at the end of a Planning Mode execution.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "summary" to mapOf("type" to "string", "description" to "Markdown summary with three sections: 做了什么 (What), 遇到什么 (Issues), 建议什么 (Next Steps)"),
                        "suggestions" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "List of next-step suggestions (at least 2)")
                    ),
                    "required" to listOf("summary")
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
            "run_baseline", "query_schema", "list_baselines",
            "trigger_knowledge_extraction"
        )

        private val SKILL_TOOLS = setOf(
            "read_skill", "run_skill_script", "list_skills"
        )

        private val MEMORY_TOOLS = setOf(
            "update_workspace_memory", "get_session_history", "analyze_codebase"
        )

        private val PLAN_TOOLS = setOf(
            "plan_create", "plan_update_task", "plan_ask_user", "plan_complete"
        )

        private val ALL_TOOLS = BUILTIN_TOOLS + SKILL_TOOLS + MEMORY_TOOLS + PLAN_TOOLS + setOf(
            "workspace_write_file", "workspace_read_file", "workspace_list_files",
            "workspace_compile", "workspace_test",
            "workspace_start_service", "workspace_stop_service",
            "workspace_delete_file",
            "workspace_git_status", "workspace_git_diff", "workspace_git_add",
            "workspace_git_commit", "workspace_git_push", "workspace_git_pull",
            "workspace_git_branch"
        )

        /**
         * Create a standard error response. Used by all handlers.
         */
        fun errorResponse(message: String): McpToolCallResponse {
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = message)),
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
