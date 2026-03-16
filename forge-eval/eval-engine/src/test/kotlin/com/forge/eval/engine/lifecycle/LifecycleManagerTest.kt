package com.forge.eval.engine.lifecycle

import com.forge.eval.protocol.Lifecycle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LifecycleManagerTest {

    private val manager = LifecycleManager()

    @Nested
    inner class CapabilityToRegression {

        @Test
        fun `should graduate when consecutive runs meet threshold`() {
            val runHistory = listOf(0.96, 0.97, 0.98, 0.99, 1.0)
            val passPowerK = listOf(0.92, 0.93, 0.95, 0.96, 0.98)

            val decision = manager.evaluate(Lifecycle.CAPABILITY, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isTrue()
            assertThat(decision.recommendedLifecycle).isEqualTo(Lifecycle.REGRESSION)
            assertThat(decision.reason).contains("满足毕业条件")
        }

        @Test
        fun `should not graduate when insufficient consecutive runs`() {
            val runHistory = listOf(0.50, 0.96, 0.97, 0.98, 0.99)
            val passPowerK = listOf(0.0, 0.92, 0.95, 0.96, 0.98)

            val decision = manager.evaluate(Lifecycle.CAPABILITY, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isFalse()
            assertThat(decision.recommendedLifecycle).isEqualTo(Lifecycle.CAPABILITY)
        }

        @Test
        fun `should not graduate when pass power k too low`() {
            val runHistory = listOf(0.96, 0.97, 0.98, 0.99, 1.0)
            val passPowerK = listOf(0.5, 0.6, 0.7, 0.75, 0.80)

            val decision = manager.evaluate(Lifecycle.CAPABILITY, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isFalse()
            assertThat(decision.reason).contains("Pass^k 未达标")
        }

        @Test
        fun `should not graduate with empty history`() {
            val decision = manager.evaluate(Lifecycle.CAPABILITY, emptyList(), emptyList())

            assertThat(decision.shouldTransition).isFalse()
            assertThat(decision.metrics.consecutivePassingRuns).isEqualTo(0)
        }

        @Test
        fun `should count consecutive runs from most recent`() {
            // Early failure then 5 passing
            val runHistory = listOf(0.5, 0.3, 0.96, 0.97, 0.98, 0.99, 1.0)
            val passPowerK = listOf(0.0, 0.0, 0.92, 0.93, 0.95, 0.96, 0.98)

            val decision = manager.evaluate(Lifecycle.CAPABILITY, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isTrue()
            assertThat(decision.metrics.consecutivePassingRuns).isEqualTo(5)
        }

        @Test
        fun `should handle single passing run`() {
            val decision = manager.evaluate(Lifecycle.CAPABILITY, listOf(1.0), listOf(1.0))

            assertThat(decision.shouldTransition).isFalse()
            assertThat(decision.metrics.consecutivePassingRuns).isEqualTo(1)
        }
    }

    @Nested
    inner class RegressionToSaturation {

        @Test
        fun `should saturate after 20 consecutive perfect runs`() {
            val runHistory = List(20) { 1.0 }
            val passPowerK = List(20) { 1.0 }

            val decision = manager.evaluate(Lifecycle.REGRESSION, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isTrue()
            assertThat(decision.recommendedLifecycle).isEqualTo(Lifecycle.SATURATED)
            assertThat(decision.reason).contains("饱和")
        }

        @Test
        fun `should not saturate with insufficient runs`() {
            val runHistory = List(10) { 1.0 }
            val passPowerK = List(10) { 1.0 }

            val decision = manager.evaluate(Lifecycle.REGRESSION, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isFalse()
            assertThat(decision.reason).contains("回归守护中")
        }

        @Test
        fun `should not saturate with imperfect pass power k`() {
            val runHistory = List(20) { 0.96 }
            val passPowerK = List(20) { 0.90 }

            val decision = manager.evaluate(Lifecycle.REGRESSION, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isFalse()
        }
    }

    @Nested
    inner class SaturatedState {

        @Test
        fun `saturated tasks stay saturated`() {
            val decision = manager.evaluate(Lifecycle.SATURATED, List(30) { 1.0 }, List(30) { 1.0 })

            assertThat(decision.shouldTransition).isFalse()
            assertThat(decision.recommendedLifecycle).isEqualTo(Lifecycle.SATURATED)
        }
    }

    @Nested
    inner class SaturatedTaskScheduling {

        @Test
        fun `should run saturated task at reduced frequency`() {
            assertThat(manager.shouldRunSaturatedTask(5)).isTrue()
            assertThat(manager.shouldRunSaturatedTask(10)).isTrue()
            assertThat(manager.shouldRunSaturatedTask(15)).isTrue()
        }

        @Test
        fun `should skip saturated task between scheduled runs`() {
            assertThat(manager.shouldRunSaturatedTask(1)).isFalse()
            assertThat(manager.shouldRunSaturatedTask(3)).isFalse()
            assertThat(manager.shouldRunSaturatedTask(7)).isFalse()
        }

        @Test
        fun `custom frequency should work`() {
            assertThat(manager.shouldRunSaturatedTask(3, frequency = 3)).isTrue()
            assertThat(manager.shouldRunSaturatedTask(4, frequency = 3)).isFalse()
        }
    }

    @Nested
    inner class CustomThresholds {

        @Test
        fun `custom graduation thresholds should apply`() {
            val customManager = LifecycleManager(
                requiredConsecutiveRuns = 3,
                graduationPassRate = 0.80,
                graduationPassPowerK = 0.70
            )

            val runHistory = listOf(0.85, 0.90, 0.95)
            val passPowerK = listOf(0.75, 0.80, 0.85)

            val decision = customManager.evaluate(Lifecycle.CAPABILITY, runHistory, passPowerK)

            assertThat(decision.shouldTransition).isTrue()
        }
    }
}
