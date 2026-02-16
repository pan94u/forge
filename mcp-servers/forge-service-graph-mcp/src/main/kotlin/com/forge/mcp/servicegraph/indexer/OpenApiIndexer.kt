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

/**
 * Indexes service dependencies from OpenAPI specification files.
 *
 * Reads OpenAPI specs (v3) to discover service endpoints and inter-service
 * references. Dependencies are inferred from:
 * 1. Server URLs referencing other services
 * 2. x-dependencies custom extension fields
 * 3. Webhook callback URLs
 */
class OpenApiIndexer(
    private val specRegistryUrl: String = System.getenv("FORGE_OPENAPI_REGISTRY_URL") ?: ""
) {
    private val logger = LoggerFactory.getLogger(OpenApiIndexer::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 30_000
        }
    }

    /**
     * Indexes OpenAPI specs from the spec registry and populates the
     * ServiceGraphStore with discovered service relationships.
     */
    fun index() {
        if (specRegistryUrl.isBlank()) {
            logger.info("OpenAPI spec registry URL not configured, skipping indexing")
            return
        }

        logger.info("Starting OpenAPI spec indexing from: {}", specRegistryUrl)

        runBlocking {
            try {
                val specList = fetchSpecList()
                logger.info("Found {} OpenAPI specs to index", specList.size)

                for ((serviceName, specUrl) in specList) {
                    try {
                        val spec = fetchSpec(specUrl)
                        processSpec(serviceName, spec)
                    } catch (e: Exception) {
                        logger.warn("Failed to process OpenAPI spec for '{}': {}", serviceName, e.message)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch OpenAPI spec list: {}", e.message, e)
            }
        }

        logger.info("OpenAPI spec indexing complete")
    }

    /**
     * Fetches the list of available OpenAPI specs from the registry.
     * Expects a JSON response with a list of {name, specUrl} objects.
     */
    private suspend fun fetchSpecList(): List<Pair<String, String>> {
        val response = httpClient.get("$specRegistryUrl/api/specs") {
            accept(ContentType.Application.Json)
        }

        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Spec registry returned status: ${response.status}")
        }

        val body = response.body<JsonArray>()
        return body.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = obj["specUrl"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            name to url
        }
    }

    /**
     * Fetches an individual OpenAPI spec.
     */
    private suspend fun fetchSpec(url: String): JsonObject {
        val response = httpClient.get(url) {
            accept(ContentType.Application.Json)
        }
        return response.body<JsonObject>()
    }

    /**
     * Processes an OpenAPI spec to extract dependency information.
     */
    private fun processSpec(serviceName: String, spec: JsonObject) {
        // Extract service info
        val info = spec["info"]?.jsonObject
        val description = info?.get("description")?.jsonPrimitive?.contentOrNull ?: ""

        // Look for x-dependencies extension
        val xDeps = spec["x-dependencies"]?.jsonArray
        if (xDeps != null) {
            for (dep in xDeps) {
                val depObj = dep.jsonObject
                val depName = depObj["service"]?.jsonPrimitive?.contentOrNull ?: continue
                val protocol = depObj["protocol"]?.jsonPrimitive?.contentOrNull ?: "HTTP"
                val depDescription = depObj["description"]?.jsonPrimitive?.contentOrNull

                ServiceGraphStore.addEdge(
                    ServiceGraphStore.ServiceEdge(
                        from = serviceName,
                        to = depName,
                        protocol = protocol,
                        description = depDescription,
                        isSynchronous = protocol.uppercase() in setOf("HTTP", "GRPC")
                    )
                )
            }
        }

        // Inspect server URLs for service references
        val servers = spec["servers"]?.jsonArray
        if (servers != null) {
            for (server in servers) {
                val url = server.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: continue
                val referencedService = extractServiceFromUrl(url)
                if (referencedService != null && referencedService != serviceName) {
                    ServiceGraphStore.addEdge(
                        ServiceGraphStore.ServiceEdge(
                            from = serviceName,
                            to = referencedService,
                            protocol = "HTTP",
                            description = "Server URL reference from OpenAPI spec",
                            isSynchronous = true
                        )
                    )
                }
            }
        }

        // Check webhooks for callback dependencies
        val webhooks = spec["webhooks"]?.jsonObject
        if (webhooks != null) {
            for ((webhookName, _) in webhooks) {
                val callbackService = extractServiceFromWebhookName(webhookName)
                if (callbackService != null) {
                    ServiceGraphStore.addEdge(
                        ServiceGraphStore.ServiceEdge(
                            from = serviceName,
                            to = callbackService,
                            protocol = "HTTP/Webhook",
                            description = "Webhook callback: $webhookName",
                            isSynchronous = false
                        )
                    )
                }
            }
        }

        logger.debug("Processed OpenAPI spec for service '{}'", serviceName)
    }

    /**
     * Attempts to extract a service name from a URL.
     * Handles patterns like https://payment-service.internal:8080/api
     */
    private fun extractServiceFromUrl(url: String): String? {
        val pattern = Regex("https?://([\\w-]+?)(?:\\.internal|\\.svc|\\.local)?(?::\\d+)?(?:/.*)?$")
        return pattern.find(url)?.groupValues?.getOrNull(1)
    }

    /**
     * Attempts to extract a target service name from a webhook name.
     * Handles patterns like "notify-order-service" or "order-service.order-completed"
     */
    private fun extractServiceFromWebhookName(name: String): String? {
        val pattern = Regex("(?:notify-)?([\\w-]+-service)")
        return pattern.find(name)?.groupValues?.getOrNull(1)
    }
}
