package com.forge.eval

import org.slf4j.LoggerFactory

/**
 * Verifies that evaluation results meet minimum baseline pass rates.
 *
 * Baselines are the quality floor -- they represent the minimum acceptable
 * performance for each skill profile. If a model change, skill update, or
 * configuration change causes results to drop below baseline, the checker
 * flags this as a regression.
 *
 * Baseline pass rates are defined per-profile and can be overridden per-eval.
 * The default baseline for any profile is 80% pass rate.
 */
class BaselineChecker(
    private val defaultBaselinePassRate: Double = 0.8
) {

    private val logger = LoggerFactory.getLogger(BaselineChecker::class.java)

    /**
     * Per-profile baseline pass rates. These represent the minimum acceptable
     * pass rates established through historical evaluation runs.
     */
    private val profileBaselines = mapOf(
        "planning" to 0.85,      // Planning evals should be highly reliable
        "development" to 0.80,   // Code generation has more variance
        "testing" to 0.85,       // Test generation should be reliable
        "design" to 0.75,        // Architecture design is more subjective
        "learning-loop" to 0.70  // Learning metrics have inherent variance
    )

    data class BaselineCheckResult(
        val allPassed: Boolean,
        val profileResults: Map<String, ProfileBaselineResult>,
        val failures: List<BaselineFailure>
    )

    data class ProfileBaselineResult(
        val profile: String,
        val baselinePassRate: Double,
        val actualPassRate: Double,
        val passed: Boolean,
        val evalCount: Int
    )

    data class BaselineFailure(
        val profile: String,
        val expectedPassRate: Double,
        val actualPassRate: Double,
        val message: String
    )

    /**
     * Check all evaluation results against their profile baselines.
     *
     * @param results The evaluation results to check
     * @return Baseline check result with pass/fail status per profile
     */
    fun checkBaselines(results: List<EvalRunner.EvalResult>): BaselineCheckResult {
        val profileResults = mutableMapOf<String, ProfileBaselineResult>()
        val failures = mutableListOf<BaselineFailure>()

        // Group results by profile
        val byProfile = results.groupBy { it.profile }

        for ((profile, profileEvals) in byProfile) {
            val baseline = profileBaselines[profile] ?: defaultBaselinePassRate
            val passedCount = profileEvals.count { it.passed }
            val actualPassRate = if (profileEvals.isNotEmpty()) {
                passedCount.toDouble() / profileEvals.size
            } else {
                1.0
            }

            val passed = actualPassRate >= baseline

            profileResults[profile] = ProfileBaselineResult(
                profile = profile,
                baselinePassRate = baseline,
                actualPassRate = actualPassRate,
                passed = passed,
                evalCount = profileEvals.size
            )

            if (!passed) {
                val message = "Profile '$profile' pass rate ${"%.1f".format(actualPassRate * 100)}% " +
                        "is below baseline ${"%.1f".format(baseline * 100)}% " +
                        "($passedCount/${profileEvals.size} passed)"
                failures.add(
                    BaselineFailure(
                        profile = profile,
                        expectedPassRate = baseline,
                        actualPassRate = actualPassRate,
                        message = message
                    )
                )
                logger.warn("BASELINE FAILURE: {}", message)
            } else {
                logger.info(
                    "Baseline OK: profile='{}' rate={:.1f}% (baseline={:.1f}%)",
                    profile, actualPassRate * 100, baseline * 100
                )
            }
        }

        return BaselineCheckResult(
            allPassed = failures.isEmpty(),
            profileResults = profileResults,
            failures = failures
        )
    }

    /**
     * Compare current results against a previous run to detect regressions.
     *
     * @param current Current evaluation results
     * @param previous Previous evaluation results (from last known-good run)
     * @return List of regressions detected
     */
    fun detectRegressions(
        current: List<EvalRunner.EvalResult>,
        previous: List<EvalRunner.EvalResult>
    ): List<RegressionInfo> {
        val regressions = mutableListOf<RegressionInfo>()

        val currentById = current.associateBy { it.evalId }
        val previousById = previous.associateBy { it.evalId }

        for ((evalId, prevResult) in previousById) {
            val currResult = currentById[evalId] ?: continue

            // Detect pass -> fail regression
            if (prevResult.passed && !currResult.passed) {
                regressions.add(
                    RegressionInfo(
                        evalId = evalId,
                        profile = currResult.profile,
                        previousScore = prevResult.score,
                        currentScore = currResult.score,
                        type = RegressionType.PASS_TO_FAIL,
                        message = "Eval '$evalId' regressed from PASS to FAIL"
                    )
                )
            }

            // Detect significant score drop (>15% decrease)
            if (currResult.score < prevResult.score * 0.85) {
                regressions.add(
                    RegressionInfo(
                        evalId = evalId,
                        profile = currResult.profile,
                        previousScore = prevResult.score,
                        currentScore = currResult.score,
                        type = RegressionType.SCORE_DROP,
                        message = "Eval '$evalId' score dropped from " +
                                "${"%.2f".format(prevResult.score)} to ${"%.2f".format(currResult.score)}"
                    )
                )
            }
        }

        return regressions
    }

    data class RegressionInfo(
        val evalId: String,
        val profile: String,
        val previousScore: Double,
        val currentScore: Double,
        val type: RegressionType,
        val message: String
    )

    enum class RegressionType {
        PASS_TO_FAIL,
        SCORE_DROP
    }
}
