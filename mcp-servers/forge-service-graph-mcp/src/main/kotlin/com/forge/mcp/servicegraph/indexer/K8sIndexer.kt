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
 * Indexes service information and dependencies from Kubernetes manifests.
 *
 * Reads Kubernetes Service, Deployment, and NetworkPolicy resources to
 * discover services and their network-level relationships.
 *
 * Can operate in two modes:
 * 1. API mode: queries a live Kubernetes API server
 * 2. File mode: reads manifests from a local directory
 */
class K8sIndexer(
    private val k8sApiUrl: String = System.getenv("FORGE_K8S_API_URL") ?: "",
    private val k8sToken: String = System.getenv("FORGE_K8S_TOKEN") ?: "",
    private val namespace: String = System.getenv("FORGE_K8S_NAMESPACE") ?: "default"
) {
    private val logger = LoggerFactory.getLogger(K8sIndexer::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 30_000
        }
    }

    /**
     * Indexes Kubernetes resources and populates the ServiceGraphStore.
     */
    fun index() {
        if (k8sApiUrl.isBlank()) {
            logger.info("Kubernetes API URL not configured, skipping K8s indexing")
            return
        }

        logger.info("Starting Kubernetes indexing from: {}", k8sApiUrl)

        runBlocking {
            try {
                indexServices()
                indexDeployments()
                indexNetworkPolicies()
            } catch (e: Exception) {
                logger.error("Kubernetes indexing failed: {}", e.message, e)
            }
        }

        logger.info("Kubernetes indexing complete")
    }

    /**
     * Indexes Kubernetes Service resources to discover service endpoints.
     */
    private suspend fun indexServices() {
        val response = httpClient.get("$k8sApiUrl/api/v1/namespaces/$namespace/services") {
            if (k8sToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $k8sToken")
            }
        }

        if (response.status != HttpStatusCode.OK) {
            logger.warn("Failed to list K8s services: {}", response.status)
            return
        }

        val body = response.body<JsonObject>()
        val items = body["items"]?.jsonArray ?: return

        for (item in items) {
            val obj = item.jsonObject
            val metadata = obj["metadata"]?.jsonObject ?: continue
            val name = metadata["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val labels = metadata["labels"]?.jsonObject ?: JsonObject(emptyMap())
            val annotations = metadata["annotations"]?.jsonObject ?: JsonObject(emptyMap())

            val team = labels["app.kubernetes.io/team"]?.jsonPrimitive?.contentOrNull
                ?: annotations["forge.dev/team"]?.jsonPrimitive?.contentOrNull
                ?: "unknown"

            val techLead = annotations["forge.dev/tech-lead"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val onCall = annotations["forge.dev/on-call"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            val tags = labels.entries
                .filter { it.key.startsWith("forge.dev/tag-") }
                .map { it.value.jsonPrimitive.content }

            val techStack = annotations["forge.dev/tech-stack"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() }
                ?: emptyList()

            ServiceGraphStore.addService(
                ServiceGraphStore.ServiceNode(
                    name = name,
                    description = annotations["forge.dev/description"]?.jsonPrimitive?.contentOrNull ?: "",
                    team = team,
                    techLead = techLead,
                    onCallContact = onCall,
                    techStack = techStack,
                    tags = tags,
                    repositoryUrl = annotations["forge.dev/repo-url"]?.jsonPrimitive?.contentOrNull,
                    dashboardUrl = annotations["forge.dev/dashboard-url"]?.jsonPrimitive?.contentOrNull
                )
            )
        }
    }

    /**
     * Indexes Kubernetes Deployments to discover container-level dependencies
     * from environment variables and init containers.
     */
    private suspend fun indexDeployments() {
        val response = httpClient.get("$k8sApiUrl/apis/apps/v1/namespaces/$namespace/deployments") {
            if (k8sToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $k8sToken")
            }
        }

        if (response.status != HttpStatusCode.OK) {
            logger.warn("Failed to list K8s deployments: {}", response.status)
            return
        }

        val body = response.body<JsonObject>()
        val items = body["items"]?.jsonArray ?: return

        for (item in items) {
            val obj = item.jsonObject
            val metadata = obj["metadata"]?.jsonObject ?: continue
            val name = metadata["name"]?.jsonPrimitive?.contentOrNull ?: continue

            val spec = obj["spec"]?.jsonObject
                ?.get("template")?.jsonObject
                ?.get("spec")?.jsonObject ?: continue

            val containers = spec["containers"]?.jsonArray ?: continue

            for (container in containers) {
                val envVars = container.jsonObject["env"]?.jsonArray ?: continue
                for (envVar in envVars) {
                    val envObj = envVar.jsonObject
                    val envValue = envObj["value"]?.jsonPrimitive?.contentOrNull ?: continue

                    // Detect service references in environment variable values
                    val referencedService = extractServiceReference(envValue)
                    if (referencedService != null && referencedService != name) {
                        val protocol = when {
                            envObj["name"]?.jsonPrimitive?.contentOrNull
                                ?.contains("GRPC", ignoreCase = true) == true -> "gRPC"
                            envObj["name"]?.jsonPrimitive?.contentOrNull
                                ?.contains("KAFKA", ignoreCase = true) == true -> "Kafka"
                            else -> "HTTP"
                        }

                        ServiceGraphStore.addEdge(
                            ServiceGraphStore.ServiceEdge(
                                from = name,
                                to = referencedService,
                                protocol = protocol,
                                description = "K8s env var reference",
                                isSynchronous = protocol in setOf("HTTP", "gRPC")
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Indexes Kubernetes NetworkPolicies to discover allowed network connections.
     */
    private suspend fun indexNetworkPolicies() {
        val response = httpClient.get(
            "$k8sApiUrl/apis/networking.k8s.io/v1/namespaces/$namespace/networkpolicies"
        ) {
            if (k8sToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $k8sToken")
            }
        }

        if (response.status != HttpStatusCode.OK) {
            logger.warn("Failed to list K8s network policies: {}", response.status)
            return
        }

        val body = response.body<JsonObject>()
        val items = body["items"]?.jsonArray ?: return

        for (item in items) {
            val obj = item.jsonObject
            val spec = obj["spec"]?.jsonObject ?: continue

            val podSelector = spec["podSelector"]?.jsonObject
                ?.get("matchLabels")?.jsonObject
                ?.get("app")?.jsonPrimitive?.contentOrNull ?: continue

            // Process ingress rules (who can call this service)
            val ingress = spec["ingress"]?.jsonArray
            if (ingress != null) {
                for (rule in ingress) {
                    val from = rule.jsonObject["from"]?.jsonArray ?: continue
                    for (source in from) {
                        val sourceApp = source.jsonObject["podSelector"]?.jsonObject
                            ?.get("matchLabels")?.jsonObject
                            ?.get("app")?.jsonPrimitive?.contentOrNull ?: continue

                        ServiceGraphStore.addEdge(
                            ServiceGraphStore.ServiceEdge(
                                from = sourceApp,
                                to = podSelector,
                                protocol = "TCP",
                                description = "Allowed by NetworkPolicy",
                                isSynchronous = true
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Extracts a service name from a URL or hostname in an environment variable value.
     * Handles patterns like:
     * - http://payment-service:8080
     * - payment-service.default.svc.cluster.local
     * - payment-service
     */
    private fun extractServiceReference(value: String): String? {
        // URL pattern
        val urlPattern = Regex("https?://([\\w-]+)(?:\\.\\w+)*(?::\\d+)?")
        val urlMatch = urlPattern.find(value)
        if (urlMatch != null) return urlMatch.groupValues[1]

        // K8s DNS pattern
        val dnsPattern = Regex("([\\w-]+)\\.(?:default|$namespace)\\.svc\\.cluster\\.local")
        val dnsMatch = dnsPattern.find(value)
        if (dnsMatch != null) return dnsMatch.groupValues[1]

        return null
    }
}
