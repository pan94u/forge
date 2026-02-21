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

class GeminiAdapterTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var adapter: GeminiAdapter
    private val gson = Gson()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        adapter = GeminiAdapter(
            apiKey = "test-api-key",
            baseUrl = mockServer.url("/v1beta").toString().removeSuffix("/v1beta")
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
            "candidates": [{
                "content": {"parts": [{"text": "Hello!"}], "role": "model"},
                "finishReason": "STOP"
            }],
            "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5}
        }"""

        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(responseBody))

        val result = adapter.complete("Hi", CompletionOptions(maxTokens = 100))

        assertThat(result.content).isEqualTo("Hello!")
        assertThat(result.stopReason).isEqualTo(StopReason.END_TURN)
        assertThat(result.usage.inputTokens).isEqualTo(10)
        assertThat(result.usage.outputTokens).isEqualTo(5)

        // Verify the request URL contains the model and API key
        val request = mockServer.takeRequest()
        assertThat(request.path).contains("generateContent")
        assertThat(request.path).contains("key=test-api-key")
    }

    @Test
    fun `complete throws AuthenticationException on blank key`() {
        val adapterNoKey = GeminiAdapter(apiKey = "", baseUrl = mockServer.url("/").toString())

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapterNoKey.complete("Hi", CompletionOptions(maxTokens = 100))
            }
        }.isInstanceOf(AuthenticationException::class.java)
            .hasMessageContaining("Gemini API key not set")
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
            .setBody("""{"error":{"message":"Resource exhausted"}}"""))

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                adapter.complete("Hi", CompletionOptions(maxTokens = 100))
            }
        }.isInstanceOf(RateLimitException::class.java)
    }

    // --- Streaming Tests ---

    @Test
    fun `streamComplete emits text chunks`() = runTest {
        val sseBody = """data: {"candidates":[{"content":{"parts":[{"text":"Hello"}],"role":"model"}}]}

data: {"candidates":[{"content":{"parts":[{"text":" world"}],"role":"model"}}]}

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
        val sseBody = """data: {"candidates":[{"content":{"parts":[{"text":"Hello"}],"role":"model"}}]}

data: {"candidates":[{"content":{"parts":[{"text":" world"}],"role":"model"},"finishReason":"STOP"}]}

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

        assertThat(events.last()).isEqualTo(StreamEvent.MessageStop)
    }

    @Test
    fun `streamWithTools emits function call events`() = runTest {
        val sseBody = """data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"search_knowledge","args":{"query":"test"}}}],"role":"model"},"finishReason":"FUNCTION_CALL"}]}

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

        val toolDeltas = events.filterIsInstance<StreamEvent.ToolInputDelta>()
        assertThat(toolDeltas).hasSize(1)
        assertThat(toolDeltas[0].partialJson).contains("query")

        val toolEnds = events.filterIsInstance<StreamEvent.ToolUseEnd>()
        assertThat(toolEnds).hasSize(1)

        val messageDelta = events.filterIsInstance<StreamEvent.MessageDelta>()
        assertThat(messageDelta[0].stopReason).isEqualTo(StopReason.TOOL_USE)
    }

    // --- Request Body Tests ---

    @Test
    fun `buildMessagesRequestBody includes function declarations`() {
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
            tools = tools
        )

        val json = gson.fromJson(body, JsonObject::class.java)

        // Verify systemInstruction
        val sysInstr = json.getAsJsonObject("systemInstruction")
        assertThat(sysInstr).isNotNull
        assertThat(sysInstr.getAsJsonArray("parts")[0].asJsonObject.get("text").asString)
            .isEqualTo("You are helpful")

        // Verify tools
        val toolsArray = json.getAsJsonArray("tools")
        assertThat(toolsArray).hasSize(1)
        val funcDecls = toolsArray[0].asJsonObject.getAsJsonArray("functionDeclarations")
        assertThat(funcDecls).hasSize(1)
        assertThat(funcDecls[0].asJsonObject.get("name").asString).isEqualTo("search_knowledge")

        // Verify contents
        val contents = json.getAsJsonArray("contents")
        assertThat(contents).hasSize(1)
        assertThat(contents[0].asJsonObject.get("role").asString).isEqualTo("user")
    }

    @Test
    fun `buildMessagesRequestBody handles function response`() {
        val messages = listOf(
            Message(Message.Role.USER, "Search for X"),
            Message(
                role = Message.Role.ASSISTANT,
                content = "",
                toolUses = listOf(ToolUse("call_1", "search_knowledge", mapOf("query" to "X")))
            ),
            Message(
                role = Message.Role.TOOL,
                content = "",
                toolResults = listOf(ToolResult("call_1", "Found: X docs", false))
            )
        )

        val body = adapter.buildMessagesRequestBody(
            messages = messages,
            options = CompletionOptions(maxTokens = 1000),
            tools = emptyList()
        )

        val json = gson.fromJson(body, JsonObject::class.java)
        val contents = json.getAsJsonArray("contents")
        assertThat(contents.size()).isEqualTo(3)

        // Assistant message should have functionCall part
        val assistantContent = contents[1].asJsonObject
        assertThat(assistantContent.get("role").asString).isEqualTo("model")
        val parts = assistantContent.getAsJsonArray("parts")
        val fcPart = parts.firstOrNull { it.asJsonObject.has("functionCall") }
        assertThat(fcPart).isNotNull

        // Tool result message should have functionResponse part
        val toolContent = contents[2].asJsonObject
        val toolParts = toolContent.getAsJsonArray("parts")
        val frPart = toolParts.firstOrNull { it.asJsonObject.has("functionResponse") }
        assertThat(frPart).isNotNull
    }

    // --- Model Info Tests ---

    @Test
    fun `supportedModels returns expected models`() {
        val models = adapter.supportedModels()
        assertThat(models).hasSize(3)
        assertThat(models.map { it.id }).containsExactly(
            "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite"
        )
        assertThat(models.all { it.provider == "google" }).isTrue()
    }
}
