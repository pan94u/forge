package com.forge.webide.service

import com.forge.webide.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import javax.sql.DataSource

/**
 * Handles built-in MCP tools: search_knowledge, read_file, get_service_info,
 * run_baseline, list_baselines, query_schema.
 */
@Service
class BuiltinToolHandler(
    private val baselineService: BaselineService,
    private val dataSource: DataSource,
    private val knowledgeIndexService: KnowledgeIndexService
) {

    private val logger = LoggerFactory.getLogger(BuiltinToolHandler::class.java)

    @Value("\${forge.plugins.base-path:plugins}")
    private var pluginsBasePath: String = "plugins"

    @Value("\${forge.session.working-directory:/workspace}")
    private var workingDirectory: String = "/workspace"

    fun handle(toolName: String, args: Map<String, Any?>, workspaceId: String?): McpToolCallResponse {
        return when (toolName) {
            "search_knowledge" -> handleSearchKnowledge(args)
            "read_file" -> handleReadFile(args)
            "get_service_info" -> handleGetServiceInfo(args)
            "run_baseline" -> handleRunBaseline(args)
            "query_schema" -> handleQuerySchema(args)
            "list_baselines" -> handleListBaselines()
            else -> McpProxyService.errorResponse("Unknown builtin tool: $toolName")
        }
    }

    /**
     * Search knowledge-base/ directory AND DB documents matching the query.
     * Supports filtering by type (adr, runbook, convention, api-doc) and scope.
     */
    private fun handleSearchKnowledge(arguments: Map<String, Any?>): McpToolCallResponse {
        val query = (arguments["query"] as? String ?: "").lowercase()
        val typeFilter = arguments["type"] as? String
        val scopeFilter = arguments["scope"] as? String
        val workspaceId = arguments["workspaceId"] as? String
        val userId = arguments["userId"] as? String

        val results = mutableListOf<Map<String, String>>()

        // 1. Search DB-backed knowledge documents (with scope)
        val docType = typeFilter?.let {
            try { DocumentType.valueOf(it.uppercase().replace("-", "_")) } catch (_: Exception) { null }
        }
        val docScope = scopeFilter?.let {
            try { KnowledgeScope.valueOf(it.uppercase()) } catch (_: Exception) { null }
        }

        val dbResults = knowledgeIndexService.search(
            query = query,
            type = docType,
            scope = docScope,
            workspaceId = workspaceId,
            userId = userId
        )

        dbResults.forEach { doc ->
            results.add(mapOf(
                "title" to doc.title,
                "path" to "db://${doc.id}",
                "type" to doc.type.name.lowercase(),
                "scope" to doc.scope.toValue(),
                "excerpt" to doc.snippet.take(200)
            ))
        }

        // 2. Also search knowledge-base/ filesystem (always global scope)
        if (scopeFilter == null || scopeFilter.equals("global", ignoreCase = true)) {
            val knowledgeBaseDir = resolveKnowledgeBaseDir()
            if (knowledgeBaseDir.exists()) {
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

                        val queryTerms = query.split("\\s+".toRegex())
                        val matches = query.isBlank() || queryTerms.any { term ->
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
                                "scope" to "global",
                                "excerpt" to excerpt
                            ))
                        }
                    }
            }
        }

        if (results.isEmpty()) {
            return McpToolCallResponse(
                content = listOf(McpContent(
                    type = "text",
                    text = "No results found for query: '$query'" +
                        (if (typeFilter != null) " (type: $typeFilter)" else "") +
                        (if (scopeFilter != null) " (scope: $scopeFilter)" else "")
                ))
            )
        }

        val resultText = buildString {
            appendLine("Found ${results.size} result(s) for '$query':")
            appendLine()
            results.forEachIndexed { i, r ->
                appendLine("${i + 1}. **${r["title"]}** [${r["type"]}] (${r["scope"]})")
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

        if (path.contains("..")) {
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Error: path traversal not allowed")),
                isError = true
            )
        }

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

    // ---- Utility ----

    internal fun resolveKnowledgeBaseDir(): File {
        val relative = File("knowledge-base")
        if (relative.exists()) return relative

        val pluginsParent = File(pluginsBasePath).parentFile
        if (pluginsParent != null) {
            val candidate = pluginsParent.resolve("knowledge-base")
            if (candidate.exists()) return candidate
        }

        val dockerMount = File("/knowledge-base")
        if (dockerMount.exists()) return dockerMount

        return relative
    }
}
