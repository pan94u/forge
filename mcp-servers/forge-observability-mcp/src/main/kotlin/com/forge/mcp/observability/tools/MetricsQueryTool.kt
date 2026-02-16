package com.forge.mcp.observability.tools

import com.forge.mcp.common.*
import com.forge.mcp.observability.McpTool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Queries metrics from Prometheus or compatible metrics backend.
 *
 * Input:
 * - metricName (string, required): Prometheus metric name
 * - service (string, required): Service label to filter
 * - timeRange (string, required): Time range (e.g., "1h", "30m", "24h")
 * - aggregation (string, required): Aggregation function (avg, sum, max, min, rate, count)
 *
 * Returns time series data points with timestamps and values.
 */
class MetricsQueryTool(
    private val prometheusUrl: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(MetricsQueryTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 30_000
        }
    }

    override val definition = ToolDefinition(
        name = "metrics_query",
        description = "Query time-series metrics from Prometheus. Supports aggregation functions (avg, sum, max, min, rate, count). Returns data points with timestamps.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("metricName") {
                    put("type", "string")
                    put("description", "Prometheus metric name (e.g., 'http_requests_total', 'jvm_memory_used_bytes')")
                }
                putJsonObject("service") {
                    put("type", "string")
                    put("description", "Service name to filter metrics for")
                }
                putJsonObject("timeRange") {
                    put("type", "string")
                    put("description", "Time range (e.g., '1h', '30m', '24h', '7d')")
                }
                putJsonObject("aggregation") {
                    put("type", "string")
                    put("description", "Aggregation function to apply")
                    putJsonArray("enum") {
                        add("avg")
                        add("sum")
                        add("max")
                        add("min")
                        add("rate")
                        add("count")
                    }
                }
            }
            putJsonArray("required") {
                add("metricName")
                add("service")
                add("timeRange")
                add("aggregation")
            }
        }
    )

    @Serializable
    data class DataPoint(
        val timestamp: String,
        val value: Double,
        val epochSeconds: Long
    )

    @Serializable
    data class MetricSeries(
        val labels: Map<String, String>,
        val dataPoints: List<DataPoint>
    )

    @Serializable
    data class MetricsQueryResponse(
        val metricName: String,
        val service: String,
        val aggregation: String,
        val timeRange: String,
        val series: List<MetricSeries>,
        val summary: MetricsSummary
    )

    @Serializable
    data class MetricsSummary(
        val totalDataPoints: Int,
        val seriesCount: Int,
        val min: Double?,
        val max: Double?,
        val avg: Double?,
        val current: Double?
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val metricName = arguments["metricName"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'metricName' is required")

        val service = arguments["service"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'service' is required")

        val timeRange = arguments["timeRange"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'timeRange' is required")

        val aggregation = arguments["aggregation"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'aggregation' is required")

        val validAggregations = setOf("avg", "sum", "max", "min", "rate", "count")
        if (aggregation !in validAggregations) {
            throw McpError.InvalidArguments(
                "Invalid aggregation '$aggregation'. Must be one of: ${validAggregations.joinToString()}"
            )
        }

        return try {
            val duration = parseTimeRange(timeRange)
            val end = Instant.now()
            val start = end.minus(duration)

            // Calculate step based on time range
            val step = calculateStep(duration)

            // Build PromQL query
            val promqlQuery = buildPromqlQuery(metricName, service, aggregation, duration)

            val response = httpClient.get("$prometheusUrl/api/v1/query_range") {
                parameter("query", promqlQuery)
                parameter("start", start.epochSecond.toString())
                parameter("end", end.epochSecond.toString())
                parameter("step", step)
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Prometheus query returned status {}", response.status)
                return ToolCallResponse(
                    content = listOf(
                        ToolContent.Text("Metrics query failed with status: ${response.status}")
                    ),
                    isError = true
                )
            }

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            val resultType = data?.get("resultType")?.jsonPrimitive?.contentOrNull
            val results = data?.get("result")?.jsonArray ?: JsonArray(emptyList())

            val allSeries = results.map { result ->
                val resultObj = result.jsonObject
                val metric = resultObj["metric"]?.jsonObject ?: JsonObject(emptyMap())
                val labels = metric.entries.associate { it.key to it.value.jsonPrimitive.content }

                val values = resultObj["values"]?.jsonArray ?: JsonArray(emptyList())
                val dataPoints = values.mapNotNull { v ->
                    val pair = v.jsonArray
                    if (pair.size < 2) return@mapNotNull null

                    val epochSeconds = pair[0].jsonPrimitive.double.toLong()
                    val value = pair[1].jsonPrimitive.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null

                    DataPoint(
                        timestamp = Instant.ofEpochSecond(epochSeconds).toString(),
                        value = value,
                        epochSeconds = epochSeconds
                    )
                }

                MetricSeries(
                    labels = labels,
                    dataPoints = dataPoints
                )
            }

            // Compute summary statistics
            val allValues = allSeries.flatMap { it.dataPoints }.map { it.value }
            val summary = MetricsSummary(
                totalDataPoints = allValues.size,
                seriesCount = allSeries.size,
                min = allValues.minOrNull(),
                max = allValues.maxOrNull(),
                avg = if (allValues.isNotEmpty()) allValues.average() else null,
                current = allSeries.firstOrNull()?.dataPoints?.lastOrNull()?.value
            )

            val queryResponse = MetricsQueryResponse(
                metricName = metricName,
                service = service,
                aggregation = aggregation,
                timeRange = timeRange,
                series = allSeries,
                summary = summary
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(queryResponse))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "Metrics query failed for metric '{}' service '{}': {}",
                metricName, service, e.message, e
            )
            ToolCallResponse(
                content = listOf(ToolContent.Text("Metrics query failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Builds a PromQL query with the specified aggregation.
     */
    private fun buildPromqlQuery(
        metricName: String,
        service: String,
        aggregation: String,
        duration: Duration
    ): String {
        val selector = "$metricName{service=\"$service\"}"
        val rangeVector = "[${formatDuration(duration)}]"

        return when (aggregation) {
            "rate" -> "rate($selector$rangeVector)"
            "avg" -> "avg_over_time($selector$rangeVector)"
            "sum" -> "sum_over_time($selector$rangeVector)"
            "max" -> "max_over_time($selector$rangeVector)"
            "min" -> "min_over_time($selector$rangeVector)"
            "count" -> "count_over_time($selector$rangeVector)"
            else -> selector
        }
    }

    /**
     * Calculates an appropriate step interval based on the total time range.
     */
    private fun calculateStep(duration: Duration): String {
        val seconds = duration.seconds
        return when {
            seconds <= 3600 -> "15s"         // <= 1h: 15-second intervals
            seconds <= 21600 -> "60s"        // <= 6h: 1-minute intervals
            seconds <= 86400 -> "300s"       // <= 24h: 5-minute intervals
            seconds <= 604800 -> "1800s"     // <= 7d: 30-minute intervals
            else -> "3600s"                  // > 7d: 1-hour intervals
        }
    }

    /**
     * Formats a Duration as a PromQL-compatible duration string.
     */
    private fun formatDuration(duration: Duration): String {
        val minutes = duration.toMinutes()
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h"
            else -> "${minutes / 1440}d"
        }
    }

    /**
     * Parses a human-readable time range string into a Duration.
     */
    private fun parseTimeRange(timeRange: String): Duration {
        val pattern = Regex("^(\\d+)([smhd])$")
        val match = pattern.matchEntire(timeRange.trim().lowercase())
            ?: throw McpError.InvalidArguments(
                "Invalid time range '$timeRange'. Use format like '30m', '1h', '24h', '7d'"
            )

        val value = match.groupValues[1].toLong()
        val unit = match.groupValues[2]

        return when (unit) {
            "s" -> Duration.ofSeconds(value)
            "m" -> Duration.ofMinutes(value)
            "h" -> Duration.ofHours(value)
            "d" -> Duration.ofDays(value)
            else -> throw McpError.InvalidArguments("Unknown time unit: $unit")
        }
    }
}
