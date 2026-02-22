package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.model.*
import com.forge.webide.entity.ChatMessageEntity
import com.forge.webide.entity.ExecutionRecordEntity
import com.forge.webide.entity.HitlCheckpointEntity
import com.forge.webide.entity.ToolCallEntity
import com.forge.webide.repository.ChatMessageRepository
import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.repository.ExecutionRecordRepository
import com.forge.webide.repository.HitlCheckpointRepository
import com.forge.webide.service.skill.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private val dynamicAdapterFactory: DynamicAdapterFactory,
    private val mcpProxyService: McpProxyService,
    private val knowledgeGapDetectorService: KnowledgeGapDetectorService,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val hitlCheckpointRepository: HitlCheckpointRepository,
    private val executionRecordRepository: ExecutionRecordRepository,
    private val profileRouter: ProfileRouter,
    private val skillLoader: SkillLoader,
    private val systemPromptAssembler: SystemPromptAssembler,
    private val metricsService: MetricsService,
    private val baselineService: BaselineService
) {
    private val logger = LoggerFactory.getLogger(ClaudeAgentService::class.java)
    private val executor = Executors.newFixedThreadPool(10)

    // HITL checkpoint futures: sessionId → CompletableFuture<HitlDecision>
    private val pendingCheckpoints = ConcurrentHashMap<String, CompletableFuture<HitlDecision>>()

    /** Emit a fine-grained sub_step event for execution transparency. */
    private fun emitSubStep(onEvent: (Map<String, Any?>) -> Unit, message: String) {
        onEvent(mapOf(
            "type" to "sub_step",
            "message" to message,
            "timestamp" to Instant.now().toString()
        ))
    }

    /**
     * Build a dynamic system prompt based on the user's message.
     * Falls back to a static prompt if skill loading fails.
     */
    private fun buildDynamicSystemPrompt(message: String): DynamicPromptResult {
        return try {
            val routing = profileRouter.route(message)
            val skills = skillLoader.loadSkillsForProfile(routing.profile, message)
            val systemPrompt = systemPromptAssembler.assemble(routing.profile, skills)
            metricsService.recordProfileRoute(routing.profile.name, routing.reason)
            metricsService.recordSkillLoaded(routing.profile.name, skills.size)
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
        workspaceId: String,
        model: String = "claude-sonnet-4-6",
        userId: String = "anonymous"
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

            val provider = dynamicAdapterFactory.providerForModel(model)
            val adapter = dynamicAdapterFactory.createForUser(userId, provider)

            val options = CompletionOptions(
                model = model,
                maxTokens = 4096,
                systemPrompt = promptResult.systemPrompt
            )

            // Run single-turn completion
            val result = runBlocking {
                val events = adapter.streamWithTools(messages, options, tools).toList()
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
        model: String = "claude-sonnet-4-6",
        userId: String = "anonymous",
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
                onEvent(mapOf("type" to "ooda_phase", "phase" to "observe",
                    "detail" to "解析用户意图"))
                metricsService.recordOodaPhase("observe")
                emitSubStep(onEvent, "解析用户意图（${message.length} 字符）")

                // 创建 per-request adapter（从用户数据库配置）
                val provider = dynamicAdapterFactory.providerForModel(model)
                val adapter = try {
                    dynamicAdapterFactory.createForUser(userId, provider)
                } catch (e: ProviderNotConfiguredException) {
                    onError(Exception(e.message))
                    return@submit
                }

                val promptResult = buildDynamicSystemPrompt(message)
                logger.info("Stream profile: {}, Skills: {}, model: {}, provider: {}",
                    promptResult.activeProfile, promptResult.loadedSkills, model, provider)

                val options = CompletionOptions(
                    model = model,
                    maxTokens = 4096,
                    systemPrompt = promptResult.systemPrompt
                )

                // OODA: Orient — profile routed, context analyzed
                onEvent(mapOf("type" to "ooda_phase", "phase" to "orient",
                    "detail" to "路由到 ${promptResult.activeProfile}"))
                metricsService.recordOodaPhase("orient")
                emitSubStep(onEvent, "路由到 ${promptResult.activeProfile}，加载 ${promptResult.loadedSkills.size} 个 Skills")

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
                onEvent(mapOf("type" to "ooda_phase", "phase" to "decide",
                    "detail" to "AI 制定响应策略"))
                metricsService.recordOodaPhase("decide")
                emitSubStep(onEvent, "组装 system prompt: ${promptResult.systemPrompt.length} 字符")

                // Run the agentic loop
                val result = runBlocking {
                    agenticStream(
                        messages = history + Message(role = Message.Role.USER, content = fullMessage),
                        options = options,
                        tools = tools,
                        adapter = adapter,
                        onEvent = onEvent,
                        workspaceId = workspaceId
                    )
                }

                // Baseline auto-check: if code was generated (workspace_write_file used),
                // run profile baselines and retry if they fail (max 2 retries)
                var finalResult = result
                val hasCodeGeneration = result.toolCalls.any { it.name == "workspace_write_file" && it.status != "error" }
                if (hasCodeGeneration && promptResult.activeProfile != "fallback") {
                    finalResult = runBaselineAutoCheck(
                        result = result,
                        promptResult = promptResult,
                        messages = history + Message(role = Message.Role.USER, content = fullMessage),
                        options = options,
                        tools = tools,
                        adapter = adapter,
                        workspaceId = workspaceId,
                        onEvent = onEvent
                    )
                }

                // HITL checkpoint: if profile has a hitlCheckpoint defined, pause for approval
                val activeProfileDef = skillLoader.loadProfile(promptResult.activeProfile)
                if (activeProfileDef != null && activeProfileDef.hitlCheckpoint.isNotBlank()) {
                    val deliverables = finalResult.toolCalls
                        .filter { it.name == "workspace_write_file" && it.status != "error" }
                        .mapNotNull { tc -> tc.input["path"] as? String }

                    val decision = awaitHitlCheckpoint(
                        sessionId = sessionId,
                        profile = activeProfileDef,
                        deliverables = deliverables,
                        baselineResults = null,
                        onEvent = onEvent
                    )

                    when (decision.action) {
                        HitlAction.REJECT -> {
                            // Terminate: send summary and return
                            val rejectContent = finalResult.content +
                                "\n\n---\n⛔ 用户拒绝了此阶段产出。反馈: ${decision.feedback ?: "无"}"
                            finalResult = AgenticResult(content = rejectContent, toolCalls = finalResult.toolCalls)
                        }
                        HitlAction.MODIFY -> {
                            // Re-enter agentic loop with modified prompt
                            val modifiedMessages = (history + Message(role = Message.Role.USER, content = fullMessage)).toMutableList()
                            modifiedMessages.add(Message(role = Message.Role.ASSISTANT, content = finalResult.content))
                            modifiedMessages.add(Message(role = Message.Role.USER, content = decision.modifiedPrompt ?: decision.feedback ?: "请修改"))

                            emitSubStep(onEvent, "根据修改指令重新执行...")
                            onEvent(mapOf("type" to "ooda_phase", "phase" to "orient", "detail" to "根据修改指令重入"))

                            finalResult = runBlocking {
                                agenticStream(
                                    messages = modifiedMessages,
                                    options = options,
                                    tools = tools,
                                    adapter = adapter,
                                    onEvent = onEvent,
                                    workspaceId = workspaceId
                                )
                            }
                        }
                        HitlAction.APPROVE -> {
                            // Re-enter agentic loop to continue execution after approval
                            emitSubStep(onEvent, "用户已批准「${activeProfileDef.hitlCheckpoint}」，继续执行...")
                            onEvent(mapOf("type" to "ooda_phase", "phase" to "orient", "detail" to "审批通过，继续执行"))

                            val continueMessages = (history + Message(role = Message.Role.USER, content = fullMessage)).toMutableList()
                            continueMessages.add(Message(role = Message.Role.ASSISTANT, content = finalResult.content))
                            continueMessages.add(Message(role = Message.Role.USER, content =
                                "用户已审批通过「${activeProfileDef.hitlCheckpoint}」。" +
                                (if (decision.feedback.isNullOrBlank()) "" else "用户反馈: ${decision.feedback}。") +
                                "请输出本阶段的完整总结报告，包括：1) 已完成的工作 2) 产出物清单 3) 关键决策 4) 建议的下一步。"
                            ))

                            finalResult = runBlocking {
                                agenticStream(
                                    messages = continueMessages,
                                    options = options,
                                    tools = tools,
                                    onEvent = onEvent,
                                    workspaceId = workspaceId
                                )
                            }
                        }
                    }
                }

                // OODA: Complete — response delivered
                onEvent(mapOf("type" to "ooda_phase", "phase" to "complete"))
                metricsService.recordOodaPhase("complete")

                // Record total message duration
                val messageDurationMs = System.currentTimeMillis() - messageStartMs
                metricsService.recordMessageDuration(messageDurationMs)

                // Persist the final assistant response
                persistMessage(sessionId, Message.Role.ASSISTANT, finalResult.content, finalResult.toolCalls)

                knowledgeGapDetectorService.analyzeForGaps(message, finalResult.content, contexts)

                // Persist execution record for quality dashboard
                try {
                    val gson = com.google.gson.Gson()
                    val toolCallsSummary = finalResult.toolCalls.map { tc ->
                        mapOf("name" to tc.name, "status" to tc.status)
                    }
                    executionRecordRepository.save(ExecutionRecordEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        profile = promptResult.activeProfile,
                        skillsLoaded = promptResult.loadedSkills.size,
                        toolCalls = gson.toJson(toolCallsSummary),
                        totalDurationMs = messageDurationMs,
                        totalTurns = finalResult.toolCalls.size.coerceAtLeast(1)
                    ))
                } catch (e: Exception) {
                    logger.warn("Failed to save execution record: {}", e.message)
                }

                val assistantMessage = ChatMessage(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = finalResult.content,
                    toolCalls = finalResult.toolCalls
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
        adapter: ModelAdapter,
        onEvent: (Map<String, Any?>) -> Unit,
        workspaceId: String = ""
    ): AgenticResult {
        var currentMessages = messages.toMutableList()
        var allToolCalls = mutableListOf<ToolCallRecord>()
        var finalContent = ""
        var lastStopReason: StopReason? = null
        var lastTurn = 0

        for (turn in 1..MAX_AGENTIC_TURNS) {
            lastTurn = turn
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
            adapter.streamWithTools(currentMessages, options, tools).collect { event ->
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
                // OODA: Act — executing tools
                onEvent(mapOf("type" to "ooda_phase", "phase" to "act",
                    "detail" to "执行 ${currentToolUses.size} 个工具",
                    "turn" to turn, "maxTurns" to MAX_AGENTIC_TURNS))
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
                    emitSubStep(onEvent, "Turn $turn/$MAX_AGENTIC_TURNS — 调用 ${toolUse.name} (${toolIdx + 1}/${currentToolUses.size})")
                    val startMs = System.currentTimeMillis()
                    val result = try {
                        val mcpResult = mcpProxyService.callTool(toolUse.name, toolUse.input, workspaceId.ifBlank { null })
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

            // No more tool calls — we're done
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
            adapter.streamWithTools(currentMessages, options, emptyList()).collect { event ->
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

    // ---- Baseline auto-check ----

    companion object {
        private const val MAX_AGENTIC_TURNS = 8
        private const val MAX_BASELINE_RETRIES = 2
        private const val BASELINE_TIMEOUT_SECONDS = 30L
        private const val HITL_TIMEOUT_SECONDS = 300L
    }

    /**
     * Resolve a pending HITL checkpoint (called from WebSocket handler).
     */
    fun resolveCheckpoint(sessionId: String, decision: HitlDecision) {
        val future = pendingCheckpoints[sessionId]
        if (future != null) {
            future.complete(decision)
            logger.info("HITL checkpoint resolved for session $sessionId: ${decision.action}")
        } else {
            logger.warn("No pending HITL checkpoint for session $sessionId")
        }
    }

    /**
     * Check if a session has a pending HITL checkpoint (for reconnection).
     */
    fun getPendingCheckpoint(sessionId: String): HitlCheckpointEntity? {
        return hitlCheckpointRepository.findBySessionIdAndStatus(sessionId, "PENDING").firstOrNull()
    }

    /**
     * Trigger HITL checkpoint: emit event, persist state, wait for approval.
     * Returns the decision (APPROVE/REJECT/MODIFY) or TIMEOUT after 5 minutes.
     */
    private fun awaitHitlCheckpoint(
        sessionId: String,
        profile: ProfileDefinition,
        deliverables: List<String>,
        baselineResults: List<Map<String, String>>?,
        onEvent: (Map<String, Any?>) -> Unit
    ): HitlDecision {
        val checkpointId = UUID.randomUUID().toString()

        // Persist to DB
        val entity = HitlCheckpointEntity(
            id = checkpointId,
            sessionId = sessionId,
            profile = profile.name,
            checkpoint = profile.hitlCheckpoint,
            deliverables = com.google.gson.Gson().toJson(deliverables),
            baselineResults = baselineResults?.let { com.google.gson.Gson().toJson(it) },
            status = "PENDING"
        )
        hitlCheckpointRepository.save(entity)

        // Emit checkpoint event to frontend
        onEvent(mapOf(
            "type" to "hitl_checkpoint",
            "status" to "awaiting_approval",
            "profile" to profile.name,
            "checkpoint" to profile.hitlCheckpoint,
            "deliverables" to deliverables,
            "baselineResults" to (baselineResults ?: emptyList<Map<String, String>>()),
            "timeoutSeconds" to HITL_TIMEOUT_SECONDS
        ))
        emitSubStep(onEvent, "等待用户审批: ${profile.hitlCheckpoint}")

        // Create and register future
        val future = CompletableFuture<HitlDecision>()
        pendingCheckpoints[sessionId] = future

        return try {
            val decision = future.get(HITL_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // Update DB
            entity.status = decision.action.name
            entity.feedback = decision.feedback
            entity.resolvedAt = Instant.now()
            hitlCheckpointRepository.save(entity)

            metricsService.recordHitlResult(profile.name, decision.action.name)
            emitSubStep(onEvent, "审批结果: ${decision.action}${if (decision.feedback != null) " — ${decision.feedback}" else ""}")

            onEvent(mapOf(
                "type" to "hitl_checkpoint",
                "status" to decision.action.name.lowercase(),
                "profile" to profile.name,
                "hitlFeedback" to (decision.feedback ?: "")
            ))

            decision
        } catch (e: java.util.concurrent.TimeoutException) {
            entity.status = "TIMEOUT"
            entity.resolvedAt = Instant.now()
            hitlCheckpointRepository.save(entity)

            metricsService.recordHitlResult(profile.name, "TIMEOUT")
            emitSubStep(onEvent, "审批超时 (${HITL_TIMEOUT_SECONDS}s)，自动继续")

            onEvent(mapOf(
                "type" to "hitl_checkpoint",
                "status" to "timeout",
                "profile" to profile.name
            ))

            HitlDecision(action = HitlAction.APPROVE, feedback = "Auto-approved due to timeout")
        } finally {
            pendingCheckpoints.remove(sessionId)
        }
    }

    /**
     * Run baseline auto-check after code generation.
     * If baselines fail, inject failure context and run another agentic loop to fix.
     * Max [MAX_BASELINE_RETRIES] retry attempts.
     */
    private fun runBaselineAutoCheck(
        result: AgenticResult,
        promptResult: DynamicPromptResult,
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>,
        adapter: ModelAdapter,
        workspaceId: String,
        onEvent: (Map<String, Any?>) -> Unit
    ): AgenticResult {
        // Determine which baselines to run from the profile
        val profile = skillLoader.loadProfile(promptResult.activeProfile)
        val baselineNames = (profile?.baselines ?: emptyList()).ifEmpty {
            listOf("code-style-baseline", "security-baseline")
        }

        var currentResult = result
        for (attempt in 1..MAX_BASELINE_RETRIES) {
            logger.info("Baseline auto-check attempt {}/{}", attempt, MAX_BASELINE_RETRIES)
            emitSubStep(onEvent, "运行底线检查 (${attempt}/$MAX_BASELINE_RETRIES): ${baselineNames.joinToString()}")

            onEvent(mapOf(
                "type" to "baseline_check",
                "status" to "running",
                "attempt" to attempt,
                "baselines" to baselineNames
            ))

            val report = try {
                baselineService.runBaselines(baselineNames).also { r ->
                    r.results.forEach { br ->
                        metricsService.recordBaselineResult(br.name, br.status == "PASS")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Baseline execution failed: {}", e.message)
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "error",
                    "attempt" to attempt,
                    "message" to "Baseline execution error: ${e.message}"
                ))
                // Don't block on baseline execution errors
                return currentResult
            }

            if (report.allPassed) {
                logger.info("Baselines passed on attempt {}", attempt)
                emitSubStep(onEvent, "底线检查全部通过 ✅")
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "passed",
                    "attempt" to attempt,
                    "summary" to report.summary
                ))
                return currentResult
            }

            logger.info("Baselines failed on attempt {}: {}", attempt, report.summary)
            emitSubStep(onEvent, "底线检查失败 ❌: ${report.summary.take(100)}")
            onEvent(mapOf(
                "type" to "baseline_check",
                "status" to "failed",
                "attempt" to attempt,
                "summary" to report.summary
            ))

            if (attempt >= MAX_BASELINE_RETRIES) {
                // Max retries reached, report failure and return current result
                logger.warn("Baselines still failing after {} attempts, returning result with warning", MAX_BASELINE_RETRIES)
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "exhausted",
                    "summary" to report.summary
                ))
                return currentResult
            }

            // Loop back to Observe: inject baseline failure and re-run agentic loop
            onEvent(mapOf("type" to "ooda_phase", "phase" to "observe"))
            metricsService.recordOodaPhase("observe")

            val failureContext = report.results
                .filter { it.status == "FAIL" || it.status == "ERROR" }
                .joinToString("\n") { r ->
                    "- ${r.name}: ${r.output.take(500)}"
                }

            val fixMessages = messages.toMutableList()
            fixMessages.add(Message(
                role = Message.Role.ASSISTANT,
                content = currentResult.content
            ))
            fixMessages.add(Message(
                role = Message.Role.USER,
                content = """底线检查失败，请修复以下问题后重新提交代码：

$failureContext

请分析失败原因，修改相关文件使底线检查通过。修改完成后不要再调用底线检查工具。"""
            ))

            // Run another agentic loop to fix (with rate-limit protection)
            try {
            currentResult = runBlocking {
                agenticStream(
                    messages = fixMessages,
                    options = options,
                    tools = tools,
                    adapter = adapter,
                    onEvent = onEvent,
                    workspaceId = workspaceId
                )
            }
            } catch (e: Exception) {
                // Rate limit or other API error during baseline fix — skip fix, return what we have
                logger.warn("Baseline fix loop aborted ({}): {}", e.javaClass.simpleName, e.message?.take(100))
                emitSubStep(onEvent, "底线修复跳过（API 限制），返回当前结果")
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "exhausted",
                    "summary" to "Baseline fix skipped due to: ${e.message?.take(80)}"
                ))
                return currentResult
            }
        }

        return currentResult
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
