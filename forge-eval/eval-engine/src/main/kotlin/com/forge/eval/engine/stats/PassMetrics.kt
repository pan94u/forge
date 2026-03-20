package com.forge.eval.engine.stats

import com.forge.eval.protocol.TrialOutcome
import kotlin.math.pow

/**
 * Non-deterministic evaluation metrics based on Anthropic's Agent Eval methodology.
 *
 * - Pass@k: probability of at least one success in k attempts (exploration capability)
 * - Pass^k: probability of all k attempts succeeding (reliability)
 */
object PassMetrics {

    /**
     * Pass@k: at least 1 success in k trials.
     *
     * Unbiased estimator: Pass@k = 1 - C(n-c, k) / C(n, k)
     * where n = total trials, c = successful trials, k = sample size.
     *
     * When k >= n, falls back to simple ratio: c/n if c==n, else uses combinatorial.
     *
     * @param outcomes List of trial outcomes
     * @param k Sample size (defaults to total trial count)
     * @return Probability in [0.0, 1.0]
     */
    fun passAtK(outcomes: List<TrialOutcome>, k: Int = outcomes.size): Double {
        if (outcomes.isEmpty() || k <= 0) return 0.0
        val n = outcomes.size
        val c = outcomes.count { it == TrialOutcome.PASS }
        if (c == 0) return 0.0
        if (c == n) return 1.0
        if (k >= n) return if (c > 0) 1.0 else 0.0

        // Unbiased estimator: 1 - C(n-c, k) / C(n, k)
        val numerator = combinatorial(n - c, k)
        val denominator = combinatorial(n, k)
        return if (denominator == 0.0) 0.0 else 1.0 - numerator / denominator
    }

    /**
     * Pass^k: all k trials succeed.
     *
     * Simple estimator: (c/n)^k where c = successes, n = total.
     *
     * @param outcomes List of trial outcomes
     * @param k Power (defaults to total trial count)
     * @return Probability in [0.0, 1.0]
     */
    fun passPowerK(outcomes: List<TrialOutcome>, k: Int = outcomes.size): Double {
        if (outcomes.isEmpty() || k <= 0) return 0.0
        val n = outcomes.size
        val c = outcomes.count { it == TrialOutcome.PASS }
        val passRate = c.toDouble() / n
        return passRate.pow(k)
    }

    /**
     * Simple pass rate: successes / total.
     */
    fun passRate(outcomes: List<TrialOutcome>): Double {
        if (outcomes.isEmpty()) return 0.0
        return outcomes.count { it == TrialOutcome.PASS }.toDouble() / outcomes.size
    }

    /**
     * Wilson Score Interval for binomial proportion confidence interval.
     * More accurate than normal approximation for small samples.
     *
     * @param successes Number of successes
     * @param total Total trials
     * @param z Z-score (default 1.96 for 95% CI)
     * @return Pair(lower, upper) bounds
     */
    fun wilsonScoreInterval(successes: Int, total: Int, z: Double = 1.96): Pair<Double, Double> {
        if (total == 0) return Pair(0.0, 0.0)
        val p = successes.toDouble() / total
        val n = total.toDouble()
        val denominator = 1 + z * z / n
        val center = p + z * z / (2 * n)
        val spread = z * Math.sqrt((p * (1 - p) + z * z / (4 * n)) / n)
        val lower = ((center - spread) / denominator).coerceIn(0.0, 1.0)
        val upper = ((center + spread) / denominator).coerceIn(0.0, 1.0)
        return Pair(lower, upper)
    }

    /**
     * Binomial test: is the observed pass rate significantly lower than the baseline?
     *
     * Uses normal approximation to binomial for simplicity.
     *
     * @param observed Number of successes
     * @param total Total trials
     * @param baseline Expected pass rate
     * @param alpha Significance level (default 0.05)
     * @return true if the observed rate is significantly lower than baseline
     */
    fun isSignificantRegression(
        observed: Int,
        total: Int,
        baseline: Double,
        alpha: Double = 0.05
    ): Boolean {
        if (total == 0) return false
        val p = observed.toDouble() / total
        val se = Math.sqrt(baseline * (1 - baseline) / total)
        if (se == 0.0) return p < baseline
        val zScore = (p - baseline) / se
        // One-tailed test: z < -z_alpha means significant regression
        val zAlpha = when {
            alpha <= 0.01 -> -2.326
            alpha <= 0.05 -> -1.645
            alpha <= 0.10 -> -1.282
            else -> -1.0
        }
        return zScore < zAlpha
    }

    /**
     * Compute C(n, k) as a Double to handle large values.
     */
    private fun combinatorial(n: Int, k: Int): Double {
        if (k > n) return 0.0
        if (k == 0 || k == n) return 1.0
        val effectiveK = minOf(k, n - k)
        var result = 1.0
        for (i in 0 until effectiveK) {
            result = result * (n - i) / (i + 1)
        }
        return result
    }
}
