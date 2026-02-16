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
 * Searches distributed traces from Jaeger or compatible tracing backend.
 *
 * Input:
 * - traceId (string, optional): Specific trace ID to look up
 * - service (string, optional): Filter by service name
 * - minDuration (string, optional): Minimum span duration (e.g., "100ms", "1s")
 *
 * Returns trace spans with timing, service, operation, status, and tags.
 */
class TraceSearchTool(
    private val jaegerUrl: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(TraceSearchTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 30_000
        }
    }

    override val definition = ToolDefinition(
        name = "trace_search",
        description = "Search distributed traces across services. Look up specific traces by ID, filter by service or minimum duration. Returns spans with timing, service, status, and tags.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("traceId") {
                    put("type", "string")
                    put("description", "Specific trace ID to look up (optional)")
                }
                putJsonObject("service") {
                    put("type", "string")
                    put("description", "Filter traces by service name (optional)")
                }
                putJsonObject("minDuration") {
                    put("type", "string")
                    put("description", "Minimum span duration filter, e.g., '100ms', '1s', '500us' (optional)")
                }
                putJsonObject("operation") {
                    put("type", "string")
                    put("description", "Filter by operation name (optional)")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Maximum number of traces to return (default: 20)")
                    put("default", 20)
                }
            }
        }
    )

    @Serializable
    data class SpanInfo(
        val traceId: String,
        val spanId: String,
        val parentSpanId: String?,
        val operationName: String,
        val serviceName: String,
        val startTime: String,
        val duration: String,
        val durationMs: Long,
        val status: String,
        val tags: Map<String, String>,
        val logs: List<SpanLog>
    )

    @Serializable
    data class SpanLog(
        val timestamp: String,
        val message: String
    )

    @Serializable
    data class TraceInfo(
        val traceId: String,
        val spans: List<SpanInfo>,
        val totalSpans: Int,
        val services: List<String>,
        val duration: String,
        val durationMs: Long,
        val rootOperation: String,
        val hasErrors: Boolean
    )

    @Serializable
    data class TraceSearchResponse(
        val traces: List<TraceInfo>,
        val totalTraces: Int,
        val searchParams: Map<String, String>
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val traceId = arguments["traceId"]?.jsonPrimitive?.contentOrNull
        val service = arguments["service"]?.jsonPrimitive?.contentOrNull
        val minDuration = arguments["minDuration"]?.jsonPrimitive?.contentOrNull
        val operation = arguments["operation"]?.jsonPrimitive?.contentOrNull
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 20

        // At least one search parameter is required
        if (traceId.isNullOrBlank() && service.isNullOrBlank()) {
            throw McpError.InvalidArguments(
                "At least 'traceId' or 'service' must be provided for trace search."
            )
        }

        return try {
            val traces = if (!traceId.isNullOrBlank()) {
                // Direct trace lookup
                val trace = fetchTraceById(traceId)
                if (trace != null) listOf(trace) else emptyList()
            } else {
                // Search by parameters
                searchTraces(service!!, operation, minDuration, limit)
            }

            val searchParams = mutableMapOf<String, String>()
            if (traceId != null) searchParams["traceId"] = traceId
            if (service != null) searchParams["service"] = service
            if (minDuration != null) searchParams["minDuration"] = minDuration
            if (operation != null) searchParams["operation"] = operation

            val searchResponse = TraceSearchResponse(
                traces = traces,
                totalTraces = traces.size,
                searchParams = searchParams
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(searchResponse))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Trace search failed: {}", e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Trace search failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Fetches a specific trace by its trace ID from Jaeger.
     */
    private suspend fun fetchTraceById(traceId: String): TraceInfo? {
        val response = httpClient.get("$jaegerUrl/api/traces/$traceId")

        if (response.status != HttpStatusCode.OK) {
            logger.warn("Jaeger trace lookup returned status {} for traceId={}", response.status, traceId)
            return null
        }

        val body = response.body<JsonObject>()
        val data = body["data"]?.jsonArray ?: return null
        if (data.isEmpty()) return null

        return parseTraceData(data[0].jsonObject)
    }

    /**
     * Searches for traces matching the given parameters.
     */
    private suspend fun searchTraces(
        service: String,
        operation: String?,
        minDuration: String?,
        limit: Int
    ): List<TraceInfo> {
        val end = Instant.now()
        val start = end.minus(Duration.ofHours(1))

        val response = httpClient.get("$jaegerUrl/api/traces") {
            parameter("service", service)
            if (operation != null) parameter("operation", operation)
            if (minDuration != null) parameter("minDuration", minDuration)
            parameter("limit", limit.coerceIn(1, 100))
            parameter("start", start.toEpochMilli() * 1000) // Jaeger uses microseconds
            parameter("end", end.toEpochMilli() * 1000)
            parameter("lookback", "1h")
        }

        if (response.status != HttpStatusCode.OK) {
            logger.warn("Jaeger trace search returned status {}", response.status)
            return emptyList()
        }

        val body = response.body<JsonObject>()
        val data = body["data"]?.jsonArray ?: return emptyList()

        return data.mapNotNull { traceElement ->
            try {
                parseTraceData(traceElement.jsonObject)
            } catch (e: Exception) {
                logger.warn("Failed to parse trace: {}", e.message)
                null
            }
        }
    }

    /**
     * Parses Jaeger trace data into our TraceInfo model.
     */
    private fun parseTraceData(traceData: JsonObject): TraceInfo? {
        val traceId = traceData["traceID"]?.jsonPrimitive?.contentOrNull ?: return null
        val spans = traceData["spans"]?.jsonArray ?: return null
        val processes = traceData["processes"]?.jsonObject ?: JsonObject(emptyMap())

        val spanInfos = spans.mapNotNull { spanElement ->
            val span = spanElement.jsonObject
            val spanId = span["spanID"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val operationName = span["operationName"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val startTimeUs = span["startTime"]?.jsonPrimitive?.longOrNull ?: 0L
            val durationUs = span["duration"]?.jsonPrimitive?.longOrNull ?: 0L

            val processId = span["processID"]?.jsonPrimitive?.contentOrNull ?: ""
            val serviceName = processes[processId]?.jsonObject
                ?.get("serviceName")?.jsonPrimitive?.contentOrNull ?: "unknown"

            val references = span["references"]?.jsonArray
            val parentSpanId = references?.firstOrNull()?.jsonObject
                ?.get("spanID")?.jsonPrimitive?.contentOrNull

            // Extract tags
            val tags = span["tags"]?.jsonArray
                ?.associate { tag ->
                    val tagObj = tag.jsonObject
                    val key = tagObj["key"]?.jsonPrimitive?.contentOrNull ?: ""
                    val value = tagObj["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    key to value
                } ?: emptyMap()

            // Determine status
            val hasError = tags["error"]?.equals("true", ignoreCase = true) == true ||
                tags["otel.status_code"]?.equals("ERROR", ignoreCase = true) == true ||
                tags["http.status_code"]?.toIntOrNull()?.let { it >= 400 } == true

            val status = when {
                hasError -> "ERROR"
                tags["http.status_code"] != null -> "HTTP ${tags["http.status_code"]}"
                else -> "OK"
            }

            // Extract span logs
            val logs = span["logs"]?.jsonArray?.mapNotNull { logElement ->
                val log = logElement.jsonObject
                val logTimestamp = log["timestamp"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val fields = log["fields"]?.jsonArray ?: return@mapNotNull null

                val message = fields.mapNotNull { field ->
                    val fieldObj = field.jsonObject
                    val key = fieldObj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val value = fieldObj["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    "$key=$value"
                }.joinToString(", ")

                SpanLog(
                    timestamp = Instant.ofEpochSecond(
                        logTimestamp / 1_000_000,
                        (logTimestamp % 1_000_000) * 1000
                    ).toString(),
                    message = message
                )
            } ?: emptyList()

            SpanInfo(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parentSpanId,
                operationName = operationName,
                serviceName = serviceName,
                startTime = Instant.ofEpochSecond(
                    startTimeUs / 1_000_000,
                    (startTimeUs % 1_000_000) * 1000
                ).toString(),
                duration = formatDuration(durationUs),
                durationMs = durationUs / 1000,
                status = status,
                tags = tags,
                logs = logs
            )
        }

        if (spanInfos.isEmpty()) return null

        val services = spanInfos.map { it.serviceName }.distinct()
        val rootSpan = spanInfos.firstOrNull { it.parentSpanId == null }
            ?: spanInfos.first()

        val totalDurationMs = spanInfos.maxOf { it.durationMs }

        return TraceInfo(
            traceId = traceId,
            spans = spanInfos.sortedBy { it.startTime },
            totalSpans = spanInfos.size,
            services = services,
            duration = formatDuration(totalDurationMs * 1000), // Convert ms to us
            durationMs = totalDurationMs,
            rootOperation = "${rootSpan.serviceName}::${rootSpan.operationName}",
            hasErrors = spanInfos.any { it.status == "ERROR" }
        )
    }

    /**
     * Formats a duration in microseconds to a human-readable string.
     */
    private fun formatDuration(microseconds: Long): String {
        return when {
            microseconds < 1000 -> "${microseconds}us"
            microseconds < 1_000_000 -> "${"%.2f".format(microseconds / 1000.0)}ms"
            else -> "${"%.2f".format(microseconds / 1_000_000.0)}s"
        }
    }
}
