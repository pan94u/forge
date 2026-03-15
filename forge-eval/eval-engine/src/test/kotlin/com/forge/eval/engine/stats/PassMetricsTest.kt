package com.forge.eval.engine.stats

import com.forge.eval.protocol.TrialOutcome
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PassMetricsTest {

    private val delta = Offset.offset(0.001)

    @Nested
    inner class PassAtKTests {

        @Test
        fun `all pass - returns 1`() {
            val outcomes = List(5) { TrialOutcome.PASS }
            assertThat(PassMetrics.passAtK(outcomes, 3)).isCloseTo(1.0, delta)
        }

        @Test
        fun `all fail - returns 0`() {
            val outcomes = List(5) { TrialOutcome.FAIL }
            assertThat(PassMetrics.passAtK(outcomes, 3)).isCloseTo(0.0, delta)
        }

        @Test
        fun `mixed outcomes - probability between 0 and 1`() {
            // 3 pass, 2 fail out of 5 trials, k=3
            val outcomes = listOf(
                TrialOutcome.PASS, TrialOutcome.PASS, TrialOutcome.PASS,
                TrialOutcome.FAIL, TrialOutcome.FAIL
            )
            val result = PassMetrics.passAtK(outcomes, 3)
            // P@3 = 1 - C(2,3)/C(5,3) = 1 - 0/10 = 1.0 (since C(2,3) = 0)
            assertThat(result).isCloseTo(1.0, delta)
        }

        @Test
        fun `1 pass 4 fail k=3`() {
            val outcomes = listOf(
                TrialOutcome.PASS,
                TrialOutcome.FAIL, TrialOutcome.FAIL, TrialOutcome.FAIL, TrialOutcome.FAIL
            )
            val result = PassMetrics.passAtK(outcomes, 3)
            // P@3 = 1 - C(4,3)/C(5,3) = 1 - 4/10 = 0.6
            assertThat(result).isCloseTo(0.6, delta)
        }

        @Test
        fun `empty outcomes - returns 0`() {
            assertThat(PassMetrics.passAtK(emptyList(), 3)).isCloseTo(0.0, delta)
        }

        @Test
        fun `k equals n - returns 1 if any pass`() {
            val outcomes = listOf(TrialOutcome.PASS, TrialOutcome.FAIL)
            assertThat(PassMetrics.passAtK(outcomes, 2)).isCloseTo(1.0, delta)
        }

        @Test
        fun `k=1 equals simple pass rate`() {
            val outcomes = listOf(
                TrialOutcome.PASS, TrialOutcome.FAIL, TrialOutcome.PASS,
                TrialOutcome.FAIL, TrialOutcome.PASS
            )
            val result = PassMetrics.passAtK(outcomes, 1)
            // P@1 = 1 - C(2,1)/C(5,1) = 1 - 2/5 = 0.6
            assertThat(result).isCloseTo(0.6, delta)
        }
    }

    @Nested
    inner class PassPowerKTests {

        @Test
        fun `all pass - returns 1`() {
            val outcomes = List(5) { TrialOutcome.PASS }
            assertThat(PassMetrics.passPowerK(outcomes, 3)).isCloseTo(1.0, delta)
        }

        @Test
        fun `all fail - returns 0`() {
            val outcomes = List(5) { TrialOutcome.FAIL }
            assertThat(PassMetrics.passPowerK(outcomes, 3)).isCloseTo(0.0, delta)
        }

        @Test
        fun `80 percent pass rate k=3`() {
            val outcomes = listOf(
                TrialOutcome.PASS, TrialOutcome.PASS, TrialOutcome.PASS,
                TrialOutcome.PASS, TrialOutcome.FAIL
            )
            // (4/5)^3 = 0.512
            assertThat(PassMetrics.passPowerK(outcomes, 3)).isCloseTo(0.512, delta)
        }

        @Test
        fun `50 percent pass rate k=3`() {
            val outcomes = listOf(TrialOutcome.PASS, TrialOutcome.FAIL)
            // (0.5)^3 = 0.125
            assertThat(PassMetrics.passPowerK(outcomes, 3)).isCloseTo(0.125, delta)
        }

        @Test
        fun `empty outcomes - returns 0`() {
            assertThat(PassMetrics.passPowerK(emptyList(), 3)).isCloseTo(0.0, delta)
        }
    }

    @Nested
    inner class PassRateTests {

        @Test
        fun `simple pass rate`() {
            val outcomes = listOf(TrialOutcome.PASS, TrialOutcome.FAIL, TrialOutcome.PASS)
            assertThat(PassMetrics.passRate(outcomes)).isCloseTo(0.6667, delta)
        }

        @Test
        fun `all pass`() {
            assertThat(PassMetrics.passRate(List(3) { TrialOutcome.PASS })).isCloseTo(1.0, delta)
        }

        @Test
        fun `empty`() {
            assertThat(PassMetrics.passRate(emptyList())).isCloseTo(0.0, delta)
        }
    }

    @Nested
    inner class WilsonScoreTests {

        @Test
        fun `50 percent with 100 samples`() {
            val (lower, upper) = PassMetrics.wilsonScoreInterval(50, 100)
            assertThat(lower).isGreaterThan(0.39)
            assertThat(upper).isLessThan(0.61)
            assertThat(lower).isLessThan(0.5)
            assertThat(upper).isGreaterThan(0.5)
        }

        @Test
        fun `all pass small sample`() {
            val (lower, upper) = PassMetrics.wilsonScoreInterval(3, 3)
            assertThat(lower).isGreaterThan(0.3)
            assertThat(upper).isCloseTo(1.0, delta)
        }

        @Test
        fun `zero total returns 0,0`() {
            val (lower, upper) = PassMetrics.wilsonScoreInterval(0, 0)
            assertThat(lower).isCloseTo(0.0, delta)
            assertThat(upper).isCloseTo(0.0, delta)
        }
    }

    @Nested
    inner class RegressionTests {

        @Test
        fun `significant regression detected`() {
            // 2 out of 20 pass vs 80% baseline — clearly a regression
            assertThat(PassMetrics.isSignificantRegression(2, 20, 0.8)).isTrue()
        }

        @Test
        fun `no regression when on target`() {
            // 16 out of 20 pass vs 80% baseline — on target
            assertThat(PassMetrics.isSignificantRegression(16, 20, 0.8)).isFalse()
        }

        @Test
        fun `borderline - not significant with small sample`() {
            // 2 out of 3 pass vs 80% baseline — too few samples to be significant
            assertThat(PassMetrics.isSignificantRegression(2, 3, 0.8)).isFalse()
        }

        @Test
        fun `zero total - no regression`() {
            assertThat(PassMetrics.isSignificantRegression(0, 0, 0.8)).isFalse()
        }
    }
}
