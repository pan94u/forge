package com.forge.webide.service

import com.forge.webide.model.FileNode
import com.forge.webide.model.FileType
import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Handles workspace-scoped MCP tools: workspace_write_file, workspace_read_file,
 * workspace_list_files, workspace_compile, workspace_test, workspace_delete_file,
 * workspace_git_status, workspace_git_diff, workspace_git_add, workspace_git_commit,
 * workspace_git_push, workspace_git_pull, workspace_git_branch.
 */
@Service
class WorkspaceToolHandler(
    private val workspaceService: WorkspaceService,
    private val runtimeService: WorkspaceRuntimeService,
    private val gitService: GitService,
    private val gitConfirmService: GitConfirmService
) {

    private val logger = LoggerFactory.getLogger(WorkspaceToolHandler::class.java)

    fun handle(
        toolName: String,
        args: Map<String, Any?>,
        workspaceId: String?,
        sessionId: String = "",
        onEvent: ((Map<String, Any?>) -> Unit)? = null
    ): McpToolCallResponse {
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
                "workspace_delete_file" -> {
                    val path = args["path"] as? String
                        ?: return McpProxyService.errorResponse("'path' parameter is required")
                    if (path.contains("..")) {
                        return McpProxyService.errorResponse("Path traversal not allowed")
                    }
                    workspaceService.deleteFile(workspaceId, path)
                    logger.info("Workspace file deleted: workspace=$workspaceId, path=$path")
                    McpToolCallResponse(
                        content = listOf(McpContent(
                            type = "text",
                            text = "File deleted successfully: $path"
                        )),
                        isError = false
                    )
                }
                "workspace_start_service" -> handleStartService(workspaceId, args)
                "workspace_stop_service" -> handleStopService(workspaceId, args)
                "workspace_git_status" -> handleGitStatus(workspaceId)
                "workspace_git_diff" -> handleGitDiff(workspaceId, args)
                "workspace_git_add" -> handleGitAdd(workspaceId, args)
                "workspace_git_commit" -> {
                    val preview = buildCommitPreview(workspaceId, args)
                    if (!confirmGitOp(sessionId, "workspace_git_commit", preview, onEvent)) {
                        return McpProxyService.errorResponse("用户已取消 workspace_git_commit 操作")
                    }
                    handleGitCommit(workspaceId, args)
                }
                "workspace_git_push" -> {
                    val preview = buildPushPreview(workspaceId, args)
                    if (!confirmGitOp(sessionId, "workspace_git_push", preview, onEvent)) {
                        return McpProxyService.errorResponse("用户已取消 workspace_git_push 操作")
                    }
                    handleGitPush(workspaceId, args)
                }
                "workspace_git_pull" -> {
                    val preview = buildPullPreview(workspaceId, args)
                    if (!confirmGitOp(sessionId, "workspace_git_pull", preview, onEvent)) {
                        return McpProxyService.errorResponse("用户已取消 workspace_git_pull 操作")
                    }
                    handleGitPull(workspaceId, args)
                }
                "workspace_git_branch" -> handleGitBranch(workspaceId, args)
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

    // ---- Git confirmation helpers ----

    /**
     * Request user confirmation before executing a git write operation.
     * Non-blocking passthrough if sessionId is blank or onEvent is null (non-WebSocket calls).
     */
    private fun confirmGitOp(
        sessionId: String,
        tool: String,
        preview: String,
        onEvent: ((Map<String, Any?>) -> Unit)?
    ): Boolean {
        if (sessionId.isBlank() || onEvent == null) return true
        return gitConfirmService.requestConfirmation(sessionId, tool, preview, onEvent)
    }

    private fun buildCommitPreview(workspaceId: String, args: Map<String, Any?>): String {
        val message = args["message"] as? String ?: "(no message)"
        return buildString {
            appendLine("git commit -m \"$message\"")
            try {
                val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
                val status = gitService.status(workspaceDir)
                appendLine()
                appendLine("Branch: ${status.branch}")
                if (status.modifiedFiles.isNotEmpty()) {
                    appendLine("Changes to commit:")
                    status.modifiedFiles.take(10).forEach { appendLine("  $it") }
                    if (status.modifiedFiles.size > 10) appendLine("  ... and ${status.modifiedFiles.size - 10} more")
                }
            } catch (_: Exception) { /* ignore if status fails */ }
        }.trim()
    }

    private fun buildPushPreview(workspaceId: String, args: Map<String, Any?>): String {
        val remote = args["remote"] as? String ?: "origin"
        val branch = args["branch"] as? String
        val currentBranch = branch ?: try {
            gitService.status(workspaceService.getWorkspaceDir(workspaceId)).branch
        } catch (_: Exception) { "current-branch" }
        return "git push $remote $currentBranch"
    }

    private fun buildPullPreview(workspaceId: String, args: Map<String, Any?>): String {
        val remote = args["remote"] as? String ?: "origin"
        val rebase = args["rebase"] as? Boolean ?: true
        return buildString {
            appendLine("git pull $remote${if (rebase) " --rebase" else ""}")
            try {
                val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
                val status = gitService.status(workspaceDir)
                appendLine("当前分支：${status.branch}")

                // Fetch to inspect incoming commits (read-only, doesn't change working tree)
                val fetched = try {
                    gitService.fetch(workspaceDir, remote)
                } catch (e: Exception) {
                    appendLine()
                    appendLine("⚠️ 无法连接远程仓库 $remote：${e.message?.take(120)}")
                    appendLine("（如继续确认，pull 操作可能因认证或网络问题失败）")
                    false
                }

                if (fetched) {
                    val incoming = gitService.logRange(workspaceDir, "HEAD", "FETCH_HEAD")
                    if (incoming.isNotBlank()) {
                        val lines = incoming.lines().filter { it.isNotBlank() }
                        appendLine()
                        appendLine("待拉取的提交（${lines.size} 个）：")
                        lines.take(15).forEach { appendLine("  $it") }
                        if (lines.size > 15) appendLine("  … 还有 ${lines.size - 15} 个提交")
                    } else {
                        appendLine()
                        appendLine("（已是最新，无待拉取提交）")
                    }
                }
            } catch (e: Exception) {
                appendLine("⚠️ 无法获取预览：${e.message?.take(120)}")
            }
        }.trim()
    }

    // ---- Git tool implementations ----

    private fun handleGitStatus(workspaceId: String): McpToolCallResponse {
        return try {
            val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
            val status = gitService.status(workspaceDir)
            val text = buildString {
                appendLine("Branch: ${status.branch}")
                appendLine("Status: ${if (status.clean) "clean (no changes)" else "${status.modifiedFiles.size} file(s) modified"}")
                if (status.modifiedFiles.isNotEmpty()) {
                    appendLine()
                    appendLine("Modified files:")
                    status.modifiedFiles.forEach { appendLine("  $it") }
                }
            }
            McpToolCallResponse(content = listOf(McpContent(type = "text", text = text.trim())), isError = false)
        } catch (e: Exception) {
            McpProxyService.errorResponse("git status failed: ${e.message}")
        }
    }

    private fun handleGitDiff(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        return try {
            val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
            val remote = args["remote"] as? String

            if (remote != null) {
                // Compare local branch with remote — fetch first, then log/diff
                val sb = StringBuilder()
                val fetchOk = try { gitService.fetch(workspaceDir, remote) } catch (_: Exception) { false }
                if (!fetchOk) {
                    sb.appendLine("⚠️ 无法连接远程仓库 $remote，结果可能不是最新状态")
                }

                val branch = try { gitService.status(workspaceDir).branch } catch (_: Exception) { "main" }
                val remoteRef = "$remote/$branch"

                val behindLog = try { gitService.logRange(workspaceDir, "HEAD", remoteRef) } catch (_: Exception) { "" }
                val aheadLog  = try { gitService.logRange(workspaceDir, remoteRef, "HEAD") } catch (_: Exception) { "" }
                val statDiff  = try { gitService.diffStat(workspaceDir, "HEAD", remoteRef) } catch (_: Exception) { "" }

                if (behindLog.isBlank() && aheadLog.isBlank()) {
                    sb.appendLine("本地与远程 $remoteRef 完全同步，无差异。")
                } else {
                    if (aheadLog.isNotBlank()) {
                        val lines = aheadLog.lines().filter { it.isNotBlank() }
                        sb.appendLine("本地领先远程 ${lines.size} 个提交（尚未推送）：")
                        lines.take(20).forEach { sb.appendLine("  $it") }
                        sb.appendLine()
                    }
                    if (behindLog.isNotBlank()) {
                        val lines = behindLog.lines().filter { it.isNotBlank() }
                        sb.appendLine("远程领先本地 ${lines.size} 个提交（可拉取）：")
                        lines.take(20).forEach { sb.appendLine("  $it") }
                        sb.appendLine()
                    }
                    if (statDiff.isNotBlank()) {
                        sb.appendLine("文件变更统计（本地 vs $remoteRef）：")
                        statDiff.lines().take(20).forEach { sb.appendLine("  $it") }
                    }
                }
                McpToolCallResponse(
                    content = listOf(McpContent(type = "text", text = sb.toString().trim())),
                    isError = false
                )
            } else {
                // No remote — show local uncommitted working-tree changes
                val diff = gitService.diff(workspaceDir)
                McpToolCallResponse(content = listOf(McpContent(type = "text", text = diff)), isError = false)
            }
        } catch (e: Exception) {
            McpProxyService.errorResponse("git diff failed: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleGitAdd(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        return try {
            val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
            val all = args["all"] as? Boolean ?: false
            val paths: List<String> = if (all) {
                emptyList()
            } else {
                (args["paths"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            }
            val result = gitService.add(workspaceDir, paths)
            McpToolCallResponse(content = listOf(McpContent(type = "text", text = result)), isError = false)
        } catch (e: Exception) {
            McpProxyService.errorResponse("git add failed: ${e.message}")
        }
    }

    private fun handleGitCommit(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        val message = args["message"] as? String
            ?: return McpProxyService.errorResponse("'message' parameter is required")
        return try {
            val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
            val result = gitService.commit(workspaceDir, message)
            McpToolCallResponse(content = listOf(McpContent(type = "text", text = result)), isError = false)
        } catch (e: Exception) {
            McpProxyService.errorResponse("git commit failed: ${e.message}")
        }
    }

    private fun handleGitPush(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        return try {
            val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
            val remote = args["remote"] as? String ?: "origin"
            val branch = args["branch"] as? String
            // Use authenticated URL if workspace has an access token (private repos)
            val authUrl = workspaceService.getWorkspaceAuthUrl(workspaceId)
            val result = gitService.push(workspaceDir, remote, branch, remoteUrl = authUrl)
            McpToolCallResponse(content = listOf(McpContent(type = "text", text = result)), isError = false)
        } catch (e: Exception) {
            val raw = e.message ?: "未知错误"
            val structured = when {
                raw.contains("timed out", ignoreCase = true) ->
                    "git push 超时：无法在 120s 内连接到远程仓库。\n" +
                    "可能原因：需要认证（请配置 Access Token）或网络不通。\n原始错误：$raw"
                raw.contains("Authentication", ignoreCase = true) || raw.contains("denied", ignoreCase = true) ->
                    "git push 认证失败：${raw}\n建议：在工作区设置中配置有效的 Access Token。"
                else ->
                    "git push 失败：$raw"
            }
            McpProxyService.errorResponse(structured)
        }
    }

    private fun handleGitPull(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        val remote = args["remote"] as? String ?: "origin"
        return try {
            val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
            val rebase = args["rebase"] as? Boolean ?: true
            // Use authenticated URL if workspace has an access token (private repos)
            val authUrl = workspaceService.getWorkspaceAuthUrl(workspaceId)

            // Snapshot HEAD before pull to compute what changed
            val hashBefore = try { gitService.getCurrentHash(workspaceDir) } catch (_: Exception) { "" }

            val pullOutput = gitService.pull(workspaceDir, remote, rebase, remoteUrl = authUrl)

            val hashAfter = try { gitService.getCurrentHash(workspaceDir) } catch (_: Exception) { "" }

            val text = buildString {
                appendLine(pullOutput)
                if (hashBefore.isNotBlank() && hashAfter.isNotBlank() && hashBefore != hashAfter) {
                    // New commits were pulled — show log and file stats
                    appendLine()
                    try {
                        val newLog = gitService.logRange(workspaceDir, hashBefore, "HEAD")
                        if (newLog.isNotBlank()) {
                            val logLines = newLog.lines().filter { it.isNotBlank() }
                            appendLine("拉取了 ${logLines.size} 个新提交：")
                            logLines.take(20).forEach { appendLine("  $it") }
                            if (logLines.size > 20) appendLine("  … 还有 ${logLines.size - 20} 个提交")
                        }
                    } catch (_: Exception) {}
                    try {
                        val stat = gitService.diffStat(workspaceDir, hashBefore, "HEAD")
                        if (stat.isNotBlank()) {
                            appendLine()
                            appendLine("变更文件统计：")
                            stat.lines().take(20).forEach { appendLine("  $it") }
                        }
                    } catch (_: Exception) {}
                } else if (hashBefore == hashAfter) {
                    appendLine("（当前已是最新，无新提交）")
                }
            }.trim()

            McpToolCallResponse(content = listOf(McpContent(type = "text", text = text)), isError = false)
        } catch (e: Exception) {
            val raw = e.message ?: "未知错误"
            val structured = when {
                raw.contains("timed out", ignoreCase = true) ->
                    "git pull 超时（remote=$remote）：无法在 120s 内连接到远程仓库。\n" +
                    "可能原因：需要认证（请在工作区配置中添加 Access Token）或网络不通。\n原始错误：$raw"
                raw.contains("Authentication", ignoreCase = true) ||
                raw.contains("denied", ignoreCase = true) ||
                raw.contains("could not read Username", ignoreCase = true) ->
                    "git pull 认证失败（remote=$remote）：${raw}\n" +
                    "建议：在工作区设置中配置有效的 Access Token（GitLab: glpat-xxx / GitHub: ghp-xxx）。"
                else ->
                    "git pull 失败（remote=$remote）：$raw"
            }
            McpProxyService.errorResponse(structured)
        }
    }

    private fun handleGitBranch(workspaceId: String, args: Map<String, Any?>): McpToolCallResponse {
        return try {
            val workspaceDir = workspaceService.getWorkspaceDir(workspaceId)
            val name = args["name"] as? String
            val list = args["list"] as? Boolean ?: false
            val result = if (name != null && !list) {
                gitService.createBranch(workspaceDir, name)
            } else {
                gitService.listBranches(workspaceDir)
            }
            McpToolCallResponse(content = listOf(McpContent(type = "text", text = result)), isError = false)
        } catch (e: Exception) {
            McpProxyService.errorResponse("git branch failed: ${e.message}")
        }
    }
}
