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
 * Searches wiki/Confluence for pages matching a query.
 *
 * Input:
 * - query (string, required): The search query
 * - space (string, optional): Wiki space key to restrict search
 * - limit (int, optional, default 10): Maximum results to return
 *
 * Returns matching pages with title, excerpt, and URL.
 */
class WikiSearchTool(
    private val wikiBaseUrl: String,
    private val wikiApiToken: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(WikiSearchTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 15_000
        }
    }

    override val definition = ToolDefinition(
        name = "wiki_search",
        description = "Search wiki/Confluence pages for organizational knowledge. Returns matching pages with title, excerpt, and URL.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "The search query string")
                }
                putJsonObject("space") {
                    put("type", "string")
                    put("description", "Wiki space key to restrict search (optional)")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Maximum number of results to return (default: 10)")
                    put("default", 10)
                }
            }
            putJsonArray("required") {
                add("query")
            }
        }
    )

    @Serializable
    data class WikiSearchResult(
        val title: String,
        val excerpt: String,
        val url: String,
        val space: String,
        val lastModified: String
    )

    @Serializable
    data class WikiSearchResponse(
        val results: List<WikiSearchResult>,
        val totalCount: Int,
        val query: String
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val space = arguments["space"]?.jsonPrimitive?.contentOrNull
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 10

        return try {
            val cqlParts = mutableListOf("type = page")
            cqlParts.add("text ~ \"$query\"")
            if (!space.isNullOrBlank()) {
                cqlParts.add("space = \"$space\"")
            }
            val cql = cqlParts.joinToString(" AND ")

            val response = httpClient.get("$wikiBaseUrl/rest/api/content/search") {
                parameter("cql", cql)
                parameter("limit", limit)
                parameter("expand", "body.view,space,history.lastUpdated")
                if (wikiApiToken.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $wikiApiToken")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("Wiki search returned status {}: {}", response.status, query)
                return ToolCallResponse(
                    content = listOf(
                        ToolContent.Text("Wiki search failed with status: ${response.status}")
                    ),
                    isError = true
                )
            }

            val body = response.body<JsonObject>()
            val resultsArray = body["results"]?.jsonArray ?: JsonArray(emptyList())

            val searchResults = resultsArray.map { element ->
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                val pageId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""

                val bodyExcerpt = obj["body"]?.jsonObject
                    ?.get("view")?.jsonObject
                    ?.get("value")?.jsonPrimitive?.contentOrNull
                    ?.take(200)?.replace(Regex("<[^>]*>"), "")
                    ?: ""

                val spaceKey = obj["space"]?.jsonObject
                    ?.get("key")?.jsonPrimitive?.contentOrNull ?: ""

                val lastModified = obj["history"]?.jsonObject
                    ?.get("lastUpdated")?.jsonObject
                    ?.get("when")?.jsonPrimitive?.contentOrNull ?: ""

                WikiSearchResult(
                    title = title,
                    excerpt = bodyExcerpt.trim(),
                    url = "$wikiBaseUrl/pages/viewpage.action?pageId=$pageId",
                    space = spaceKey,
                    lastModified = lastModified
                )
            }

            val searchResponse = WikiSearchResponse(
                results = searchResults,
                totalCount = body["totalSize"]?.jsonPrimitive?.intOrNull ?: searchResults.size,
                query = query
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(searchResponse))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Wiki search failed for query '{}': {}", query, e.message, e)
            ToolCallResponse(
                content = listOf(
                    ToolContent.Text("Wiki search failed: ${e.message}")
                ),
                isError = true
            )
        }
    }
}
