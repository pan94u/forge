package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.entity.ChatMessageEntity
import com.forge.webide.model.*
import com.forge.webide.repository.ChatMessageRepository
import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.service.skill.*
import io.mockk.*
import kotlinx.coroutines.flow.flow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for ClaudeAgentService.
 *
 * Focuses on the agentic loop behavior, tool calling flow,
 * and message persistence.
 */
class ClaudeAgentServiceTest {

    private lateinit var claudeAdapter: ClaudeAdapter
    private lateinit var mcpProxyService: McpProxyService
    private lateinit var knowledgeGapDetectorService: KnowledgeGapDetectorService
    private lateinit var chatSessionRepository: ChatSessionRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var profileRouter: ProfileRouter
    private lateinit var skillLoader: SkillLoader
    private lateinit var systemPromptAssembler: SystemPromptAssembler
    private lateinit var metricsService: MetricsService
    private lateinit var service: ClaudeAgentService

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
        profileRouter = mockk()
        skillLoader = mockk()
        systemPromptAssembler = mockk()
        metricsService = mockk(relaxed = true)

        // Default routing: always route to development profile
        every { profileRouter.route(any(), any()) } returns ProfileRoutingResult(
            profile = defaultProfile,
            confidence = 0.3,
            reason = "Default fallback"
        )
        every { skillLoader.loadSkillsForProfile(any()) } returns emptyList()
        every { systemPromptAssembler.assemble(any(), any()) } returns "You are a test assistant."
        every { systemPromptAssembler.fallbackPrompt() } returns "You are a test assistant."

        service = ClaudeAgentService(
            claudeAdapter = claudeAdapter,
            mcpProxyService = mcpProxyService,
            knowledgeGapDetectorService = knowledgeGapDetectorService,
            chatSessionRepository = chatSessionRepository,
            chatMessageRepository = chatMessageRepository,
            profileRouter = profileRouter,
            skillLoader = skillLoader,
            systemPromptAssembler = systemPromptAssembler,
            metricsService = metricsService
        )

        // Default: no conversation history
        every { chatMessageRepository.findBySessionIdOrderByCreatedAt(any()) } returns emptyList()

        // Default: save returns the entity as-is
        every { chatMessageRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `sendMessage returns response from Claude`() {
        // Setup: Claude returns simple text
        val events = flow<StreamEvent> {
            emit(StreamEvent.MessageStart("msg_1", "claude-sonnet-4-20250514"))
            emit(StreamEvent.ContentDelta("Hello, "))
            emit(StreamEvent.ContentDelta("how can I help?"))
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }

        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search docs", emptyMap())
        )
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } returns events

        val result = service.sendMessage("session-1", "Hi", emptyList(), "workspace-1")

        assertThat(result.content).isEqualTo("Hello, how can I help?")
        assertThat(result.toolCalls).isEmpty()
    }

    @Test
    fun `sendMessage persists user and assistant messages`() {
        val events = flow<StreamEvent> {
            emit(StreamEvent.ContentDelta("Response"))
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }

        every { mcpProxyService.listTools() } returns emptyList()
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } returns events

        service.sendMessage("session-1", "Hello", emptyList(), "workspace-1")

        // Verify persistence: user message + assistant message = 2 saves
        verify(atLeast = 2) { chatMessageRepository.save(any()) }
    }

    @Test
    fun `sendMessage returns fallback on Claude API failure`() {
        every { mcpProxyService.listTools() } returns emptyList()
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } throws RuntimeException("API down")

        val result = service.sendMessage("session-1", "Hello", emptyList(), "workspace-1")

        assertThat(result.content).contains("fallback mode")
    }

    @Test
    fun `streamMessage emits content events`() {
        val events = flow<StreamEvent> {
            emit(StreamEvent.MessageStart("msg_1", "claude-sonnet-4-20250514"))
            emit(StreamEvent.ContentDelta("Hello"))
            emit(StreamEvent.ContentDelta(" there"))
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }

        every { mcpProxyService.listTools() } returns emptyList()
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } returns events

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
        // Turn 1: Claude requests a tool
        val turn1Events = flow<StreamEvent> {
            emit(StreamEvent.MessageStart("msg_1", "claude-sonnet-4-20250514"))
            emit(StreamEvent.ContentDelta("Let me search."))
            emit(StreamEvent.ToolUseStart(1, "toolu_001", "search_knowledge"))
            emit(StreamEvent.ToolInputDelta("""{"query":"spring"}"""))
            emit(StreamEvent.ToolUseEnd(1))
            emit(StreamEvent.MessageDelta(StopReason.TOOL_USE))
            emit(StreamEvent.MessageStop)
        }

        // Turn 2: Claude responds with the tool result
        val turn2Events = flow<StreamEvent> {
            emit(StreamEvent.MessageStart("msg_2", "claude-sonnet-4-20250514"))
            emit(StreamEvent.ContentDelta("Based on the search results, here's what I found."))
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }

        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search docs", mapOf(
                "type" to "object",
                "properties" to mapOf("query" to mapOf("type" to "string"))
            ))
        )

        // Return different flows for sequential calls
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } returnsMany listOf(turn1Events, turn2Events)

        every { mcpProxyService.callTool("search_knowledge", any()) } returns McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = "Spring Boot docs found")),
            isError = false
        )

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

        // Verify tool_use events were emitted
        val toolUseStartEvents = receivedEvents.filter { it["type"] == "tool_use_start" }
        assertThat(toolUseStartEvents).hasSize(1)
        assertThat(toolUseStartEvents[0]["toolName"]).isEqualTo("search_knowledge")

        // Verify tool_result events were emitted
        val toolResultEvents = receivedEvents.filter { it["type"] == "tool_result" }
        assertThat(toolResultEvents).hasSize(1)

        // Verify the adapter was called twice (2 turns)
        coVerify(exactly = 2) { claudeAdapter.streamWithTools(any(), any(), any()) }

        // Verify tool was called
        verify(exactly = 1) { mcpProxyService.callTool("search_knowledge", any()) }
    }

    @Test
    fun `streamMessage handles tool execution failure gracefully`() {
        val turn1Events = flow<StreamEvent> {
            emit(StreamEvent.ToolUseStart(0, "toolu_001", "search_knowledge"))
            emit(StreamEvent.ToolInputDelta("""{"query":"test"}"""))
            emit(StreamEvent.ToolUseEnd(0))
            emit(StreamEvent.MessageDelta(StopReason.TOOL_USE))
            emit(StreamEvent.MessageStop)
        }

        val turn2Events = flow<StreamEvent> {
            emit(StreamEvent.ContentDelta("Sorry, the tool failed."))
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }

        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search", emptyMap())
        )
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } returnsMany listOf(turn1Events, turn2Events)
        every { mcpProxyService.callTool(any(), any()) } throws RuntimeException("MCP server down")

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
        val events = flow<StreamEvent> {
            emit(StreamEvent.ContentDelta("I see the file."))
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }

        every { mcpProxyService.listTools() } returns emptyList()
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } returns events

        val contexts = listOf(
            ContextReference(type = "file", id = "App.kt", content = "fun main() {}")
        )

        service.sendMessage("session-1", "Explain this", contexts, "workspace-1")

        // Verify the message sent to Claude includes context
        coVerify {
            claudeAdapter.streamWithTools(
                match { messages ->
                    val lastMsg = messages.last()
                    lastMsg.content.contains("App.kt") && lastMsg.content.contains("Explain this")
                },
                any(),
                any()
            )
        }
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

        val events = flow<StreamEvent> {
            emit(StreamEvent.ContentDelta("Follow-up answer"))
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }

        every { mcpProxyService.listTools() } returns emptyList()
        coEvery { claudeAdapter.streamWithTools(any(), any(), any()) } returns events

        service.sendMessage("session-1", "Follow-up", emptyList(), "workspace-1")

        // Verify history is included: 2 history + 1 new = 3 messages
        coVerify {
            claudeAdapter.streamWithTools(
                match { messages -> messages.size == 3 },
                any(),
                any()
            )
        }
    }
}
