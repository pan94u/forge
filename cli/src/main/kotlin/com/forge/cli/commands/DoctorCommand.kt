package com.forge.cli.commands

import com.forge.cli.ForgeCli
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@Command(
    name = "doctor",
    description = ["Check environment health: Claude Code, MCP servers, plugins, and dependencies."],
    mixinStandardHelpOptions = true
)
class DoctorCommand : Callable<Int> {

    @ParentCommand
    private lateinit var parent: ForgeCli

    private data class CheckResult(
        val name: String,
        val status: Status,
        val message: String,
        val fix: String? = null
    )

    private enum class Status(val symbol: String) {
        OK("[OK]"),
        WARN("[WARN]"),
        FAIL("[FAIL]")
    }

    override fun call(): Int {
        println("Forge Doctor - Environment Health Check")
        println("=======================================")
        println()

        val results = mutableListOf<CheckResult>()

        results.add(checkClaudeCode())
        results.add(checkJavaVersion())
        results.add(checkNodeVersion())
        results.add(checkGradleWrapper())
        results.add(checkClaudeMd())
        results.add(checkMcpJson())
        results.add(checkForgeConfig())
        results.addAll(checkMcpServers())
        results.addAll(checkPlugins())
        results.add(checkDockerAvailable())

        println()
        for (result in results) {
            val statusStr = result.status.symbol.padEnd(8)
            println("$statusStr ${result.name}: ${result.message}")
            if (result.fix != null && result.status != Status.OK) {
                println("         Fix: ${result.fix}")
            }
        }

        println()
        val okCount = results.count { it.status == Status.OK }
        val warnCount = results.count { it.status == Status.WARN }
        val failCount = results.count { it.status == Status.FAIL }
        println("Summary: $okCount passed, $warnCount warnings, $failCount failures")

        return if (failCount > 0) 1 else 0
    }

    private fun checkClaudeCode(): CheckResult {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (completed && process.exitValue() == 0) {
                val version = process.inputStream.bufferedReader().readText().trim()
                CheckResult("Claude Code", Status.OK, "Installed ($version)")
            } else {
                CheckResult(
                    "Claude Code", Status.FAIL, "Not found or not responding",
                    "Install Claude Code: https://docs.anthropic.com/en/docs/claude-code"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                "Claude Code", Status.FAIL, "Not found on PATH",
                "Install Claude Code: https://docs.anthropic.com/en/docs/claude-code"
            )
        }
    }

    private fun checkJavaVersion(): CheckResult {
        return try {
            val process = ProcessBuilder("java", "-version")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (completed && process.exitValue() == 0) {
                val output = process.inputStream.bufferedReader().readText().trim()
                val versionLine = output.lines().firstOrNull() ?: "unknown"
                val versionMatch = Regex("""(\d+)""").find(versionLine)
                val majorVersion = versionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (majorVersion >= 21) {
                    CheckResult("Java", Status.OK, "Version $majorVersion detected (21+ required)")
                } else {
                    CheckResult(
                        "Java", Status.WARN, "Version $majorVersion detected (21+ recommended)",
                        "Install JDK 21+: https://adoptium.net/"
                    )
                }
            } else {
                CheckResult(
                    "Java", Status.FAIL, "Not found",
                    "Install JDK 21+: https://adoptium.net/"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                "Java", Status.FAIL, "Not found on PATH",
                "Install JDK 21+: https://adoptium.net/"
            )
        }
    }

    private fun checkNodeVersion(): CheckResult {
        return try {
            val process = ProcessBuilder("node", "--version")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (completed && process.exitValue() == 0) {
                val version = process.inputStream.bufferedReader().readText().trim()
                CheckResult("Node.js", Status.OK, "Version $version detected")
            } else {
                CheckResult(
                    "Node.js", Status.WARN, "Not found (needed for Web IDE frontend)",
                    "Install Node.js 20+: https://nodejs.org/"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                "Node.js", Status.WARN, "Not found on PATH",
                "Install Node.js 20+: https://nodejs.org/"
            )
        }
    }

    private fun checkGradleWrapper(): CheckResult {
        val gradlew = File("gradlew")
        return if (gradlew.exists() && gradlew.canExecute()) {
            CheckResult("Gradle Wrapper", Status.OK, "Found and executable")
        } else if (gradlew.exists()) {
            CheckResult(
                "Gradle Wrapper", Status.WARN, "Found but not executable",
                "Run: chmod +x gradlew"
            )
        } else {
            CheckResult(
                "Gradle Wrapper", Status.WARN, "Not found in current directory",
                "Run: gradle wrapper --gradle-version 8.10"
            )
        }
    }

    private fun checkClaudeMd(): CheckResult {
        val claudeMd = File(ForgeCli.CLAUDE_MD)
        return if (claudeMd.exists()) {
            val content = claudeMd.readText()
            val hasProjectOverview = content.contains("## Project Overview") || content.contains("## Architecture")
            val hasBuildRun = content.contains("## Build") || content.contains("## Quick Start")
            if (hasProjectOverview && hasBuildRun) {
                CheckResult("CLAUDE.md", Status.OK, "Found with required sections")
            } else {
                CheckResult(
                    "CLAUDE.md", Status.WARN, "Found but may be missing required sections",
                    "Ensure CLAUDE.md has Project Overview and Build/Run sections"
                )
            }
        } else {
            CheckResult(
                "CLAUDE.md", Status.FAIL, "Not found",
                "Run: forge init"
            )
        }
    }

    private fun checkMcpJson(): CheckResult {
        val mcpJson = File(ForgeCli.MCP_JSON)
        return if (mcpJson.exists()) {
            CheckResult("MCP Config", Status.OK, ".mcp.json found")
        } else {
            CheckResult(
                "MCP Config", Status.WARN, ".mcp.json not found",
                "Run: forge init"
            )
        }
    }

    private fun checkForgeConfig(): CheckResult {
        val forgeConfig = File(ForgeCli.CONFIG_FILE)
        return if (forgeConfig.exists()) {
            CheckResult("Forge Config", Status.OK, ".forge.json found")
        } else {
            CheckResult(
                "Forge Config", Status.WARN, ".forge.json not found",
                "Run: forge init"
            )
        }
    }

    private fun checkMcpServers(): List<CheckResult> {
        val servers = listOf(
            "forge-knowledge-mcp",
            "forge-database-mcp",
            "forge-service-graph-mcp",
            "forge-artifact-mcp",
            "forge-observability-mcp"
        )

        return servers.map { server ->
            val serverDir = File("mcp-servers/$server")
            val buildFile = File(serverDir, "build.gradle.kts")
            if (serverDir.exists() && buildFile.exists()) {
                val jarDir = File(serverDir, "build/libs")
                if (jarDir.exists() && jarDir.listFiles()?.any { it.name.endsWith(".jar") } == true) {
                    CheckResult("MCP: $server", Status.OK, "Source and JAR found")
                } else {
                    CheckResult(
                        "MCP: $server", Status.WARN, "Source found but not built",
                        "Run: ./gradlew :mcp-servers:$server:build"
                    )
                }
            } else {
                CheckResult(
                    "MCP: $server", Status.FAIL, "Server directory or build file not found"
                )
            }
        }
    }

    private fun checkPlugins(): List<CheckResult> {
        val plugins = listOf("forge-foundation", "forge-superagent")

        return plugins.map { plugin ->
            val pluginDir = File("plugins/$plugin")
            if (pluginDir.exists()) {
                CheckResult("Plugin: $plugin", Status.OK, "Found")
            } else {
                CheckResult(
                    "Plugin: $plugin", Status.WARN, "Not found in plugins/ directory"
                )
            }
        }
    }

    private fun checkDockerAvailable(): CheckResult {
        return try {
            val process = ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (completed && process.exitValue() == 0) {
                CheckResult("Docker", Status.OK, "Running and accessible")
            } else {
                CheckResult(
                    "Docker", Status.WARN, "Installed but daemon may not be running",
                    "Start Docker Desktop or run: sudo systemctl start docker"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                "Docker", Status.WARN, "Not found (needed for local MCP server orchestration)",
                "Install Docker: https://docs.docker.com/get-docker/"
            )
        }
    }
}
