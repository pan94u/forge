package com.forge.eval.engine.notification

import com.forge.eval.engine.stats.RegressionDetector
import com.forge.eval.protocol.EvalReport
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Sends webhook notifications for evaluation events.
 *
 * Supports:
 * - Regression alerts (when pass rate drops significantly)
 * - Run completion notifications
 * - Lifecycle transition notifications
 */
class WebhookNotifier(
    private val webhookUrls: List<String> = emptyList(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) {
    private val logger = LoggerFactory.getLogger(WebhookNotifier::class.java)

    data class WebhookPayload(
        val event: String,
        val data: Map<String, Any>
    )

    /**
     * Notify about detected regressions.
     */
    fun notifyRegression(
        report: RegressionDetector.RegressionReport,
        suiteId: String,
        runId: String
    ) {
        if (!report.hasRegressions || webhookUrls.isEmpty()) return

        val payload = mapOf(
            "event" to "regression_detected",
            "suiteId" to suiteId,
            "runId" to runId,
            "regressionCount" to report.regressions.size,
            "regressions" to report.regressions.map { r ->
                mapOf(
                    "taskName" to r.taskName,
                    "type" to r.type.name,
                    "baselinePassRate" to r.baselinePassRate,
                    "currentPassRate" to r.currentPassRate,
                    "significant" to r.isStatisticallySignificant,
                    "message" to r.message
                )
            }
        )

        sendToAll(payload)
    }

    /**
     * Notify about run completion.
     */
    fun notifyRunCompleted(
        report: EvalReport
    ) {
        if (webhookUrls.isEmpty()) return

        val payload = mapOf(
            "event" to "run_completed",
            "runId" to report.runId.toString(),
            "suiteName" to report.suiteName,
            "platform" to report.platform.name,
            "passRate" to report.summary.overallPassRate,
            "totalTasks" to report.summary.totalTasks,
            "totalTrials" to report.summary.totalTrials,
            "passedTrials" to report.summary.passedTrials
        )

        sendToAll(payload)
    }

    /**
     * Notify about lifecycle transition.
     */
    fun notifyLifecycleTransition(
        taskId: String,
        taskName: String,
        fromLifecycle: String,
        toLifecycle: String,
        reason: String
    ) {
        if (webhookUrls.isEmpty()) return

        val payload = mapOf(
            "event" to "lifecycle_transition",
            "taskId" to taskId,
            "taskName" to taskName,
            "from" to fromLifecycle,
            "to" to toLifecycle,
            "reason" to reason
        )

        sendToAll(payload)
    }

    private fun sendToAll(payload: Map<String, Any>) {
        val json = toJson(payload)
        for (url in webhookUrls) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build()

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept { response ->
                        if (response.statusCode() in 200..299) {
                            logger.info("Webhook sent to {}: {}", url, response.statusCode())
                        } else {
                            logger.warn("Webhook failed {}: {} {}", url, response.statusCode(), response.body())
                        }
                    }
                    .exceptionally { e ->
                        logger.error("Webhook error {}: {}", url, e.message)
                        null
                    }
            } catch (e: Exception) {
                logger.error("Failed to send webhook to {}: {}", url, e.message)
            }
        }
    }

    private fun toJson(map: Map<String, Any>): String {
        // Simple JSON serialization without external dependency
        return buildString {
            append("{")
            map.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) append(",")
                append("\"$k\":")
                appendValue(v)
            }
            append("}")
        }
    }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            is String -> append("\"${value.replace("\"", "\\\"")}\"")
            is Number -> append(value)
            is Boolean -> append(value)
            is Map<*, *> -> {
                append("{")
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(",")
                    append("\"$k\":")
                    appendValue(v)
                }
                append("}")
            }
            is List<*> -> {
                append("[")
                value.forEachIndexed { i, v ->
                    if (i > 0) append(",")
                    appendValue(v)
                }
                append("]")
            }
            null -> append("null")
            else -> append("\"$value\"")
        }
    }
}
