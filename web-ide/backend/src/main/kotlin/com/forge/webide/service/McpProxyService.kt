package com.forge.webide.service

import com.forge.webide.entity.SkillUsageEntity
import com.forge.webide.model.FileNode
import com.forge.webide.model.FileType
import com.forge.webide.model.McpContent
import com.forge.webide.model.McpTool
import com.forge.webide.model.McpToolCallResponse
import com.forge.webide.repository.SkillUsageRepository
import com.forge.webide.service.skill.SkillCategory
import com.forge.webide.service.skill.SkillLoader
import com.forge.webide.service.skill.SkillScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
    private val dataSource: DataSource,
    private val workspaceService: WorkspaceService,
    private val skillLoader: SkillLoader,
    private val skillUsageRepository: SkillUsageRepository
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
        if (toolName.startsWith("workspace_") && resolvedWsId != null) {
            return handleWorkspaceTool(toolName, arguments, resolvedWsId)
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
            // Cache not fully populated yet — try each server
            for (serverUrl in servers) {
                val result = callToolOnServer(serverUrl, toolName, arguments)
                if (!result.isError) return result
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

    // ---- Workspace tool implementations ----

    private fun handleWorkspaceTool(
        toolName: String,
        args: Map<String, Any?>,
        workspaceId: String
    ): McpToolCallResponse {
        return try {
            when (toolName) {
                "workspace_write_file" -> {
                    val path = args["path"] as? String
                        ?: return errorResponse("'path' parameter is required")
                    val content = args["content"] as? String
                        ?: return errorResponse("'content' parameter is required")
                    if (path.contains("..")) {
                        return errorResponse("Path traversal not allowed")
                    }
                    workspaceService.createFile(workspaceId, path, content)
                    logger.info("Workspace file written: workspace=$workspaceId, path=$path, size=${content.length}")
                    McpToolCallResponse(
                        content = listOf(McpContent(
                            type = "text",
                            text = "File written successfully: $path (${content.length} chars)"
                        )),
                        isError = false
                    )
                }
                "workspace_read_file" -> {
                    val path = args["path"] as? String
                        ?: return errorResponse("'path' parameter is required")
                    if (path.contains("..")) {
                        return errorResponse("Path traversal not allowed")
                    }
                    val content = workspaceService.getFileContent(workspaceId, path)
                        ?: return errorResponse("File not found: $path")
                    McpToolCallResponse(
                        content = listOf(McpContent(type = "text", text = content)),
                        isError = false
                    )
                }
                "workspace_list_files" -> {
                    val tree = workspaceService.getFileTree(workspaceId)
                    val text = formatFileTree(tree)
                    McpToolCallResponse(
                        content = listOf(McpContent(type = "text", text = text)),
                        isError = false
                    )
                }
                "workspace_compile" -> handleWorkspaceCompile(workspaceId, args)
                "workspace_test" -> handleWorkspaceTest(workspaceId, args)
                else -> errorResponse("Unknown workspace tool: $toolName")
            }
        } catch (e: Exception) {
            logger.error("Workspace tool execution failed: tool=$toolName: ${e.message}", e)
            McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Workspace tool error: ${e.message}")),
                isError = true
            )
        }
    }

    private fun errorResponse(message: String): McpToolCallResponse {
        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = "Error: $message")),
            isError = true
        )
    }

    private fun trackSkillUsage(
        skillName: String,
        action: String,
        scriptType: String? = null,
        success: Boolean = true
    ) {
        try {
            skillUsageRepository.save(
                SkillUsageEntity(
                    sessionId = "",
                    skillName = skillName,
                    action = action,
                    scriptType = scriptType,
                    success = success
                )
            )
        } catch (e: Exception) {
            logger.debug("Failed to track skill usage: {}", e.message)
        }
    }

    private fun formatFileTree(nodes: List<FileNode>, indent: String = ""): String {
        if (nodes.isEmpty()) return "Workspace is empty."
        val sb = StringBuilder()
        sb.appendLine("Workspace files:")
        for (node in nodes.sortedWith(compareBy({ it.type.name }, { it.name }))) {
            if (node.type == FileType.DIRECTORY) {
                sb.appendLine("$indent${node.name}/")
                node.children?.let { children ->
                    sb.append(formatFileTree(children, "$indent  "))
                }
            } else {
                sb.appendLine("$indent${node.name}")
            }
        }
        return sb.toString()
    }

    // ---- Compile & Test tool implementations ----
    // Note: Workspace files are in-memory (Trial mode). These tools analyze
    // workspace file contents for syntax/structure validation rather than
    // running real build tools. When deployed with disk-backed workspaces,
    // these can be upgraded to real ProcessBuilder compilation.

    private fun handleWorkspaceCompile(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        val files = workspaceService.getFileTree(workspaceId)
        if (files.isEmpty()) {
            return errorResponse("Workspace is empty, nothing to compile")
        }

        val projectType = (args["projectType"] as? String)?.lowercase() ?: detectProjectType(workspaceId)
        val startMs = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var fileCount = 0

        // Collect all file paths from tree
        val allFiles = collectFilePaths(files)
        fileCount = allFiles.size

        for (filePath in allFiles) {
            val content = workspaceService.getFileContent(workspaceId, filePath) ?: continue
            val ext = filePath.substringAfterLast('.', "")

            when (ext) {
                "ts", "tsx", "js", "jsx" -> validateTypeScript(filePath, content, errors, warnings)
                "kt" -> validateKotlin(filePath, content, errors, warnings)
                "java" -> validateJava(filePath, content, errors, warnings)
                "py" -> validatePython(filePath, content, errors, warnings)
                "json" -> validateJson(filePath, content, errors)
            }
        }

        val durationMs = System.currentTimeMillis() - startMs
        val success = errors.isEmpty()
        val output = buildString {
            appendLine("Compilation ${if (success) "SUCCESS" else "FAILED"} ($projectType)")
            appendLine("Files analyzed: $fileCount")
            appendLine("Duration: ${durationMs}ms")
            if (errors.isNotEmpty()) {
                appendLine("\nErrors (${errors.size}):")
                errors.forEach { appendLine("  ❌ $it") }
            }
            if (warnings.isNotEmpty()) {
                appendLine("\nWarnings (${warnings.size}):")
                warnings.forEach { appendLine("  ⚠️ $it") }
            }
            if (success && warnings.isEmpty()) {
                appendLine("\n✅ All files passed syntax validation.")
            }
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = output)),
            isError = !success
        )
    }

    private fun handleWorkspaceTest(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        val files = workspaceService.getFileTree(workspaceId)
        if (files.isEmpty()) {
            return errorResponse("Workspace is empty, no tests to run")
        }

        val allFiles = collectFilePaths(files)
        val startMs = System.currentTimeMillis()

        // Identify test files
        val testFiles = allFiles.filter { path ->
            path.contains(".test.") || path.contains(".spec.") ||
                path.contains("test_") || path.contains("_test.") ||
                path.contains("Test.") || path.contains("/tests/") ||
                path.contains("/__tests__/")
        }

        // Analyze test file content for test function counts
        var totalTests = 0
        var totalAssertions = 0
        val testDetails = mutableListOf<String>()

        for (testFile in testFiles) {
            val content = workspaceService.getFileContent(workspaceId, testFile) ?: continue
            val ext = testFile.substringAfterLast('.', "")

            val (tests, assertions) = when (ext) {
                "ts", "tsx", "js", "jsx" -> countJsTests(content)
                "kt", "java" -> countJvmTests(content)
                "py" -> countPyTests(content)
                else -> 0 to 0
            }

            totalTests += tests
            totalAssertions += assertions
            if (tests > 0) {
                testDetails.add("  $testFile: $tests test(s), $assertions assertion(s)")
            }
        }

        // Also check for test coverage in source files (functions without tests)
        val sourceFiles = allFiles.filter { path ->
            !testFiles.contains(path) && (
                path.endsWith(".ts") || path.endsWith(".kt") || path.endsWith(".py") ||
                path.endsWith(".java") || path.endsWith(".js")
            )
        }
        var totalFunctions = 0
        for (srcFile in sourceFiles) {
            val content = workspaceService.getFileContent(workspaceId, srcFile) ?: continue
            totalFunctions += countFunctions(content, srcFile.substringAfterLast('.', ""))
        }

        val durationMs = System.currentTimeMillis() - startMs
        val coverageEstimate = if (totalFunctions > 0) {
            ((totalTests.toDouble() / totalFunctions) * 100).coerceAtMost(100.0)
        } else 0.0

        val output = buildString {
            appendLine("Test Analysis Report")
            appendLine("Duration: ${durationMs}ms")
            appendLine()
            appendLine("Test files: ${testFiles.size}")
            appendLine("Test functions: $totalTests")
            appendLine("Assertions: $totalAssertions")
            appendLine("Source functions: $totalFunctions")
            appendLine("Estimated coverage: ${"%.1f".format(coverageEstimate)}%")
            appendLine()
            if (testDetails.isNotEmpty()) {
                appendLine("Test details:")
                testDetails.forEach { appendLine(it) }
            }
            if (testFiles.isEmpty()) {
                appendLine("⚠️ No test files found. Consider adding tests.")
            } else {
                appendLine("\n${if (totalTests > 0) "✅" else "⚠️"} Found $totalTests test(s) in ${testFiles.size} file(s).")
            }
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = output)),
            isError = false
        )
    }

    private fun collectFilePaths(nodes: List<FileNode>, prefix: String = ""): List<String> {
        val paths = mutableListOf<String>()
        for (node in nodes) {
            val path = if (prefix.isEmpty()) node.name else "$prefix/${node.name}"
            if (node.type == FileType.FILE) {
                paths.add(node.path.ifBlank { path })
            }
            node.children?.let { paths.addAll(collectFilePaths(it, path)) }
        }
        return paths
    }

    private fun detectProjectType(workspaceId: String): String {
        val files = collectFilePaths(workspaceService.getFileTree(workspaceId))
        return when {
            files.any { it.endsWith("package.json") || it.endsWith("tsconfig.json") } -> "typescript"
            files.any { it.endsWith(".gradle.kts") || it.endsWith(".gradle") } -> "kotlin"
            files.any { it.endsWith(".py") } -> "python"
            else -> "unknown"
        }
    }

    // Syntax validation helpers
    private fun validateTypeScript(path: String, content: String, errors: MutableList<String>, warnings: MutableList<String>) {
        var braces = 0; var parens = 0; var brackets = 0
        for (ch in content) {
            when (ch) { '{' -> braces++; '}' -> braces--; '(' -> parens++; ')' -> parens--; '[' -> brackets++; ']' -> brackets-- }
        }
        if (braces != 0) errors.add("$path: Unmatched braces (balance: $braces)")
        if (parens != 0) errors.add("$path: Unmatched parentheses (balance: $parens)")
        if (brackets != 0) errors.add("$path: Unmatched brackets (balance: $brackets)")
        if (content.contains("console.log") && !path.contains("test")) warnings.add("$path: Contains console.log")
    }

    private fun validateKotlin(path: String, content: String, errors: MutableList<String>, warnings: MutableList<String>) {
        var braces = 0
        for (ch in content) { when (ch) { '{' -> braces++; '}' -> braces-- } }
        if (braces != 0) errors.add("$path: Unmatched braces (balance: $braces)")
        if (!content.contains("package ")) warnings.add("$path: Missing package declaration")
    }

    private fun validateJava(path: String, content: String, errors: MutableList<String>, warnings: MutableList<String>) {
        validateKotlin(path, content, errors, warnings) // Same brace check
        if (!content.contains("class ") && !content.contains("interface ") && !content.contains("enum ")) {
            warnings.add("$path: No class/interface/enum found")
        }
    }

    private fun validatePython(path: String, content: String, errors: MutableList<String>, warnings: MutableList<String>) {
        val lines = content.lines()
        for ((i, line) in lines.withIndex()) {
            if (line.contains("\t") && lines.any { it.startsWith("    ") }) {
                warnings.add("$path:${i + 1}: Mixed tabs and spaces")
                break
            }
        }
    }

    private fun validateJson(path: String, content: String, errors: MutableList<String>) {
        try {
            com.google.gson.JsonParser.parseString(content)
        } catch (e: Exception) {
            errors.add("$path: Invalid JSON — ${e.message?.take(80)}")
        }
    }

    // Test counting helpers
    private fun countJsTests(content: String): Pair<Int, Int> {
        val testPattern = Regex("""(it|test|describe)\s*\(""")
        val assertPattern = Regex("""(expect|assert|should)\s*[\.(]""")
        return testPattern.findAll(content).count() to assertPattern.findAll(content).count()
    }

    private fun countJvmTests(content: String): Pair<Int, Int> {
        val testPattern = Regex("""@Test|fun\s+test""")
        val assertPattern = Regex("""assert|assertEquals|assertThat|verify""")
        return testPattern.findAll(content).count() to assertPattern.findAll(content).count()
    }

    private fun countPyTests(content: String): Pair<Int, Int> {
        val testPattern = Regex("""def\s+test_""")
        val assertPattern = Regex("""assert\s|assertEqual|assertTrue""")
        return testPattern.findAll(content).count() to assertPattern.findAll(content).count()
    }

    private fun countFunctions(content: String, ext: String): Int {
        val pattern = when (ext) {
            "ts", "tsx", "js", "jsx" -> Regex("""(function\s+\w+|(?:async\s+)?(?:const|let|var)\s+\w+\s*=\s*(?:async\s*)?\(|(?:async\s+)?\w+\s*\([^)]*\)\s*\{)""")
            "kt" -> Regex("""fun\s+\w+""")
            "java" -> Regex("""(public|private|protected|static|\s)+[\w<>\[\]]+\s+\w+\s*\(""")
            "py" -> Regex("""def\s+\w+""")
            else -> return 0
        }
        return pattern.findAll(content).count()
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
                "read_skill" -> handleReadSkill(arguments)
                "run_skill_script" -> handleRunSkillScript(arguments)
                "list_skills" -> handleListSkills(arguments)
                else -> McpToolCallResponse(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Unknown tool: $toolName. Available tools: search_knowledge, read_file, get_service_info, run_baseline, query_schema, list_baselines, read_skill, run_skill_script, list_skills"
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

        // Empty query = list all documents (used by Context Picker default view)

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

    // ---- Skill progressive loading tools (Phase 4) ----

    /**
     * Read a skill's SKILL.md or sub-file content (Level 2 + Level 3).
     */
    private fun handleReadSkill(arguments: Map<String, Any?>): McpToolCallResponse {
        val skillName = arguments["skill_name"] as? String
            ?: return errorResponse("'skill_name' parameter is required")

        val file = arguments["file"] as? String ?: "SKILL.md"

        // Security: prevent path traversal
        if (file.contains("..")) {
            return errorResponse("Path traversal not allowed")
        }

        val skill = skillLoader.loadSkill(skillName)
            ?: return errorResponse("Skill '$skillName' not found. Use list_skills to see available skills.")

        // If requesting SKILL.md, return the cached content directly
        if (file == "SKILL.md") {
            trackSkillUsage(skillName, "READ")
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = skill.content)),
                isError = false
            )
        }

        // For sub-files, resolve from the skill's source directory
        val skillDir = Path.of(skill.sourcePath).parent
        val targetFile = skillDir.resolve(file)

        // Security: ensure resolved path is within skill directory
        val normalizedTarget = targetFile.normalize()
        val normalizedSkillDir = skillDir.normalize()
        if (!normalizedTarget.startsWith(normalizedSkillDir)) {
            return errorResponse("Path traversal not allowed: file must be within skill directory")
        }

        if (!Files.isRegularFile(targetFile)) {
            val availableFiles = skill.subFiles.map { it.path } + skill.scripts.map { it.path }
            return errorResponse(
                "File '$file' not found in skill '$skillName'. " +
                    "Available files: ${availableFiles.joinToString(", ").ifEmpty { "none" }}"
            )
        }

        val content = try {
            val raw = Files.readString(targetFile)
            if (raw.length > 50_000) {
                raw.take(50_000) + "\n\n[... truncated at 50KB ...]"
            } else {
                raw
            }
        } catch (e: Exception) {
            return errorResponse("Failed to read file: ${e.message}")
        }

        trackSkillUsage(skillName, "READ")
        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = content)),
            isError = false
        )
    }

    /**
     * Execute a skill's script and return stdout + exitCode (Level 3).
     * Script code does NOT enter context — only the output does.
     */
    private fun handleRunSkillScript(arguments: Map<String, Any?>): McpToolCallResponse {
        val skillName = arguments["skill_name"] as? String
            ?: return errorResponse("'skill_name' parameter is required")

        val scriptPath = arguments["script"] as? String
            ?: return errorResponse("'script' parameter is required (e.g. 'scripts/validate.py')")

        // Security: prevent path traversal
        if (scriptPath.contains("..")) {
            return errorResponse("Path traversal not allowed")
        }

        val skill = skillLoader.loadSkill(skillName)
            ?: return errorResponse("Skill '$skillName' not found")

        // Verify script exists in skill definition
        val scriptDef = skill.scripts.find { it.path == scriptPath }
            ?: return errorResponse(
                "Script '$scriptPath' not found in skill '$skillName'. " +
                    "Available scripts: ${skill.scripts.joinToString(", ") { it.path }.ifEmpty { "none" }}"
            )

        val skillDir = Path.of(skill.sourcePath).parent
        val scriptFile = skillDir.resolve(scriptPath)

        // Security: ensure resolved path is within skill directory
        val normalizedScript = scriptFile.normalize()
        val normalizedSkillDir = skillDir.normalize()
        if (!normalizedScript.startsWith(normalizedSkillDir)) {
            return errorResponse("Path traversal not allowed")
        }

        if (!Files.isRegularFile(scriptFile)) {
            return errorResponse("Script file not found: $scriptPath")
        }

        // Build command based on language
        val command = when (scriptDef.language) {
            "python" -> listOf("python3", scriptFile.toString())
            "bash" -> listOf("bash", scriptFile.toString())
            "kotlin" -> listOf("kotlin", scriptFile.toString())
            else -> return errorResponse("Unsupported script language: ${scriptDef.language}")
        }

        // Add optional args
        @Suppress("UNCHECKED_CAST")
        val args = when (val argsVal = arguments["args"]) {
            is List<*> -> argsVal.filterIsInstance<String>()
            is String -> argsVal.split(" ")
            else -> emptyList()
        }

        val fullCommand = command + args

        return try {
            logger.info("Executing skill script: {} (skill={})", scriptPath, skillName)
            val process = ProcessBuilder(fullCommand)
                .directory(skillDir.toFile())
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(60, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return errorResponse("Script execution timed out after 60 seconds")
            }

            val stdout = process.inputStream.bufferedReader().readText().take(50_000)
            val stderr = process.errorStream.bufferedReader().readText().take(10_000)
            val exitCode = process.exitValue()

            val output = buildString {
                appendLine("Script: $scriptPath")
                appendLine("Exit code: $exitCode")
                if (stdout.isNotBlank()) {
                    appendLine()
                    appendLine("Output:")
                    appendLine(stdout)
                }
                if (stderr.isNotBlank()) {
                    appendLine()
                    appendLine("Errors:")
                    appendLine(stderr)
                }
            }

            trackSkillUsage(skillName, "SCRIPT_RUN", scriptDef.scriptType.name, exitCode == 0)
            McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = output)),
                isError = exitCode != 0
            )
        } catch (e: Exception) {
            trackSkillUsage(skillName, "SCRIPT_RUN", scriptDef.scriptType.name, false)
            logger.error("Script execution failed: skill={}, script={}: {}", skillName, scriptPath, e.message)
            errorResponse("Script execution failed: ${e.message}")
        }
    }

    /**
     * List all available skills with metadata (Level 1 query).
     */
    private fun handleListSkills(arguments: Map<String, Any?>): McpToolCallResponse {
        val profileFilter = arguments["profile"] as? String
        val categoryFilter = arguments["category"] as? String
        val scopeFilter = arguments["scope"] as? String

        val category = if (!categoryFilter.isNullOrBlank()) {
            try {
                SkillCategory.valueOf(categoryFilter.uppercase())
            } catch (e: IllegalArgumentException) {
                return errorResponse("Invalid category: $categoryFilter. Valid: ${SkillCategory.entries.joinToString()}")
            }
        } else null

        val scope = if (!scopeFilter.isNullOrBlank()) {
            try {
                SkillScope.valueOf(scopeFilter.uppercase())
            } catch (e: IllegalArgumentException) {
                return errorResponse("Invalid scope: $scopeFilter. Valid: ${SkillScope.entries.joinToString()}")
            }
        } else null

        val skills = skillLoader.loadSkillMetadataCatalog(profileFilter, category, scope)

        val output = buildString {
            appendLine("Available Skills (${skills.size}):")
            appendLine()
            for (skill in skills) {
                appendLine("- **${skill.name}** [${skill.scope.name.lowercase()}/${skill.category.name.lowercase()}]")
                appendLine("  ${skill.description}")
                if (skill.tags.isNotEmpty()) {
                    appendLine("  Tags: ${skill.tags.joinToString(", ")}")
                }
                if (skill.subFiles.isNotEmpty()) {
                    appendLine("  Sub-files: ${skill.subFiles.joinToString(", ") { it.path }}")
                }
                if (skill.scripts.isNotEmpty()) {
                    appendLine("  Scripts: ${skill.scripts.joinToString(", ") { "${it.path} (${it.scriptType.name.lowercase()}/${it.language})" }}")
                }
                appendLine("  Version: ${skill.version} | Scope: ${skill.scope.name.lowercase()} | Enabled: ${skill.enabled}")
                appendLine()
            }
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = output)),
            isError = false
        )
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
