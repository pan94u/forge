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
 * Creates a new knowledge page in the wiki.
 *
 * Input:
 * - title (string, required): Page title
 * - content (string, required): Page content in Markdown
 * - space (string, required): Wiki space key
 * - parentPageId (string, optional): Parent page ID for hierarchy
 *
 * Returns the newly created page URL.
 */
class PageCreateTool(
    private val wikiBaseUrl: String,
    private val wikiApiToken: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(PageCreateTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 30_000
        }
    }

    override val definition = ToolDefinition(
        name = "page_create",
        description = "Create a new knowledge page in the wiki. Accepts Markdown content. Returns the URL of the created page.",
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
                    put("description", "Wiki space key (e.g., 'ENG', 'OPS')")
                }
                putJsonObject("parentPageId") {
                    put("type", "string")
                    put("description", "Parent page ID for page hierarchy (optional)")
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

        val parentPageId = arguments["parentPageId"]?.jsonPrimitive?.contentOrNull

        if (title.isBlank()) {
            throw McpError.InvalidArguments("'title' must not be blank")
        }
        if (content.isBlank()) {
            throw McpError.InvalidArguments("'content' must not be blank")
        }

        return try {
            // Convert Markdown to Confluence storage format (simplified)
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

    /**
     * Converts basic Markdown to Confluence storage format (XHTML-based).
     * Handles headings, bold, italic, code blocks, lists, and links.
     */
    private fun markdownToStorageFormat(markdown: String): String {
        var html = markdown

        // Code blocks (``` ... ```)
        html = html.replace(Regex("```(\\w*)\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)) { match ->
            val lang = match.groupValues[1]
            val code = match.groupValues[2].trim()
            val langAttr = if (lang.isNotBlank()) " ac:language=\"$lang\"" else ""
            "<ac:structured-macro ac:name=\"code\"$langAttr><ac:plain-text-body><![CDATA[$code]]></ac:plain-text-body></ac:structured-macro>"
        }

        // Inline code (`code`)
        html = html.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }

        // Headings (## Heading)
        html = html.replace(Regex("(?m)^######\\s+(.+)$")) { "<h6>${it.groupValues[1]}</h6>" }
        html = html.replace(Regex("(?m)^#####\\s+(.+)$")) { "<h5>${it.groupValues[1]}</h5>" }
        html = html.replace(Regex("(?m)^####\\s+(.+)$")) { "<h4>${it.groupValues[1]}</h4>" }
        html = html.replace(Regex("(?m)^###\\s+(.+)$")) { "<h3>${it.groupValues[1]}</h3>" }
        html = html.replace(Regex("(?m)^##\\s+(.+)$")) { "<h2>${it.groupValues[1]}</h2>" }
        html = html.replace(Regex("(?m)^#\\s+(.+)$")) { "<h1>${it.groupValues[1]}</h1>" }

        // Bold and italic
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
        html = html.replace(Regex("\\*(.+?)\\*")) { "<em>${it.groupValues[1]}</em>" }

        // Links [text](url)
        html = html.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)")) {
            "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>"
        }

        // Unordered list items
        html = html.replace(Regex("(?m)^[-*]\\s+(.+)$")) { "<li>${it.groupValues[1]}</li>" }

        // Wrap consecutive <li> items in <ul>
        html = html.replace(Regex("((?:<li>.*?</li>\\s*)+)")) { "<ul>${it.groupValues[1]}</ul>" }

        // Paragraphs: wrap remaining plain lines
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
