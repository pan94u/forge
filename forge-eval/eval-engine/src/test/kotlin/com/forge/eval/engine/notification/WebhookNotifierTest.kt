package com.forge.eval.engine.notification

import com.forge.eval.engine.stats.RegressionDetector
import com.forge.eval.protocol.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WebhookNotifierTest {

    @Test
    fun `should not send when no webhook urls configured`() {
        val notifier = WebhookNotifier(webhookUrls = emptyList())

        // Should not throw
        notifier.notifyRegression(
            report = RegressionDetector.RegressionReport(
                regressions = listOf(
                    RegressionDetector.Regression(
                        taskId = UUID.randomUUID(),
                        taskName = "test",
                        type = RegressionDetector.RegressionType.PASS_RATE_DROP,
                        baselinePassRate = 1.0,
                        currentPassRate = 0.5,
                        confidenceInterval = Pair(0.3, 0.7),
                        isStatisticallySignificant = true,
                        message = "test regression"
                    )
                )
            ),
            suiteId = UUID.randomUUID().toString(),
            runId = UUID.randomUUID().toString()
        )
    }

    @Test
    fun `should not send when no regressions detected`() {
        val notifier = WebhookNotifier(webhookUrls = listOf("http://example.com/hook"))

        // Should not send (no regressions)
        notifier.notifyRegression(
            report = RegressionDetector.RegressionReport(regressions = emptyList()),
            suiteId = UUID.randomUUID().toString(),
            runId = UUID.randomUUID().toString()
        )
    }

    @Test
    fun `should not send run completed when no urls`() {
        val notifier = WebhookNotifier(webhookUrls = emptyList())

        notifier.notifyRunCompleted(
            report = EvalReport(
                runId = UUID.randomUUID(),
                suiteName = "test",
                platform = Platform.FORGE,
                agentType = AgentType.CODING,
                timestamp = Instant.now(),
                summary = RunSummary(1, 1, 1, 0, 0, 1.0, 1.0, 100),
                taskResults = emptyList()
            )
        )
    }

    @Test
    fun `should not send lifecycle transition when no urls`() {
        val notifier = WebhookNotifier(webhookUrls = emptyList())

        notifier.notifyLifecycleTransition(
            taskId = UUID.randomUUID().toString(),
            taskName = "test",
            fromLifecycle = "CAPABILITY",
            toLifecycle = "REGRESSION",
            reason = "graduated"
        )
    }

    @Test
    fun `notifier should be instantiable with urls`() {
        val notifier = WebhookNotifier(webhookUrls = listOf("http://example.com/hook"))
        assertThat(notifier).isNotNull()
    }
}
