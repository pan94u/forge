package com.forge.superagent.baselines

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Baseline Runner — Executes baseline scripts for a given Skill Profile and collects results.
 *
 * This is the orchestration engine for the SuperAgent's inner OODA loop quality gates.
 * It reads the profile's baseline list, executes each script, and returns a structured
 * report that the SuperAgent uses to decide whether to proceed or loop back.
 *
 * Usage:
 *   val runner = BaselineRunner(projectRoot = "/path/to/project")
 *   val report = runner.runBaselines(listOf("code-style-baseline", "security-baseline", "test-coverage-baseline"))
 *   if (!report.allPassed) {
 *       // Feed failures back into OODA Observe phase
 *   }
 */

// --- Data classes for structured results ---

enum class BaselineStatus {
    PASS,
    FAIL,
    ERROR,
    SKIPPED
}

data class BaselineResult(
    val name: String,
    val status: BaselineStatus,
    val duration: Duration,
    val output: String,
    val errorOutput: String,
    val exitCode: Int
) {
    val passed: Boolean get() = status == BaselineStatus.PASS

    fun summary(): String {
        val icon = when (status) {
            BaselineStatus.PASS -> "[PASS]"
            BaselineStatus.FAIL -> "[FAIL]"
            BaselineStatus.ERROR -> "[ERROR]"
            BaselineStatus.SKIPPED -> "[SKIP]"
        }
        return "$icon $name (${duration.toMillis()}ms)"
    }
}

data class BaselineReport(
    val results: List<BaselineResult>,
    val totalDuration: Duration,
    val timestamp: Instant
) {
    val allPassed: Boolean get() = results.all { it.passed || it.status == BaselineStatus.SKIPPED }
    val failedBaselines: List<BaselineResult> get() = results.filter { !it.passed && it.status != BaselineStatus.SKIPPED }
    val passedCount: Int get() = results.count { it.passed }
    val failedCount: Int get() = results.count { it.status == BaselineStatus.FAIL }
    val errorCount: Int get() = results.count { it.status == BaselineStatus.ERROR }
    val skippedCount: Int get() = results.count { it.status == BaselineStatus.SKIPPED }

    /**
     * Generates a human-readable report summary.
     */
    fun toSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("========================================")
        sb.appendLine("  Baseline Execution Report")
        sb.appendLine("========================================")
        sb.appendLine("Timestamp: $timestamp")
        sb.appendLine("Total Duration: ${totalDuration.toMillis()}ms")
        sb.appendLine("Results: $passedCount passed, $failedCount failed, $errorCount errors, $skippedCount skipped")
        sb.appendLine()

        results.forEach { result ->
            sb.appendLine(result.summary())
        }

        if (!allPassed) {
            sb.appendLine()
            sb.appendLine("--- Failure Details ---")
            failedBaselines.forEach { result ->
                sb.appendLine()
                sb.appendLine("Baseline: ${result.name}")
                sb.appendLine("Exit Code: ${result.exitCode}")
                if (result.output.isNotBlank()) {
                    sb.appendLine("Output:")
                    sb.appendLine(result.output.lines().joinToString("\n") { "  $it" })
                }
                if (result.errorOutput.isNotBlank()) {
                    sb.appendLine("Error Output:")
                    sb.appendLine(result.errorOutput.lines().joinToString("\n") { "  $it" })
                }
            }
        }

        sb.appendLine()
        sb.appendLine("========================================")
        if (allPassed) {
            sb.appendLine("  ALL BASELINES PASSED")
        } else {
            sb.appendLine("  BASELINES FAILED — Fix issues and re-run")
        }
        sb.appendLine("========================================")

        return sb.toString()
    }

    /**
     * Generates a structured JSON-like report for machine consumption
     * (used by the learning loop's execution logger).
     */
    fun toStructuredReport(): Map<String, Any> {
        return mapOf(
            "timestamp" to timestamp.toString(),
            "totalDurationMs" to totalDuration.toMillis(),
            "allPassed" to allPassed,
            "summary" to mapOf(
                "passed" to passedCount,
                "failed" to failedCount,
                "errors" to errorCount,
                "skipped" to skippedCount
            ),
            "results" to results.map { result ->
                mapOf(
                    "name" to result.name,
                    "status" to result.status.name,
                    "durationMs" to result.duration.toMillis(),
                    "exitCode" to result.exitCode,
                    "output" to result.output,
                    "errorOutput" to result.errorOutput
                )
            }
        )
    }
}

// --- Baseline Runner ---

class BaselineRunner(
    private val projectRoot: String,
    private val baselinesDir: String = "baselines",
    private val timeoutSeconds: Long = 300 // 5 minutes per baseline
) {
    private val baselinesPath: File
        get() {
            // Look for baselines directory relative to the plugin location
            val pluginBaselines = File(projectRoot).resolve(baselinesDir)
            if (pluginBaselines.exists()) return pluginBaselines

            // Fall back to the standard plugin path
            val forgeBaselines = File(projectRoot)
                .resolve("forge-platform/plugins/forge-superagent/baselines")
            if (forgeBaselines.exists()) return forgeBaselines

            return pluginBaselines // Return default even if it does not exist
        }

    /**
     * Run a list of baselines by name and return a structured report.
     *
     * @param baselineNames List of baseline names (without .sh extension)
     * @return BaselineReport with results for all baselines
     */
    fun runBaselines(baselineNames: List<String>): BaselineReport {
        val startTime = Instant.now()
        val results = mutableListOf<BaselineResult>()

        for (baselineName in baselineNames) {
            val result = runSingleBaseline(baselineName)
            results.add(result)

            // Log each result as it completes
            println(result.summary())
        }

        val totalDuration = Duration.between(startTime, Instant.now())
        return BaselineReport(
            results = results,
            totalDuration = totalDuration,
            timestamp = startTime
        )
    }

    /**
     * Run baselines from a profile definition.
     *
     * @param profilePath Path to the skill profile markdown file
     * @return BaselineReport with results for all baselines defined in the profile
     */
    fun runBaselinesFromProfile(profilePath: String): BaselineReport {
        val profileFile = File(profilePath)
        if (!profileFile.exists()) {
            println("[ERROR] Profile file not found: $profilePath")
            return BaselineReport(
                results = emptyList(),
                totalDuration = Duration.ZERO,
                timestamp = Instant.now()
            )
        }

        val baselineNames = parseBaselinesFromProfile(profileFile)

        if (baselineNames.isEmpty()) {
            println("[INFO] No baselines defined in profile: $profilePath")
            return BaselineReport(
                results = emptyList(),
                totalDuration = Duration.ZERO,
                timestamp = Instant.now()
            )
        }

        println("[INFO] Running ${baselineNames.size} baseline(s) from profile: ${profileFile.name}")
        return runBaselines(baselineNames)
    }

    /**
     * Execute a single baseline script.
     */
    private fun runSingleBaseline(baselineName: String): BaselineResult {
        val scriptName = if (baselineName.endsWith(".sh")) baselineName else "$baselineName.sh"
        val scriptFile = baselinesPath.resolve(scriptName)

        if (!scriptFile.exists()) {
            return BaselineResult(
                name = baselineName,
                status = BaselineStatus.SKIPPED,
                duration = Duration.ZERO,
                output = "Baseline script not found: ${scriptFile.absolutePath}",
                errorOutput = "",
                exitCode = -1
            )
        }

        if (!scriptFile.canExecute()) {
            // Try to make it executable
            scriptFile.setExecutable(true)
        }

        val startTime = Instant.now()

        return try {
            val processBuilder = ProcessBuilder("bash", scriptFile.absolutePath)
                .directory(File(projectRoot))
                .redirectErrorStream(false)

            // Set environment variables for the baseline scripts
            processBuilder.environment().apply {
                put("PROJECT_ROOT", projectRoot)
                put("BASELINE_NAME", baselineName)
            }

            val process = processBuilder.start()

            // Read stdout and stderr in parallel to avoid blocking
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val stdoutThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        stdout.appendLine(line)
                    }
                }
            }

            val stderrThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        stderr.appendLine(line)
                    }
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                val duration = Duration.between(startTime, Instant.now())
                return BaselineResult(
                    name = baselineName,
                    status = BaselineStatus.ERROR,
                    duration = duration,
                    output = stdout.toString(),
                    errorOutput = "Baseline timed out after ${timeoutSeconds}s",
                    exitCode = -1
                )
            }

            stdoutThread.join(5000)
            stderrThread.join(5000)

            val exitCode = process.exitValue()
            val duration = Duration.between(startTime, Instant.now())

            val status = when (exitCode) {
                0 -> BaselineStatus.PASS
                1 -> BaselineStatus.FAIL
                else -> BaselineStatus.ERROR
            }

            BaselineResult(
                name = baselineName,
                status = status,
                duration = duration,
                output = stdout.toString(),
                errorOutput = stderr.toString(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            val duration = Duration.between(startTime, Instant.now())
            BaselineResult(
                name = baselineName,
                status = BaselineStatus.ERROR,
                duration = duration,
                output = "",
                errorOutput = "Failed to execute baseline: ${e.message}",
                exitCode = -1
            )
        }
    }

    /**
     * Parse baseline names from a skill profile's YAML frontmatter.
     */
    private fun parseBaselinesFromProfile(profileFile: File): List<String> {
        val lines = profileFile.readLines()
        val baselines = mutableListOf<String>()
        var inFrontmatter = false
        var inBaselinesSection = false

        for (line in lines) {
            if (line.trim() == "---") {
                if (inFrontmatter) break // End of frontmatter
                inFrontmatter = true
                continue
            }

            if (!inFrontmatter) continue

            if (line.trim().startsWith("baselines:")) {
                val inlineValue = line.substringAfter("baselines:").trim()
                if (inlineValue == "[]") {
                    return emptyList()
                }
                if (inlineValue.isNotEmpty() && !inlineValue.startsWith("[")) {
                    // Single baseline on same line
                    baselines.add(inlineValue.trim())
                }
                inBaselinesSection = true
                continue
            }

            if (inBaselinesSection) {
                val trimmed = line.trim()
                if (trimmed.startsWith("- ")) {
                    baselines.add(trimmed.removePrefix("- ").trim())
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("-")) {
                    // No longer in baselines list
                    inBaselinesSection = false
                }
            }
        }

        return baselines
    }
}

// --- Main entry point ---

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0) ?: System.getProperty("user.dir") ?: "."
    val profilePath = args.getOrNull(1)

    val runner = BaselineRunner(projectRoot = projectRoot)

    val report = if (profilePath != null) {
        println("Running baselines from profile: $profilePath")
        runner.runBaselinesFromProfile(profilePath)
    } else {
        // Default: run all baselines
        println("Running all baselines...")
        runner.runBaselines(
            listOf(
                "code-style-baseline",
                "security-baseline",
                "test-coverage-baseline",
                "api-contract-baseline",
                "architecture-baseline"
            )
        )
    }

    println()
    println(report.toSummary())

    // Exit with appropriate code
    if (report.allPassed) {
        kotlin.system.exitProcess(0)
    } else {
        kotlin.system.exitProcess(1)
    }
}
