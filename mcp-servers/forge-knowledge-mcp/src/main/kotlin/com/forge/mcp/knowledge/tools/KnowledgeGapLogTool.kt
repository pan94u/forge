package com.forge.mcp.knowledge.tools

import com.forge.mcp.common.*
import com.forge.mcp.knowledge.McpTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Logs knowledge gaps when searches fail to find relevant results.
 * These gaps are tracked for later documentation generation.
 *
 * Input:
 * - query (string, required): The failed search query
 * - context (string, required): Context about what the user was trying to find
 * - suggestedTopic (string, required): Suggested topic for documentation
 *
 * Returns confirmation of the logged knowledge gap.
 */
class KnowledgeGapLogTool : McpTool {

    private val logger = LoggerFactory.getLogger(KnowledgeGapLogTool::class.java)

    /**
     * In-memory store of knowledge gaps. In production, this would be backed by
     * a persistent store (database, message queue, etc.).
     */
    private val knowledgeGaps = ConcurrentLinkedQueue<KnowledgeGapEntry>()

    override val definition = ToolDefinition(
        name = "knowledge_gap_log",
        description = "Log a knowledge gap when a search fails to find relevant documentation. Tracked gaps are used to prioritize documentation creation.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "The search query that failed to produce useful results")
                }
                putJsonObject("context") {
                    put("type", "string")
                    put("description", "Context about what the user was trying to find or accomplish")
                }
                putJsonObject("suggestedTopic") {
                    put("type", "string")
                    put("description", "Suggested topic for new documentation to fill this gap")
                }
            }
            putJsonArray("required") {
                add("query")
                add("context")
                add("suggestedTopic")
            }
        }
    )

    @Serializable
    data class KnowledgeGapEntry(
        val id: String,
        val query: String,
        val context: String,
        val suggestedTopic: String,
        val reportedBy: String,
        val reportedAt: String,
        val status: String = "open"
    )

    @Serializable
    data class KnowledgeGapLogResponse(
        val id: String,
        val message: String,
        val totalOpenGaps: Int
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val context = arguments["context"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'context' is required")

        val suggestedTopic = arguments["suggestedTopic"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'suggestedTopic' is required")

        return try {
            val gapId = UUID.randomUUID().toString().substring(0, 8)

            val entry = KnowledgeGapEntry(
                id = gapId,
                query = query,
                context = context,
                suggestedTopic = suggestedTopic,
                reportedBy = userId,
                reportedAt = Instant.now().toString()
            )

            knowledgeGaps.add(entry)

            logger.info(
                "Knowledge gap logged: id={}, topic='{}', reporter={}, query='{}'",
                gapId, suggestedTopic, userId, query
            )

            val openGaps = knowledgeGaps.count { it.status == "open" }

            val response = KnowledgeGapLogResponse(
                id = gapId,
                message = "Knowledge gap logged successfully. Topic: '$suggestedTopic'",
                totalOpenGaps = openGaps
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to log knowledge gap: {}", e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Failed to log knowledge gap: ${e.message}")),
                isError = true
            )
        }
    }
}
