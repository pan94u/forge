package com.forge.webide.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import java.time.Instant

@RestController
class BuildInfoController {

    private val startTime = Instant.now()
    private val gitCommit = System.getenv("GIT_COMMIT") ?: readGitCommit()
    private val gitBranch = System.getenv("GIT_BRANCH") ?: readGitBranch()
    private val buildTime = System.getenv("BUILD_TIME") ?: Instant.now().toString()

    @GetMapping("/api/health")
    fun health(): Map<String, Any> {
        val uptimeMs = ManagementFactory.getRuntimeMXBean().uptime
        val uptimeStr = formatUptime(uptimeMs)
        return mapOf(
            "status" to "UP",
            "build" to mapOf(
                "commit" to gitCommit,
                "branch" to gitBranch,
                "builtAt" to buildTime
            ),
            "uptime" to uptimeStr,
            "uptimeMs" to uptimeMs,
            "timestamp" to Instant.now().toString()
        )
    }

    private fun readGitCommit(): String {
        return try {
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .redirectErrorStream(true).start()
                .inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "unknown" }
    }

    private fun readGitBranch(): String {
        return try {
            ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .redirectErrorStream(true).start()
                .inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "unknown" }
    }

    private fun formatUptime(ms: Long): String {
        val seconds = ms / 1000
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
            append("${secs}s")
        }
    }
}
