package com.forge.webide.service.memory

import com.forge.adapter.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 3-phase message compressor for context window management.
 *
 * Phase 1: Truncate tool outputs to 500 chars
 * Phase 2: Summarize early messages, keep recent 3 turns
 * Phase 3: Full conversation summary (last resort)
 */
@Service
class MessageCompressor(
    private val tokenEstimator: TokenEstimator,
    private val claudeAdapter: ModelAdapter
) {
    private val logger = LoggerFactory.getLogger(MessageCompressor::class.java)

    companion object {
        const val MAX_CONVERSATION_TOKENS = 180_000
        private const val TOOL_OUTPUT_TRUNCATE_CHARS = 500
        private const val RECENT_TURNS_TO_KEEP = 3
    }

    /**
     * Compress messages if they exceed the token budget.
     * Returns the compressed message list and the compression phase applied (0=none).
     */
    fun compressIfNeeded(messages: List<Message>): CompressedResult {
        val currentTokens = tokenEstimator.estimateMessages(messages)
        if (currentTokens <= MAX_CONVERSATION_TOKENS) {
            return CompressedResult(messages, 0, currentTokens)
        }

        logger.info("Context compression needed: {} tokens > {} budget", currentTokens, MAX_CONVERSATION_TOKENS)

        // Phase 1: Truncate tool outputs
        var compressed = truncateToolOutputs(messages)
        var tokens = tokenEstimator.estimateMessages(compressed)
        if (tokens <= MAX_CONVERSATION_TOKENS) {
            logger.info("Phase 1 (tool truncation) sufficient: {} tokens", tokens)
            return CompressedResult(compressed, 1, tokens)
        }

        // Phase 2: Summarize early messages, keep recent turns
        compressed = summarizeEarlyMessages(compressed)
        tokens = tokenEstimator.estimateMessages(compressed)
        if (tokens <= MAX_CONVERSATION_TOKENS) {
            logger.info("Phase 2 (early message summary) sufficient: {} tokens", tokens)
            return CompressedResult(compressed, 2, tokens)
        }

        // Phase 3: Full conversation summary (last resort)
        compressed = fullSummary(compressed)
        tokens = tokenEstimator.estimateMessages(compressed)
        logger.info("Phase 3 (full summary) applied: {} tokens", tokens)
        return CompressedResult(compressed, 3, tokens)
    }

    /**
     * Phase 1: Truncate tool result content to TOOL_OUTPUT_TRUNCATE_CHARS.
     */
    private fun truncateToolOutputs(messages: List<Message>): List<Message> {
        return messages.map { msg ->
            val results = msg.toolResults
            if (msg.role == Message.Role.TOOL && results != null) {
                Message(
                    role = msg.role,
                    content = msg.content.take(TOOL_OUTPUT_TRUNCATE_CHARS),
                    toolResults = results.map { tr ->
                        if (tr.content.length > TOOL_OUTPUT_TRUNCATE_CHARS) {
                            ToolResult(
                                toolUseId = tr.toolUseId,
                                content = tr.content.take(TOOL_OUTPUT_TRUNCATE_CHARS) + "\n[...truncated]",
                                isError = tr.isError
                            )
                        } else {
                            tr
                        }
                    }
                )
            } else {
                msg
            }
        }
    }

    /**
     * Phase 2: Keep recent RECENT_TURNS_TO_KEEP turns intact,
     * replace earlier messages with a brief summary.
     */
    private fun summarizeEarlyMessages(messages: List<Message>): List<Message> {
        if (messages.size <= RECENT_TURNS_TO_KEEP * 2) return messages

        // A "turn" is a user message + assistant response (+ optional tool messages)
        // Keep the last RECENT_TURNS_TO_KEEP user messages and their responses
        val userIndices = messages.indices.filter { messages[it].role == Message.Role.USER }
        if (userIndices.size <= RECENT_TURNS_TO_KEEP) return messages

        val keepFromIndex = userIndices[userIndices.size - RECENT_TURNS_TO_KEEP]
        val earlyMessages = messages.subList(0, keepFromIndex)
        val recentMessages = messages.subList(keepFromIndex, messages.size)

        // Create a summary of early messages
        val earlySummary = buildString {
            appendLine("[对话历史摘要]")
            for (msg in earlyMessages) {
                when (msg.role) {
                    Message.Role.USER -> appendLine("用户: ${msg.content.take(100)}")
                    Message.Role.ASSISTANT -> appendLine("助手: ${msg.content.take(100)}")
                    Message.Role.TOOL -> appendLine("工具结果: ${msg.content.take(50)}")
                    else -> {}
                }
            }
        }.take(1000)

        return listOf(
            Message(role = Message.Role.USER, content = earlySummary)
        ) + recentMessages
    }

    /**
     * Phase 3: Generate a full conversation summary using Claude, replacing all history.
     */
    private fun fullSummary(messages: List<Message>): List<Message> {
        return try {
            val summaryContent = buildString {
                appendLine("请用 500 字以内总结以下对话的关键内容（已完成的工作、做出的决策、未解决的问题）：")
                appendLine()
                for (msg in messages.takeLast(20)) {
                    when (msg.role) {
                        Message.Role.USER -> appendLine("用户: ${msg.content.take(200)}")
                        Message.Role.ASSISTANT -> appendLine("助手: ${msg.content.take(200)}")
                        Message.Role.TOOL -> appendLine("工具: ${msg.content.take(100)}")
                        else -> {}
                    }
                }
            }

            val summaryMessages = listOf(
                Message(role = Message.Role.USER, content = summaryContent)
            )

            val options = CompletionOptions(
                maxTokens = 512,
                systemPrompt = "你是一个对话摘要生成器。简洁地总结对话要点。",
                temperature = 0.3
            )

            val result = runBlocking {
                claudeAdapter.streamWithTools(summaryMessages, options, emptyList()).toList()
            }

            val summaryText = result.filterIsInstance<StreamEvent.ContentDelta>()
                .joinToString("") { it.text }

            // Keep the summary as context + the last user message
            val lastUserMsg = messages.lastOrNull { it.role == Message.Role.USER }
            val condensed = mutableListOf(
                Message(role = Message.Role.USER, content = "[对话历史总结]\n$summaryText")
            )
            if (lastUserMsg != null && lastUserMsg != condensed.first()) {
                condensed.add(lastUserMsg)
            }

            condensed
        } catch (e: Exception) {
            logger.warn("Full summary generation failed, falling back to truncation: {}", e.message)
            // Fallback: just keep the last few messages
            messages.takeLast(6)
        }
    }
}

/**
 * Result of message compression.
 */
data class CompressedResult(
    val messages: List<Message>,
    val phase: Int,       // 0=none, 1=tool truncation, 2=early summary, 3=full summary
    val tokenCount: Int
)
