package com.forge.adapter.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.*

/**
 * Tests for ClaudeAdapter's tool calling and SSE streaming capabilities.
 *
 * Uses OkHttp MockWebServer to simulate Claude API SSE responses and verify
 * that the adapter correctly parses all event types.
 */
class ClaudeAdapterToolCallingTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var adapter: ClaudeAdapter
    private val gson = Gson()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        adapter = ClaudeAdapter(
            apiKey = "test-api-key",
            baseUrl = mockServer.url("/").toString().removeSuffix("/")
        )
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    // --- SSE Parsing Tests ---

    @Test
    fun `streamWithTools parses text content events`() = runTest {
        val sseBody = buildSseResponse(
            sseEvent("message_start", """{"type":"message_start","message":{"id":"msg_123","model":"claude-sonnet-4-20250514","role":"assistant"}}"""),
            sseEvent("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
            sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""),
            sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" world"}}"""),
            sseEvent("content_block_stop", """{"type":"content_block_stop","index":0}"""),
            sseEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}"""),
            sseEvent("message_stop", """{"type":"message_stop"}""")
        )

        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sseBody))

        val events = adapter.streamWithTools(
            messages = listOf(Message(Message.Role.USER, "Hi")),
            options = CompletionOptions(maxTokens = 100),
            tools = emptyList()
        ).toList()

        assertThat(events).hasSize(6)
        assertThat(events[0]).isInstanceOf(StreamEvent.MessageStart::class.java)
        assertThat((events[0] as StreamEvent.MessageStart).messageId).isEqualTo("msg_123")

        assertThat(events[1]).isEqualTo(StreamEvent.ContentDelta("Hello"))
        assertThat(events[2]).isEqualTo(StreamEvent.ContentDelta(" world"))

        assertThat(events[3]).isInstanceOf(StreamEvent.ToolUseEnd::class.java) // content_block_stop
        assertThat(events[4]).isInstanceOf(StreamEvent.MessageDelta::class.java)
        assertThat((events[4] as StreamEvent.MessageDelta).stopReason).isEqualTo(StopReason.END_TURN)
        assertThat(events[5]).isEqualTo(StreamEvent.MessageStop)
    }

    @Test
    fun `streamWithTools parses tool_use events`() = runTest {
        val sseBody = buildSseResponse(
            sseEvent("message_start", """{"type":"message_start","message":{"id":"msg_456","model":"claude-sonnet-4-20250514","role":"assistant"}}"""),
            sseEvent("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
            sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Let me search."}}"""),
            sseEvent("content_block_stop", """{"type":"content_block_stop","index":0}"""),
            sseEvent("content_block_start", """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_001","name":"search_knowledge"}}"""),
            sseEvent("content_block_delta", """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":"}}"""),
            sseEvent("content_block_delta", """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"spring boot\"}"}}"""),
            sseEvent("content_block_stop", """{"type":"content_block_stop","index":1}"""),
            sseEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}"""),
            sseEvent("message_stop", """{"type":"message_stop"}""")
        )

        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sseBody))

        val events = adapter.streamWithTools(
            messages = listOf(Message(Message.Role.USER, "Search for spring boot docs")),
            options = CompletionOptions(maxTokens = 1000),
            tools = listOf(ToolDefinition(
                name = "search_knowledge",
                description = "Search knowledge base",
                inputSchema = mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string")))
            ))
        ).toList()

        // Verify tool_use events
        val toolUseStart = events.filterIsInstance<StreamEvent.ToolUseStart>()
        assertThat(toolUseStart).hasSize(1)
        assertThat(toolUseStart[0].id).isEqualTo("toolu_001")
        assertThat(toolUseStart[0].name).isEqualTo("search_knowledge")

        val toolInputDeltas = events.filterIsInstance<StreamEvent.ToolInputDelta>()
        assertThat(toolInputDeltas).hasSize(2)
        val fullInput = toolInputDeltas.joinToString("") { it.partialJson }
        assertThat(fullInput).isEqualTo("""{"query":"spring boot"}""")

        val messageDelta = events.filterIsInstance<StreamEvent.MessageDelta>()
        assertThat(messageDelta).hasSize(1)
        assertThat(messageDelta[0].stopReason).isEqualTo(StopReason.TOOL_USE)
    }

    @Test
    fun `streamWithTools handles error events`() = runTest {
        val sseBody = buildSseResponse(
            sseEvent("error", """{"type":"error","error":{"type":"overloaded_error","message":"API is temporarily overloaded"}}""")
        )

        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sseBody))

        val events = adapter.streamWithTools(
            messages = listOf(Message(Message.Role.USER, "Hi")),
            options = CompletionOptions(maxTokens = 100),
            tools = emptyList()
        ).toList()

        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(StreamEvent.Error::class.java)
        assertThat((events[0] as StreamEvent.Error).message).isEqualTo("API is temporarily overloaded")
    }

    // --- HTTP Error Handling Tests ---

    @Test
    fun `streamWithTools throws AuthenticationException on 401`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"error":{"type":"authentication_error","message":"Invalid API key"}}"""))

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapter.streamWithTools(
                    messages = listOf(Message(Message.Role.USER, "Hi")),
                    options = CompletionOptions(maxTokens = 100),
                    tools = emptyList()
                ).toList()
            }
        }.isInstanceOf(AuthenticationException::class.java)
            .hasMessageContaining("Invalid API key")
    }

    @Test
    fun `streamWithTools throws RateLimitException on 429`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(429)
            .setBody("""{"error":{"type":"rate_limit_error","message":"Rate limit exceeded"}}"""))

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapter.streamWithTools(
                    messages = listOf(Message(Message.Role.USER, "Hi")),
                    options = CompletionOptions(maxTokens = 100),
                    tools = emptyList()
                ).toList()
            }
        }.isInstanceOf(RateLimitException::class.java)
            .hasMessageContaining("Rate limit")
    }

    @Test
    fun `streamWithTools throws ModelAdapterException on 500`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("""{"error":{"type":"api_error","message":"Internal server error"}}"""))

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapter.streamWithTools(
                    messages = listOf(Message(Message.Role.USER, "Hi")),
                    options = CompletionOptions(maxTokens = 100),
                    tools = emptyList()
                ).toList()
            }
        }.isInstanceOf(ModelAdapterException::class.java)
            .hasMessageContaining("500")
    }

    // --- Request Body Tests ---

    @Test
    fun `buildMessagesRequestBody includes tools`() {
        val messages = listOf(Message(Message.Role.USER, "Hello"))
        val tools = listOf(
            ToolDefinition(
                name = "search_knowledge",
                description = "Search docs",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf("query" to mapOf("type" to "string")),
                    "required" to listOf("query")
                )
            )
        )

        val body = adapter.buildMessagesRequestBody(
            messages = messages,
            options = CompletionOptions(maxTokens = 1000, systemPrompt = "You are helpful"),
            model = "claude-sonnet-4-20250514",
            tools = tools,
            stream = true
        )

        val json = gson.fromJson(body, JsonObject::class.java)
        assertThat(json.get("model").asString).isEqualTo("claude-sonnet-4-20250514")
        assertThat(json.get("max_tokens").asInt).isEqualTo(1000)
        assertThat(json.get("stream").asBoolean).isTrue()
        assertThat(json.get("system").asString).isEqualTo("You are helpful")

        val toolsArray = json.getAsJsonArray("tools")
        assertThat(toolsArray).hasSize(1)
        assertThat(toolsArray[0].asJsonObject.get("name").asString).isEqualTo("search_knowledge")
    }

    @Test
    fun `buildMessagesRequestBody formats tool_use and tool_result messages`() {
        val messages = listOf(
            Message(Message.Role.USER, "Search for X"),
            Message(
                role = Message.Role.ASSISTANT,
                content = "I'll search for you.",
                toolUses = listOf(ToolUse("toolu_001", "search_knowledge", mapOf("query" to "X")))
            ),
            Message(
                role = Message.Role.TOOL,
                content = "",
                toolResults = listOf(ToolResult("toolu_001", "Found: X docs", false))
            ),
            Message(Message.Role.ASSISTANT, "Based on the search results...")
        )

        val body = adapter.buildMessagesRequestBody(
            messages = messages,
            options = CompletionOptions(maxTokens = 1000),
            model = "claude-sonnet-4-20250514",
            tools = emptyList(),
            stream = false
        )

        val json = gson.fromJson(body, JsonObject::class.java)
        val apiMessages = json.getAsJsonArray("messages")
        assertThat(apiMessages).hasSize(4)

        // Verify assistant message has tool_use content block
        val assistantMsg = apiMessages[1].asJsonObject
        assertThat(assistantMsg.get("role").asString).isEqualTo("assistant")
        val assistantContent = assistantMsg.getAsJsonArray("content")
        assertThat(assistantContent).isNotNull
        // Should have text block + tool_use block
        val toolUseBlock = assistantContent.firstOrNull {
            it.asJsonObject.get("type")?.asString == "tool_use"
        }
        assertThat(toolUseBlock).isNotNull
        assertThat(toolUseBlock!!.asJsonObject.get("id").asString).isEqualTo("toolu_001")
        assertThat(toolUseBlock.asJsonObject.get("name").asString).isEqualTo("search_knowledge")

        // Verify tool result message
        val toolMsg = apiMessages[2].asJsonObject
        assertThat(toolMsg.get("role").asString).isEqualTo("user")
        val toolContent = toolMsg.getAsJsonArray("content")
        assertThat(toolContent).isNotNull
        val toolResultBlock = toolContent[0].asJsonObject
        assertThat(toolResultBlock.get("type").asString).isEqualTo("tool_result")
        assertThat(toolResultBlock.get("tool_use_id").asString).isEqualTo("toolu_001")
        assertThat(toolResultBlock.get("content").asString).isEqualTo("Found: X docs")
    }

    @Test
    fun `complete returns parsed CompletionResult`() = runTest {
        val responseBody = """{
            "id": "msg_789",
            "type": "message",
            "role": "assistant",
            "content": [{"type": "text", "text": "Hello!"}],
            "model": "claude-sonnet-4-20250514",
            "stop_reason": "end_turn",
            "usage": {"input_tokens": 10, "output_tokens": 5}
        }"""

        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(responseBody))

        val result = adapter.complete("Hi", CompletionOptions(maxTokens = 100))

        assertThat(result.content).isEqualTo("Hello!")
        assertThat(result.model).isEqualTo("claude-sonnet-4-20250514")
        assertThat(result.stopReason).isEqualTo(StopReason.END_TURN)
        assertThat(result.usage.inputTokens).isEqualTo(10)
        assertThat(result.usage.outputTokens).isEqualTo(5)
    }

    @Test
    fun `validateApiKey throws on blank key`() {
        val adapterNoKey = ClaudeAdapter(apiKey = "", baseUrl = mockServer.url("/").toString())

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapterNoKey.complete("Hi", CompletionOptions(maxTokens = 100))
            }
        }.isInstanceOf(AuthenticationException::class.java)
            .hasMessageContaining("API key not set")
    }

    @Test
    fun `supportedModels returns expected models`() {
        val models = adapter.supportedModels()
        assertThat(models).hasSize(3)
        assertThat(models.map { it.id }).containsExactly(
            "claude-opus-4-20250514",
            "claude-sonnet-4-20250514",
            "claude-haiku-3-5-20241022"
        )
    }

    // --- Helpers ---

    private fun sseEvent(eventType: String, data: String): String {
        return "event: $eventType\ndata: $data\n\n"
    }

    private fun buildSseResponse(vararg events: String): String {
        return events.joinToString("")
    }
}
