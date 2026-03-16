package com.forge.eval.engine.lifecycle

import com.forge.eval.protocol.Lifecycle
import com.forge.eval.protocol.TrialOutcome
import org.slf4j.LoggerFactory

/**
 * Manages evaluation task lifecycle transitions:
 * CAPABILITY → REGRESSION → SATURATED
 *
 * Graduation rules (Capability → Regression):
 * 1. Consecutive runs >= requiredConsecutiveRuns with pass rate >= 95%
 * 2. Pass^3 >= 90%
 *
 * Saturation rules (Regression → Saturated):
 * Consecutive runs >= saturationWindow with Pass^3 = 100%
 */
class LifecycleManager(
    private val requiredConsecutiveRuns: Int = 5,
    private val graduationPassRate: Double = 0.95,
    private val graduationPassPowerK: Double = 0.90,
    private val saturationWindow: Int = 20,
    private val saturationPassPowerK: Double = 1.0
) {
    private val logger = LoggerFactory.getLogger(LifecycleManager::class.java)

    data class LifecycleDecision(
        val currentLifecycle: Lifecycle,
        val recommendedLifecycle: Lifecycle,
        val shouldTransition: Boolean,
        val reason: String,
        val metrics: LifecycleMetrics
    )

    data class LifecycleMetrics(
        val consecutivePassingRuns: Int,
        val recentPassRate: Double,
        val recentPassPowerK: Double?,
        val totalRuns: Int
    )

    /**
     * Evaluate whether a task should transition to a new lifecycle stage.
     *
     * @param currentLifecycle Current lifecycle stage
     * @param runHistory History of pass rates (most recent last)
     * @param passPowerKHistory History of Pass^k values (most recent last)
     * @return Decision about lifecycle transition
     */
    fun evaluate(
        currentLifecycle: Lifecycle,
        runHistory: List<Double>,
        passPowerKHistory: List<Double> = emptyList()
    ): LifecycleDecision {
        val metrics = computeMetrics(runHistory, passPowerKHistory)

        return when (currentLifecycle) {
            Lifecycle.CAPABILITY -> evaluateGraduation(metrics)
            Lifecycle.REGRESSION -> evaluateSaturation(metrics)
            Lifecycle.SATURATED -> LifecycleDecision(
                currentLifecycle = Lifecycle.SATURATED,
                recommendedLifecycle = Lifecycle.SATURATED,
                shouldTransition = false,
                reason = "已饱和，降低运行频率",
                metrics = metrics
            )
        }
    }

    private fun evaluateGraduation(metrics: LifecycleMetrics): LifecycleDecision {
        val passRateMet = metrics.consecutivePassingRuns >= requiredConsecutiveRuns
        val passPowerKMet = metrics.recentPassPowerK != null &&
            metrics.recentPassPowerK >= graduationPassPowerK

        val shouldGraduate = passRateMet && passPowerKMet

        val reason = when {
            shouldGraduate -> "满足毕业条件：连续 ${metrics.consecutivePassingRuns} 次通过率 >= ${(graduationPassRate * 100).toInt()}%，" +
                "Pass^k = ${"%.1f".format((metrics.recentPassPowerK ?: 0.0) * 100)}% >= ${(graduationPassPowerK * 100).toInt()}%"
            !passRateMet -> "未满足连续通过次数：${metrics.consecutivePassingRuns}/$requiredConsecutiveRuns"
            else -> "Pass^k 未达标：${"%.1f".format((metrics.recentPassPowerK ?: 0.0) * 100)}% < ${(graduationPassPowerK * 100).toInt()}%"
        }

        return LifecycleDecision(
            currentLifecycle = Lifecycle.CAPABILITY,
            recommendedLifecycle = if (shouldGraduate) Lifecycle.REGRESSION else Lifecycle.CAPABILITY,
            shouldTransition = shouldGraduate,
            reason = reason,
            metrics = metrics
        )
    }

    private fun evaluateSaturation(metrics: LifecycleMetrics): LifecycleDecision {
        val isSaturated = metrics.consecutivePassingRuns >= saturationWindow &&
            metrics.recentPassPowerK != null &&
            metrics.recentPassPowerK >= saturationPassPowerK

        val reason = if (isSaturated) {
            "饱和：连续 ${metrics.consecutivePassingRuns} 次 Pass^k = 100%，建议降低运行频率或创建更难变体"
        } else {
            "回归守护中：${metrics.consecutivePassingRuns}/$saturationWindow 连续通过"
        }

        return LifecycleDecision(
            currentLifecycle = Lifecycle.REGRESSION,
            recommendedLifecycle = if (isSaturated) Lifecycle.SATURATED else Lifecycle.REGRESSION,
            shouldTransition = isSaturated,
            reason = reason,
            metrics = metrics
        )
    }

    private fun computeMetrics(
        runHistory: List<Double>,
        passPowerKHistory: List<Double>
    ): LifecycleMetrics {
        val consecutivePassing = runHistory.asReversed()
            .takeWhile { it >= graduationPassRate }
            .size

        val recentPassRate = if (runHistory.isNotEmpty()) runHistory.last() else 0.0
        val recentPassPowerK = passPowerKHistory.lastOrNull()

        return LifecycleMetrics(
            consecutivePassingRuns = consecutivePassing,
            recentPassRate = recentPassRate,
            recentPassPowerK = recentPassPowerK,
            totalRuns = runHistory.size
        )
    }

    /**
     * Check if a saturated task should be included in the current run.
     * Saturated tasks run at reduced frequency.
     */
    fun shouldRunSaturatedTask(runNumber: Int, frequency: Int = 5): Boolean {
        return runNumber % frequency == 0
    }
}
