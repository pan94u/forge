package com.forge.mcp.common

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Snapshot of all collected metrics at a point in time.
 */
@Serializable
data class MetricsSnapshot(
    val totalRequests: Long,
    val totalErrors: Long,
    val toolCalls: Map<String, ToolMetrics>,
    val uptimeMs: Long
)

/**
 * Per-tool metrics including call count, error count, and latency distribution.
 */
@Serializable
data class ToolMetrics(
    val callCount: Long,
    val errorCount: Long,
    val totalLatencyMs: Long,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val avgLatencyMs: Double,
    val p50LatencyMs: Long,
    val p95LatencyMs: Long,
    val p99LatencyMs: Long
)

/**
 * Thread-safe metrics collector for MCP tool calls.
 *
 * Tracks total requests, per-tool call counts, error counts, and latency
 * distributions. Latency histograms are maintained using a reservoir sampling
 * approach bounded to prevent unbounded memory growth.
 */
object McpMetrics {

    private val startTimeMs = System.currentTimeMillis()
    private val totalRequests = AtomicLong(0)
    private val totalErrors = AtomicLong(0)

    /**
     * Per-tool accumulator storing counts and latency samples.
     */
    private class ToolAccumulator {
        val callCount = AtomicLong(0)
        val errorCount = AtomicLong(0)
        val totalLatencyMs = AtomicLong(0)
        val minLatencyMs = AtomicLong(Long.MAX_VALUE)
        val maxLatencyMs = AtomicLong(0)

        // Bounded reservoir of latency samples for percentile calculation.
        // Using synchronized access on this list.
        private val latencySamples = mutableListOf<Long>()
        private val maxSamples = 10_000

        fun record(durationMs: Long, success: Boolean) {
            callCount.incrementAndGet()
            totalLatencyMs.addAndGet(durationMs)

            // Update min atomically
            var currentMin = minLatencyMs.get()
            while (durationMs < currentMin) {
                if (minLatencyMs.compareAndSet(currentMin, durationMs)) break
                currentMin = minLatencyMs.get()
            }

            // Update max atomically
            var currentMax = maxLatencyMs.get()
            while (durationMs > currentMax) {
                if (maxLatencyMs.compareAndSet(currentMax, durationMs)) break
                currentMax = maxLatencyMs.get()
            }

            if (!success) {
                errorCount.incrementAndGet()
            }

            synchronized(latencySamples) {
                if (latencySamples.size >= maxSamples) {
                    // Evict oldest quarter when reservoir is full
                    val evictCount = maxSamples / 4
                    repeat(evictCount) { latencySamples.removeFirst() }
                }
                latencySamples.add(durationMs)
            }
        }

        fun snapshot(): ToolMetrics {
            val count = callCount.get()
            val total = totalLatencyMs.get()
            val min = minLatencyMs.get().let { if (it == Long.MAX_VALUE) 0L else it }
            val max = maxLatencyMs.get()
            val avg = if (count > 0) total.toDouble() / count else 0.0

            val sortedSamples: List<Long>
            synchronized(latencySamples) {
                sortedSamples = latencySamples.sorted()
            }

            return ToolMetrics(
                callCount = count,
                errorCount = errorCount.get(),
                totalLatencyMs = total,
                minLatencyMs = min,
                maxLatencyMs = max,
                avgLatencyMs = avg,
                p50LatencyMs = percentile(sortedSamples, 50.0),
                p95LatencyMs = percentile(sortedSamples, 95.0),
                p99LatencyMs = percentile(sortedSamples, 99.0)
            )
        }

        private fun percentile(sorted: List<Long>, percentile: Double): Long {
            if (sorted.isEmpty()) return 0L
            val index = ((percentile / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
            return sorted[index]
        }
    }

    private val toolAccumulators = ConcurrentHashMap<String, ToolAccumulator>()

    /**
     * Records a tool call with its duration and success/failure status.
     *
     * @param toolName   the name of the tool that was called
     * @param durationMs how long the call took in milliseconds
     * @param success    whether the call completed successfully
     */
    fun recordToolCall(toolName: String, durationMs: Long, success: Boolean) {
        totalRequests.incrementAndGet()
        if (!success) {
            totalErrors.incrementAndGet()
        }
        val accumulator = toolAccumulators.computeIfAbsent(toolName) { ToolAccumulator() }
        accumulator.record(durationMs, success)
    }

    /**
     * Returns a snapshot of all metrics at the current point in time.
     */
    fun getMetrics(): MetricsSnapshot {
        val toolSnapshots = toolAccumulators.mapValues { (_, acc) -> acc.snapshot() }
        return MetricsSnapshot(
            totalRequests = totalRequests.get(),
            totalErrors = totalErrors.get(),
            toolCalls = toolSnapshots,
            uptimeMs = System.currentTimeMillis() - startTimeMs
        )
    }

    /**
     * Resets all metrics. Primarily intended for testing.
     */
    fun reset() {
        totalRequests.set(0)
        totalErrors.set(0)
        toolAccumulators.clear()
    }
}

/**
 * Registers the /metrics endpoint on the given [Route].
 */
fun Route.metricsRoute() {
    get("/metrics") {
        call.respond(HttpStatusCode.OK, McpMetrics.getMetrics())
    }
}
