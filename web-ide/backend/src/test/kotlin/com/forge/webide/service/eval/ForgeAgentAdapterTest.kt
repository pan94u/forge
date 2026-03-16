package com.forge.webide.service.eval

import com.forge.webide.model.ChatMessage
import com.forge.webide.model.ContextReference
import com.forge.webide.model.MessageRole
import com.forge.webide.service.ForgeAgentService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForgeAgentAdapterTest {

    private lateinit var forgeAgentService: ForgeAgentService
    private lateinit var adapter: ForgeAgentAdapter

    @BeforeEach
    fun setUp() {
        forgeAgentService = mockk()
        adapter = ForgeAgentAdapter(forgeAgentService)
    }

    @Test
    fun `execute returns output from onComplete callback`() {
        every {
            forgeAgentService.streamMessage(
                any(), any(), any(), any(), any(),
                onEvent = any(), onComplete = any(), onError = any()
            )
        } answers {
            val onEvent = arg<(Map<String, Any?>) -> Unit>(5)
            val onComplete = arg<(ChatMessage) -> Unit>(6)
            onEvent(mapOf("type" to "content", "text" to "Hello "))
            onEvent(mapOf("type" to "content", "text" to "world"))
            onComplete(ChatMessage(
                sessionId = "test",
                role = MessageRole.ASSISTANT,
                content = "Hello world"
            ))
        }

        val response = adapter.execute("test input", AgentExecutionConfig(timeoutMs = 5000))

        assertThat(response.success).isTrue()
        assertThat(response.output).isEqualTo("Hello world")
        assertThat(response.durationMs).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `execute collects transcript from events`() {
        every {
            forgeAgentService.streamMessage(
                any(), any(), any(), any(), any(),
                onEvent = any(), onComplete = any(), onError = any()
            )
        } answers {
            val onEvent = arg<(Map<String, Any?>) -> Unit>(5)
            val onComplete = arg<(ChatMessage) -> Unit>(6)
            onEvent(mapOf("type" to "tool_use_start", "name" to "search_knowledge"))
            onEvent(mapOf("type" to "tool_use", "name" to "search_knowledge", "result" to "found doc"))
            onEvent(mapOf("type" to "content", "text" to "Based on the search..."))
            onComplete(ChatMessage(
                sessionId = "test",
                role = MessageRole.ASSISTANT,
                content = "Based on the search..."
            ))
        }

        val response = adapter.execute("find docs", AgentExecutionConfig(timeoutMs = 5000))

        assertThat(response.success).isTrue()
        // user turn + tool_use_start + tool_use + assistant final
        assertThat(response.transcript).hasSizeGreaterThanOrEqualTo(4)
        assertThat(response.transcript[0].role).isEqualTo("user")
        assertThat(response.transcript[1].role).isEqualTo("assistant")
        assertThat(response.transcript[1].toolName).isEqualTo("search_knowledge")
        assertThat(response.transcript[2].role).isEqualTo("tool")
    }

    @Test
    fun `execute handles onError callback`() {
        every {
            forgeAgentService.streamMessage(
                any(), any(), any(), any(), any(),
                onEvent = any(), onComplete = any(), onError = any()
            )
        } answers {
            val onError = arg<(Exception) -> Unit>(7)
            onError(RuntimeException("Model API failed"))
        }

        val response = adapter.execute("test", AgentExecutionConfig(timeoutMs = 5000))

        assertThat(response.success).isFalse()
        assertThat(response.errorMessage).isEqualTo("Model API failed")
    }

    @Test
    fun `execute times out correctly`() {
        every {
            forgeAgentService.streamMessage(
                any(), any(), any(), any(), any(),
                onEvent = any(), onComplete = any(), onError = any()
            )
        } answers {
            // Never call onComplete or onError — simulate hang
        }

        val response = adapter.execute("test", AgentExecutionConfig(timeoutMs = 500))

        assertThat(response.success).isFalse()
        assertThat(response.errorMessage).contains("timed out")
    }

    @Test
    fun `sessionId starts with eval prefix`() {
        val capturedSessionId = slot<String>()
        every {
            forgeAgentService.streamMessage(
                capture(capturedSessionId), any(), any(), any(), any(),
                onEvent = any(), onComplete = any(), onError = any()
            )
        } answers {
            val onComplete = arg<(ChatMessage) -> Unit>(6)
            onComplete(ChatMessage(
                sessionId = capturedSessionId.captured,
                role = MessageRole.ASSISTANT,
                content = "done"
            ))
        }

        adapter.execute("test", AgentExecutionConfig(timeoutMs = 5000))

        assertThat(capturedSessionId.captured).startsWith("eval-")
    }

    @Test
    fun `execute uses provided modelName`() {
        val capturedModelId = mutableListOf<String?>()
        every {
            forgeAgentService.streamMessage(
                any(), any(), any(), any(), captureNullable(capturedModelId),
                onEvent = any(), onComplete = any(), onError = any()
            )
        } answers {
            val onComplete = arg<(ChatMessage) -> Unit>(6)
            onComplete(ChatMessage(
                sessionId = "test",
                role = MessageRole.ASSISTANT,
                content = "done"
            ))
        }

        adapter.execute("test", AgentExecutionConfig(
            modelName = "claude-opus-4-20250514",
            timeoutMs = 5000
        ))

        assertThat(capturedModelId).isNotEmpty
        assertThat(capturedModelId[0]).isEqualTo("claude-opus-4-20250514")
    }
}
