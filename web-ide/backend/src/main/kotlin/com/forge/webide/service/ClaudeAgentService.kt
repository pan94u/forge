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
import com.forge.webide.service.memory.MemoryContext
import com.forge.webide.service.memory.MemoryContextLoader
import com.forge.webide.service.memory.SessionSummaryService
import com.forge.webide.service.skill.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Integrates with the Claude API via [ModelAdapter] for AI-powered chat capabilities.
 *
 * Supports:
 * - Real streaming via SSE (no artificial delays)
 * - Multi-turn agentic tool calling loop (max 5 turns)
 * - Database persistence of conversations
 *
 * Delegates to:
 * - [AgenticLoopOrchestrator] for the multi-turn agentic streaming loop
 * - [HitlCheckpointManager] for human-in-the-loop checkpoint management
 * - [BaselineAutoChecker] for baseline quality gate auto-checks
 */
@Service
class ClaudeAgentService(
    private val claudeAdapter: ModelAdapter,
    private val modelRegistry: ModelRegistry,
    private val mcpProxyService: McpProxyService,
    private val knowledgeGapDetectorService: KnowledgeGapDetectorService,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val executionRecordRepository: ExecutionRecordRepository,
    private val profileRouter: ProfileRouter,
    private val skillLoader: SkillLoader,
    private val systemPromptAssembler: SystemPromptAssembler,
    private val metricsService: MetricsService,
    private val sessionSummaryService: SessionSummaryService,
    private val memoryContextLoader: MemoryContextLoader,
    private val userModelConfigService: UserModelConfigService,
    // Extracted services
    private val agenticLoopOrchestrator: AgenticLoopOrchestrator,
    private val hitlCheckpointManager: HitlCheckpointManager,
    private val baselineAutoChecker: BaselineAutoChecker,
    private val interactionEvaluationService: com.forge.webide.service.learning.InteractionEvaluationService
) {
    private val logger = LoggerFactory.getLogger(ClaudeAgentService::class.java)
    private val executor = Executors.newFixedThreadPool(10)

    @Value("\${forge.model.name:\${forge.claude.model:claude-sonnet-4-6}}")
    private var model: String = "claude-sonnet-4-6"

    /**
     * Resolve the user's API key for the given session and provider.
     * Looks up userId from the chat session entity, then queries UserModelConfigService.
     * Returns null if no user key is configured (adapter falls back to server default).
     */
    private fun resolveUserApiKey(sessionId: String, provider: String = "anthropic"): String? {
        return try {
            val session = chatSessionRepository.findById(sessionId).orElse(null) ?: return null
            val userId = session.userId.takeIf { it.isNotBlank() } ?: return null
            userModelConfigService.getDecryptedApiKey(userId, provider)
        } catch (e: Exception) {
            logger.debug("Failed to resolve user API key for session {}: {}", sessionId, e.message)
            null
        }
    }

    /**
     * Resolve the user's base URL override for the given session and provider.
     */
    private fun resolveUserBaseUrl(sessionId: String, provider: String): String? {
        return try {
            val session = chatSessionRepository.findById(sessionId).orElse(null) ?: return null
            val userId = session.userId.takeIf { it.isNotBlank() } ?: return null
            val config = userModelConfigService.getUserConfig(userId, provider)
            config?.baseUrl?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Build a dynamic system prompt based on the user's message.
     * Injects 3-layer memory context for cross-session continuity.
     * Falls back to a static prompt if skill loading fails.
     */
    private fun buildDynamicSystemPrompt(message: String, workspaceId: String = ""): DynamicPromptResult {
        return try {
            val routing = profileRouter.route(message)
            // Load memory context for cross-session continuity
            val memoryContext = if (workspaceId.isNotBlank()) {
                try {
                    memoryContextLoader.loadMemoryContext(workspaceId, routing.profile.name)
                } catch (e: Exception) {
                    logger.debug("Failed to load memory context: {}", e.message)
                    MemoryContext()
                }
            } else {
                MemoryContext()
            }
            val skills = skillLoader.loadSkillsForProfile(routing.profile, message)
            val systemPrompt = systemPromptAssembler.assemble(routing.profile, skills, memoryContext)
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
        modelId: String? = null
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
            val promptResult = buildDynamicSystemPrompt(message, workspaceId)
            logger.info("Profile: {}, Skills: {}", promptResult.activeProfile, promptResult.loadedSkills)

            // Resolve adapter and model based on modelId
            val actualModel = modelId ?: model
            val adapter = if (modelId != null) modelRegistry.adapterForModel(modelId) else claudeAdapter
            val provider = if (modelId != null) modelRegistry.providerForModel(modelId) ?: "anthropic" else "anthropic"
            val userApiKey = resolveUserApiKey(sessionId, provider)
            val options = CompletionOptions(
                model = actualModel,
                maxTokens = 4096,
                systemPrompt = promptResult.systemPrompt,
                apiKeyOverride = userApiKey
            )

            // Run single-turn completion with rate limit retry
            val result = runBlocking {
                val events = agenticLoopOrchestrator.streamWithRetry { adapter.streamWithTools(messages, options, tools) }.toList()
                agenticLoopOrchestrator.collectStreamResult(events)
            }

            // Persist messages
            persistMessage(sessionId, Message.Role.USER, fullMessage)
            persistMessage(sessionId, Message.Role.ASSISTANT, result.content, result.toolCalls)

            knowledgeGapDetectorService.analyzeForGaps(message, result.content, contexts, workspaceId)

            ChatMessageResponse(result.content, result.toolCalls)
        } catch (e: Exception) {
            logger.error("Claude API call failed: ${e.message}", e)
            val fallback = generateFallbackResponse(message, contexts)
            persistMessage(sessionId, Message.Role.USER, fullMessage)
            persistMessage(sessionId, Message.Role.ASSISTANT, fallback)
            ChatMessageResponse(fallback, emptyList())
        }
    }

    companion object {
        /** Confidence threshold below which intent confirmation is triggered */
        const val INTENT_CONFIRMATION_THRESHOLD = 0.5
    }

    // Pending intent confirmations: sessionId → CompletableFuture<String>
    private val pendingIntentConfirmations = ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<String>>()

    /**
     * Resolve an intent confirmation (called from WebSocket handler when user selects an option).
     */
    fun resolveIntentConfirmation(sessionId: String, selectedProfile: String) {
        val future = pendingIntentConfirmations.remove(sessionId)
        if (future != null) {
            future.complete(selectedProfile)
            logger.info("Intent confirmation resolved for session {}: {}", sessionId, selectedProfile)
        } else {
            logger.warn("No pending intent confirmation for session {}", sessionId)
        }
    }

    /**
     * Stream a message with real-time events via the agentic loop.
     *
     * Supports multi-turn tool calling: Claude requests tools -> tools execute ->
     * results feed back -> Claude continues. Max turns defined in [AgenticLoopOrchestrator].
     *
     * Phase 7: Low-confidence routing triggers intent confirmation before proceeding.
     */
    fun streamMessage(
        sessionId: String,
        message: String,
        contexts: List<ContextReference>,
        workspaceId: String,
        modelId: String? = null,
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

                // OODA: Observe -- understanding user intent
                onEvent(mapOf("type" to "ooda_phase", "phase" to "observe",
                    "detail" to "解析用户意图"))
                metricsService.recordOodaPhase("observe")
                emitSubStep(onEvent, "解析用户意图（${message.length} 字符）")

                var promptResult = buildDynamicSystemPrompt(message, workspaceId)
                logger.info("Stream profile: {}, Skills: {}, confidence: {}", promptResult.activeProfile, promptResult.loadedSkills, promptResult.confidence)

                // Low-confidence intent confirmation
                if (promptResult.confidence < INTENT_CONFIRMATION_THRESHOLD) {
                    logger.info("Low confidence routing ({}) for session {}, requesting intent confirmation",
                        promptResult.confidence, sessionId)

                    val confirmationFuture = java.util.concurrent.CompletableFuture<String>()
                    pendingIntentConfirmations[sessionId] = confirmationFuture

                    onEvent(mapOf(
                        "type" to "intent_confirmation",
                        "currentProfile" to promptResult.activeProfile,
                        "confidence" to promptResult.confidence,
                        "reason" to promptResult.routingReason,
                        "options" to listOf(
                            mapOf("id" to "evaluation-profile", "label" to "分析/评估", "description" to "查看进度、评估代码、生成报告"),
                            mapOf("id" to "development-profile", "label" to "开发/修复", "description" to "写代码、修 Bug、实现功能"),
                            mapOf("id" to "planning-profile", "label" to "规划/需求", "description" to "需求分析、产品规划"),
                            mapOf("id" to "design-profile", "label" to "设计/架构", "description" to "架构设计、API 设计、数据库设计"),
                            mapOf("id" to "testing-profile", "label" to "测试", "description" to "写测试、测试分析"),
                            mapOf("id" to "ops-profile", "label" to "运维/部署", "description" to "部署、发布、监控")
                        )
                    ))

                    try {
                        val selectedProfile = confirmationFuture.get(60, java.util.concurrent.TimeUnit.SECONDS)
                        pendingIntentConfirmations.remove(sessionId)

                        if (selectedProfile != promptResult.activeProfile) {
                            val confirmedProfile = skillLoader.loadProfile(selectedProfile)
                            if (confirmedProfile != null) {
                                val memoryContext = if (workspaceId.isNotBlank()) {
                                    try { memoryContextLoader.loadMemoryContext(workspaceId, confirmedProfile.name) }
                                    catch (e: Exception) { com.forge.webide.service.memory.MemoryContext() }
                                } else { com.forge.webide.service.memory.MemoryContext() }
                                val skills = skillLoader.loadSkillsForProfile(confirmedProfile, message)
                                val systemPrompt = systemPromptAssembler.assemble(confirmedProfile, skills, memoryContext)
                                promptResult = DynamicPromptResult(
                                    systemPrompt = systemPrompt,
                                    activeProfile = confirmedProfile.name,
                                    loadedSkills = skills.map { it.name },
                                    routingReason = "User confirmed: $selectedProfile",
                                    confidence = 1.0
                                )
                            }
                        } else {
                            promptResult = promptResult.copy(
                                confidence = 1.0,
                                routingReason = "User confirmed: ${promptResult.routingReason}"
                            )
                        }

                        // Inject clarification instruction: AI should ask user what they specifically want
                        val clarifyInstruction = "\n\n[IMPORTANT] 用户的原始消息比较模糊，系统已通过意图确认将你切换到 ${promptResult.activeProfile.replace("-profile", "")} 模式。" +
                            "请先用简短的一句话确认你理解了用户的需求方向，然后**主动追问**用户具体想做什么（给出 2-3 个具体选项），" +
                            "等用户明确后再开始执行。不要直接猜测并执行任务。"
                        promptResult = promptResult.copy(
                            systemPrompt = promptResult.systemPrompt + clarifyInstruction
                        )

                        emitSubStep(onEvent, "用户确认意图：${promptResult.activeProfile}")
                    } catch (e: java.util.concurrent.TimeoutException) {
                        pendingIntentConfirmations.remove(sessionId)
                        logger.info("Intent confirmation timed out for session {}, proceeding with default", sessionId)
                        emitSubStep(onEvent, "意图确认超时，使用默认路由: ${promptResult.activeProfile}")
                    }
                }

                // Resolve adapter and model based on modelId
                val actualModel = modelId ?: model
                val adapter = if (modelId != null) modelRegistry.adapterForModel(modelId) else claudeAdapter
                val provider = if (modelId != null) modelRegistry.providerForModel(modelId) ?: "anthropic" else "anthropic"
                val userApiKey = resolveUserApiKey(sessionId, provider)
                logger.info("Stream profile: {}, model: {}, provider: {}", promptResult.activeProfile, actualModel, provider)
                val options = CompletionOptions(
                    model = actualModel,
                    maxTokens = 4096,
                    systemPrompt = promptResult.systemPrompt,
                    apiKeyOverride = userApiKey
                )

                // OODA: Orient -- profile routed, context analyzed
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

                // OODA: Decide -- Claude formulating response
                onEvent(mapOf("type" to "ooda_phase", "phase" to "decide",
                    "detail" to "AI 制定响应策略"))
                metricsService.recordOodaPhase("decide")
                emitSubStep(onEvent, "组装 system prompt: ${promptResult.systemPrompt.length} 字符")

                // Run the agentic loop
                val result = runBlocking {
                    agenticLoopOrchestrator.agenticStream(
                        messages = history + Message(role = Message.Role.USER, content = fullMessage),
                        options = options,
                        tools = tools,
                        onEvent = onEvent,
                        workspaceId = workspaceId,
                        adapter = adapter
                    )
                }

                // Baseline auto-check: if code was generated (workspace_write_file used),
                // run profile baselines and retry if they fail (max 2 retries)
                var finalResult = result
                val hasCodeGeneration = result.toolCalls.any { it.name == "workspace_write_file" && it.status != "error" }
                if (hasCodeGeneration && promptResult.activeProfile != "fallback") {
                    finalResult = baselineAutoChecker.runBaselineAutoCheck(
                        result = result,
                        promptResult = promptResult,
                        messages = history + Message(role = Message.Role.USER, content = fullMessage),
                        options = options,
                        tools = tools,
                        workspaceId = workspaceId,
                        onEvent = onEvent,
                        adapter = adapter
                    )
                }

                // HITL checkpoint: disabled — will be redesigned later
                // (previous implementation triggered at inappropriate times)

                // OODA: Complete -- response delivered
                onEvent(mapOf("type" to "ooda_phase", "phase" to "complete"))
                metricsService.recordOodaPhase("complete")

                // Record total message duration
                val messageDurationMs = System.currentTimeMillis() - messageStartMs
                metricsService.recordMessageDuration(messageDurationMs)

                // Persist the final assistant response
                persistMessage(sessionId, Message.Role.ASSISTANT, finalResult.content, finalResult.toolCalls)

                knowledgeGapDetectorService.analyzeForGaps(message, finalResult.content, contexts)

                // Persist execution record for quality dashboard
                val toolSuccessCount = finalResult.toolCalls.count { it.status != "error" }
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

                // Phase 7: Record interaction evaluation for Learning Loop
                try {
                    val profileDef = skillLoader.loadProfile(promptResult.activeProfile)
                    interactionEvaluationService.recordEvaluation(
                        sessionId = sessionId,
                        workspaceId = workspaceId,
                        profile = promptResult.activeProfile,
                        mode = profileDef?.mode ?: "default",
                        routingConfidence = promptResult.confidence,
                        intentConfirmed = promptResult.confidence >= 1.0 && promptResult.routingReason.startsWith("User confirmed"),
                        toolCallCount = finalResult.toolCalls.size,
                        toolSuccessCount = toolSuccessCount,
                        turnCount = finalResult.toolCalls.size.coerceAtLeast(1),
                        durationMs = messageDurationMs
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to record interaction evaluation: {}", e.message)
                }

                // Generate session summary and update memory layers asynchronously (non-blocking)
                executor.submit {
                    try {
                        sessionSummaryService.generateAndSave(
                            sessionId = sessionId,
                            workspaceId = workspaceId,
                            profile = promptResult.activeProfile,
                            conversationHistory = history + listOf(
                                Message(role = Message.Role.USER, content = fullMessage),
                                Message(role = Message.Role.ASSISTANT, content = finalResult.content)
                            ),
                            toolCalls = finalResult.toolCalls
                        )
                        // Update Stage Memory and Workspace Memory from the generated summary
                        memoryContextLoader.updateFromSessionSummary(
                            workspaceId = workspaceId,
                            profile = promptResult.activeProfile,
                            sessionId = sessionId
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to generate session summary: {}", e.message)
                    }
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
     * Resolve a pending HITL checkpoint (called from WebSocket handler).
     */
    fun resolveCheckpoint(sessionId: String, decision: HitlDecision) {
        hitlCheckpointManager.resolveCheckpoint(sessionId, decision)
    }

    /**
     * Check if a session has a pending HITL checkpoint (for reconnection).
     */
    fun getPendingCheckpoint(sessionId: String): HitlCheckpointEntity? {
        return hitlCheckpointManager.getPendingCheckpoint(sessionId)
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
}

data class ChatMessageResponse(
    val content: String,
    val toolCalls: List<ToolCallRecord>
)
