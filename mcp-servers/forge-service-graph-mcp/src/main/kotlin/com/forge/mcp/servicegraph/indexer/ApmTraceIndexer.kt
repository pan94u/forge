package com.forge.mcp.servicegraph.indexer

import com.forge.mcp.servicegraph.ServiceGraphStore
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Indexes service dependencies from APM (Application Performance Monitoring) trace data.
 *
 * Queries the APM backend (e.g., Jaeger, Zipkin, Datadog) for recent traces and
 * extracts inter-service call patterns to build the dependency graph.
 *
 * This provides the most accurate real-world dependency information as it is based
 * on actual traffic patterns rather than static analysis.
 */
class ApmTraceIndexer(
    private val apmBaseUrl: String = System.getenv("FORGE_APM_URL") ?: "",
    private val apmApiKey: String = System.getenv("FORGE_APM_API_KEY") ?: "",
    private val lookbackHours: Long = System.getenv("FORGE_APM_LOOKBACK_HOURS")?.toLongOrNull() ?: 24
) {
    private val logger = LoggerFactory.getLogger(ApmTraceIndexer::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 60_000
        }
    }

    /**
     * Indexes APM trace data and populates the ServiceGraphStore with
     * observed service dependencies.
     */
    fun index() {
        if (apmBaseUrl.isBlank()) {
            logger.info("APM URL not configured, skipping trace indexing")
            return
        }

        logger.info("Starting APM trace indexing from: {} (lookback: {}h)", apmBaseUrl, lookbackHours)

        runBlocking {
            try {
                val dependencies = fetchServiceDependencies()
                logger.info("Discovered {} service dependencies from APM traces", dependencies.size)

                for (dep in dependencies) {
                    ServiceGraphStore.addEdge(dep)
                }
            } catch (e: Exception) {
                logger.error("APM trace indexing failed: {}", e.message, e)
            }
        }

        logger.info("APM trace indexing complete")
    }

    /**
     * Fetches service dependency information from the APM backend.
     * Supports both Jaeger-style and generic dependency graph APIs.
     */
    private suspend fun fetchServiceDependencies(): List<ServiceGraphStore.ServiceEdge> {
        val endTime = Instant.now()
        val startTime = endTime.minus(lookbackHours, ChronoUnit.HOURS)

        // Try Jaeger-style dependencies API
        val jaegerDeps = tryJaegerDependencies(startTime, endTime)
        if (jaegerDeps.isNotEmpty()) return jaegerDeps

        // Try generic trace-based dependency extraction
        return tryTraceDependencyExtraction(startTime, endTime)
    }

    /**
     * Queries a Jaeger-compatible /api/dependencies endpoint.
     */
    private suspend fun tryJaegerDependencies(
        startTime: Instant,
        endTime: Instant
    ): List<ServiceGraphStore.ServiceEdge> {
        return try {
            val response = httpClient.get("$apmBaseUrl/api/dependencies") {
                parameter("endTs", endTime.toEpochMilli())
                parameter("lookback", lookbackHours * 3600 * 1000)
                if (apmApiKey.isNotBlank()) {
                    header("X-API-Key", apmApiKey)
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return emptyList()
            }

            val body = response.body<JsonArray>()
            body.mapNotNull { element ->
                val obj = element.jsonObject
                val parent = obj["parent"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val child = obj["child"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val callCount = obj["callCount"]?.jsonPrimitive?.longOrNull ?: 0

                ServiceGraphStore.ServiceEdge(
                    from = parent,
                    to = child,
                    protocol = inferProtocol(obj),
                    description = "Observed in APM traces (${callCount} calls in ${lookbackHours}h)",
                    isSynchronous = true // Traces typically capture synchronous calls
                )
            }
        } catch (e: Exception) {
            logger.debug("Jaeger dependencies API not available: {}", e.message)
            emptyList()
        }
    }

    /**
     * Extracts dependencies by sampling recent traces and analyzing span relationships.
     */
    private suspend fun tryTraceDependencyExtraction(
        startTime: Instant,
        endTime: Instant
    ): List<ServiceGraphStore.ServiceEdge> {
        return try {
            val response = httpClient.get("$apmBaseUrl/api/traces") {
                parameter("start", startTime.toEpochMilli())
                parameter("end", endTime.toEpochMilli())
                parameter("limit", 1000)
                if (apmApiKey.isNotBlank()) {
                    header("X-API-Key", apmApiKey)
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return emptyList()
            }

            val body = response.body<JsonObject>()
            val traces = body["data"]?.jsonArray ?: body["traces"]?.jsonArray ?: return emptyList()

            // Collect unique service-to-service edges
            val edgeSet = mutableSetOf<Triple<String, String, String>>()

            for (traceElement in traces) {
                val trace = traceElement.jsonObject
                val spans = trace["spans"]?.jsonArray ?: continue

                // Build span parent-child relationships
                val spanMap = mutableMapOf<String, JsonObject>()
                for (span in spans) {
                    val spanObj = span.jsonObject
                    val spanId = spanObj["spanID"]?.jsonPrimitive?.contentOrNull ?: continue
                    spanMap[spanId] = spanObj
                }

                for (span in spans) {
                    val spanObj = span.jsonObject
                    val serviceName = extractServiceName(spanObj) ?: continue
                    val references = spanObj["references"]?.jsonArray ?: continue

                    for (ref in references) {
                        val refObj = ref.jsonObject
                        val parentSpanId = refObj["spanID"]?.jsonPrimitive?.contentOrNull ?: continue
                        val parentSpan = spanMap[parentSpanId] ?: continue
                        val parentService = extractServiceName(parentSpan) ?: continue

                        if (parentService != serviceName) {
                            val protocol = extractProtocolFromSpan(spanObj)
                            edgeSet.add(Triple(parentService, serviceName, protocol))
                        }
                    }
                }
            }

            edgeSet.map { (from, to, protocol) ->
                ServiceGraphStore.ServiceEdge(
                    from = from,
                    to = to,
                    protocol = protocol,
                    description = "Observed in APM traces",
                    isSynchronous = protocol in setOf("HTTP", "gRPC")
                )
            }
        } catch (e: Exception) {
            logger.debug("Trace-based dependency extraction failed: {}", e.message)
            emptyList()
        }
    }

    /**
     * Extracts the service name from a span object.
     */
    private fun extractServiceName(span: JsonObject): String? {
        // Try process.serviceName (Jaeger format)
        val processServiceName = span["process"]?.jsonObject
            ?.get("serviceName")?.jsonPrimitive?.contentOrNull
        if (processServiceName != null) return processServiceName

        // Try service.name tag
        val tags = span["tags"]?.jsonArray ?: return null
        for (tag in tags) {
            val tagObj = tag.jsonObject
            if (tagObj["key"]?.jsonPrimitive?.contentOrNull == "service.name") {
                return tagObj["value"]?.jsonPrimitive?.contentOrNull
            }
        }

        return null
    }

    /**
     * Infers the protocol type from APM dependency data.
     */
    private fun inferProtocol(depObj: JsonObject): String {
        val source = depObj["source"]?.jsonPrimitive?.contentOrNull ?: ""
        return when {
            source.contains("grpc", ignoreCase = true) -> "gRPC"
            source.contains("kafka", ignoreCase = true) -> "Kafka"
            source.contains("rabbitmq", ignoreCase = true) -> "AMQP"
            else -> "HTTP"
        }
    }

    /**
     * Extracts protocol information from a span's tags.
     */
    private fun extractProtocolFromSpan(span: JsonObject): String {
        val tags = span["tags"]?.jsonArray ?: return "HTTP"
        for (tag in tags) {
            val tagObj = tag.jsonObject
            val key = tagObj["key"]?.jsonPrimitive?.contentOrNull ?: continue
            val value = tagObj["value"]?.jsonPrimitive?.contentOrNull ?: continue

            when {
                key == "component" && value.contains("grpc", ignoreCase = true) -> return "gRPC"
                key == "component" && value.contains("kafka", ignoreCase = true) -> return "Kafka"
                key == "http.method" -> return "HTTP"
                key == "messaging.system" -> return value.uppercase()
            }
        }
        return "HTTP"
    }
}
