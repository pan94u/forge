package com.forge.eval.engine.review

import com.forge.eval.protocol.EvalGrade
import com.forge.eval.protocol.GraderType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class ReviewTriggerRulesTest {

    @Nested
    inner class LowConfidence {

        @Test
        fun `should flag low confidence model grades`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val grades = listOf(
                makeGrade(GraderType.MODEL_BASED, score = 0.8, confidence = 0.5)
            )

            val decision = rules.shouldReview(grades)

            assertThat(decision.shouldReview).isTrue()
            assertThat(decision.reasons).anyMatch { it.rule == ReviewTriggerRules.ReviewRule.LOW_CONFIDENCE }
        }

        @Test
        fun `should not flag high confidence model grades`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val grades = listOf(
                makeGrade(GraderType.MODEL_BASED, score = 0.8, confidence = 0.9)
            )

            val decision = rules.shouldReview(grades)

            assertThat(decision.reasons.none { it.rule == ReviewTriggerRules.ReviewRule.LOW_CONFIDENCE }).isTrue()
        }

        @Test
        fun `should only check model-based grades for confidence`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val grades = listOf(
                makeGrade(GraderType.CODE_BASED, score = 0.8, confidence = 0.3)
            )

            val decision = rules.shouldReview(grades)

            assertThat(decision.reasons.none { it.rule == ReviewTriggerRules.ReviewRule.LOW_CONFIDENCE }).isTrue()
        }
    }

    @Nested
    inner class GraderDivergence {

        @Test
        fun `should flag when code and model scores diverge`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val grades = listOf(
                makeGrade(GraderType.CODE_BASED, score = 1.0, confidence = 1.0),
                makeGrade(GraderType.MODEL_BASED, score = 0.4, confidence = 0.8)
            )

            val decision = rules.shouldReview(grades)

            assertThat(decision.shouldReview).isTrue()
            assertThat(decision.reasons).anyMatch { it.rule == ReviewTriggerRules.ReviewRule.GRADER_DIVERGENCE }
        }

        @Test
        fun `should not flag when scores are close`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val grades = listOf(
                makeGrade(GraderType.CODE_BASED, score = 0.8, confidence = 1.0),
                makeGrade(GraderType.MODEL_BASED, score = 0.7, confidence = 0.9)
            )

            val decision = rules.shouldReview(grades)

            assertThat(decision.reasons.none { it.rule == ReviewTriggerRules.ReviewRule.GRADER_DIVERGENCE }).isTrue()
        }
    }

    @Nested
    inner class RandomSampling {

        @Test
        fun `should flag with 100% sampling rate`() {
            val rules = ReviewTriggerRules(samplingRate = 1.0)
            val grades = listOf(makeGrade(GraderType.CODE_BASED, score = 1.0, confidence = 1.0))

            val decision = rules.shouldReview(grades)

            assertThat(decision.reasons).anyMatch { it.rule == ReviewTriggerRules.ReviewRule.RANDOM_SAMPLE }
        }

        @Test
        fun `should not flag with 0% sampling rate`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0)
            val grades = listOf(makeGrade(GraderType.CODE_BASED, score = 1.0, confidence = 1.0))

            val decision = rules.shouldReview(grades)

            assertThat(decision.reasons.none { it.rule == ReviewTriggerRules.ReviewRule.RANDOM_SAMPLE }).isTrue()
        }
    }

    @Nested
    inner class BootstrapTask {

        @Test
        fun `should flag new tasks within bootstrap window`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val grades = listOf(makeGrade(GraderType.CODE_BASED, score = 1.0, confidence = 1.0))

            val decision = rules.shouldReview(grades, taskRunCount = 1)

            assertThat(decision.shouldReview).isTrue()
            assertThat(decision.reasons).anyMatch { it.rule == ReviewTriggerRules.ReviewRule.BOOTSTRAP_TASK }
        }

        @Test
        fun `should not flag mature tasks`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val grades = listOf(makeGrade(GraderType.CODE_BASED, score = 1.0, confidence = 1.0))

            val decision = rules.shouldReview(grades, taskRunCount = 10)

            assertThat(decision.reasons.none { it.rule == ReviewTriggerRules.ReviewRule.BOOTSTRAP_TASK }).isTrue()
        }
    }

    @Nested
    inner class MultipleReasons {

        @Test
        fun `can flag for multiple reasons simultaneously`() {
            val rules = ReviewTriggerRules(samplingRate = 1.0)
            val grades = listOf(
                makeGrade(GraderType.CODE_BASED, score = 1.0, confidence = 1.0),
                makeGrade(GraderType.MODEL_BASED, score = 0.3, confidence = 0.4)
            )

            val decision = rules.shouldReview(grades, taskRunCount = 1)

            assertThat(decision.shouldReview).isTrue()
            assertThat(decision.reasons.size).isGreaterThanOrEqualTo(3)
        }

        @Test
        fun `empty grades should not trigger review (except sampling and bootstrap)`() {
            val rules = ReviewTriggerRules(samplingRate = 0.0, random = Random(42))
            val decision = rules.shouldReview(emptyList())

            assertThat(decision.reasons.none { it.rule == ReviewTriggerRules.ReviewRule.LOW_CONFIDENCE }).isTrue()
            assertThat(decision.reasons.none { it.rule == ReviewTriggerRules.ReviewRule.GRADER_DIVERGENCE }).isTrue()
        }
    }

    private fun makeGrade(
        graderType: GraderType,
        score: Double,
        confidence: Double
    ) = EvalGrade(
        trialId = UUID.randomUUID(),
        graderType = graderType,
        score = score,
        passed = score >= 0.5,
        confidence = confidence
    )
}
