package com.forge.mcp.artifact.tools

import com.forge.mcp.common.*
import com.forge.mcp.artifact.McpTool
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
 * Searches artifact repositories for dependencies.
 *
 * Input:
 * - groupId (string, optional): Maven group ID to filter
 * - artifactId (string, optional): Maven artifact ID to filter
 * - query (string, required): Free-text search query
 *
 * Returns available versions, latest stable version, and usage count.
 */
class DependencySearchTool(
    private val nexusUrl: String,
    private val nexusToken: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(DependencySearchTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 15_000
        }
    }

    override val definition = ToolDefinition(
        name = "dependency_search",
        description = "Search artifact repositories for Maven/Gradle dependencies. Returns available versions, latest stable version, and usage information.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("groupId") {
                    put("type", "string")
                    put("description", "Maven group ID to filter (e.g., 'org.springframework.boot')")
                }
                putJsonObject("artifactId") {
                    put("type", "string")
                    put("description", "Maven artifact ID to filter (e.g., 'spring-boot-starter-web')")
                }
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Free-text search query for finding artifacts")
                }
            }
            putJsonArray("required") {
                add("query")
            }
        }
    )

    @Serializable
    data class ArtifactResult(
        val groupId: String,
        val artifactId: String,
        val latestVersion: String,
        val latestStableVersion: String?,
        val versions: List<String>,
        val description: String?,
        val packaging: String,
        val lastUpdated: String?,
        val usageCount: Long?
    )

    @Serializable
    data class DependencySearchResponse(
        val results: List<ArtifactResult>,
        val totalCount: Int,
        val query: String
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val groupId = arguments["groupId"]?.jsonPrimitive?.contentOrNull
        val artifactId = arguments["artifactId"]?.jsonPrimitive?.contentOrNull

        return try {
            val results = searchArtifacts(query, groupId, artifactId)

            val response = DependencySearchResponse(
                results = results,
                totalCount = results.size,
                query = query
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Dependency search failed for '{}': {}", query, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Dependency search failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Searches Maven Central via the Solr-based search API.
     */
    private suspend fun searchArtifacts(
        query: String,
        groupId: String?,
        artifactId: String?
    ): List<ArtifactResult> {
        val queryParts = mutableListOf<String>()

        if (!groupId.isNullOrBlank()) {
            queryParts.add("g:\"$groupId\"")
        }
        if (!artifactId.isNullOrBlank()) {
            queryParts.add("a:\"$artifactId\"")
        }
        if (queryParts.isEmpty() || query.isNotBlank()) {
            queryParts.add(query)
        }

        val searchQuery = queryParts.joinToString(" AND ")

        // Use Maven Central Search API
        val response = httpClient.get("https://search.maven.org/solrsearch/select") {
            parameter("q", searchQuery)
            parameter("rows", 20)
            parameter("wt", "json")
            parameter("core", "gav")
        }

        if (response.status != HttpStatusCode.OK) {
            logger.warn("Maven Central search returned status {}", response.status)

            // Fallback: try the configured Nexus repository
            return searchNexus(query, groupId, artifactId)
        }

        val body = response.body<JsonObject>()
        val responseObj = body["response"]?.jsonObject ?: return emptyList()
        val docs = responseObj["docs"]?.jsonArray ?: return emptyList()

        // Group by groupId:artifactId and collect versions
        val artifactMap = mutableMapOf<String, MutableList<JsonObject>>()
        for (doc in docs) {
            val obj = doc.jsonObject
            val g = obj["g"]?.jsonPrimitive?.contentOrNull ?: continue
            val a = obj["a"]?.jsonPrimitive?.contentOrNull ?: continue
            val key = "$g:$a"
            artifactMap.getOrPut(key) { mutableListOf() }.add(obj)
        }

        return artifactMap.map { (_, versions) ->
            val first = versions.first()
            val g = first["g"]?.jsonPrimitive?.content ?: ""
            val a = first["a"]?.jsonPrimitive?.content ?: ""

            val allVersions = versions.mapNotNull {
                it["v"]?.jsonPrimitive?.contentOrNull
            }.sorted().reversed()

            val latestStable = allVersions.firstOrNull { v ->
                !v.contains("-SNAPSHOT", ignoreCase = true) &&
                    !v.contains("-alpha", ignoreCase = true) &&
                    !v.contains("-beta", ignoreCase = true) &&
                    !v.contains("-rc", ignoreCase = true) &&
                    !v.contains("-M", ignoreCase = true)
            }

            val timestamp = first["timestamp"]?.jsonPrimitive?.longOrNull
            val lastUpdated = timestamp?.let {
                java.time.Instant.ofEpochMilli(it).toString()
            }

            ArtifactResult(
                groupId = g,
                artifactId = a,
                latestVersion = allVersions.firstOrNull() ?: "unknown",
                latestStableVersion = latestStable,
                versions = allVersions.take(10),
                description = first["p"]?.jsonPrimitive?.contentOrNull,
                packaging = first["p"]?.jsonPrimitive?.contentOrNull ?: "jar",
                lastUpdated = lastUpdated,
                usageCount = null
            )
        }
    }

    /**
     * Fallback search against a Nexus repository.
     */
    private suspend fun searchNexus(
        query: String,
        groupId: String?,
        artifactId: String?
    ): List<ArtifactResult> {
        return try {
            val response = httpClient.get("$nexusUrl/service/rest/v1/search") {
                parameter("q", query)
                if (!groupId.isNullOrBlank()) parameter("group", groupId)
                if (!artifactId.isNullOrBlank()) parameter("name", artifactId)
                if (nexusToken.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $nexusToken")
                }
            }

            if (response.status != HttpStatusCode.OK) return emptyList()

            val body = response.body<JsonObject>()
            val items = body["items"]?.jsonArray ?: return emptyList()

            items.mapNotNull { item ->
                val obj = item.jsonObject
                val g = obj["group"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val a = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val v = obj["version"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

                ArtifactResult(
                    groupId = g,
                    artifactId = a,
                    latestVersion = v,
                    latestStableVersion = v,
                    versions = listOf(v),
                    description = null,
                    packaging = "jar",
                    lastUpdated = null,
                    usageCount = null
                )
            }
        } catch (e: Exception) {
            logger.warn("Nexus search failed: {}", e.message)
            emptyList()
        }
    }
}
