package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.model.ToolCallRecord
import com.forge.webide.service.memory.MessageCompressor
import com.forge.webide.service.memory.TokenEstimator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Orchestrates the multi-turn agentic streaming loop.
 *
 * Each turn:
 * 1. Streams ModelAdapter.streamWithTools() and emits events to WebSocket
 * 2. Collects the full response (text + tool_use blocks)
 * 3. If stop_reason == TOOL_USE: execute tools, build tool_result, loop
 * 4. If stop_reason == END_TURN: return final result
 */
@Service
class AgenticLoopOrchestrator(
    private val claudeAdapter: ModelAdapter,
    private val mcpProxyService: McpProxyService,
    private val messageCompressor: MessageCompressor,
    private val metricsService: MetricsService,
    private val tokenEstimator: TokenEstimator
) {
    private val logger = LoggerFactory.getLogger(AgenticLoopOrchestrator::class.java)

    companion object {
        /** Absolute safety cap to prevent infinite loops. */
        const val MAX_AGENTIC_TURNS = 50
    }

    /**
     * Core agentic streaming loop.
     *
     * Each turn:
     * 1. Streams ClaudeAdapter.streamWithTools() -> emits events to WebSocket
     * 2. Collects the full response (text + tool_use blocks)
     * 3. If stop_reason == TOOL_USE: execute tools -> build tool_result -> loop
     * 4. If stop_reason == END_TURN: return final result
     */
    suspend fun agenticStream(
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>,
        onEvent: (Map<String, Any?>) -> Unit,
        workspaceId: String = "",
        sessionId: String = "",
        adapter: ModelAdapter? = null
    ): AgenticResult {
        val activeAdapter = adapter ?: claudeAdapter
        var currentMessages = messages.toMutableList()
        var allToolCalls = mutableListOf<ToolCallRecord>()
        var finalContent = ""
        var lastStopReason: StopReason? = null
        var lastTurn = 0

        for (turn in 1..MAX_AGENTIC_TURNS) {
            lastTurn = turn
            logger.debug("Agentic turn $turn")

            // Compress messages if context is getting large
            val compressed = messageCompressor.compressIfNeeded(currentMessages)
            if (compressed.phase > 0) {
                currentMessages = compressed.messages.toMutableList()
                logger.info("Context compressed: phase={}, tokens={}", compressed.phase, compressed.tokenCount)
            }

            // Always emit context usage so the frontend can show the indicator
            val currentTokens = if (compressed.phase > 0) compressed.tokenCount
                else tokenEstimator.estimateMessages(currentMessages)
            onEvent(mapOf(
                "type" to "context_usage",
                "tokensUsed" to currentTokens,
                "tokenBudget" to MessageCompressor.MAX_CONVERSATION_TOKENS,
                "compressionPhase" to compressed.phase,
                "turn" to turn
            ))

            // Accumulate events from this turn
            var turnText = StringBuilder()
            var currentToolUses = mutableListOf<PendingToolUse>()
            var currentToolInputJson = StringBuilder()
            var stopReason: StopReason? = null
            var currentToolId = ""
            var currentToolName = ""
            var firstEventReceived = false
            val turnStartMs = System.currentTimeMillis()

            // Stream and emit events in real-time (with rate limit retry)
            streamWithRetry { activeAdapter.streamWithTools(currentMessages, options, tools) }.collect { event ->
                if (!firstEventReceived) {
                    firstEventReceived = true
                    logger.debug("First event received for turn $turn in ${System.currentTimeMillis() - turnStartMs}ms: ${event::class.simpleName}")
                }
                when (event) {
                    is StreamEvent.MessageStart -> {
                        // no-op for the client; they already know we're streaming
                    }
                    is StreamEvent.ContentDelta -> {
                        turnText.append(event.text)
                        onEvent(mapOf("type" to "content", "content" to event.text))
                    }
                    is StreamEvent.ToolUseStart -> {
                        currentToolId = event.id
                        currentToolName = event.name
                        currentToolInputJson = StringBuilder()
                        onEvent(mapOf(
                            "type" to "tool_use_start",
                            "toolCallId" to event.id,
                            "toolName" to event.name
                        ))
                    }
                    is StreamEvent.ToolInputDelta -> {
                        currentToolInputJson.append(event.partialJson)
                    }
                    is StreamEvent.ToolUseEnd -> {
                        // Only process if we have a valid tool use in progress
                        if (currentToolId.isNotBlank()) {
                            val inputStr = currentToolInputJson.toString()
                            val input = parseToolInput(inputStr)
                            currentToolUses.add(PendingToolUse(
                                id = currentToolId,
                                name = currentToolName,
                                input = input
                            ))
                            onEvent(mapOf(
                                "type" to "tool_use",
                                "toolCallId" to currentToolId,
                                "toolName" to currentToolName,
                                "toolInput" to input
                            ))
                            // Reset for next tool use
                            currentToolId = ""
                            currentToolName = ""
                        }
                    }
                    is StreamEvent.MessageDelta -> {
                        stopReason = event.stopReason
                    }
                    is StreamEvent.MessageStop -> {
                        // Turn complete
                    }
                    is StreamEvent.Error -> {
                        onEvent(mapOf("type" to "error", "content" to event.message))
                    }
                }
            }

            val turnDurationMs = System.currentTimeMillis() - turnStartMs
            logger.debug("Turn $turn completed in ${turnDurationMs}ms, stopReason=$stopReason, tools=${currentToolUses.size}, textLength=${turnText.length}")
            metricsService.recordTurnDuration(turn, turnDurationMs)

            finalContent = turnText.toString()
            lastStopReason = stopReason

            // If tool_use stop reason, execute tools and loop
            if (stopReason == StopReason.TOOL_USE && currentToolUses.isNotEmpty()) {
                // OODA: Act -- executing tools
                onEvent(mapOf("type" to "ooda_phase", "phase" to "act",
                    "detail" to "执行 ${currentToolUses.size} 个工具",
                    "turn" to turn))
                metricsService.recordOodaPhase("act")

                // Build assistant message with tool uses
                val assistantMsg = Message(
                    role = Message.Role.ASSISTANT,
                    content = finalContent,
                    toolUses = currentToolUses.map { ToolUse(it.id, it.name, it.input) }
                )
                currentMessages.add(assistantMsg)

                // Execute each tool and collect results
                val toolResults = mutableListOf<ToolResult>()
                for ((toolIdx, toolUse) in currentToolUses.withIndex()) {
                    emitSubStep(onEvent, "Turn $turn — 调用 ${toolUse.name} (${toolIdx + 1}/${currentToolUses.size})")
                    val startMs = System.currentTimeMillis()
                    val result = try {
                        val mcpResult = mcpProxyService.callTool(toolUse.name, toolUse.input, workspaceId.ifBlank { null }, sessionId, onEvent)
                        val output = McpProxyService.formatResult(mcpResult)
                        val status = if (mcpResult.isError) "error" else "complete"
                        val durationMs = System.currentTimeMillis() - startMs

                        metricsService.recordToolCall(toolUse.name, !mcpResult.isError)
                        metricsService.recordToolDuration(toolUse.name, durationMs)
                        emitSubStep(onEvent, "${toolUse.name} 完成 (${durationMs}ms)${if (mcpResult.isError) " ❌" else " ✅"}")

                        allToolCalls.add(ToolCallRecord(
                            id = toolUse.id,
                            name = toolUse.name,
                            input = toolUse.input,
                            output = output,
                            status = status
                        ))

                        onEvent(mapOf(
                            "type" to "tool_result",
                            "toolCallId" to toolUse.id,
                            "content" to output,
                            "durationMs" to durationMs
                        ))

                        // Emit file_changed event for workspace write operations
                        if (toolUse.name == "workspace_write_file" && !mcpResult.isError) {
                            val filePath = toolUse.input["path"] as? String ?: ""
                            onEvent(mapOf(
                                "type" to "file_changed",
                                "action" to "created",
                                "path" to filePath
                            ))
                        }

                        ToolResult(toolUseId = toolUse.id, content = output, isError = mcpResult.isError)
                    } catch (e: Exception) {
                        val errorOutput = "Error executing tool: ${e.message}"
                        val durationMs = System.currentTimeMillis() - startMs

                        metricsService.recordToolCall(toolUse.name, false)
                        metricsService.recordToolDuration(toolUse.name, durationMs)
                        emitSubStep(onEvent, "${toolUse.name} 失败 (${durationMs}ms): ${e.message?.take(80)}")

                        allToolCalls.add(ToolCallRecord(
                            id = toolUse.id,
                            name = toolUse.name,
                            input = toolUse.input,
                            output = errorOutput,
                            status = "error"
                        ))

                        onEvent(mapOf(
                            "type" to "tool_result",
                            "toolCallId" to toolUse.id,
                            "content" to errorOutput,
                            "durationMs" to durationMs
                        ))

                        ToolResult(toolUseId = toolUse.id, content = errorOutput, isError = true)
                    }
                    toolResults.add(result)
                }

                // Add tool results as a TOOL message
                val toolMsg = Message(
                    role = Message.Role.TOOL,
                    content = "",
                    toolResults = toolResults
                )
                currentMessages.add(toolMsg)

                // Continue to next turn
                continue
            }

            // No more tool calls -- we're done
            break
        }

        // Safety net: if all turns exhausted and AI still wanted to use tools,
        // OR if final content is blank, force a summary turn WITHOUT tools.
        // BUG-016 fix: previous condition only checked isBlank(), but Claude often
        // produces partial text (e.g. "让我看看...") before tool calls, making
        // isBlank() return false even when there's no real user-facing answer.
        val turnsExhaustedWithToolUse = lastTurn >= MAX_AGENTIC_TURNS && lastStopReason == StopReason.TOOL_USE
        if (finalContent.isBlank() || turnsExhaustedWithToolUse) {
            logger.info("Forcing final summary turn: turnsExhausted=$turnsExhaustedWithToolUse, " +
                "contentBlank=${finalContent.isBlank()}, lastTurn=$lastTurn, lastStopReason=$lastStopReason")
            onEvent(mapOf("type" to "ooda_phase", "phase" to "complete"))

            // Build tool call summary for context
            val toolSummary = if (allToolCalls.isNotEmpty()) {
                val summaryLines = allToolCalls.takeLast(10).joinToString("\n") { tc ->
                    "- ${tc.name}: ${tc.output?.take(200) ?: "(no output)"}"
                }
                "\n\n已调用的工具及结果摘要：\n$summaryLines"
            } else ""

            currentMessages.add(Message(
                role = Message.Role.USER,
                content = "你已经收集了足够的信息（已使用 $lastTurn 轮工具调用）。$toolSummary\n\n请基于以上工具调用的结果，直接给出完整、详细的回复。不要再调用任何工具。"
            ))

            var summaryText = StringBuilder()
            activeAdapter.streamWithTools(currentMessages, options, emptyList()).collect { event ->
                when (event) {
                    is StreamEvent.ContentDelta -> {
                        summaryText.append(event.text)
                        onEvent(mapOf("type" to "content", "content" to event.text))
                    }
                    else -> {} // ignore other events in summary turn
                }
            }
            finalContent = summaryText.toString()
            logger.info("Summary turn produced ${finalContent.length} chars")
        }

        return AgenticResult(content = finalContent, toolCalls = allToolCalls)
    }

    /**
     * Wrap a streaming API call with exponential backoff retry on rate limit (429).
     * Retries up to [maxRetries] times with delays of 1s, 2s, 4s... (max 30s).
     *
     * IMPORTANT: RateLimitException can be thrown both during Flow creation AND
     * during Flow collection (when the HTTP SSE connection is established).
     * This wrapper catches both cases by re-collecting on rate limit errors.
     */
    suspend fun streamWithRetry(
        maxRetries: Int = 3,
        block: suspend () -> Flow<StreamEvent>
    ): Flow<StreamEvent> = flow {
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                val upstream = block()
                upstream.collect { event -> emit(event) }
                return@flow // success
            } catch (e: RateLimitException) {
                lastException = e
                if (attempt >= maxRetries) break
                val delayMs = (1000L * (1 shl attempt)).coerceAtMost(30_000L)
                logger.warn("Rate limited (during stream), retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)")
                delay(delayMs)
            } catch (e: java.io.IOException) {
                lastException = e
                if (attempt >= maxRetries) break
                val delayMs = (1000L * (1 shl attempt)).coerceAtMost(30_000L)
                logger.warn("IOException (SSL/network), retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries): ${e.message}")
                delay(delayMs)
            }
        }
        throw lastException!!
    }

    @Suppress("UNCHECKED_CAST")
    fun parseToolInput(jsonStr: String): Map<String, Any?> {
        return try {
            if (jsonStr.isBlank()) return emptyMap()
            val gson = com.google.gson.Gson()
            gson.fromJson(jsonStr, Map::class.java) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) {
            logger.warn("Failed to parse tool input JSON: $jsonStr")
            mapOf("raw" to jsonStr)
        }
    }

    fun collectStreamResult(events: List<StreamEvent>): AgenticResult {
        val text = StringBuilder()
        val toolCalls = mutableListOf<ToolCallRecord>()

        for (event in events) {
            when (event) {
                is StreamEvent.ContentDelta -> text.append(event.text)
                else -> {} // handled by agentic loop
            }
        }

        return AgenticResult(content = text.toString(), toolCalls = toolCalls)
    }
}

/** Shared data class for agentic loop results. */
data class AgenticResult(
    val content: String,
    val toolCalls: List<ToolCallRecord>
)

/** Shared data class for dynamic prompt assembly results. */
data class DynamicPromptResult(
    val systemPrompt: String,
    val activeProfile: String,
    val loadedSkills: List<String>,
    val routingReason: String,
    val confidence: Double
)

private data class PendingToolUse(
    val id: String,
    val name: String,
    val input: Map<String, Any?>
)

/** Emit a fine-grained sub_step event for execution transparency. */
fun emitSubStep(onEvent: (Map<String, Any?>) -> Unit, message: String) {
    onEvent(mapOf(
        "type" to "sub_step",
        "message" to message,
        "timestamp" to Instant.now().toString()
    ))
}
