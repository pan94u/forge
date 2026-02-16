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
 * Searches API documentation across services.
 *
 * Input:
 * - query (string, required): Search query for API endpoints
 * - service (string, optional): Filter by service name
 *
 * Returns API endpoint, HTTP method, description, and request/response schema.
 */
class ApiDocSearchTool(
    private val wikiBaseUrl: String,
    private val wikiApiToken: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(ApiDocSearchTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 15_000
        }
    }

    override val definition = ToolDefinition(
        name = "api_doc_search",
        description = "Search API documentation across services. Returns API endpoint, method, description, and request/response schema.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query for API endpoints or documentation")
                }
                putJsonObject("service") {
                    put("type", "string")
                    put("description", "Filter results by service name (optional)")
                }
            }
            putJsonArray("required") {
                add("query")
            }
        }
    )

    @Serializable
    data class ApiEndpointResult(
        val endpoint: String,
        val method: String,
        val description: String,
        val service: String,
        val requestSchema: String?,
        val responseSchema: String?,
        val authentication: String,
        val url: String
    )

    @Serializable
    data class ApiDocSearchResponse(
        val results: List<ApiEndpointResult>,
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
                "label = \"api-doc\"",
                "text ~ \"$query\""
            )
            if (!service.isNullOrBlank()) {
                cqlParts.add("label = \"service-$service\"")
            }
            val cql = cqlParts.joinToString(" AND ")

            val response = httpClient.get("$wikiBaseUrl/rest/api/content/search") {
                parameter("cql", cql)
                parameter("limit", 20)
                parameter("expand", "body.storage,metadata.labels")
                if (wikiApiToken.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $wikiApiToken")
                }
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn("API doc search returned status {}", response.status)
                return ToolCallResponse(
                    content = listOf(ToolContent.Text("API doc search failed with status: ${response.status}")),
                    isError = true
                )
            }

            val body = response.body<JsonObject>()
            val resultsArray = body["results"]?.jsonArray ?: JsonArray(emptyList())

            val apiResults = resultsArray.mapNotNull { element ->
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

                val serviceName = labels.firstOrNull { it.startsWith("service-") }
                    ?.removePrefix("service-")
                    ?: "unknown"

                // Parse API details from the page content
                val endpoints = extractApiEndpoints(bodyContent, serviceName, pageId)
                endpoints
            }.flatten()

            // Filter endpoints matching the query more closely
            val filteredResults = apiResults.filter { endpoint ->
                val searchText = "${endpoint.endpoint} ${endpoint.description} ${endpoint.method}".lowercase()
                query.lowercase().split(" ").any { word -> word in searchText }
            }.ifEmpty { apiResults }

            val searchResponse = ApiDocSearchResponse(
                results = filteredResults.take(20),
                totalCount = filteredResults.size,
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
            logger.error("API doc search failed for query '{}': {}", query, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("API doc search failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Extracts API endpoint definitions from page content. Looks for common patterns
     * like "GET /api/v1/resource" or table-formatted API specs.
     */
    private fun extractApiEndpoints(
        htmlContent: String,
        serviceName: String,
        pageId: String
    ): List<ApiEndpointResult> {
        val plainText = htmlContent.replace(Regex("<[^>]*>"), "\n").trim()
        val results = mutableListOf<ApiEndpointResult>()

        // Pattern: HTTP_METHOD /path/to/endpoint
        val endpointPattern = Regex(
            "(?m)(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\s+(/[\\w/{}\\-.:?&=]+)"
        )

        val matches = endpointPattern.findAll(plainText)
        for (match in matches) {
            val method = match.groupValues[1]
            val endpoint = match.groupValues[2]

            // Try to extract a description from surrounding text
            val startIndex = (match.range.first - 200).coerceAtLeast(0)
            val endIndex = (match.range.last + 200).coerceAtMost(plainText.length)
            val context = plainText.substring(startIndex, endIndex)

            val descriptionLine = context.lines()
                .firstOrNull { line ->
                    line.isNotBlank() &&
                        !line.contains(Regex("^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)")) &&
                        line.length > 10
                } ?: ""

            // Try to find request/response schema references
            val requestSchema = extractSchemaRef(context, "request")
            val responseSchema = extractSchemaRef(context, "response")

            val auth = when {
                context.contains("Bearer", ignoreCase = true) -> "Bearer Token"
                context.contains("API key", ignoreCase = true) -> "API Key"
                context.contains("OAuth", ignoreCase = true) -> "OAuth2"
                context.contains("public", ignoreCase = true) -> "None"
                else -> "Required"
            }

            results.add(
                ApiEndpointResult(
                    endpoint = endpoint,
                    method = method,
                    description = descriptionLine.trim().take(200),
                    service = serviceName,
                    requestSchema = requestSchema,
                    responseSchema = responseSchema,
                    authentication = auth,
                    url = "$wikiBaseUrl/pages/viewpage.action?pageId=$pageId"
                )
            )
        }

        return results
    }

    /**
     * Tries to extract a schema reference (e.g., JSON schema or model name) from context text.
     */
    private fun extractSchemaRef(context: String, type: String): String? {
        val pattern = Regex("(?i)$type\\s*(?:schema|body|payload)[:\\s]+([\\w.{}\\[\\]]+)")
        return pattern.find(context)?.groupValues?.getOrNull(1)
    }
}
