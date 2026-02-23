package com.forge.mcp.knowledge.tools

import com.forge.mcp.common.*
import com.forge.mcp.knowledge.LocalKnowledgeProvider
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
import java.io.File

/**
 * Creates a new knowledge page.
 *
 * - In **local mode** (localProvider != null): writes a Markdown file under the knowledge-base directory.
 *   The `space` parameter maps to a subdirectory (e.g., "adr", "conventions", "api-docs").
 * - In **wiki mode**: calls the Confluence REST API.
 */
class PageCreateTool(
    private val wikiBaseUrl: String,
    private val wikiApiToken: String,
    private val localProvider: LocalKnowledgeProvider? = null,
    private val knowledgeBasePath: String = "/knowledge-base"
) : McpTool {

    private val logger = LoggerFactory.getLogger(PageCreateTool::class.java)

    private val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                requestTimeout = 30_000
            }
        }
    }

    override val definition = ToolDefinition(
        name = "page_create",
        description = "Create a new knowledge page. In local mode, writes a Markdown file to knowledge-base/<space>/. In wiki mode, creates a Confluence page. Returns the path or URL of the created page.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Page title")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Page content in Markdown format")
                }
                putJsonObject("space") {
                    put("type", "string")
                    put("description", "Knowledge category / subdirectory (e.g., 'adr', 'conventions', 'api-docs', 'runbooks', 'architecture')")
                }
                putJsonObject("parentPageId") {
                    put("type", "string")
                    put("description", "Parent page ID for page hierarchy (optional, wiki mode only)")
                }
            }
            putJsonArray("required") {
                add("title")
                add("content")
                add("space")
            }
        }
    )

    @Serializable
    data class PageCreateResult(
        val pageId: String,
        val title: String,
        val url: String,
        val space: String
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'title' is required")

        val content = arguments["content"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'content' is required")

        val space = arguments["space"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'space' is required")

        if (title.isBlank()) {
            throw McpError.InvalidArguments("'title' must not be blank")
        }
        if (content.isBlank()) {
            throw McpError.InvalidArguments("'content' must not be blank")
        }

        // Local mode: write Markdown file to knowledge-base directory
        if (localProvider != null) {
            return executeLocal(title, content, space, userId)
        }

        // Wiki mode: call Confluence API
        return executeWiki(title, content, space, arguments["parentPageId"]?.jsonPrimitive?.contentOrNull, userId)
    }

    private fun executeLocal(title: String, content: String, space: String, userId: String): ToolCallResponse {
        return try {
            // Sanitize space name for directory
            val safeSpace = space.lowercase().replace(Regex("[^a-z0-9_-]"), "-")
            val spaceDir = File(knowledgeBasePath, safeSpace)
            if (!spaceDir.exists()) {
                spaceDir.mkdirs()
                logger.info("Created knowledge subdirectory: {}", spaceDir.absolutePath)
            }

            // Sanitize title for filename
            val safeTitle = title.lowercase()
                .replace(Regex("[\\s]+"), "-")
                .replace(Regex("[^a-z0-9\\u4e00-\\u9fff_-]"), "")
                .take(80)
            val fileName = "$safeTitle.md"
            val targetFile = File(spaceDir, fileName)

            // Add title header if content doesn't start with one
            val fullContent = if (!content.trimStart().startsWith("# ")) {
                "# $title\n\n$content"
            } else {
                content
            }

            targetFile.writeText(fullContent)

            // Re-index so the new file is immediately searchable
            localProvider!!.reload()

            val relativePath = "$safeSpace/$fileName"
            logger.info("Knowledge page created locally: '{}' -> {} by user {}", title, relativePath, userId)

            val result = PageCreateResult(
                pageId = relativePath,
                title = title,
                url = "local://$relativePath",
                space = safeSpace
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(result))
                )
            )
        } catch (e: Exception) {
            logger.error("Local page creation failed for '{}': {}", title, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Page creation failed: ${e.message}")),
                isError = true
            )
        }
    }

    private suspend fun executeWiki(
        title: String, content: String, space: String,
        parentPageId: String?, userId: String
    ): ToolCallResponse {
        return try {
            val storageContent = markdownToStorageFormat(content)

            val requestBody = buildJsonObject {
                put("type", "page")
                put("title", title)
                putJsonObject("space") {
                    put("key", space)
                }
                putJsonObject("body") {
                    putJsonObject("storage") {
                        put("value", storageContent)
                        put("representation", "storage")
                    }
                }
                if (parentPageId != null) {
                    putJsonArray("ancestors") {
                        addJsonObject {
                            put("id", parentPageId)
                        }
                    }
                }
            }

            val response = httpClient.post("$wikiBaseUrl/rest/api/content") {
                contentType(ContentType.Application.Json)
                if (wikiApiToken.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $wikiApiToken")
                }
                setBody(requestBody)
            }

            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
                val errorBody = try {
                    response.body<JsonObject>()["message"]?.jsonPrimitive?.contentOrNull
                } catch (_: Exception) { null }

                logger.warn("Page creation failed with status {}: {}", response.status, errorBody)
                return ToolCallResponse(
                    content = listOf(
                        ToolContent.Text("Page creation failed: ${errorBody ?: response.status}")
                    ),
                    isError = true
                )
            }

            val responseBody = response.body<JsonObject>()
            val pageId = responseBody["id"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            val result = PageCreateResult(
                pageId = pageId,
                title = title,
                url = "$wikiBaseUrl/pages/viewpage.action?pageId=$pageId",
                space = space
            )

            logger.info("Page created: '{}' in space {} by user {}", title, space, userId)

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(result))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Page creation failed for '{}': {}", title, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Page creation failed: ${e.message}")),
                isError = true
            )
        }
    }

    private fun markdownToStorageFormat(markdown: String): String {
        var html = markdown

        html = html.replace(Regex("```(\\w*)\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)) { match ->
            val lang = match.groupValues[1]
            val code = match.groupValues[2].trim()
            val langAttr = if (lang.isNotBlank()) " ac:language=\"$lang\"" else ""
            "<ac:structured-macro ac:name=\"code\"$langAttr><ac:plain-text-body><![CDATA[$code]]></ac:plain-text-body></ac:structured-macro>"
        }

        html = html.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }

        html = html.replace(Regex("(?m)^######\\s+(.+)$")) { "<h6>${it.groupValues[1]}</h6>" }
        html = html.replace(Regex("(?m)^#####\\s+(.+)$")) { "<h5>${it.groupValues[1]}</h5>" }
        html = html.replace(Regex("(?m)^####\\s+(.+)$")) { "<h4>${it.groupValues[1]}</h4>" }
        html = html.replace(Regex("(?m)^###\\s+(.+)$")) { "<h3>${it.groupValues[1]}</h3>" }
        html = html.replace(Regex("(?m)^##\\s+(.+)$")) { "<h2>${it.groupValues[1]}</h2>" }
        html = html.replace(Regex("(?m)^#\\s+(.+)$")) { "<h1>${it.groupValues[1]}</h1>" }

        html = html.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
        html = html.replace(Regex("\\*(.+?)\\*")) { "<em>${it.groupValues[1]}</em>" }

        html = html.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)")) {
            "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>"
        }

        html = html.replace(Regex("(?m)^[-*]\\s+(.+)$")) { "<li>${it.groupValues[1]}</li>" }
        html = html.replace(Regex("((?:<li>.*?</li>\\s*)+)")) { "<ul>${it.groupValues[1]}</ul>" }

        html = html.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank() &&
                !trimmed.startsWith("<h") &&
                !trimmed.startsWith("<ul") &&
                !trimmed.startsWith("<li") &&
                !trimmed.startsWith("<ac:") &&
                !trimmed.startsWith("</")
            ) {
                "<p>$trimmed</p>"
            } else {
                trimmed
            }
        }

        return html
    }
}
