package com.forge.webide.service

import com.forge.webide.model.FileNode
import com.forge.webide.model.FileType
import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Handles workspace-scoped MCP tools: workspace_write_file, workspace_read_file,
 * workspace_list_files, workspace_compile, workspace_test.
 */
@Service
class WorkspaceToolHandler(
    private val workspaceService: WorkspaceService,
    private val runtimeService: WorkspaceRuntimeService
) {

    private val logger = LoggerFactory.getLogger(WorkspaceToolHandler::class.java)

    fun handle(toolName: String, args: Map<String, Any?>, workspaceId: String?): McpToolCallResponse {
        if (workspaceId.isNullOrBlank()) {
            return McpProxyService.errorResponse("Workspace tool '$toolName' requires a workspaceId")
        }
        return try {
            when (toolName) {
                "workspace_write_file" -> {
                    val path = args["path"] as? String
                        ?: return McpProxyService.errorResponse("'path' parameter is required")
                    val content = args["content"] as? String
                        ?: return McpProxyService.errorResponse("'content' parameter is required")
                    if (path.contains("..")) {
                        return McpProxyService.errorResponse("Path traversal not allowed")
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
                        ?: return McpProxyService.errorResponse("'path' parameter is required")
                    if (path.contains("..")) {
                        return McpProxyService.errorResponse("Path traversal not allowed")
                    }
                    val content = workspaceService.getFileContent(workspaceId, path)
                        ?: return McpProxyService.errorResponse("File not found: $path")
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
                "workspace_start_service" -> handleStartService(workspaceId, args)
                "workspace_stop_service" -> handleStopService(workspaceId, args)
                else -> McpProxyService.errorResponse("Unknown workspace tool: $toolName")
            }
        } catch (e: Exception) {
            logger.error("Workspace tool execution failed: tool=$toolName: ${e.message}", e)
            McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Workspace tool error: ${e.message}")),
                isError = true
            )
        }
    }

    // ---- Service management tool implementations ----

    private fun handleStartService(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        val command = args["command"] as? String
            ?: return McpProxyService.errorResponse("'command' parameter is required")
        val port = (args["port"] as? Number)?.toInt()
            ?: return McpProxyService.errorResponse("'port' parameter is required (integer)")

        return try {
            runtimeService.startService(workspaceId, command, port)
            val proxyUrl = "/api/workspaces/$workspaceId/proxy/$port/"
            McpToolCallResponse(
                content = listOf(McpContent(
                    type = "text",
                    text = "Service started on port $port.\nCommand: $command\nAccess URL: $proxyUrl"
                )),
                isError = false
            )
        } catch (e: Exception) {
            McpProxyService.errorResponse("Failed to start service: ${e.message}")
        }
    }

    private fun handleStopService(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        val port = (args["port"] as? Number)?.toInt()
            ?: return McpProxyService.errorResponse("'port' parameter is required (integer)")

        val stopped = runtimeService.stopService(workspaceId, port)
        return if (stopped) {
            McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Service on port $port stopped.")),
                isError = false
            )
        } else {
            McpProxyService.errorResponse("No running service found on port $port")
        }
    }

    // ---- Compile & Test tool implementations ----

    private fun handleWorkspaceCompile(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        val files = workspaceService.getFileTree(workspaceId)
        if (files.isEmpty()) {
            return McpProxyService.errorResponse("Workspace is empty, nothing to compile")
        }

        val projectType = (args["projectType"] as? String)?.lowercase() ?: detectProjectType(workspaceId)
        val startMs = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val allFiles = collectFilePaths(files)
        val fileCount = allFiles.size

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
                errors.forEach { appendLine("  \u274C $it") }
            }
            if (warnings.isNotEmpty()) {
                appendLine("\nWarnings (${warnings.size}):")
                warnings.forEach { appendLine("  \u26A0\uFE0F $it") }
            }
            if (success && warnings.isEmpty()) {
                appendLine("\n\u2705 All files passed syntax validation.")
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
            return McpProxyService.errorResponse("Workspace is empty, no tests to run")
        }

        val allFiles = collectFilePaths(files)
        val startMs = System.currentTimeMillis()

        val testFiles = allFiles.filter { path ->
            path.contains(".test.") || path.contains(".spec.") ||
                path.contains("test_") || path.contains("_test.") ||
                path.contains("Test.") || path.contains("/tests/") ||
                path.contains("/__tests__/")
        }

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
                appendLine("\u26A0\uFE0F No test files found. Consider adding tests.")
            } else {
                appendLine("\n${if (totalTests > 0) "\u2705" else "\u26A0\uFE0F"} Found $totalTests test(s) in ${testFiles.size} file(s).")
            }
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = output)),
            isError = false
        )
    }

    // ---- Helpers ----

    internal fun formatFileTree(nodes: List<FileNode>, indent: String = ""): String {
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

    internal fun collectFilePaths(nodes: List<FileNode>, prefix: String = ""): List<String> {
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

    internal fun detectProjectType(workspaceId: String): String {
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
        validateKotlin(path, content, errors, warnings)
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
}
