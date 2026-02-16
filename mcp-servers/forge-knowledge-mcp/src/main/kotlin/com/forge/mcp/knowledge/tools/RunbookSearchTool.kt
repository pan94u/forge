package com.forge.mcp.knowledge.tools

import com.forge.mcp.common.*
import com.forge.mcp.knowledge.McpTool
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

/**
 * Searches operational runbooks for incident response and procedures.
 *
 * Input:
 * - query (string, required): Search query for runbook content
 * - service (string, optional): Filter by service name
 *
 * Returns runbook title, steps summary, and related services.
 */
class RunbookSearchTool(
    private val wikiBaseUrl: String,
    private val wikiApiToken: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(RunbookSearchTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 15_000
        }
    }

    override val definition = ToolDefinition(
        name = "runbook_search",
        description = "Search operational runbooks for incident response procedures and operational guides. Returns runbook title, steps summary, and related services.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query for runbook content")
                }
                putJsonObject("service") {
                    put("type", "string")
                    put("description", "Filter runbooks by service name (optional)")
                }
            }
            putJsonArray("required") {
                add("query")
            }
        }
    )

    @Serializable
    data class RunbookResult(
        val title: String,
        val stepsSummary: List<String>,
        val relatedServices: List<String>,
        val severity: String,
        val url: String,
        val lastUpdated: String
    )

    @Serializable
    data class RunbookSearchResponse(
        val results: List<RunbookResult>,
        val totalCount: Int,
        val query: String,
        val serviceFilter: String?
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val service = arguments["service"]?.jsonPrimitive?.contentOrNull

        return try {
            val cqlParts = mutableListOf(
                "type = page",
                "label = \"runbook\"",
                "text ~ \"$query\""
            )
            if (!service.isNullOrBlank()) {
                cqlParts.add("label = \"service-$service\"")
            }
            val cql = cqlParts.joinToString(" AND ")

            val response = httpClient.get("$wikiBaseUrl/rest/api/content/search") {
                parameter("cql", cql)
                parameter("limit", 20)
                parameter("expand", "body.storage,metadata.labels,history.lastUpdated")
                if (wikiApiToken.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $wikiApiToken")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Runbook search returned status {}", response.status)
                return ToolCallResponse(
                    content = listOf(ToolContent.Text("Runbook search failed with status: ${response.status}")),
                    isError = true
                )
            }

            val body = response.body<JsonObject>()
            val resultsArray = body["results"]?.jsonArray ?: JsonArray(emptyList())

            val runbookResults = resultsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val pageId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

                val bodyContent = obj["body"]?.jsonObject
                    ?.get("storage")?.jsonObject
                    ?.get("value")?.jsonPrimitive?.contentOrNull ?: ""

                val labels = obj["metadata"]?.jsonObject
                    ?.get("labels")?.jsonObject
                    ?.get("results")?.jsonArray
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                    ?: emptyList()

                val relatedServices = labels
                    .filter { it.startsWith("service-") }
                    .map { it.removePrefix("service-") }

                val severity = labels.firstOrNull { it.startsWith("severity-") }
                    ?.removePrefix("severity-")
                    ?: "unknown"

                val lastUpdated = obj["history"]?.jsonObject
                    ?.get("lastUpdated")?.jsonObject
                    ?.get("when")?.jsonPrimitive?.contentOrNull ?: ""

                val steps = extractRunbookSteps(bodyContent)

                RunbookResult(
                    title = title,
                    stepsSummary = steps,
                    relatedServices = relatedServices,
                    severity = severity,
                    url = "$wikiBaseUrl/pages/viewpage.action?pageId=$pageId",
                    lastUpdated = lastUpdated
                )
            }

            val searchResponse = RunbookSearchResponse(
                results = runbookResults,
                totalCount = runbookResults.size,
                query = query,
                serviceFilter = service
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(searchResponse))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Runbook search failed for query '{}': {}", query, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Runbook search failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Extracts numbered or bulleted steps from runbook HTML content.
     */
    private fun extractRunbookSteps(htmlContent: String): List<String> {
        // Extract list items from the HTML
        val listItemPattern = Regex("<li[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
        val steps = listItemPattern.findAll(htmlContent)
            .map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
            .filter { it.isNotBlank() }
            .take(10)
            .toList()

        if (steps.isNotEmpty()) return steps

        // Fallback: split by numbered patterns (e.g., "1. ", "Step 1:")
        val plainText = htmlContent.replace(Regex("<[^>]*>"), "\n").trim()
        val numberedPattern = Regex("(?m)^\\s*(?:step\\s*)?\\d+[.):]+\\s*(.+)", RegexOption.IGNORE_CASE)
        return numberedPattern.findAll(plainText)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .take(10)
            .toList()
    }
}
