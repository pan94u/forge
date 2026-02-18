package com.forge.webide.service

import com.forge.webide.model.McpContent
import com.forge.webide.model.McpTool
import com.forge.webide.model.McpToolCallResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * Proxies MCP (Model Context Protocol) tool calls to configured MCP servers.
 *
 * When no external MCP servers are configured (Trial mode), provides built-in
 * tool implementations that work with local data:
 * - search_knowledge: searches knowledge-base/ directory files
 * - read_file: reads files from workspace or project
 * - get_service_info: returns service architecture info
 * - run_baseline: executes baseline quality gate scripts
 * - query_schema: queries database schema metadata
 * - list_baselines: lists available baseline scripts
 */
@Service
class McpProxyService(
    private val baselineService: BaselineService,
    private val dataSource: DataSource
) {

    private val logger = LoggerFactory.getLogger(McpProxyService::class.java)

    @Value("\${forge.mcp.servers:}")
    private var mcpServerUrls: String = ""

    @Value("\${forge.plugins.base-path:plugins}")
    private var pluginsBasePath: String = "plugins"

    @Value("\${forge.session.working-directory:/workspace}")
    private var workingDirectory: String = "/workspace"

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
                val response = client.post()
                    .uri("/mcp/tools/list")
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

                logger.debug("Discovered ${tools.size} tools from $serverUrl")
            } catch (e: Exception) {
                logger.warn("Failed to list tools from $serverUrl: ${e.message}")
            }
        }

        return allTools.ifEmpty { getDefaultTools() }
    }

    /**
     * Call an MCP tool by name with the given arguments.
     */
    fun callTool(toolName: String, arguments: Map<String, Any?>): McpToolCallResponse {
        val servers = parseMcpServers()

        // Find which server has this tool
        for (serverUrl in servers) {
            val tools = toolCache[serverUrl]
            if (tools != null && tools.any { it.name == toolName }) {
                return callToolOnServer(serverUrl, toolName, arguments)
            }
        }

        // If no server found, try all servers
        for (serverUrl in servers) {
            try {
                return callToolOnServer(serverUrl, toolName, arguments)
            } catch (e: Exception) {
                logger.debug("Tool $toolName not found on $serverUrl")
            }
        }

        // Fallback to built-in tools
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
            val response = client.post()
                .uri("/mcp/tools/call")
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

    // ---- Built-in tool implementations (Trial mode) ----

    private fun handleBuiltinTool(
        toolName: String,
        arguments: Map<String, Any?>
    ): McpToolCallResponse {
        return try {
            when (toolName) {
                "search_knowledge" -> handleSearchKnowledge(arguments)
                "read_file" -> handleReadFile(arguments)
                "get_service_info" -> handleGetServiceInfo(arguments)
                "run_baseline" -> handleRunBaseline(arguments)
                "query_schema" -> handleQuerySchema(arguments)
                "list_baselines" -> handleListBaselines()
                else -> McpToolCallResponse(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Unknown tool: $toolName. Available tools: search_knowledge, read_file, get_service_info, run_baseline, query_schema, list_baselines"
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

    /**
     * Search knowledge-base/ directory for documents matching the query.
     * Supports filtering by type (adr, runbook, convention, api-doc).
     */
    private fun handleSearchKnowledge(arguments: Map<String, Any?>): McpToolCallResponse {
        val query = (arguments["query"] as? String ?: "").lowercase()
        val typeFilter = arguments["type"] as? String

        if (query.isBlank()) {
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Error: 'query' parameter is required")),
                isError = true
            )
        }

        val knowledgeBaseDir = resolveKnowledgeBaseDir()
        if (!knowledgeBaseDir.exists()) {
            return McpToolCallResponse(
                content = listOf(McpContent(
                    type = "text",
                    text = "Knowledge base directory not found at: ${knowledgeBaseDir.absolutePath}"
                )),
                isError = true
            )
        }

        val results = mutableListOf<Map<String, String>>()

        knowledgeBaseDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .filter { file ->
                if (typeFilter != null) {
                    val parentDir = file.parentFile?.name ?: ""
                    when (typeFilter.lowercase()) {
                        "adr" -> parentDir == "adr"
                        "runbook", "runbooks" -> parentDir == "runbooks"
                        "convention", "conventions" -> parentDir == "conventions"
                        "api-doc", "api-docs" -> parentDir == "api-docs"
                        else -> true
                    }
                } else true
            }
            .forEach { file ->
                val content = file.readText()
                val contentLower = content.lowercase()
                val fileNameLower = file.nameWithoutExtension.lowercase()

                // Simple relevance: query terms appear in filename or content
                val queryTerms = query.split("\\s+".toRegex())
                val matches = queryTerms.any { term ->
                    fileNameLower.contains(term) || contentLower.contains(term)
                }

                if (matches) {
                    val title = content.lines().firstOrNull { it.startsWith("# ") }
                        ?.removePrefix("# ")?.trim()
                        ?: file.nameWithoutExtension

                    val excerpt = content.lines()
                        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("---") }
                        .take(3)
                        .joinToString(" ")
                        .take(200)

                    results.add(mapOf(
                        "title" to title,
                        "path" to file.relativeTo(knowledgeBaseDir).path,
                        "type" to (file.parentFile?.name ?: "unknown"),
                        "excerpt" to excerpt
                    ))
                }
            }

        if (results.isEmpty()) {
            return McpToolCallResponse(
                content = listOf(McpContent(
                    type = "text",
                    text = "No results found for query: '$query'" +
                        (if (typeFilter != null) " (type: $typeFilter)" else "") +
                        "\nKnowledge base location: ${knowledgeBaseDir.absolutePath}" +
                        "\nAvailable categories: ${knowledgeBaseDir.listFiles()?.filter { it.isDirectory }?.joinToString { it.name } ?: "none"}"
                ))
            )
        }

        val resultText = buildString {
            appendLine("Found ${results.size} result(s) for '$query':")
            appendLine()
            results.forEachIndexed { i, r ->
                appendLine("${i + 1}. **${r["title"]}** [${r["type"]}]")
                appendLine("   Path: ${r["path"]}")
                appendLine("   ${r["excerpt"]}")
                appendLine()
            }
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = resultText))
        )
    }

    /**
     * Read a file from the knowledge-base or workspace.
     */
    private fun handleReadFile(arguments: Map<String, Any?>): McpToolCallResponse {
        val path = arguments["path"] as? String ?: ""

        if (path.isBlank()) {
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Error: 'path' parameter is required")),
                isError = true
            )
        }

        // Security: prevent path traversal
        if (path.contains("..")) {
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Error: path traversal not allowed")),
                isError = true
            )
        }

        // Try knowledge-base first, then workspace, then plugins
        val candidates = listOf(
            resolveKnowledgeBaseDir().resolve(path),
            File(workingDirectory).resolve(path),
            File(pluginsBasePath).resolve(path)
        )

        val file = candidates.firstOrNull { it.exists() && it.isFile }

        if (file == null) {
            return McpToolCallResponse(
                content = listOf(McpContent(
                    type = "text",
                    text = "File not found: $path\nSearched in: ${candidates.joinToString { it.absolutePath }}"
                )),
                isError = true
            )
        }

        val content = if (file.length() > 100_000) {
            file.readText().take(100_000) + "\n\n[... truncated at 100KB ...]"
        } else {
            file.readText()
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = content))
        )
    }

    /**
     * Get service architecture information.
     */
    private fun handleGetServiceInfo(arguments: Map<String, Any?>): McpToolCallResponse {
        val service = (arguments["service"] as? String ?: "").lowercase()

        val serviceInfo = mapOf(
            "backend" to """
                |Service: forge-web-ide-backend
                |Type: Spring Boot 3.3 (Kotlin)
                |Port: 8080
                |Database: H2 (Trial) / PostgreSQL (Production)
                |Dependencies: ClaudeAdapter (model-adapter), MCP Proxy
                |Endpoints: /api/chat/*, /api/workspaces/*, /api/mcp/*, /api/knowledge/*
                |WebSocket: /ws/chat/{sessionId}, /ws/terminal/{workspaceId}
                |Health: /actuator/health
            """.trimMargin(),
            "frontend" to """
                |Service: forge-web-ide-frontend
                |Type: Next.js 15 (React 19, TypeScript)
                |Port: 3000
                |Dependencies: Backend API (via Nginx proxy)
                |Routes: / (Dashboard), /workspace/[id] (IDE), /knowledge, /workflows
                |Build: standalone mode for Docker deployment
            """.trimMargin(),
            "nginx" to """
                |Service: nginx (reverse proxy)
                |Port: 9000 (external entry point)
                |Routes:
                |  /api/* → backend:8080
                |  /ws/*  → backend:8080 (WebSocket upgrade)
                |  /*     → frontend:3000
            """.trimMargin()
        )

        val result = if (service.isBlank()) {
            // Return overview of all services
            buildString {
                appendLine("Forge Platform Services:")
                appendLine()
                serviceInfo.forEach { (name, info) ->
                    appendLine("=== $name ===")
                    appendLine(info)
                    appendLine()
                }
            }
        } else {
            serviceInfo.entries.firstOrNull { service in it.key }?.let {
                "=== ${it.key} ===\n${it.value}"
            } ?: "Service '$service' not found. Available: ${serviceInfo.keys.joinToString()}"
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = result))
        )
    }

    /**
     * Execute baseline quality gate scripts via BaselineService.
     */
    private fun handleRunBaseline(arguments: Map<String, Any?>): McpToolCallResponse {
        @Suppress("UNCHECKED_CAST")
        val baselineNames = when (val raw = arguments["baselines"]) {
            is List<*> -> raw.filterIsInstance<String>()
            is String -> if (raw.isBlank()) null else raw.split(",").map { it.trim() }
            else -> null
        }

        val projectRoot = arguments["project_root"] as? String

        logger.info("Running baselines: ${baselineNames ?: "all"}")
        val report = baselineService.runBaselines(baselineNames, projectRoot)

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = report.summary)),
            isError = !report.allPassed
        )
    }

    /**
     * List available baseline scripts.
     */
    private fun handleListBaselines(): McpToolCallResponse {
        val baselines = baselineService.listBaselines()
        val text = if (baselines.isEmpty()) {
            "No baseline scripts found."
        } else {
            "Available baselines (${baselines.size}):\n" +
                baselines.joinToString("\n") { "  - $it" }
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = text))
        )
    }

    /**
     * Query database schema metadata from the connected datasource.
     */
    private fun handleQuerySchema(arguments: Map<String, Any?>): McpToolCallResponse {
        val tableName = arguments["table"] as? String

        return try {
            dataSource.connection.use { conn ->
                val metadata = conn.metaData

                if (tableName.isNullOrBlank()) {
                    // List all tables
                    val tables = mutableListOf<String>()
                    val rs = metadata.getTables(null, null, "%", arrayOf("TABLE"))
                    while (rs.next()) {
                        val schema = rs.getString("TABLE_SCHEM") ?: ""
                        val name = rs.getString("TABLE_NAME") ?: ""
                        if (schema != "INFORMATION_SCHEMA") {
                            tables.add(name)
                        }
                    }
                    rs.close()

                    val text = if (tables.isEmpty()) {
                        "No tables found in the database."
                    } else {
                        "Database tables (${tables.size}):\n" +
                            tables.joinToString("\n") { "  - $it" }
                    }

                    McpToolCallResponse(
                        content = listOf(McpContent(type = "text", text = text))
                    )
                } else {
                    // Describe specific table
                    val columns = mutableListOf<Map<String, String>>()
                    val rs = metadata.getColumns(null, null, tableName.uppercase(), "%")
                    while (rs.next()) {
                        columns.add(mapOf(
                            "name" to (rs.getString("COLUMN_NAME") ?: ""),
                            "type" to (rs.getString("TYPE_NAME") ?: ""),
                            "size" to (rs.getString("COLUMN_SIZE") ?: ""),
                            "nullable" to (rs.getString("IS_NULLABLE") ?: "")
                        ))
                    }
                    rs.close()

                    if (columns.isEmpty()) {
                        McpToolCallResponse(
                            content = listOf(McpContent(
                                type = "text",
                                text = "Table '$tableName' not found or has no columns."
                            )),
                            isError = true
                        )
                    } else {
                        val text = buildString {
                            appendLine("Table: $tableName (${columns.size} columns)")
                            appendLine()
                            appendLine("| Column | Type | Size | Nullable |")
                            appendLine("|--------|------|------|----------|")
                            columns.forEach { col ->
                                appendLine("| ${col["name"]} | ${col["type"]} | ${col["size"]} | ${col["nullable"]} |")
                            }
                        }

                        McpToolCallResponse(
                            content = listOf(McpContent(type = "text", text = text))
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Schema query failed: {}", e.message)
            McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Schema query error: ${e.message}")),
                isError = true
            )
        }
    }

    // ---- Tool definitions ----

    private fun getDefaultTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "search_knowledge",
                description = "Search the knowledge base for documentation, ADRs, runbooks, conventions, and API docs. Returns matching documents with titles and excerpts.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Search query (keywords to find in document titles and content)"),
                        "type" to mapOf("type" to "string", "description" to "Document type filter", "enum" to listOf("adr", "runbook", "convention", "api-doc"))
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
            )
        )
    }

    // ---- Utility ----

    private fun resolveKnowledgeBaseDir(): File {
        // Try relative to current working directory
        val relative = File("knowledge-base")
        if (relative.exists()) return relative

        // Try relative to plugins parent
        val pluginsParent = File(pluginsBasePath).parentFile
        if (pluginsParent != null) {
            val candidate = pluginsParent.resolve("knowledge-base")
            if (candidate.exists()) return candidate
        }

        // Docker mount
        val dockerMount = File("/knowledge-base")
        if (dockerMount.exists()) return dockerMount

        return relative // Return default even if not found
    }

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
