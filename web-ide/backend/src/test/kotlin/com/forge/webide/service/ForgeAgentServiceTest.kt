package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.entity.ChatMessageEntity
import com.forge.webide.model.*
import com.forge.webide.repository.ChatMessageRepository
import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.service.memory.*
import com.forge.webide.service.skill.*
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for ForgeAgentService.
 *
 * After the Sprint 6.4 refactoring, the agentic loop logic lives in
 * AgenticLoopOrchestrator. These tests verify ForgeAgentService's own
 * responsibilities: persistence, system prompt building, error handling,
 * and correct delegation to the orchestrator.
 */
class ForgeAgentServiceTest {

    private lateinit var claudeAdapter: ClaudeAdapter
    private lateinit var mcpProxyService: McpProxyService
    private lateinit var knowledgeGapDetectorService: KnowledgeGapDetectorService
    private lateinit var chatSessionRepository: ChatSessionRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var skillLoader: SkillLoader
    private lateinit var systemPromptAssembler: SystemPromptAssembler
    private lateinit var metricsService: MetricsService
    private lateinit var agenticLoopOrchestrator: AgenticLoopOrchestrator
    private lateinit var hitlCheckpointManager: HitlCheckpointManager
    private lateinit var baselineAutoChecker: BaselineAutoChecker
    private lateinit var service: ForgeAgentService

    private val defaultProfile = ProfileDefinition(
        name = "development-profile",
        description = "Test development profile",
        skills = emptyList(),
        baselines = emptyList(),
        hitlCheckpoint = "",
        oodaGuidance = "",
        sourcePath = "test"
    )

    @BeforeEach
    fun setUp() {
        claudeAdapter = mockk()
        mcpProxyService = mockk()
        knowledgeGapDetectorService = mockk(relaxed = true)
        chatSessionRepository = mockk(relaxed = true)
        chatMessageRepository = mockk(relaxed = true)
        skillLoader = mockk()
        systemPromptAssembler = mockk()
        metricsService = mockk(relaxed = true)
        agenticLoopOrchestrator = mockk(relaxed = true)
        hitlCheckpointManager = mockk(relaxed = true)
        baselineAutoChecker = mockk(relaxed = true)

        every { skillLoader.loadSkillsForProfile(any(), any()) } returns emptyList()
        every { skillLoader.loadProfile(any()) } returns defaultProfile
        every { systemPromptAssembler.assemble(any(), any()) } returns "You are a test assistant."
        every { systemPromptAssembler.assemble(any(), any(), any()) } returns "You are a test assistant."
        every { systemPromptAssembler.fallbackPrompt() } returns "You are a test assistant."

        service = ForgeAgentService(
            claudeAdapter = claudeAdapter,
            modelRegistry = mockk(relaxed = true),
            mcpProxyService = mcpProxyService,
            knowledgeGapDetectorService = knowledgeGapDetectorService,
            chatSessionRepository = chatSessionRepository,
            chatMessageRepository = chatMessageRepository,
            executionRecordRepository = mockk(relaxed = true),
            intentSkillRouter = mockk(relaxed = true),
            skillLoader = skillLoader,
            systemPromptAssembler = systemPromptAssembler,
            metricsService = metricsService,
            sessionSummaryService = mockk(relaxed = true),
            memoryContextLoader = mockk(relaxed = true),
            userModelConfigService = mockk(relaxed = true),
            agenticLoopOrchestrator = agenticLoopOrchestrator,
            hitlCheckpointManager = hitlCheckpointManager,
            baselineAutoChecker = baselineAutoChecker,
            interactionEvaluationService = mockk(relaxed = true)
        )

        // Default: no conversation history
        every { chatMessageRepository.findBySessionIdOrderByCreatedAt(any()) } returns emptyList()

        // Default: save returns the entity as-is
        every { chatMessageRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `sendMessage returns response from Claude`() {
        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search docs", emptyMap())
        )
        // Mock the orchestrator to return expected content
        every { agenticLoopOrchestrator.collectStreamResult(any()) } returns AgenticResult(
            content = "Hello, how can I help?",
            toolCalls = emptyList()
        )

        val result = service.sendMessage("session-1", "Hi", emptyList(), "workspace-1")

        assertThat(result.content).isEqualTo("Hello, how can I help?")
        assertThat(result.toolCalls).isEmpty()
    }

    @Test
    fun `sendMessage persists user and assistant messages`() {
        every { mcpProxyService.listTools() } returns emptyList()
        every { agenticLoopOrchestrator.collectStreamResult(any()) } returns AgenticResult(
            content = "Response",
            toolCalls = emptyList()
        )

        service.sendMessage("session-1", "Hello", emptyList(), "workspace-1")

        // Verify persistence: user message + assistant message = 2 saves
        verify(atLeast = 2) { chatMessageRepository.save(any()) }
    }

    @Test
    fun `sendMessage returns fallback on Claude API failure`() {
        every { mcpProxyService.listTools() } returns emptyList()
        // Make collectStreamResult throw to simulate API failure
        every { agenticLoopOrchestrator.collectStreamResult(any()) } throws RuntimeException("API down")

        val result = service.sendMessage("session-1", "Hello", emptyList(), "workspace-1")

        assertThat(result.content).contains("fallback mode")
    }

    @Test
    fun `streamMessage emits content events`() {
        every { mcpProxyService.listTools() } returns emptyList()
        // Mock agenticStream to emit content events via callback and return result
        coEvery { agenticLoopOrchestrator.agenticStream(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val onEvent = args[3] as (Map<String, Any?>) -> Unit
            onEvent(mapOf("type" to "content", "content" to "Hello"))
            onEvent(mapOf("type" to "content", "content" to " there"))
            AgenticResult(content = "Hello there", toolCalls = emptyList())
        }

        val receivedEvents = CopyOnWriteArrayList<Map<String, Any?>>()
        val latch = CountDownLatch(1)
        var completedMessage: ChatMessage? = null

        service.streamMessage(
            sessionId = "session-1",
            message = "Hi",
            contexts = emptyList(),
            workspaceId = "workspace-1",
            onEvent = { receivedEvents.add(it) },
            onComplete = { msg ->
                completedMessage = msg
                latch.countDown()
            },
            onError = { latch.countDown() }
        )

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()

        val contentEvents = receivedEvents.filter { it["type"] == "content" }
        assertThat(contentEvents).hasSize(2)
        assertThat(contentEvents[0]["content"]).isEqualTo("Hello")
        assertThat(contentEvents[1]["content"]).isEqualTo(" there")

        assertThat(completedMessage).isNotNull
        assertThat(completedMessage!!.role).isEqualTo(MessageRole.ASSISTANT)
    }

    @Test
    fun `streamMessage executes tool calling agentic loop`() {
        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search docs", mapOf(
                "type" to "object",
                "properties" to mapOf("query" to mapOf("type" to "string"))
            ))
        )
        // Mock agenticStream to simulate tool calling flow
        coEvery { agenticLoopOrchestrator.agenticStream(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val onEvent = args[3] as (Map<String, Any?>) -> Unit
            onEvent(mapOf("type" to "tool_use_start", "toolCallId" to "toolu_001", "toolName" to "search_knowledge"))
            onEvent(mapOf("type" to "tool_result", "toolCallId" to "toolu_001",
                "content" to "Spring Boot docs found", "durationMs" to 100L))
            onEvent(mapOf("type" to "content", "content" to "Based on the search results, here's what I found."))
            AgenticResult(
                content = "Based on the search results, here's what I found.",
                toolCalls = listOf(ToolCallRecord(
                    id = "toolu_001",
                    name = "search_knowledge",
                    input = mapOf("query" to "spring"),
                    output = "Spring Boot docs found",
                    status = "complete"
                ))
            )
        }

        val receivedEvents = CopyOnWriteArrayList<Map<String, Any?>>()
        val latch = CountDownLatch(1)
        var completedMessage: ChatMessage? = null

        service.streamMessage(
            sessionId = "session-1",
            message = "Tell me about Spring Boot",
            contexts = emptyList(),
            workspaceId = "workspace-1",
            onEvent = { receivedEvents.add(it) },
            onComplete = { msg ->
                completedMessage = msg
                latch.countDown()
            },
            onError = { latch.countDown() }
        )

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()

        // Verify tool_use events were emitted by the orchestrator
        val toolUseStartEvents = receivedEvents.filter { it["type"] == "tool_use_start" }
        assertThat(toolUseStartEvents).hasSize(1)
        assertThat(toolUseStartEvents[0]["toolName"]).isEqualTo("search_knowledge")

        // Verify tool_result events were emitted
        val toolResultEvents = receivedEvents.filter { it["type"] == "tool_result" }
        assertThat(toolResultEvents).hasSize(1)

        // Verify completed message includes tool calls
        assertThat(completedMessage).isNotNull
        assertThat(completedMessage!!.toolCalls!!).hasSize(1)
        assertThat(completedMessage!!.toolCalls!![0].name).isEqualTo("search_knowledge")
    }

    @Test
    fun `streamMessage handles tool execution failure gracefully`() {
        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search", emptyMap())
        )
        // Mock agenticStream to simulate tool execution error
        coEvery { agenticLoopOrchestrator.agenticStream(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val onEvent = args[3] as (Map<String, Any?>) -> Unit
            onEvent(mapOf("type" to "tool_use_start", "toolCallId" to "toolu_001", "toolName" to "search_knowledge"))
            onEvent(mapOf("type" to "tool_result", "toolCallId" to "toolu_001",
                "content" to "Error executing tool: MCP server down", "durationMs" to 50L))
            onEvent(mapOf("type" to "content", "content" to "Sorry, the tool failed."))
            AgenticResult(
                content = "Sorry, the tool failed.",
                toolCalls = listOf(ToolCallRecord(
                    id = "toolu_001",
                    name = "search_knowledge",
                    input = mapOf("query" to "test"),
                    output = "Error executing tool: MCP server down",
                    status = "error"
                ))
            )
        }

        val receivedEvents = CopyOnWriteArrayList<Map<String, Any?>>()
        val latch = CountDownLatch(1)

        service.streamMessage(
            sessionId = "session-1",
            message = "Search",
            contexts = emptyList(),
            workspaceId = "workspace-1",
            onEvent = { receivedEvents.add(it) },
            onComplete = { latch.countDown() },
            onError = { latch.countDown() }
        )

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()

        // Tool result should contain error
        val toolResultEvents = receivedEvents.filter { it["type"] == "tool_result" }
        assertThat(toolResultEvents).hasSize(1)
        assertThat(toolResultEvents[0]["content"] as String).contains("Error executing tool")
    }

    @Test
    fun `sendMessage includes context in the message`() {
        every { mcpProxyService.listTools() } returns emptyList()
        every { agenticLoopOrchestrator.collectStreamResult(any()) } returns AgenticResult(
            content = "I see the file.",
            toolCalls = emptyList()
        )

        val contexts = listOf(
            ContextReference(type = "file", id = "App.kt", content = "fun main() {}")
        )

        // Capture persisted messages to verify context inclusion
        val savedEntities = mutableListOf<ChatMessageEntity>()
        every { chatMessageRepository.save(capture(savedEntities)) } answers { firstArg() }

        service.sendMessage("session-1", "Explain this", contexts, "workspace-1")

        // Verify the persisted user message includes the context
        val userMsg = savedEntities.first { it.role == "user" }
        assertThat(userMsg.content).contains("App.kt")
        assertThat(userMsg.content).contains("Explain this")
    }

    @Test
    fun `sendMessage loads conversation history from database`() {
        every { chatMessageRepository.findBySessionIdOrderByCreatedAt("session-1") } returns listOf(
            ChatMessageEntity(
                id = "msg-1",
                sessionId = "session-1",
                role = "user",
                content = "Previous question"
            ),
            ChatMessageEntity(
                id = "msg-2",
                sessionId = "session-1",
                role = "assistant",
                content = "Previous answer"
            )
        )

        every { mcpProxyService.listTools() } returns emptyList()
        every { agenticLoopOrchestrator.collectStreamResult(any()) } returns AgenticResult(
            content = "Follow-up answer",
            toolCalls = emptyList()
        )

        val result = service.sendMessage("session-1", "Follow-up", emptyList(), "workspace-1")

        // Verify history was loaded from database
        verify { chatMessageRepository.findBySessionIdOrderByCreatedAt("session-1") }
        // Verify the response content
        assertThat(result.content).isEqualTo("Follow-up answer")
    }
}
