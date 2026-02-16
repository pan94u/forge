package com.forge.mcp.servicegraph.tools

import com.forge.mcp.common.*
import com.forge.mcp.servicegraph.McpTool
import com.forge.mcp.servicegraph.ServiceGraphStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Lists services in the service graph with optional filtering by team or tag.
 *
 * Input:
 * - team (string, optional): Filter by owning team
 * - tag (string, optional): Filter by service tag
 *
 * Returns service name, description, owner team, and tech stack.
 */
class ServiceListTool : McpTool {

    private val logger = LoggerFactory.getLogger(ServiceListTool::class.java)

    override val definition = ToolDefinition(
        name = "service_list",
        description = "List services in the service dependency graph. Filter by team or tag. Returns service name, description, owner team, and tech stack.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("team") {
                    put("type", "string")
                    put("description", "Filter services by owning team name (optional)")
                }
                putJsonObject("tag") {
                    put("type", "string")
                    put("description", "Filter services by tag (optional)")
                }
            }
        }
    )

    @Serializable
    data class ServiceListEntry(
        val name: String,
        val description: String,
        val team: String,
        val techStack: List<String>,
        val tags: List<String>
    )

    @Serializable
    data class ServiceListResponse(
        val services: List<ServiceListEntry>,
        val totalCount: Int,
        val filters: Map<String, String>
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val team = arguments["team"]?.jsonPrimitive?.contentOrNull
        val tag = arguments["tag"]?.jsonPrimitive?.contentOrNull

        return try {
            var services = ServiceGraphStore.getAllServices()

            val filters = mutableMapOf<String, String>()

            if (team != null) {
                services = ServiceGraphStore.findServicesByTeam(team)
                filters["team"] = team
            }

            if (tag != null) {
                services = if (team != null) {
                    services.filter { tag in it.tags }
                } else {
                    ServiceGraphStore.findServicesByTag(tag)
                }
                filters["tag"] = tag
            }

            val entries = services.map { svc ->
                ServiceListEntry(
                    name = svc.name,
                    description = svc.description,
                    team = svc.team,
                    techStack = svc.techStack,
                    tags = svc.tags
                )
            }.sortedBy { it.name }

            val response = ServiceListResponse(
                services = entries,
                totalCount = entries.size,
                filters = filters
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: Exception) {
            logger.error("Service list query failed: {}", e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Service list query failed: ${e.message}")),
                isError = true
            )
        }
    }
}
