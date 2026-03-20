package com.forge.eval.engine

import com.forge.eval.protocol.*
import java.time.Instant

/**
 * Generates evaluation reports from run results.
 * Supports both structured (EvalReport) and Markdown formats.
 */
class ReportGenerator {

    fun generateReport(
        suite: EvalSuite,
        tasks: List<EvalTask>,
        result: EvalRunResult
    ): EvalReport {
        val taskMap = tasks.associateBy { it.id }
        val trialsByTask = result.trials.groupBy { it.trial.taskId }

        val taskReports = trialsByTask.map { (taskId, trialResults) ->
            val task = taskMap[taskId]
            TaskReport(
                taskId = taskId,
                taskName = task?.name ?: "unknown",
                difficulty = task?.difficulty ?: Difficulty.MEDIUM,
                trials = trialResults.map { tr ->
                    TrialReport(
                        trialNumber = tr.trial.trialNumber,
                        outcome = tr.trial.outcome,
                        score = tr.trial.score,
                        durationMs = tr.trial.durationMs,
                        grades = tr.grades.map { grade ->
                            GradeResponse(
                                id = grade.id,
                                graderType = grade.graderType,
                                score = grade.score,
                                passed = grade.passed,
                                assertionResults = grade.assertionResults,
                                explanation = grade.explanation,
                                confidence = grade.confidence
                            )
                        }
                    )
                }
            )
        }

        return EvalReport(
            runId = result.run.id,
            suiteName = suite.name,
            platform = suite.platform,
            agentType = suite.agentType,
            timestamp = Instant.now(),
            summary = result.run.summary ?: RunSummary(0, 0, 0, 0, 0, 0.0, 0.0, 0),
            taskResults = taskReports
        )
    }

    fun toMarkdown(report: EvalReport): String = buildString {
        appendLine("# Forge Eval Report")
        appendLine()
        appendLine("**Suite:** ${report.suiteName}")
        appendLine("**Platform:** ${report.platform}")
        appendLine("**Agent Type:** ${report.agentType}")
        appendLine("**Timestamp:** ${report.timestamp}")
        appendLine()

        // Summary
        appendLine("## Summary")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|--------|-------|")
        appendLine("| Total Tasks | ${report.summary.totalTasks} |")
        appendLine("| Total Trials | ${report.summary.totalTrials} |")
        appendLine("| Passed | ${report.summary.passedTrials} |")
        appendLine("| Failed | ${report.summary.failedTrials} |")
        appendLine("| Errors | ${report.summary.errorTrials} |")
        appendLine("| Pass Rate | ${"%.1f".format(report.summary.overallPassRate * 100)}% |")
        appendLine("| Avg Score | ${"%.2f".format(report.summary.averageScore)} |")
        appendLine("| Duration | ${report.summary.totalDurationMs}ms |")
        appendLine()

        // Task details
        appendLine("## Task Results")
        appendLine()
        for (taskReport in report.taskResults) {
            appendLine("### ${taskReport.taskName}")
            appendLine()
            appendLine("**Difficulty:** ${taskReport.difficulty}")
            appendLine()
            for (trial in taskReport.trials) {
                val icon = when (trial.outcome) {
                    TrialOutcome.PASS -> "PASS"
                    TrialOutcome.FAIL -> "FAIL"
                    TrialOutcome.PARTIAL -> "PARTIAL"
                    TrialOutcome.ERROR -> "ERROR"
                }
                appendLine("**Trial #${trial.trialNumber}:** $icon (score=${
                    "%.2f".format(trial.score)
                }, ${trial.durationMs}ms)")
                appendLine()

                for (grade in trial.grades) {
                    for (assertion in grade.assertionResults) {
                        val check = if (assertion.passed) "[x]" else "[ ]"
                        appendLine("- $check ${assertion.description}")
                    }
                }
                appendLine()
            }
        }
    }
}
