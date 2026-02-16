package com.forge.mcp.servicegraph.tools

import com.forge.mcp.common.*
import com.forge.mcp.servicegraph.McpTool
import com.forge.mcp.servicegraph.ServiceGraphStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Gets upstream and downstream dependencies for a given service.
 *
 * Input:
 * - serviceName (string, required): The service to query dependencies for
 *
 * Returns upstream and downstream dependencies with protocol types.
 */
class DependenciesTool : McpTool {

    private val logger = LoggerFactory.getLogger(DependenciesTool::class.java)

    override val definition = ToolDefinition(
        name = "service_dependencies",
        description = "Get upstream and downstream dependencies for a service. Shows what a service depends on and what depends on it, including protocol types (HTTP, gRPC, Kafka, etc.).",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("serviceName") {
                    put("type", "string")
                    put("description", "Name of the service to query dependencies for")
                }
            }
            putJsonArray("required") {
                add("serviceName")
            }
        }
    )

    @Serializable
    data class DependencyEntry(
        val serviceName: String,
        val protocol: String,
        val description: String?,
        val isSynchronous: Boolean,
        val team: String?
    )

    @Serializable
    data class DependenciesResponse(
        val serviceName: String,
        val upstream: List<DependencyEntry>,
        val downstream: List<DependencyEntry>,
        val upstreamCount: Int,
        val downstreamCount: Int
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val serviceName = arguments["serviceName"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'serviceName' is required")

        return try {
            val service = ServiceGraphStore.getService(serviceName)
            if (service == null) {
                return ToolCallResponse(
                    content = listOf(
                        ToolContent.Text("Service '$serviceName' not found in the service graph.")
                    ),
                    isError = true
                )
            }

            // Downstream: services that this service calls
            val downstreamEdges = ServiceGraphStore.getDownstreamDependencies(serviceName)
            val downstream = downstreamEdges.map { edge ->
                val targetService = ServiceGraphStore.getService(edge.to)
                DependencyEntry(
                    serviceName = edge.to,
                    protocol = edge.protocol,
                    description = edge.description,
                    isSynchronous = edge.isSynchronous,
                    team = targetService?.team
                )
            }

            // Upstream: services that call this service
            val upstreamEdges = ServiceGraphStore.getUpstreamDependencies(serviceName)
            val upstream = upstreamEdges.map { edge ->
                val sourceService = ServiceGraphStore.getService(edge.from)
                DependencyEntry(
                    serviceName = edge.from,
                    protocol = edge.protocol,
                    description = edge.description,
                    isSynchronous = edge.isSynchronous,
                    team = sourceService?.team
                )
            }

            val response = DependenciesResponse(
                serviceName = serviceName,
                upstream = upstream,
                downstream = downstream,
                upstreamCount = upstream.size,
                downstreamCount = downstream.size
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Dependencies query failed for '{}': {}", serviceName, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Dependencies query failed: ${e.message}")),
                isError = true
            )
        }
    }
}
