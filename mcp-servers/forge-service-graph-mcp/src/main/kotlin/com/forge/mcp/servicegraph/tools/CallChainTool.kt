package com.forge.mcp.servicegraph.tools

import com.forge.mcp.common.*
import com.forge.mcp.servicegraph.McpTool
import com.forge.mcp.servicegraph.ServiceGraphStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Traces all call chains (paths) between two services in the dependency graph.
 *
 * Input:
 * - fromService (string, required): The source service
 * - toService (string, required): The target service
 *
 * Returns all paths between the two services with protocol and hop details.
 */
class CallChainTool : McpTool {

    private val logger = LoggerFactory.getLogger(CallChainTool::class.java)

    override val definition = ToolDefinition(
        name = "call_chain",
        description = "Trace all call chains (paths) between two services in the dependency graph. Shows how requests flow from one service to another.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("fromService") {
                    put("type", "string")
                    put("description", "Name of the source service")
                }
                putJsonObject("toService") {
                    put("type", "string")
                    put("description", "Name of the target/destination service")
                }
            }
            putJsonArray("required") {
                add("fromService")
                add("toService")
            }
        }
    )

    @Serializable
    data class CallChainHop(
        val from: String,
        val to: String,
        val protocol: String,
        val isSynchronous: Boolean
    )

    @Serializable
    data class CallChain(
        val hops: List<CallChainHop>,
        val length: Int,
        val protocols: List<String>,
        val isFullySynchronous: Boolean
    )

    @Serializable
    data class CallChainResponse(
        val fromService: String,
        val toService: String,
        val chains: List<CallChain>,
        val shortestPath: Int?,
        val totalPaths: Int
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val fromService = arguments["fromService"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'fromService' is required")

        val toService = arguments["toService"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'toService' is required")

        return try {
            // Validate both services exist
            if (ServiceGraphStore.getService(fromService) == null) {
                return ToolCallResponse(
                    content = listOf(
                        ToolContent.Text("Source service '$fromService' not found in the service graph.")
                    ),
                    isError = true
                )
            }
            if (ServiceGraphStore.getService(toService) == null) {
                return ToolCallResponse(
                    content = listOf(
                        ToolContent.Text("Target service '$toService' not found in the service graph.")
                    ),
                    isError = true
                )
            }

            val paths = ServiceGraphStore.findAllPaths(fromService, toService)

            val chains = paths.map { edgePath ->
                val hops = edgePath.map { edge ->
                    CallChainHop(
                        from = edge.from,
                        to = edge.to,
                        protocol = edge.protocol,
                        isSynchronous = edge.isSynchronous
                    )
                }
                val protocols = hops.map { it.protocol }.distinct()
                CallChain(
                    hops = hops,
                    length = hops.size,
                    protocols = protocols,
                    isFullySynchronous = hops.all { it.isSynchronous }
                )
            }.sortedBy { it.length }

            val response = CallChainResponse(
                fromService = fromService,
                toService = toService,
                chains = chains.take(20), // Limit to 20 paths
                shortestPath = chains.firstOrNull()?.length,
                totalPaths = chains.size
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Call chain query failed from '{}' to '{}': {}", fromService, toService, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Call chain query failed: ${e.message}")),
                isError = true
            )
        }
    }
}
