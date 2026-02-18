package com.forge.webide.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Service that executes baseline quality gate scripts.
 *
 * Wraps the BaselineRunner logic as a Spring-managed service so it can be
 * invoked by McpProxyService as the `run_baseline` MCP tool.
 *
 * Baselines are shell scripts in the plugins/forge-superagent/baselines/ directory.
 * Each script exits with 0 (pass), 1 (fail), or other (error).
 */
@Service
class BaselineService(
    @Value("\${forge.plugins.base-path:plugins}") private val pluginsBasePath: String,
    @Value("\${forge.session.working-directory:/workspace}") private val workingDirectory: String
) {
    private val logger = LoggerFactory.getLogger(BaselineService::class.java)

    companion object {
        private const val TIMEOUT_SECONDS = 300L // 5 minutes per baseline
        private const val BASELINES_SUBPATH = "forge-superagent/baselines"
    }

    data class BaselineResult(
        val name: String,
        val status: String, // PASS, FAIL, ERROR, SKIPPED
        val durationMs: Long,
        val output: String,
        val errorOutput: String,
        val exitCode: Int
    )

    data class BaselineReport(
        val results: List<BaselineResult>,
        val totalDurationMs: Long,
        val timestamp: String,
        val allPassed: Boolean,
        val summary: String
    )

    /**
     * Run specified baselines and return a structured report.
     *
     * @param baselineNames list of baseline names (without .sh extension),
     *                      or empty/null to run all available baselines
     * @param projectRoot override project root directory (defaults to working-directory)
     */
    fun runBaselines(
        baselineNames: List<String>? = null,
        projectRoot: String? = null
    ): BaselineReport {
        val startTime = Instant.now()
        val baselinesDir = resolveBaselinesDir()

        if (baselinesDir == null || !baselinesDir.exists()) {
            logger.warn("Baselines directory not found: {}", baselinesDir?.absolutePath)
            return BaselineReport(
                results = emptyList(),
                totalDurationMs = 0,
                timestamp = startTime.toString(),
                allPassed = true,
                summary = "No baselines directory found at ${baselinesDir?.absolutePath ?: "unknown"}"
            )
        }

        val names = if (baselineNames.isNullOrEmpty()) {
            // Discover all .sh files in the baselines directory
            baselinesDir.listFiles { f -> f.extension == "sh" }
                ?.map { it.nameWithoutExtension }
                ?.sorted()
                ?: emptyList()
        } else {
            baselineNames
        }

        if (names.isEmpty()) {
            return BaselineReport(
                results = emptyList(),
                totalDurationMs = 0,
                timestamp = startTime.toString(),
                allPassed = true,
                summary = "No baseline scripts found"
            )
        }

        logger.info("Running {} baseline(s): {}", names.size, names)

        val effectiveRoot = projectRoot ?: workingDirectory
        val results = names.map { name -> runSingleBaseline(name, baselinesDir, effectiveRoot) }
        val totalDuration = Duration.between(startTime, Instant.now()).toMillis()

        val passedCount = results.count { it.status == "PASS" }
        val failedCount = results.count { it.status == "FAIL" }
        val errorCount = results.count { it.status == "ERROR" }
        val skippedCount = results.count { it.status == "SKIPPED" }
        val allPassed = results.all { it.status == "PASS" || it.status == "SKIPPED" }

        val summary = buildString {
            appendLine("Baseline Report: $passedCount passed, $failedCount failed, $errorCount errors, $skippedCount skipped")
            results.forEach { r ->
                appendLine("  [${r.status}] ${r.name} (${r.durationMs}ms)")
            }
            if (!allPassed) {
                appendLine()
                appendLine("Failed baselines:")
                results.filter { it.status == "FAIL" || it.status == "ERROR" }.forEach { r ->
                    appendLine("  ${r.name}: ${r.output.take(500)}")
                    if (r.errorOutput.isNotBlank()) {
                        appendLine("  stderr: ${r.errorOutput.take(300)}")
                    }
                }
            }
        }

        logger.info("Baseline execution complete: {} passed, {} failed in {}ms", passedCount, failedCount, totalDuration)

        return BaselineReport(
            results = results,
            totalDurationMs = totalDuration,
            timestamp = startTime.toString(),
            allPassed = allPassed,
            summary = summary
        )
    }

    /**
     * List all available baseline scripts.
     */
    fun listBaselines(): List<String> {
        val baselinesDir = resolveBaselinesDir() ?: return emptyList()
        return baselinesDir.listFiles { f -> f.extension == "sh" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    private fun runSingleBaseline(
        baselineName: String,
        baselinesDir: File,
        projectRoot: String
    ): BaselineResult {
        val scriptName = if (baselineName.endsWith(".sh")) baselineName else "$baselineName.sh"
        val scriptFile = baselinesDir.resolve(scriptName)

        if (!scriptFile.exists()) {
            return BaselineResult(
                name = baselineName,
                status = "SKIPPED",
                durationMs = 0,
                output = "Script not found: ${scriptFile.absolutePath}",
                errorOutput = "",
                exitCode = -1
            )
        }

        if (!scriptFile.canExecute()) {
            scriptFile.setExecutable(true)
        }

        val startTime = Instant.now()

        return try {
            val processBuilder = ProcessBuilder("bash", scriptFile.absolutePath)
                .directory(File(projectRoot))
                .redirectErrorStream(false)

            processBuilder.environment().apply {
                put("PROJECT_ROOT", projectRoot)
                put("BASELINE_NAME", baselineName)
            }

            val process = processBuilder.start()

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val stdoutThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lineSequence().forEach { line -> stdout.appendLine(line) }
                }
            }
            val stderrThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line -> stderr.appendLine(line) }
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                val durationMs = Duration.between(startTime, Instant.now()).toMillis()
                return BaselineResult(
                    name = baselineName,
                    status = "ERROR",
                    durationMs = durationMs,
                    output = stdout.toString(),
                    errorOutput = "Timed out after ${TIMEOUT_SECONDS}s",
                    exitCode = -1
                )
            }

            stdoutThread.join(5000)
            stderrThread.join(5000)

            val exitCode = process.exitValue()
            val durationMs = Duration.between(startTime, Instant.now()).toMillis()

            val status = when (exitCode) {
                0 -> "PASS"
                1 -> "FAIL"
                else -> "ERROR"
            }

            BaselineResult(
                name = baselineName,
                status = status,
                durationMs = durationMs,
                output = stdout.toString(),
                errorOutput = stderr.toString(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            val durationMs = Duration.between(startTime, Instant.now()).toMillis()
            logger.error("Failed to execute baseline {}: {}", baselineName, e.message)
            BaselineResult(
                name = baselineName,
                status = "ERROR",
                durationMs = durationMs,
                output = "",
                errorOutput = "Exception: ${e.message}",
                exitCode = -1
            )
        }
    }

    private fun resolveBaselinesDir(): File? {
        // Try relative to plugins base path
        val pluginsDir = File(pluginsBasePath)
        val candidate = pluginsDir.resolve(BASELINES_SUBPATH)
        if (candidate.exists()) return candidate

        // Try absolute path
        val absoluteCandidate = File(pluginsBasePath).resolve(BASELINES_SUBPATH)
        if (absoluteCandidate.exists()) return absoluteCandidate

        // Try /plugins mount (Docker)
        val dockerCandidate = File("/plugins").resolve(BASELINES_SUBPATH)
        if (dockerCandidate.exists()) return dockerCandidate

        logger.debug("Baselines directory candidates: {}, {}, {}", candidate, absoluteCandidate, dockerCandidate)
        return candidate // Return default even if not found
    }
}
