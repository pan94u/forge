package com.forge.webide.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class GitStatus(
    val branch: String,
    val clean: Boolean,
    val modifiedFiles: List<String> = emptyList()
)

@Service
class GitService {

    private val logger = LoggerFactory.getLogger(GitService::class.java)

    fun cloneRepository(url: String, branch: String?, targetDir: Path): Path {
        val cmd = mutableListOf("git", "clone", "--depth", "1")
        if (!branch.isNullOrBlank()) {
            cmd.addAll(listOf("-b", branch))
        }
        cmd.addAll(listOf(url, targetDir.toString()))

        logger.info("Cloning repository: {} -> {}", url, targetDir)
        val result = runGitCommand(cmd, targetDir.parent)

        if (result.exitCode != 0) {
            throw GitOperationException("git clone failed (exit=${result.exitCode}): ${result.stderr}")
        }

        logger.info("Repository cloned successfully: {}", url)
        return targetDir
    }

    fun pull(workspaceDir: Path): String {
        return pull(workspaceDir, rebase = false)
    }

    fun status(workspaceDir: Path): GitStatus {
        val branchResult = runGitCommand(listOf("git", "rev-parse", "--abbrev-ref", "HEAD"), workspaceDir)
        val branch = branchResult.stdout.trim().ifBlank { "unknown" }

        val statusResult = runGitCommand(listOf("git", "status", "--porcelain"), workspaceDir)
        val modifiedFiles = statusResult.stdout.lines()
            .filter { it.isNotBlank() }
            .map { it.substring(3).trim() }

        return GitStatus(
            branch = branch,
            clean = modifiedFiles.isEmpty(),
            modifiedFiles = modifiedFiles
        )
    }

    fun diff(workspaceDir: Path): String {
        val result = runGitCommand(listOf("git", "diff", "HEAD"), workspaceDir)
        return result.stdout.ifBlank { "(no changes)" }
    }

    fun add(workspaceDir: Path, paths: List<String>): String {
        val cmd = if (paths.isEmpty()) {
            listOf("git", "add", "-A")
        } else {
            listOf("git", "add") + paths
        }
        val result = runGitCommand(cmd, workspaceDir)
        if (result.exitCode != 0) {
            throw GitOperationException("git add failed: ${result.stderr}")
        }
        return "Staged: ${if (paths.isEmpty()) "all changes" else paths.joinToString(", ")}"
    }

    fun commit(workspaceDir: Path, message: String): String {
        val taggedMessage = if (message.contains("[Forge-Agent]")) message else "$message [Forge-Agent]"
        val result = runGitCommand(listOf("git", "commit", "-m", taggedMessage), workspaceDir)
        if (result.exitCode != 0) {
            throw GitOperationException("git commit failed: ${result.stderr}")
        }
        return result.stdout.trim()
    }

    fun push(workspaceDir: Path, remote: String = "origin", branch: String? = null, remoteUrl: String? = null): String {
        val currentBranch = branch ?: runGitCommand(
            listOf("git", "rev-parse", "--abbrev-ref", "HEAD"), workspaceDir
        ).stdout.trim()

        // Safety: warn instead of pushing directly to main/master
        if (currentBranch == "main" || currentBranch == "master") {
            return "⚠️ 安全警告：当前在 $currentBranch 分支上。直接推送到 $currentBranch 存在风险。\n" +
                "建议：先创建 feature branch，通过 PR 合并到 $currentBranch。\n" +
                "如确需推送，请手动执行 git push $remote $currentBranch。"
        }

        // Use authenticated remoteUrl if provided (for private repos), otherwise use remote alias
        val remoteSrc = remoteUrl ?: remote
        val cmd = listOf("git", "push", remoteSrc, currentBranch)
        val result = runGitCommand(cmd, workspaceDir)
        if (result.exitCode != 0) {
            throw GitOperationException("git push failed: ${result.stderr}")
        }
        return result.stdout.ifBlank { "Pushed $currentBranch to $remote" }
    }

    fun pull(workspaceDir: Path, remote: String = "origin", rebase: Boolean = true, remoteUrl: String? = null): String {
        // Use authenticated remoteUrl if provided (for private repos), otherwise use remote alias
        val remoteSrc = remoteUrl ?: remote
        val cmd = if (rebase) {
            listOf("git", "pull", "--rebase", remoteSrc)
        } else {
            listOf("git", "pull", remoteSrc)
        }
        val result = runGitCommand(cmd, workspaceDir)
        if (result.exitCode != 0) {
            throw GitOperationException("git pull failed: ${result.stderr}")
        }
        return result.stdout.trim().ifBlank { "Already up to date." }
    }

    fun createBranch(workspaceDir: Path, name: String): String {
        val result = runGitCommand(listOf("git", "checkout", "-b", name), workspaceDir)
        if (result.exitCode != 0) {
            throw GitOperationException("git checkout -b failed: ${result.stderr}")
        }
        return "Created and switched to branch: $name"
    }

    fun listBranches(workspaceDir: Path): String {
        val result = runGitCommand(listOf("git", "branch", "-a"), workspaceDir)
        return result.stdout.trim().ifBlank { "(no branches)" }
    }

    fun log(workspaceDir: Path, limit: Int = 10): String {
        val result = runGitCommand(
            listOf("git", "log", "--oneline", "-$limit"),
            workspaceDir
        )
        return result.stdout.trim().ifBlank { "(no commits)" }
    }

    private fun runGitCommand(cmd: List<String>, workDir: Path): GitResult {
        val process = ProcessBuilder(cmd)
            .directory(workDir.toFile())
            .redirectErrorStream(false)
            .start()

        val finished = process.waitFor(120, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw GitOperationException("Git command timed out after 120s: ${cmd.joinToString(" ")}")
        }

        return GitResult(
            exitCode = process.exitValue(),
            stdout = process.inputStream.bufferedReader().readText(),
            stderr = process.errorStream.bufferedReader().readText()
        )
    }

    private data class GitResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )
}

class GitOperationException(message: String) : RuntimeException(message)
