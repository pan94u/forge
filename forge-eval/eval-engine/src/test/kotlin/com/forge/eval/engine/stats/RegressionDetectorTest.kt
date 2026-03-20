package com.forge.eval.engine.stats

import com.forge.eval.protocol.RunSummary
import com.forge.eval.protocol.TrialOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RegressionDetectorTest {

    private val detector = RegressionDetector()
    private val task1 = UUID.randomUUID()
    private val task2 = UUID.randomUUID()

    @Test
    fun `no regression when current matches baseline`() {
        val baseline = mapOf(task1 to List(5) { TrialOutcome.PASS })
        val current = mapOf(task1 to List(5) { TrialOutcome.PASS })

        val report = detector.detectRegressions(current, baseline)
        assertThat(report.hasRegressions).isFalse()
    }

    @Test
    fun `detects pass to fail regression`() {
        val baseline = mapOf(task1 to List(5) { TrialOutcome.PASS })
        val current = mapOf(task1 to listOf(
            TrialOutcome.PASS, TrialOutcome.FAIL, TrialOutcome.FAIL,
            TrialOutcome.FAIL, TrialOutcome.FAIL
        ))
        val names = mapOf(task1 to "test-task")

        val report = detector.detectRegressions(current, baseline, names)
        assertThat(report.hasRegressions).isTrue()
        assertThat(report.regressions).hasSize(1)
        assertThat(report.regressions[0].type).isEqualTo(RegressionDetector.RegressionType.PASS_TO_FAIL)
        assertThat(report.regressions[0].taskName).isEqualTo("test-task")
        assertThat(report.regressions[0].isStatisticallySignificant).isTrue()
    }

    @Test
    fun `detects pass rate drop`() {
        val baseline = mapOf(task1 to listOf(
            TrialOutcome.PASS, TrialOutcome.PASS, TrialOutcome.PASS,
            TrialOutcome.PASS, TrialOutcome.FAIL
        ))
        val current = mapOf(task1 to listOf(
            TrialOutcome.PASS, TrialOutcome.FAIL, TrialOutcome.FAIL,
            TrialOutcome.FAIL, TrialOutcome.FAIL
        ))

        val report = detector.detectRegressions(current, baseline)
        assertThat(report.hasRegressions).isTrue()
        assertThat(report.regressions[0].type).isEqualTo(RegressionDetector.RegressionType.PASS_RATE_DROP)
    }

    @Test
    fun `small variance not flagged as significant`() {
        // 4/5 vs 5/5 with baseline rate 0.8 — small drop, not significant
        val baseline = mapOf(task1 to listOf(
            TrialOutcome.PASS, TrialOutcome.PASS, TrialOutcome.PASS,
            TrialOutcome.PASS, TrialOutcome.FAIL
        ))
        val current = mapOf(task1 to listOf(
            TrialOutcome.PASS, TrialOutcome.PASS, TrialOutcome.PASS,
            TrialOutcome.FAIL, TrialOutcome.FAIL
        ))

        val report = detector.detectRegressions(current, baseline)
        assertThat(report.hasRegressions).isTrue()
        assertThat(report.regressions[0].isStatisticallySignificant).isFalse()
    }

    @Test
    fun `multiple tasks - only regressed ones reported`() {
        val baseline = mapOf(
            task1 to List(5) { TrialOutcome.PASS },
            task2 to List(5) { TrialOutcome.PASS }
        )
        val current = mapOf(
            task1 to List(5) { TrialOutcome.PASS },
            task2 to List(5) { TrialOutcome.FAIL }
        )

        val report = detector.detectRegressions(current, baseline)
        assertThat(report.regressions).hasSize(1)
        assertThat(report.regressions[0].taskId).isEqualTo(task2)
    }

    @Test
    fun `task missing in current - skipped`() {
        val baseline = mapOf(task1 to List(5) { TrialOutcome.PASS })
        val current = emptyMap<UUID, List<TrialOutcome>>()

        val report = detector.detectRegressions(current, baseline)
        assertThat(report.hasRegressions).isFalse()
    }

    @Test
    fun `compareRuns - detects overall regression`() {
        val baseline = RunSummary(
            totalTasks = 5, totalTrials = 25, passedTrials = 20,
            failedTrials = 5, errorTrials = 0, overallPassRate = 0.8,
            averageScore = 0.8, totalDurationMs = 1000
        )
        val current = RunSummary(
            totalTasks = 5, totalTrials = 25, passedTrials = 5,
            failedTrials = 20, errorTrials = 0, overallPassRate = 0.2,
            averageScore = 0.2, totalDurationMs = 1000
        )

        val report = detector.compareRuns(current, baseline)
        assertThat(report.hasRegressions).isTrue()
        assertThat(report.regressions[0].isStatisticallySignificant).isTrue()
    }

    @Test
    fun `compareRuns - no regression when improved`() {
        val baseline = RunSummary(
            totalTasks = 5, totalTrials = 25, passedTrials = 20,
            failedTrials = 5, errorTrials = 0, overallPassRate = 0.8,
            averageScore = 0.8, totalDurationMs = 1000
        )
        val current = RunSummary(
            totalTasks = 5, totalTrials = 25, passedTrials = 23,
            failedTrials = 2, errorTrials = 0, overallPassRate = 0.92,
            averageScore = 0.92, totalDurationMs = 1000
        )

        val report = detector.compareRuns(current, baseline)
        assertThat(report.hasRegressions).isFalse()
    }

    @Test
    fun `confidence interval is populated`() {
        val baseline = mapOf(task1 to List(10) { TrialOutcome.PASS })
        val current = mapOf(task1 to (List(5) { TrialOutcome.PASS } + List(5) { TrialOutcome.FAIL }))

        val report = detector.detectRegressions(current, baseline)
        val ci = report.regressions[0].confidenceInterval
        assertThat(ci.first).isGreaterThan(0.0)
        assertThat(ci.second).isLessThan(1.0)
        assertThat(ci.first).isLessThan(ci.second)
    }
}
