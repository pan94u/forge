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
 * Searches application logs via Loki or compatible log aggregation backend.
 *
 * Input:
 * - service (string, required): Service name to search logs for
 * - query (string, required): Log search query (keyword or LogQL expression)
 * - timeRange (string, required): Time range (e.g., "1h", "30m", "24h", "7d")
 * - level (string, optional): Log level filter (DEBUG, INFO, WARN, ERROR)
 *
 * Returns log entries with timestamp, level, message, and traceId.
 */
class LogSearchTool(
    private val lokiUrl: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(LogSearchTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 30_000
        }
    }

    override val definition = ToolDefinition(
        name = "log_search",
        description = "Search application logs across services. Supports keyword search and log level filtering. Returns log entries with timestamps, levels, messages, and trace IDs.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("service") {
                    put("type", "string")
                    put("description", "Service name to search logs for")
                }
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Log search query (keyword or LogQL expression)")
                }
                putJsonObject("timeRange") {
                    put("type", "string")
                    put("description", "Time range to search (e.g., '1h', '30m', '24h', '7d')")
                }
                putJsonObject("level") {
                    put("type", "string")
                    put("description", "Log level filter (optional)")
                    putJsonArray("enum") {
                        add("DEBUG")
                        add("INFO")
                        add("WARN")
                        add("ERROR")
                    }
                }
            }
            putJsonArray("required") {
                add("service")
                add("query")
                add("timeRange")
            }
        }
    )

    @Serializable
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val message: String,
        val traceId: String?,
        val spanId: String?,
        val logger: String?,
        val raw: String
    )

    @Serializable
    data class LogSearchResponse(
        val service: String,
        val entries: List<LogEntry>,
        val totalCount: Int,
        val timeRange: String,
        val query: String,
        val levelFilter: String?,
        val truncated: Boolean
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val service = arguments["service"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'service' is required")

        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val timeRange = arguments["timeRange"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'timeRange' is required")

        val level = arguments["level"]?.jsonPrimitive?.contentOrNull

        // Validate level
        val validLevels = setOf("DEBUG", "INFO", "WARN", "ERROR")
        if (level != null && level.uppercase() !in validLevels) {
            throw McpError.InvalidArguments(
                "Invalid log level '$level'. Must be one of: ${validLevels.joinToString()}"
            )
        }

        return try {
            val duration = parseTimeRange(timeRange)
            val end = Instant.now()
            val start = end.minus(duration)

            // Build LogQL query
            val logqlQuery = buildLogqlQuery(service, query, level)

            val response = httpClient.get("$lokiUrl/loki/api/v1/query_range") {
                parameter("query", logqlQuery)
                parameter("start", start.epochSecond.toString())
                parameter("end", end.epochSecond.toString())
                parameter("limit", 500)
                parameter("direction", "backward")
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Loki query returned status {}: query={}", response.status, logqlQuery)
                return ToolCallResponse(
                    content = listOf(
                        ToolContent.Text("Log search failed with status: ${response.status}")
                    ),
                    isError = true
                )
            }

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            val resultType = data?.get("resultType")?.jsonPrimitive?.contentOrNull
            val results = data?.get("result")?.jsonArray ?: JsonArray(emptyList())

            val entries = mutableListOf<LogEntry>()

            for (stream in results) {
                val streamObj = stream.jsonObject
                val streamLabels = streamObj["stream"]?.jsonObject ?: JsonObject(emptyMap())
                val values = streamObj["values"]?.jsonArray ?: continue

                for (value in values) {
                    val pair = value.jsonArray
                    if (pair.size < 2) continue

                    val timestampNanos = pair[0].jsonPrimitive.content
                    val logLine = pair[1].jsonPrimitive.content

                    val entry = parseLogLine(logLine, timestampNanos, streamLabels)
                    entries.add(entry)
                }
            }

            // Sort by timestamp descending (most recent first)
            val sortedEntries = entries.sortedByDescending { it.timestamp }
            val truncated = sortedEntries.size >= 500

            val searchResponse = LogSearchResponse(
                service = service,
                entries = sortedEntries.take(200),
                totalCount = sortedEntries.size,
                timeRange = timeRange,
                query = query,
                levelFilter = level,
                truncated = truncated
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(searchResponse))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Log search failed for service '{}': {}", service, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Log search failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Builds a LogQL query string from the search parameters.
     */
    private fun buildLogqlQuery(service: String, query: String, level: String?): String {
        val streamSelector = "{service=\"$service\"}"
        val filters = mutableListOf<String>()

        if (level != null) {
            filters.add("|= \"${level.uppercase()}\"")
        }

        // If the query is already a LogQL expression, use it directly
        if (query.startsWith("{") || query.startsWith("|")) {
            return query
        }

        // Treat as a keyword search
        filters.add("|~ \"(?i)$query\"")

        return "$streamSelector ${filters.joinToString(" ")}"
    }

    /**
     * Parses a raw log line into a structured LogEntry.
     * Handles common log formats: JSON, logback patterns, and plain text.
     */
    private fun parseLogLine(
        logLine: String,
        timestampNanos: String,
        streamLabels: JsonObject
    ): LogEntry {
        val timestamp = try {
            val nanos = timestampNanos.toLong()
            Instant.ofEpochSecond(nanos / 1_000_000_000, nanos % 1_000_000_000).toString()
        } catch (_: Exception) {
            timestampNanos
        }

        // Try parsing as JSON log
        try {
            val jsonLog = Json.parseToJsonElement(logLine).jsonObject
            return LogEntry(
                timestamp = jsonLog["timestamp"]?.jsonPrimitive?.contentOrNull
                    ?: jsonLog["@timestamp"]?.jsonPrimitive?.contentOrNull
                    ?: timestamp,
                level = jsonLog["level"]?.jsonPrimitive?.contentOrNull
                    ?: jsonLog["severity"]?.jsonPrimitive?.contentOrNull
                    ?: "INFO",
                message = jsonLog["message"]?.jsonPrimitive?.contentOrNull
                    ?: jsonLog["msg"]?.jsonPrimitive?.contentOrNull
                    ?: logLine,
                traceId = jsonLog["traceId"]?.jsonPrimitive?.contentOrNull
                    ?: jsonLog["trace_id"]?.jsonPrimitive?.contentOrNull,
                spanId = jsonLog["spanId"]?.jsonPrimitive?.contentOrNull
                    ?: jsonLog["span_id"]?.jsonPrimitive?.contentOrNull,
                logger = jsonLog["logger"]?.jsonPrimitive?.contentOrNull
                    ?: jsonLog["logger_name"]?.jsonPrimitive?.contentOrNull,
                raw = logLine
            )
        } catch (_: Exception) {
            // Not JSON, try pattern parsing
        }

        // Try logback-style pattern: timestamp LEVEL [thread] logger - message
        val logbackPattern = Regex(
            "^(\\S+\\s+\\S+)\\s+(\\w+)\\s+\\[([^]]+)]\\s+(\\S+)\\s+-\\s+(.*)"
        )
        val logbackMatch = logbackPattern.find(logLine)
        if (logbackMatch != null) {
            val (ts, lvl, _, lgr, msg) = logbackMatch.destructured
            val traceId = extractTraceId(logLine)
            return LogEntry(
                timestamp = timestamp,
                level = lvl,
                message = msg,
                traceId = traceId,
                spanId = null,
                logger = lgr,
                raw = logLine
            )
        }

        // Fallback: try to detect level from the raw line
        val level = when {
            logLine.contains("ERROR", ignoreCase = false) -> "ERROR"
            logLine.contains("WARN", ignoreCase = false) -> "WARN"
            logLine.contains("DEBUG", ignoreCase = false) -> "DEBUG"
            else -> "INFO"
        }

        return LogEntry(
            timestamp = timestamp,
            level = level,
            message = logLine,
            traceId = extractTraceId(logLine),
            spanId = null,
            logger = streamLabels["logger"]?.jsonPrimitive?.contentOrNull,
            raw = logLine
        )
    }

    /**
     * Tries to extract a trace ID from a log line.
     */
    private fun extractTraceId(logLine: String): String? {
        // Common trace ID patterns (32-char hex or UUID)
        val hexPattern = Regex("(?:trace[_-]?id|traceId)[=:]\\s*([a-fA-F0-9]{32})")
        val uuidPattern = Regex("(?:trace[_-]?id|traceId)[=:]\\s*([a-fA-F0-9-]{36})")

        return hexPattern.find(logLine)?.groupValues?.getOrNull(1)
            ?: uuidPattern.find(logLine)?.groupValues?.getOrNull(1)
    }

    /**
     * Parses a human-readable time range string into a Duration.
     * Supported formats: "30m", "1h", "24h", "7d", "30s"
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
