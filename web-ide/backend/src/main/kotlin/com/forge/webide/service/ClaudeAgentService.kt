package com.forge.webide.service

import com.forge.webide.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Integrates with the Claude Agent SDK for AI-powered chat capabilities.
 * Manages conversation context, tool calls, and streaming responses.
 */
@Service
class ClaudeAgentService(
    private val mcpProxyService: McpProxyService,
    private val knowledgeGapDetectorService: KnowledgeGapDetectorService
) {
    private val logger = LoggerFactory.getLogger(ClaudeAgentService::class.java)
    private val executor = Executors.newFixedThreadPool(10)

    @Value("\${forge.claude.api-key:}")
    private var apiKey: String = ""

    @Value("\${forge.claude.model:claude-sonnet-4-20250514}")
    private var model: String = "claude-sonnet-4-20250514"

    @Value("\${forge.claude.api-url:https://api.anthropic.com}")
    private var apiUrl: String = "https://api.anthropic.com"

    private val conversationHistory = ConcurrentHashMap<String, MutableList<Map<String, Any>>>()

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(apiUrl)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    /**
     * Send a synchronous (non-streaming) message to Claude and return the response.
     */
    fun sendMessage(
        sessionId: String,
        message: String,
        contexts: List<ContextReference>,
        workspaceId: String
    ): ChatMessageResponse {
        val history = conversationHistory.getOrPut(sessionId) { mutableListOf() }

        // Build the user message with context
        val fullMessage = buildContextualMessage(message, contexts)

        history.add(mapOf(
            "role" to "user",
            "content" to fullMessage
        ))

        // Get available MCP tools
        val tools = mcpProxyService.listTools()

        return try {
            val requestBody = buildRequestBody(history, tools)

            val response = webClient.post()
                .uri("/v1/messages")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            val content = extractContent(response)
            val toolCalls = extractToolCalls(response)

            // Process tool calls if any
            val processedToolCalls = toolCalls.map { tc ->
                try {
                    val result = mcpProxyService.callTool(tc.name, tc.input)
                    tc.copy(
                        output = McpProxyService.formatResult(result),
                        status = if (result.isError) "error" else "complete"
                    )
                } catch (e: Exception) {
                    tc.copy(output = "Error: ${e.message}", status = "error")
                }
            }

            history.add(mapOf(
                "role" to "assistant",
                "content" to content
            ))

            // Detect knowledge gaps
            knowledgeGapDetectorService.analyzeForGaps(message, content, contexts)

            ChatMessageResponse(content, processedToolCalls)
        } catch (e: Exception) {
            logger.error("Claude API call failed: ${e.message}", e)

            // Fallback response
            val fallback = generateFallbackResponse(message, contexts)
            history.add(mapOf("role" to "assistant", "content" to fallback))

            ChatMessageResponse(fallback, emptyList())
        }
    }

    /**
     * Stream a message to Claude with real-time event callbacks.
     */
    fun streamMessage(
        sessionId: String,
        message: String,
        contexts: List<ContextReference>,
        workspaceId: String,
        onEvent: (Map<String, Any?>) -> Unit,
        onComplete: (ChatMessage) -> Unit,
        onError: (Exception) -> Unit
    ) {
        executor.submit {
            try {
                val result = sendMessage(sessionId, message, contexts, workspaceId)

                // Simulate streaming by sending content in chunks
                onEvent(mapOf("type" to "thinking", "content" to "Analyzing your question..."))

                // Send tool call events
                result.toolCalls.forEach { tc ->
                    onEvent(mapOf(
                        "type" to "tool_use",
                        "toolCallId" to tc.id,
                        "toolName" to tc.name,
                        "toolInput" to tc.input
                    ))
                    onEvent(mapOf(
                        "type" to "tool_result",
                        "toolCallId" to tc.id,
                        "content" to (tc.output ?: "")
                    ))
                }

                // Stream content in chunks to simulate real streaming
                val chunks = result.content.chunked(50)
                chunks.forEach { chunk ->
                    onEvent(mapOf("type" to "content", "content" to chunk))
                    Thread.sleep(20)
                }

                val assistantMessage = ChatMessage(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = result.content,
                    toolCalls = result.toolCalls
                )

                onComplete(assistantMessage)
            } catch (e: Exception) {
                logger.error("Stream message failed: ${e.message}", e)
                onError(e)
            }
        }
    }

    private fun buildContextualMessage(
        message: String,
        contexts: List<ContextReference>
    ): String {
        if (contexts.isEmpty()) return message

        val contextBlock = contexts.joinToString("\n\n") { ctx ->
            when (ctx.type) {
                "file" -> "<file path=\"${ctx.id}\">\n${ctx.content ?: "[file content]"}\n</file>"
                "knowledge" -> "<knowledge id=\"${ctx.id}\">\n${ctx.content ?: "[knowledge content]"}\n</knowledge>"
                "schema" -> "<schema id=\"${ctx.id}\">\n${ctx.content ?: "[schema content]"}\n</schema>"
                "service" -> "<service id=\"${ctx.id}\">\n${ctx.content ?: "[service info]"}\n</service>"
                else -> "<context type=\"${ctx.type}\" id=\"${ctx.id}\">\n${ctx.content ?: ""}\n</context>"
            }
        }

        return """
            |Context:
            |$contextBlock
            |
            |User Question:
            |$message
        """.trimMargin()
    }

    private fun buildRequestBody(
        history: List<Map<String, Any>>,
        tools: List<McpTool>
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "model" to model,
            "max_tokens" to 4096,
            "messages" to history,
            "system" to SYSTEM_PROMPT
        )

        if (tools.isNotEmpty()) {
            body["tools"] = tools.map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "input_schema" to tool.inputSchema
                )
            }
        }

        return body
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractContent(response: Map<*, *>?): String {
        if (response == null) return "No response received."

        val content = response["content"] as? List<Map<String, Any>> ?: return "No content in response."

        return content
            .filter { it["type"] == "text" }
            .mapNotNull { it["text"] as? String }
            .joinToString("\n")
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractToolCalls(response: Map<*, *>?): List<ToolCallRecord> {
        if (response == null) return emptyList()

        val content = response["content"] as? List<Map<String, Any>> ?: return emptyList()

        return content
            .filter { it["type"] == "tool_use" }
            .map { block ->
                ToolCallRecord(
                    id = block["id"] as? String ?: "",
                    name = block["name"] as? String ?: "",
                    input = block["input"] as? Map<String, Any?> ?: emptyMap()
                )
            }
    }

    private fun generateFallbackResponse(
        message: String,
        contexts: List<ContextReference>
    ): String {
        val contextInfo = if (contexts.isNotEmpty()) {
            "\n\nI can see you've attached ${contexts.size} context item(s). " +
            "Once the Claude API connection is configured, I'll be able to analyze them in detail."
        } else {
            ""
        }

        return """
            |I received your message: "$message"
            |
            |I'm currently running in fallback mode because the Claude API key is not configured.
            |To enable full AI capabilities, please set the `forge.claude.api-key` configuration.
            |$contextInfo
            |
            |In the meantime, you can:
            |- Browse the knowledge base for documentation
            |- Use the file explorer to navigate code
            |- Create and manage workflows
        """.trimMargin()
    }

    companion object {
        private const val SYSTEM_PROMPT = """You are Forge AI, an intelligent development assistant embedded in the Forge Web IDE.

You help developers with:
- Understanding and explaining code
- Answering questions about the codebase and architecture
- Suggesting improvements and best practices
- Debugging issues
- Writing and modifying code
- Navigating the knowledge base

When provided with file context, analyze the code carefully and provide specific, actionable advice.
When using MCP tools, explain what you're doing and why.
Always be concise but thorough in your responses."""
    }
}

data class ChatMessageResponse(
    val content: String,
    val toolCalls: List<ToolCallRecord>
)
