package com.forge.eval.engine.stats

import com.forge.eval.protocol.RunSummary
import com.forge.eval.protocol.TrialOutcome
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Detects regressions by comparing evaluation runs against baselines.
 *
 * Uses statistical significance testing (binomial test) to distinguish
 * real regressions from random variance in non-deterministic agent outputs.
 */
class RegressionDetector(
    private val significanceLevel: Double = 0.05
) {

    private val logger = LoggerFactory.getLogger(RegressionDetector::class.java)

    data class RegressionReport(
        val regressions: List<Regression>,
        val hasRegressions: Boolean = regressions.isNotEmpty()
    )

    data class Regression(
        val taskId: UUID,
        val taskName: String,
        val type: RegressionType,
        val baselinePassRate: Double,
        val currentPassRate: Double,
        val confidenceInterval: Pair<Double, Double>,
        val isStatisticallySignificant: Boolean,
        val message: String
    )

    enum class RegressionType {
        PASS_RATE_DROP,
        PASS_TO_FAIL,
        SCORE_DROP
    }

    /**
     * Compare current run against a baseline run to detect regressions.
     *
     * @param currentTrials Current run trial results grouped by task ID
     * @param baselineTrials Baseline run trial results grouped by task ID
     * @param taskNames Map of task ID to name for human-readable output
     * @return RegressionReport with any detected regressions
     */
    fun detectRegressions(
        currentTrials: Map<UUID, List<TrialOutcome>>,
        baselineTrials: Map<UUID, List<TrialOutcome>>,
        taskNames: Map<UUID, String> = emptyMap()
    ): RegressionReport {
        val regressions = mutableListOf<Regression>()

        for ((taskId, baselineOutcomes) in baselineTrials) {
            val currentOutcomes = currentTrials[taskId] ?: continue
            val taskName = taskNames[taskId] ?: taskId.toString()
            val baselineRate = PassMetrics.passRate(baselineOutcomes)
            val currentRate = PassMetrics.passRate(currentOutcomes)
            val ci = PassMetrics.wilsonScoreInterval(
                currentOutcomes.count { it == TrialOutcome.PASS },
                currentOutcomes.size
            )

            // Check for pass rate drop
            if (currentRate < baselineRate) {
                val isSignificant = PassMetrics.isSignificantRegression(
                    observed = currentOutcomes.count { it == TrialOutcome.PASS },
                    total = currentOutcomes.size,
                    baseline = baselineRate,
                    alpha = significanceLevel
                )

                val type = if (baselineRate == 1.0 && currentRate < 1.0) {
                    RegressionType.PASS_TO_FAIL
                } else {
                    RegressionType.PASS_RATE_DROP
                }

                regressions.add(Regression(
                    taskId = taskId,
                    taskName = taskName,
                    type = type,
                    baselinePassRate = baselineRate,
                    currentPassRate = currentRate,
                    confidenceInterval = ci,
                    isStatisticallySignificant = isSignificant,
                    message = buildMessage(taskName, type, baselineRate, currentRate, isSignificant)
                ))

                if (isSignificant) {
                    logger.warn("REGRESSION: {}", regressions.last().message)
                } else {
                    logger.info("Potential regression (not significant): {} rate {:.1f}% → {:.1f}%",
                        taskName, baselineRate * 100, currentRate * 100)
                }
            }
        }

        return RegressionReport(regressions)
    }

    /**
     * Compare two RunSummary objects for overall regression.
     */
    fun compareRuns(current: RunSummary, baseline: RunSummary): RegressionReport {
        val regressions = mutableListOf<Regression>()
        val overallId = UUID(0, 0)

        if (current.overallPassRate < baseline.overallPassRate) {
            val isSignificant = PassMetrics.isSignificantRegression(
                observed = current.passedTrials,
                total = current.totalTrials,
                baseline = baseline.overallPassRate,
                alpha = significanceLevel
            )
            val ci = PassMetrics.wilsonScoreInterval(current.passedTrials, current.totalTrials)

            regressions.add(Regression(
                taskId = overallId,
                taskName = "(overall)",
                type = RegressionType.PASS_RATE_DROP,
                baselinePassRate = baseline.overallPassRate,
                currentPassRate = current.overallPassRate,
                confidenceInterval = ci,
                isStatisticallySignificant = isSignificant,
                message = "Overall pass rate dropped: ${"%.1f".format(baseline.overallPassRate * 100)}% → ${"%.1f".format(current.overallPassRate * 100)}%${if (isSignificant) " (SIGNIFICANT)" else ""}"
            ))
        }

        return RegressionReport(regressions)
    }

    private fun buildMessage(
        taskName: String,
        type: RegressionType,
        baseline: Double,
        current: Double,
        significant: Boolean
    ): String {
        val typeLabel = when (type) {
            RegressionType.PASS_TO_FAIL -> "PASS→FAIL"
            RegressionType.PASS_RATE_DROP -> "pass rate drop"
            RegressionType.SCORE_DROP -> "score drop"
        }
        val sigLabel = if (significant) " (STATISTICALLY SIGNIFICANT)" else " (not significant)"
        return "Task '$taskName' $typeLabel: ${"%.1f".format(baseline * 100)}% → ${"%.1f".format(current * 100)}%$sigLabel"
    }
}
