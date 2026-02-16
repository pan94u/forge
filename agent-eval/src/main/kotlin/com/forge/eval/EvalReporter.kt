package com.forge.eval

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates evaluation reports in JSON and Markdown formats.
 *
 * After the EvalRunner completes a suite, the reporter produces:
 * - A JSON report suitable for CI pipelines and dashboards
 * - A Markdown report suitable for PR comments and human review
 * - Profile-level summary statistics
 * - Trend data when historical results are available
 */
class EvalReporter(
    private val outputDir: File = File("build/eval-reports")
) {

    private val logger = LoggerFactory.getLogger(EvalReporter::class.java)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class ReportSummary(
        val timestamp: String,
        val totalEvals: Int,
        val passed: Int,
        val failed: Int,
        val passRate: Double,
        val averageScore: Double,
        val profileBreakdown: Map<String, ProfileSummary>,
        val baselineStatus: BaselineChecker.BaselineCheckResult
    )

    data class ProfileSummary(
        val profile: String,
        val total: Int,
        val passed: Int,
        val failed: Int,
        val passRate: Double,
        val averageScore: Double
    )

    /**
     * Generate both JSON and Markdown reports.
     */
    fun generateReport(
        results: List<EvalRunner.EvalResult>,
        baselineResults: BaselineChecker.BaselineCheckResult
    ): ReportSummary {
        outputDir.mkdirs()

        val summary = buildSummary(results, baselineResults)

        generateJsonReport(results, summary)
        generateMarkdownReport(results, summary)

        logger.info("Reports written to: {}", outputDir.absolutePath)
        return summary
    }

    private fun buildSummary(
        results: List<EvalRunner.EvalResult>,
        baselineResults: BaselineChecker.BaselineCheckResult
    ): ReportSummary {
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        val avgScore = if (results.isNotEmpty()) results.map { it.score }.average() else 0.0

        val profileBreakdown = results.groupBy { it.profile }.map { (profile, profileResults) ->
            val profilePassed = profileResults.count { it.passed }
            profile to ProfileSummary(
                profile = profile,
                total = profileResults.size,
                passed = profilePassed,
                failed = profileResults.size - profilePassed,
                passRate = if (profileResults.isNotEmpty()) profilePassed.toDouble() / profileResults.size else 0.0,
                averageScore = if (profileResults.isNotEmpty()) profileResults.map { it.score }.average() else 0.0
            )
        }.toMap()

        return ReportSummary(
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            totalEvals = results.size,
            passed = passed,
            failed = failed,
            passRate = if (results.isNotEmpty()) passed.toDouble() / results.size else 0.0,
            averageScore = avgScore,
            profileBreakdown = profileBreakdown,
            baselineStatus = baselineResults
        )
    }

    private fun generateJsonReport(results: List<EvalRunner.EvalResult>, summary: ReportSummary) {
        val report = mapOf(
            "summary" to mapOf(
                "timestamp" to summary.timestamp,
                "total" to summary.totalEvals,
                "passed" to summary.passed,
                "failed" to summary.failed,
                "passRate" to summary.passRate,
                "averageScore" to summary.averageScore,
                "baselinePassed" to summary.baselineStatus.allPassed
            ),
            "profiles" to summary.profileBreakdown.map { (name, profile) ->
                mapOf(
                    "name" to name,
                    "total" to profile.total,
                    "passed" to profile.passed,
                    "passRate" to profile.passRate,
                    "averageScore" to profile.averageScore
                )
            },
            "results" to results.map { result ->
                mapOf(
                    "evalId" to result.evalId,
                    "profile" to result.profile,
                    "scenario" to result.scenario,
                    "passed" to result.passed,
                    "score" to result.score,
                    "durationMs" to result.durationMs,
                    "assertions" to result.assertions.map { assertion ->
                        mapOf(
                            "description" to assertion.description,
                            "passed" to assertion.passed,
                            "expected" to assertion.expected,
                            "actual" to assertion.actual
                        )
                    }
                )
            }
        )

        val jsonFile = File(outputDir, "eval-report.json")
        jsonFile.writeText(gson.toJson(report))
        logger.info("JSON report: {}", jsonFile.absolutePath)
    }

    private fun generateMarkdownReport(results: List<EvalRunner.EvalResult>, summary: ReportSummary) {
        val md = buildString {
            appendLine("# Forge Evaluation Report")
            appendLine()
            appendLine("**Generated:** ${summary.timestamp}")
            appendLine()

            // Overall summary
            appendLine("## Summary")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Total Evaluations | ${summary.totalEvals} |")
            appendLine("| Passed | ${summary.passed} |")
            appendLine("| Failed | ${summary.failed} |")
            appendLine("| Pass Rate | ${"%.1f".format(summary.passRate * 100)}% |")
            appendLine("| Average Score | ${"%.2f".format(summary.averageScore)} |")
            appendLine("| Baseline Check | ${if (summary.baselineStatus.allPassed) "PASSED" else "FAILED"} |")
            appendLine()

            // Profile breakdown
            appendLine("## Profile Breakdown")
            appendLine()
            appendLine("| Profile | Total | Passed | Failed | Pass Rate | Avg Score |")
            appendLine("|---------|-------|--------|--------|-----------|-----------|")
            for ((name, profile) in summary.profileBreakdown) {
                appendLine("| $name | ${profile.total} | ${profile.passed} | ${profile.failed} | " +
                        "${"%.1f".format(profile.passRate * 100)}% | ${"%.2f".format(profile.averageScore)} |")
            }
            appendLine()

            // Detailed results
            appendLine("## Detailed Results")
            appendLine()
            for (result in results) {
                val icon = if (result.passed) "PASS" else "FAIL"
                appendLine("### [$icon] ${result.evalId}")
                appendLine()
                appendLine("- **Profile:** ${result.profile}")
                appendLine("- **Scenario:** ${result.scenario}")
                appendLine("- **Score:** ${"%.2f".format(result.score)}")
                appendLine("- **Duration:** ${result.durationMs}ms")
                appendLine()

                if (result.assertions.isNotEmpty()) {
                    appendLine("**Assertions:**")
                    appendLine()
                    for (assertion in result.assertions) {
                        val assertIcon = if (assertion.passed) "[x]" else "[ ]"
                        appendLine("- $assertIcon ${assertion.description}")
                    }
                    appendLine()
                }
            }

            // Baseline details
            if (!summary.baselineStatus.allPassed) {
                appendLine("## Baseline Failures")
                appendLine()
                for (failure in summary.baselineStatus.failures) {
                    appendLine("- **${failure.profile}**: ${failure.message}")
                }
                appendLine()
            }
        }

        val mdFile = File(outputDir, "eval-report.md")
        mdFile.writeText(md)
        logger.info("Markdown report: {}", mdFile.absolutePath)
    }
}
