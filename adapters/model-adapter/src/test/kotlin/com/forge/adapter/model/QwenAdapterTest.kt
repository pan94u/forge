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

class QwenAdapterTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var adapter: QwenAdapter
    private val gson = Gson()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        adapter = QwenAdapter(
            apiKey = "test-api-key",
            baseUrl = mockServer.url("/v1").toString().removeSuffix("/v1")
        )
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    // --- Complete Tests ---

    @Test
    fun `complete returns parsed CompletionResult`() = runTest {
        val responseBody = """{
            "id": "chatcmpl-123",
            "model": "qwen-plus",
            "choices": [{"message": {"content": "Hello!"}, "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 10, "completion_tokens": 5}
        }"""

        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(responseBody))

        val result = adapter.complete("Hi", CompletionOptions(maxTokens = 100))

        assertThat(result.content).isEqualTo("Hello!")
        assertThat(result.model).isEqualTo("qwen-plus")
        assertThat(result.stopReason).isEqualTo(StopReason.END_TURN)
        assertThat(result.usage.inputTokens).isEqualTo(10)
        assertThat(result.usage.outputTokens).isEqualTo(5)
    }

    @Test
    fun `complete throws AuthenticationException on blank key`() {
        val adapterNoKey = QwenAdapter(apiKey = "", baseUrl = mockServer.url("/").toString())

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapterNoKey.complete("Hi", CompletionOptions(maxTokens = 100))
            }
        }.isInstanceOf(AuthenticationException::class.java)
            .hasMessageContaining("DashScope API key not set")
    }

    @Test
    fun `complete throws AuthenticationException on 401`() {
        mockServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"error":{"message":"Invalid API key"}}"""))

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapter.complete("Hi", CompletionOptions(maxTokens = 100))
            }
        }.isInstanceOf(AuthenticationException::class.java)
    }

    @Test
    fun `complete throws RateLimitException on 429`() {
        mockServer.enqueue(MockResponse()
            .setResponseCode(429)
            .setBody("""{"error":{"message":"Rate limit exceeded"}}"""))

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapter.complete("Hi", CompletionOptions(maxTokens = 100))
            }
        }.isInstanceOf(RateLimitException::class.java)
    }

    // --- Streaming Tests ---

    @Test
    fun `streamComplete emits text chunks`() = runTest {
        val sseBody = """data: {"choices":[{"delta":{"content":"Hello"}}]}

data: {"choices":[{"delta":{"content":" world"}}]}

data: [DONE]

"""
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sseBody))

        val chunks = adapter.streamComplete("Hi", CompletionOptions(maxTokens = 100)).toList()

        assertThat(chunks).containsExactly("Hello", " world")
    }

    // --- StreamWithTools Tests ---

    @Test
    fun `streamWithTools emits text events`() = runTest {
        val sseBody = """data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}

data: {"choices":[{"delta":{"content":" world"},"finish_reason":null}]}

data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

data: [DONE]

"""
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sseBody))

        val events = adapter.streamWithTools(
            messages = listOf(Message(Message.Role.USER, "Hi")),
            options = CompletionOptions(maxTokens = 100),
            tools = emptyList()
        ).toList()

        val messageStart = events.filterIsInstance<StreamEvent.MessageStart>()
        assertThat(messageStart).hasSize(1)

        val contentDeltas = events.filterIsInstance<StreamEvent.ContentDelta>()
        assertThat(contentDeltas.map { it.text }).containsExactly("Hello", " world")

        val messageDelta = events.filterIsInstance<StreamEvent.MessageDelta>()
        assertThat(messageDelta).hasSize(1)
        assertThat(messageDelta[0].stopReason).isEqualTo(StopReason.END_TURN)

        assertThat(events.last()).isEqualTo(StreamEvent.MessageStop)
    }

    @Test
    fun `streamWithTools emits tool calling events`() = runTest {
        val sseBody = """data: {"choices":[{"delta":{"tool_calls":[{"id":"call_1","type":"function","function":{"name":"search_knowledge","arguments":""}}]},"finish_reason":null}]}

data: {"choices":[{"delta":{"tool_calls":[{"function":{"arguments":"{\"query\":"}}]},"finish_reason":null}]}

data: {"choices":[{"delta":{"tool_calls":[{"function":{"arguments":"\"test\"}"}}]},"finish_reason":null}]}

data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

data: [DONE]

"""
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(sseBody))

        val events = adapter.streamWithTools(
            messages = listOf(Message(Message.Role.USER, "Search for test")),
            options = CompletionOptions(maxTokens = 1000),
            tools = listOf(ToolDefinition("search_knowledge", "Search", mapOf("type" to "object")))
        ).toList()

        val toolStarts = events.filterIsInstance<StreamEvent.ToolUseStart>()
        assertThat(toolStarts).hasSize(1)
        assertThat(toolStarts[0].name).isEqualTo("search_knowledge")
        assertThat(toolStarts[0].id).isEqualTo("call_1")

        val toolDeltas = events.filterIsInstance<StreamEvent.ToolInputDelta>()
        assertThat(toolDeltas).isNotEmpty

        val messageDelta = events.filterIsInstance<StreamEvent.MessageDelta>()
        assertThat(messageDelta[0].stopReason).isEqualTo(StopReason.TOOL_USE)
    }

    // --- Request Body Tests ---

    @Test
    fun `buildMessagesRequestBody includes tools in OpenAI format`() {
        val messages = listOf(Message(Message.Role.USER, "Hello"))
        val tools = listOf(
            ToolDefinition(
                name = "search_knowledge",
                description = "Search docs",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf("query" to mapOf("type" to "string"))
                )
            )
        )

        val body = adapter.buildMessagesRequestBody(
            messages = messages,
            options = CompletionOptions(maxTokens = 1000, systemPrompt = "You are helpful"),
            model = "qwen-plus",
            tools = tools,
            stream = true
        )

        val json = gson.fromJson(body, JsonObject::class.java)
        assertThat(json.get("model").asString).isEqualTo("qwen-plus")
        assertThat(json.get("stream").asBoolean).isTrue()

        val toolsArray = json.getAsJsonArray("tools")
        assertThat(toolsArray).hasSize(1)
        val toolObj = toolsArray[0].asJsonObject
        assertThat(toolObj.get("type").asString).isEqualTo("function")
        assertThat(toolObj.getAsJsonObject("function").get("name").asString).isEqualTo("search_knowledge")

        // Verify system message is included
        val apiMessages = json.getAsJsonArray("messages")
        val sysMsg = apiMessages[0].asJsonObject
        assertThat(sysMsg.get("role").asString).isEqualTo("system")
    }

    // --- Model Info Tests ---

    @Test
    fun `supportedModels returns expected models`() {
        val models = adapter.supportedModels()
        assertThat(models).hasSize(4)
        assertThat(models.map { it.id }).containsExactly(
            "qwen3.5-plus", "qwen-plus", "qwen-turbo", "qwen-long"
        )
        assertThat(models.all { it.provider == "dashscope" }).isTrue()
    }
}
