package com.forge.mcp.servicegraph.tools

import com.forge.mcp.common.*
import com.forge.mcp.servicegraph.McpTool
import com.forge.mcp.servicegraph.ServiceGraphStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Finds the ownership information for a given service.
 *
 * Input:
 * - serviceName (string, required): The service to look up
 *
 * Returns team name, tech lead, and on-call contact.
 */
class OwnerTool : McpTool {

    private val logger = LoggerFactory.getLogger(OwnerTool::class.java)

    override val definition = ToolDefinition(
        name = "service_owner",
        description = "Find the ownership information for a service: team name, tech lead, on-call contact, repository URL, and dashboard URL.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("serviceName") {
                    put("type", "string")
                    put("description", "Name of the service to look up ownership for")
                }
            }
            putJsonArray("required") {
                add("serviceName")
            }
        }
    )

    @Serializable
    data class OwnershipInfo(
        val serviceName: String,
        val team: String,
        val techLead: String,
        val onCallContact: String,
        val repositoryUrl: String?,
        val dashboardUrl: String?,
        val techStack: List<String>,
        val tags: List<String>
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

            val ownerInfo = OwnershipInfo(
                serviceName = service.name,
                team = service.team,
                techLead = service.techLead,
                onCallContact = service.onCallContact,
                repositoryUrl = service.repositoryUrl,
                dashboardUrl = service.dashboardUrl,
                techStack = service.techStack,
                tags = service.tags
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(ownerInfo))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Owner lookup failed for '{}': {}", serviceName, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Owner lookup failed: ${e.message}")),
                isError = true
            )
        }
    }
}
