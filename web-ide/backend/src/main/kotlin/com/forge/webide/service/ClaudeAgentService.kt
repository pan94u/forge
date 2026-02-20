package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.model.*
import com.forge.webide.repository.ChatMessageRepository
import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.entity.ChatMessageEntity
import com.forge.webide.entity.ToolCallEntity
import com.forge.webide.service.skill.ProfileRouter
import com.forge.webide.service.skill.SkillLoader
import com.forge.webide.service.skill.SystemPromptAssembler
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Integrates with the Claude API via [ClaudeAdapter] for AI-powered chat capabilities.
 *
 * Supports:
 * - Real streaming via SSE (no artificial delays)
 * - Multi-turn agentic tool calling loop (max 5 turns)
 * - Database persistence of conversations
 */
@Service
class ClaudeAgentService(
    private val claudeAdapter: ModelAdapter,
    private val mcpProxyService: McpProxyService,
    private val knowledgeGapDetectorService: KnowledgeGapDetectorService,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val profileRouter: ProfileRouter,
    private val skillLoader: SkillLoader,
    private val systemPromptAssembler: SystemPromptAssembler,
    private val metricsService: MetricsService
) {
    private val logger = LoggerFactory.getLogger(ClaudeAgentService::class.java)
    private val executor = Executors.newFixedThreadPool(10)

    @Value("\${forge.model.name:\${forge.claude.model:claude-sonnet-4-20250514}}")
    private var model: String = "claude-sonnet-4-20250514"

    companion object {
        private const val MAX_AGENTIC_TURNS = 8
    }

    /**
     * Build a dynamic system prompt based on the user's message.
     * Falls back to a static prompt if skill loading fails.
     */
    private fun buildDynamicSystemPrompt(message: String): DynamicPromptResult {
        return try {
            val routing = profileRouter.route(message)
            val skills = skillLoader.loadSkillsForProfile(routing.profile)
            val systemPrompt = systemPromptAssembler.assemble(routing.profile, skills)
            metricsService.recordProfileRoute(routing.profile.name, routing.reason)
            DynamicPromptResult(
                systemPrompt = systemPrompt,
                activeProfile = routing.profile.name,
                loadedSkills = skills.map { it.name },
                routingReason = routing.reason,
                confidence = routing.confidence
            )
        } catch (e: Exception) {
            logger.warn("Failed to build dynamic system prompt, using fallback: {}", e.message)
            DynamicPromptResult(
                systemPrompt = systemPromptAssembler.fallbackPrompt(),
                activeProfile = "fallback",
                loadedSkills = emptyList(),
                routingReason = "Fallback due to error: ${e.message}",
                confidence = 0.0
            )
        }
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
        val fullMessage = buildContextualMessage(message, contexts)
        val history = loadConversationHistory(sessionId)

        val messages = history + Message(role = Message.Role.USER, content = fullMessage)

        val tools = mcpProxyService.listTools().map { tool ->
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }

        return try {
            val promptResult = buildDynamicSystemPrompt(message)
            logger.info("Profile: {}, Skills: {}", promptResult.activeProfile, promptResult.loadedSkills)

            val options = CompletionOptions(
                model = model,
                maxTokens = 4096,
                systemPrompt = promptResult.systemPrompt
            )

            // Run single-turn completion
            val result = runBlocking {
                val events = claudeAdapter.streamWithTools(messages, options, tools).toList()
                collectStreamResult(events)
            }

            // Persist messages
            persistMessage(sessionId, Message.Role.USER, fullMessage)
            persistMessage(sessionId, Message.Role.ASSISTANT, result.content, result.toolCalls)

            knowledgeGapDetectorService.analyzeForGaps(message, result.content, contexts)

            ChatMessageResponse(result.content, result.toolCalls)
        } catch (e: Exception) {
            logger.error("Claude API call failed: ${e.message}", e)
            val fallback = generateFallbackResponse(message, contexts)
            persistMessage(sessionId, Message.Role.USER, fullMessage)
            persistMessage(sessionId, Message.Role.ASSISTANT, fallback)
            ChatMessageResponse(fallback, emptyList())
        }
    }

    /**
     * Stream a message with real-time events via the agentic loop.
     *
     * Supports multi-turn tool calling: Claude requests tools → tools execute →
     * results feed back → Claude continues. Max [MAX_AGENTIC_TURNS] turns.
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
                val messageStartMs = System.currentTimeMillis()
                val fullMessage = buildContextualMessage(message, contexts)
                val history = loadConversationHistory(sessionId)

                val tools = mcpProxyService.listTools().map { tool ->
                    ToolDefinition(
                        name = tool.name,
                        description = tool.description,
                        inputSchema = tool.inputSchema
                    )
                }

                // OODA: Observe — understanding user intent
                onEvent(mapOf("type" to "ooda_phase", "phase" to "observe"))
                metricsService.recordOodaPhase("observe")

                val promptResult = buildDynamicSystemPrompt(message)
                logger.info("Stream profile: {}, Skills: {}", promptResult.activeProfile, promptResult.loadedSkills)

                val options = CompletionOptions(
                    model = model,
                    maxTokens = 4096,
                    systemPrompt = promptResult.systemPrompt
                )

                // OODA: Orient — profile routed, context analyzed
                onEvent(mapOf("type" to "ooda_phase", "phase" to "orient"))
                metricsService.recordOodaPhase("orient")

                // Emit profile routing info
                onEvent(mapOf(
                    "type" to "profile_active",
                    "activeProfile" to promptResult.activeProfile,
                    "loadedSkills" to promptResult.loadedSkills,
                    "routingReason" to promptResult.routingReason,
                    "confidence" to promptResult.confidence
                ))

                // Persist the user message
                persistMessage(sessionId, Message.Role.USER, fullMessage)

                // OODA: Decide — Claude formulating response
                onEvent(mapOf("type" to "ooda_phase", "phase" to "decide"))
                metricsService.recordOodaPhase("decide")

                // Run the agentic loop
                val result = runBlocking {
                    agenticStream(
                        messages = history + Message(role = Message.Role.USER, content = fullMessage),
                        options = options,
                        tools = tools,
                        onEvent = onEvent,
                        workspaceId = workspaceId
                    )
                }

                // OODA: Complete — response delivered
                onEvent(mapOf("type" to "ooda_phase", "phase" to "complete"))
                metricsService.recordOodaPhase("complete")

                // Record total message duration
                val messageDurationMs = System.currentTimeMillis() - messageStartMs
                metricsService.recordMessageDuration(messageDurationMs)

                // Persist the final assistant response
                persistMessage(sessionId, Message.Role.ASSISTANT, result.content, result.toolCalls)

                knowledgeGapDetectorService.analyzeForGaps(message, result.content, contexts)

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

    /**
     * Core agentic streaming loop.
     *
     * Each turn:
     * 1. Streams ClaudeAdapter.streamWithTools() → emits events to WebSocket
     * 2. Collects the full response (text + tool_use blocks)
     * 3. If stop_reason == TOOL_USE: execute tools → build tool_result → loop
     * 4. If stop_reason == END_TURN: return final result
     */
    private suspend fun agenticStream(
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>,
        onEvent: (Map<String, Any?>) -> Unit,
        workspaceId: String = ""
    ): AgenticResult {
        var currentMessages = messages.toMutableList()
        var allToolCalls = mutableListOf<ToolCallRecord>()
        var finalContent = ""

        for (turn in 1..MAX_AGENTIC_TURNS) {
            logger.debug("Agentic turn $turn / $MAX_AGENTIC_TURNS")

            // Accumulate events from this turn
            var turnText = StringBuilder()
            var currentToolUses = mutableListOf<PendingToolUse>()
            var currentToolInputJson = StringBuilder()
            var stopReason: StopReason? = null
            var currentToolId = ""
            var currentToolName = ""
            var firstEventReceived = false
            val turnStartMs = System.currentTimeMillis()

            // Stream and emit events in real-time
            claudeAdapter.streamWithTools(currentMessages, options, tools).collect { event ->
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

            // If tool_use stop reason, execute tools and loop
            if (stopReason == StopReason.TOOL_USE && currentToolUses.isNotEmpty()) {
                // OODA: Act — executing tools
                onEvent(mapOf("type" to "ooda_phase", "phase" to "act"))
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
                for (toolUse in currentToolUses) {
                    val startMs = System.currentTimeMillis()
                    val result = try {
                        val mcpResult = mcpProxyService.callTool(toolUse.name, toolUse.input, workspaceId.ifBlank { null })
                        val output = McpProxyService.formatResult(mcpResult)
                        val status = if (mcpResult.isError) "error" else "complete"
                        val durationMs = System.currentTimeMillis() - startMs

                        metricsService.recordToolCall(toolUse.name, !mcpResult.isError)
                        metricsService.recordToolDuration(toolUse.name, durationMs)

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

            // No more tool calls — we're done
            break
        }

        // Safety net: if all turns exhausted or AI didn't produce text output,
        // inject a user message requesting summary and do one final turn WITHOUT tools
        val needsSummaryTurn = finalContent.isBlank() && allToolCalls.isNotEmpty()
        if (needsSummaryTurn || turn > MAX_AGENTIC_TURNS) {
            val reason = if (finalContent.isBlank()) "AI produced no text output" else "Max turns exhausted"
            logger.info("Safety net triggered: $reason. Forcing final summary turn. allToolCalls=${allToolCalls.size}")
            onEvent(mapOf("type" to "ooda_phase", "phase" to "complete"))

            // Add a user message to explicitly instruct the AI to summarize
            // Enhanced prompt with multiple fallback options
            currentMessages.add(Message(
                role = Message.Role.USER,
                content = buildSummaryPrompt(allToolCalls)
            ))

            var summaryText = StringBuilder()
            claudeAdapter.streamWithTools(currentMessages, options, emptyList()).collect { event ->
                when (event) {
                    is StreamEvent.ContentDelta -> {
                        summaryText.append(event.text)
                        onEvent(mapOf("type" to "content", "content" to event.text))
                    }
                    else -> {} // ignore other events in summary turn
                }
            }
            finalContent = summaryText.toString()
            logger.debug("Summary turn produced ${finalContent.length} chars")

            // If still no content, generate a fallback response based on tool calls
            if (finalContent.isBlank()) {
                logger.warn("Summary turn produced no content. Generating fallback response based on ${allToolCalls.size} tool calls.")
                finalContent = generateFallbackFromToolCalls(allToolCalls)
                onEvent(mapOf("type" to "content", "content" to finalContent))
            }
        }

        return AgenticResult(content = finalContent, toolCalls = allToolCalls)
    }

    // ---- Persistence helpers ----

    private fun loadConversationHistory(sessionId: String): List<Message> {
        return try {
            chatMessageRepository.findBySessionIdOrderByCreatedAt(sessionId).map { entity ->
                Message(
                    role = when (entity.role) {
                        "user" -> Message.Role.USER
                        "assistant" -> Message.Role.ASSISTANT
                        "tool" -> Message.Role.TOOL
                        else -> Message.Role.USER
                    },
                    content = entity.content
                )
            }
        } catch (e: Exception) {
            logger.debug("Could not load history for session $sessionId: ${e.message}")
            emptyList()
        }
    }

    private fun persistMessage(
        sessionId: String,
        role: Message.Role,
        content: String,
        toolCalls: List<ToolCallRecord> = emptyList()
    ) {
        try {
            val entity = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = when (role) {
                    Message.Role.USER -> "user"
                    Message.Role.ASSISTANT -> "assistant"
                    Message.Role.TOOL -> "tool"
                    Message.Role.SYSTEM -> "system"
                },
                content = content,
                createdAt = Instant.now()
            )

            val savedMessage = chatMessageRepository.save(entity)

            // Persist tool calls
            if (toolCalls.isNotEmpty()) {
                for (tc in toolCalls) {
                    val toolCallEntity = ToolCallEntity(
                        id = UUID.randomUUID().toString(),
                        messageId = savedMessage.id,
                        toolName = tc.name,
                        input = tc.input.toString(),
                        output = tc.output,
                        status = tc.status
                    )
                    // Tool call entities are saved via cascade or separate repo
                    savedMessage.toolCalls.add(toolCallEntity)
                }
                chatMessageRepository.save(savedMessage)
            }
        } catch (e: Exception) {
            logger.warn("Failed to persist message for session $sessionId: ${e.message}")
        }
    }

    // ---- Utility methods ----

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

    @Suppress("UNCHECKED_CAST")
    private fun parseToolInput(jsonStr: String): Map<String, Any?> {
        return try {
            if (jsonStr.isBlank()) return emptyMap()
            val gson = com.google.gson.Gson()
            gson.fromJson(jsonStr, Map::class.java) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) {
            logger.warn("Failed to parse tool input JSON: $jsonStr")
            mapOf("raw" to jsonStr)
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

    /**
     * Build an enhanced summary prompt for the safety net turn.
     *
     * The prompt is designed to:
     * 1. Force the AI to produce text output
     * 2. Provide clear instructions for summarizing tool results
     * 3. Offer fallback options if summarization is not possible
     */
    private fun buildSummaryPrompt(toolCalls: List<ToolCallRecord>): String {
        val toolNames = toolCalls.map { it.name }.distinct().joinToString(", ")
        val toolCount = toolCalls.size

        return """
            |你已经完成了 $toolCount 次工具调用，使用的工具包括：$toolNames。
            |
            |请基于工具调用的结果，为用户提供一个完整、详细的回复。回复应该：
            |1. 总结你通过工具发现或获取的关键信息
            |2. 直接回答用户的问题，而不是描述你做了什么
            |3. 如果信息不完整，明确说明你能确认的和不能确认的部分
            |
            |如果无法基于工具调用结果给出回复，至少提供一个基于你的理解的合理回答。
            |
            |请现在开始给出你的回复：
        """.trimMargin()
    }

    /**
     * Generate a fallback response based on tool call results.
     *
     * This is used when the safety net summary turn also produces no content,
     * which can happen in edge cases with long conversation histories.
     */
    private fun generateFallbackFromToolCalls(toolCalls: List<ToolCallRecord>): String {
        if (toolCalls.isEmpty()) {
            return "已完成工具调用，但未能生成回复。"
        }

        val toolSummaries = toolCalls.map { call ->
            val status = when (call.status) {
                "complete" -> "成功"
                "error" -> "失败"
                else -> call.status
            }
            "- ${call.name}: $status"
        }.joinToString("\n")

        return """
            |根据工具调用结果：
            |
            |$toolSummaries
            |
            |已执行完成所有操作。如果需要进一步分析或有其他问题，请重新提问。
        """.trimMargin()
    }

    private fun collectStreamResult(events: List<StreamEvent>): AgenticResult {
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

data class ChatMessageResponse(
    val content: String,
    val toolCalls: List<ToolCallRecord>
)

private data class AgenticResult(
    val content: String,
    val toolCalls: List<ToolCallRecord>
)

private data class DynamicPromptResult(
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
