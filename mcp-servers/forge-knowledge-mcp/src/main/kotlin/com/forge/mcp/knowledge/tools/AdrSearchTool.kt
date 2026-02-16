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
 * Searches Architecture Decision Records (ADRs).
 *
 * Input:
 * - query (string, required): Search query for ADR content
 * - status (string, optional): Filter by status — proposed, accepted, deprecated, superseded
 *
 * Returns matching ADRs with title, status, date, and summary.
 */
class AdrSearchTool(
    private val wikiBaseUrl: String,
    private val wikiApiToken: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(AdrSearchTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 15_000
        }
    }

    override val definition = ToolDefinition(
        name = "adr_search",
        description = "Search Architecture Decision Records (ADRs). Returns ADR title, status, date, and summary.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query for ADR content")
                }
                putJsonObject("status") {
                    put("type", "string")
                    put("description", "Filter by ADR status")
                    putJsonArray("enum") {
                        add("proposed")
                        add("accepted")
                        add("deprecated")
                        add("superseded")
                    }
                }
            }
            putJsonArray("required") {
                add("query")
            }
        }
    )

    @Serializable
    data class AdrResult(
        val id: String,
        val title: String,
        val status: String,
        val date: String,
        val summary: String,
        val url: String,
        val supersededBy: String? = null
    )

    @Serializable
    data class AdrSearchResponse(
        val results: List<AdrResult>,
        val totalCount: Int,
        val query: String,
        val statusFilter: String?
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val status = arguments["status"]?.jsonPrimitive?.contentOrNull

        // Validate status if provided
        val validStatuses = setOf("proposed", "accepted", "deprecated", "superseded")
        if (status != null && status !in validStatuses) {
            throw McpError.InvalidArguments(
                "Invalid status '$status'. Must be one of: ${validStatuses.joinToString()}"
            )
        }

        return try {
            val cqlParts = mutableListOf(
                "type = page",
                "label = \"adr\"",
                "text ~ \"$query\""
            )
            if (status != null) {
                cqlParts.add("label = \"adr-$status\"")
            }
            val cql = cqlParts.joinToString(" AND ")

            val response = httpClient.get("$wikiBaseUrl/rest/api/content/search") {
                parameter("cql", cql)
                parameter("limit", 25)
                parameter("expand", "body.storage,metadata.labels,history")
                if (wikiApiToken.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $wikiApiToken")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("ADR search returned status {}", response.status)
                return ToolCallResponse(
                    content = listOf(ToolContent.Text("ADR search failed with status: ${response.status}")),
                    isError = true
                )
            }

            val body = response.body<JsonObject>()
            val resultsArray = body["results"]?.jsonArray ?: JsonArray(emptyList())

            val adrResults = resultsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val pageId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

                val labels = obj["metadata"]?.jsonObject
                    ?.get("labels")?.jsonObject
                    ?.get("results")?.jsonArray
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                    ?: emptyList()

                val adrStatus = when {
                    "adr-superseded" in labels -> "superseded"
                    "adr-deprecated" in labels -> "deprecated"
                    "adr-accepted" in labels -> "accepted"
                    "adr-proposed" in labels -> "proposed"
                    else -> "unknown"
                }

                val createdDate = obj["history"]?.jsonObject
                    ?.get("createdDate")?.jsonPrimitive?.contentOrNull ?: ""

                val bodyContent = obj["body"]?.jsonObject
                    ?.get("storage")?.jsonObject
                    ?.get("value")?.jsonPrimitive?.contentOrNull ?: ""

                // Extract summary from body — look for context/decision sections
                val summary = extractAdrSummary(bodyContent)

                // Check if superseded
                val supersededBy = if (adrStatus == "superseded") {
                    extractSupersededBy(bodyContent)
                } else null

                AdrResult(
                    id = pageId,
                    title = title,
                    status = adrStatus,
                    date = createdDate,
                    summary = summary,
                    url = "$wikiBaseUrl/pages/viewpage.action?pageId=$pageId",
                    supersededBy = supersededBy
                )
            }

            val searchResponse = AdrSearchResponse(
                results = adrResults,
                totalCount = adrResults.size,
                query = query,
                statusFilter = status
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(searchResponse))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("ADR search failed for query '{}': {}", query, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("ADR search failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Extracts a summary from ADR body content by looking for Decision or Context sections.
     */
    private fun extractAdrSummary(htmlContent: String): String {
        val plainText = htmlContent.replace(Regex("<[^>]*>"), " ").trim()

        // Try to find the Decision section
        val decisionPattern = Regex("(?i)decision[:\\s]+(.*?)(?=\\n\\n|status|consequences|$)", RegexOption.DOT_MATCHES_ALL)
        val decisionMatch = decisionPattern.find(plainText)
        if (decisionMatch != null) {
            return decisionMatch.groupValues[1].trim().take(300)
        }

        // Fall back to the Context section
        val contextPattern = Regex("(?i)context[:\\s]+(.*?)(?=\\n\\n|decision|$)", RegexOption.DOT_MATCHES_ALL)
        val contextMatch = contextPattern.find(plainText)
        if (contextMatch != null) {
            return contextMatch.groupValues[1].trim().take(300)
        }

        // Last resort: take the first 300 characters
        return plainText.take(300)
    }

    /**
     * Extracts the "superseded by" ADR reference from body content.
     */
    private fun extractSupersededBy(htmlContent: String): String? {
        val pattern = Regex("(?i)superseded\\s+by[:\\s]+([\\w\\-]+)")
        return pattern.find(htmlContent)?.groupValues?.getOrNull(1)
    }
}
