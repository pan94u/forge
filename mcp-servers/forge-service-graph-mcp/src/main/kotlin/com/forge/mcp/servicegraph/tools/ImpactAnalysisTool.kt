package com.forge.mcp.servicegraph.tools

import com.forge.mcp.common.*
import com.forge.mcp.servicegraph.McpTool
import com.forge.mcp.servicegraph.ServiceGraphStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Analyzes the impact of a change to a service on the broader service graph.
 *
 * Input:
 * - serviceName (string, required): The service being changed
 * - changeType (string, required): Type of change — api_change, schema_change, config_change
 *
 * Returns list of affected services, risk level, and recommended actions.
 */
class ImpactAnalysisTool : McpTool {

    private val logger = LoggerFactory.getLogger(ImpactAnalysisTool::class.java)

    override val definition = ToolDefinition(
        name = "impact_analysis",
        description = "Analyze the impact of a change to a service. Returns affected services, risk levels, and recommended actions based on the service dependency graph.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("serviceName") {
                    put("type", "string")
                    put("description", "Name of the service being changed")
                }
                putJsonObject("changeType") {
                    put("type", "string")
                    put("description", "Type of change being made")
                    putJsonArray("enum") {
                        add("api_change")
                        add("schema_change")
                        add("config_change")
                    }
                }
            }
            putJsonArray("required") {
                add("serviceName")
                add("changeType")
            }
        }
    )

    @Serializable
    data class AffectedService(
        val name: String,
        val team: String,
        val impactType: String,
        val riskLevel: String,
        val protocol: String,
        val distance: Int
    )

    @Serializable
    data class ImpactAnalysisResponse(
        val serviceName: String,
        val changeType: String,
        val overallRisk: String,
        val affectedServices: List<AffectedService>,
        val affectedTeams: List<String>,
        val recommendedActions: List<String>,
        val totalAffected: Int
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val serviceName = arguments["serviceName"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'serviceName' is required")

        val changeType = arguments["changeType"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'changeType' is required")

        val validChangeTypes = setOf("api_change", "schema_change", "config_change")
        if (changeType !in validChangeTypes) {
            throw McpError.InvalidArguments(
                "Invalid changeType '$changeType'. Must be one of: ${validChangeTypes.joinToString()}"
            )
        }

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

            // Find all services that depend on this service (upstream callers)
            val affectedServices = mutableListOf<AffectedService>()
            val visited = mutableSetOf<String>()
            val queue = ArrayDeque<Pair<String, Int>>()

            // Start with direct upstream dependencies
            val directUpstream = ServiceGraphStore.getUpstreamDependencies(serviceName)
            for (edge in directUpstream) {
                queue.add(edge.from to 1)
            }

            while (queue.isNotEmpty()) {
                val (current, distance) = queue.removeFirst()
                if (current in visited || distance > 5) continue
                visited.add(current)

                val currentService = ServiceGraphStore.getService(current)
                val directEdge = directUpstream.firstOrNull { it.from == current }
                    ?: ServiceGraphStore.getUpstreamDependencies(current)
                        .firstOrNull { it.to in visited || it.to == serviceName }

                val riskLevel = calculateRiskLevel(changeType, distance, directEdge?.isSynchronous ?: false)
                val impactType = when {
                    distance == 1 && directEdge?.isSynchronous == true -> "direct_synchronous"
                    distance == 1 -> "direct_asynchronous"
                    else -> "transitive"
                }

                affectedServices.add(
                    AffectedService(
                        name = current,
                        team = currentService?.team ?: "unknown",
                        impactType = impactType,
                        riskLevel = riskLevel,
                        protocol = directEdge?.protocol ?: "unknown",
                        distance = distance
                    )
                )

                // Continue traversal for significant changes
                if (changeType in setOf("api_change", "schema_change")) {
                    val upstream = ServiceGraphStore.getUpstreamDependencies(current)
                    for (edge in upstream) {
                        if (edge.from !in visited) {
                            queue.add(edge.from to (distance + 1))
                        }
                    }
                }
            }

            val affectedTeams = affectedServices.map { it.team }.distinct().sorted()
            val overallRisk = when {
                affectedServices.any { it.riskLevel == "critical" } -> "critical"
                affectedServices.any { it.riskLevel == "high" } -> "high"
                affectedServices.any { it.riskLevel == "medium" } -> "medium"
                affectedServices.isNotEmpty() -> "low"
                else -> "none"
            }

            val recommendations = generateRecommendations(changeType, affectedServices, affectedTeams)

            val response = ImpactAnalysisResponse(
                serviceName = serviceName,
                changeType = changeType,
                overallRisk = overallRisk,
                affectedServices = affectedServices.sortedBy { it.distance },
                affectedTeams = affectedTeams,
                recommendedActions = recommendations,
                totalAffected = affectedServices.size
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Impact analysis failed for '{}': {}", serviceName, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Impact analysis failed: ${e.message}")),
                isError = true
            )
        }
    }

    private fun calculateRiskLevel(changeType: String, distance: Int, isSynchronous: Boolean): String {
        return when {
            changeType == "api_change" && distance == 1 && isSynchronous -> "critical"
            changeType == "schema_change" && distance == 1 -> "high"
            changeType == "api_change" && distance == 1 -> "high"
            changeType == "api_change" && distance == 2 -> "medium"
            changeType == "config_change" && distance == 1 -> "medium"
            distance >= 3 -> "low"
            else -> "medium"
        }
    }

    private fun generateRecommendations(
        changeType: String,
        affected: List<AffectedService>,
        teams: List<String>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        when (changeType) {
            "api_change" -> {
                recommendations.add("Version the API endpoint (e.g., /v2/) to allow gradual migration")
                recommendations.add("Maintain backward compatibility for at least one release cycle")
                if (affected.any { it.riskLevel == "critical" }) {
                    recommendations.add("CRITICAL: Coordinate deployment with directly affected synchronous consumers")
                }
            }
            "schema_change" -> {
                recommendations.add("Use backward-compatible schema migrations (additive changes only)")
                recommendations.add("Run schema migration in a separate deployment step before code changes")
                recommendations.add("Verify all consumers can handle the new schema")
            }
            "config_change" -> {
                recommendations.add("Document the configuration change and notify affected teams")
                recommendations.add("Consider feature flags for gradual rollout")
            }
        }

        if (teams.size > 1) {
            recommendations.add("Notify teams: ${teams.joinToString(", ")}")
        }

        if (affected.size > 5) {
            recommendations.add("Consider a staged rollout plan due to high number of affected services (${affected.size})")
        }

        return recommendations
    }
}
