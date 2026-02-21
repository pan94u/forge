package com.forge.adapter.model

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * ModelAdapter implementation for the Anthropic Claude API.
 *
 * Supports Claude Opus, Sonnet, and Haiku model families via the
 * Anthropic Messages API (https://docs.anthropic.com/en/docs/api-reference).
 *
 * @param apiKey The Anthropic API key. Falls back to ANTHROPIC_API_KEY environment variable.
 * @param baseUrl Base URL for the Anthropic API (default: https://api.anthropic.com)
 */
class ClaudeAdapter(
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: "",
    private val baseUrl: String = "https://api.anthropic.com",
    private val customModels: List<ModelInfo>? = null
) : ModelAdapter {

    private val logger = LoggerFactory.getLogger(ClaudeAdapter::class.java)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val API_VERSION = "2023-06-01"
        private const val DEFAULT_MODEL = "claude-sonnet-4-6"
        private const val MESSAGES_PATH = "/v1/messages"

        val SUPPORTED_MODELS = listOf(
            ModelInfo(
                id = "claude-opus-4-6",
                displayName = "Claude Opus 4.6",
                provider = "anthropic",
                contextWindow = 200_000,
                maxOutputTokens = 128_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.HIGH
            ),
            ModelInfo(
                id = "claude-sonnet-4-6",
                displayName = "Claude Sonnet 4.6",
                provider = "anthropic",
                contextWindow = 200_000,
                maxOutputTokens = 64_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.MEDIUM
            ),
            ModelInfo(
                id = "claude-haiku-4-5-20251001",
                displayName = "Claude Haiku 4.5",
                provider = "anthropic",
                contextWindow = 200_000,
                maxOutputTokens = 64_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.LOW
            )
        )
    }

    override suspend fun complete(prompt: String, options: CompletionOptions): CompletionResult {
        validateApiKey()
        val model = options.model ?: DEFAULT_MODEL

        val requestBody = buildRequestBody(prompt, options, model, stream = false)
        val request = buildHttpRequest(requestBody)

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            response.use { resp ->
                val body = resp.body?.string() ?: throw ModelAdapterException("Empty response body")

                if (!resp.isSuccessful) {
                    handleErrorResponse(resp.code, body)
                }

                parseCompletionResponse(body, model, latency)
            }
        }
    }

    override suspend fun streamComplete(prompt: String, options: CompletionOptions): Flow<String> {
        validateApiKey()
        val model = options.model ?: DEFAULT_MODEL

        val requestBody = buildRequestBody(prompt, options, model, stream = true)
        val request = buildHttpRequest(requestBody)

        return flow {
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                response.close()
                handleErrorResponse(response.code, body)
            }

            val reader: BufferedReader = response.body?.byteStream()?.bufferedReader()
                ?: throw ModelAdapterException("Empty response stream")

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (!currentLine.startsWith("data: ")) continue
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    try {
                        val event = gson.fromJson(data, JsonObject::class.java)
                        val eventType = event.get("type")?.asString

                        if (eventType == "content_block_delta") {
                            val delta = event.getAsJsonObject("delta")
                            val text = delta?.get("text")?.asString
                            if (text != null) {
                                emit(text)
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Skipping unparseable SSE event: {}", data)
                    }
                }
            } finally {
                reader.close()
                response.close()
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Stream a completion with tool calling support.
     *
     * Emits structured [StreamEvent]s that include tool use detection,
     * partial JSON input streaming, and message-level metadata.
     */
    override suspend fun streamWithTools(
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>
    ): Flow<StreamEvent> {
        validateApiKey()
        val model = options.model ?: DEFAULT_MODEL

        val requestBody = buildMessagesRequestBody(messages, options, model, tools, stream = true)
        val request = buildHttpRequest(requestBody)

        return flow {
            logger.debug("Calling Claude API: model=$model, messages=${messages.size}, tools=${tools.size}")
            val response = client.newCall(request).execute()
            logger.debug("Claude API response: status=${response.code}")

            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                response.close()
                handleErrorResponse(response.code, body)
            }

            val reader: BufferedReader = response.body?.byteStream()?.bufferedReader()
                ?: throw ModelAdapterException("Empty response stream")

            try {
                var contentBlockIndex = 0
                val toolUseBlockIndices = mutableSetOf<Int>()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (!currentLine.startsWith("data: ")) continue
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    try {
                        val event = gson.fromJson(data, JsonObject::class.java)
                        val eventType = event.get("type")?.asString ?: continue

                        when (eventType) {
                            "message_start" -> {
                                val msg = event.getAsJsonObject("message")
                                val msgId = msg?.get("id")?.asString ?: ""
                                val msgModel = msg?.get("model")?.asString ?: model
                                emit(StreamEvent.MessageStart(msgId, msgModel))
                            }

                            "content_block_start" -> {
                                contentBlockIndex = event.get("index")?.asInt ?: contentBlockIndex
                                val contentBlock = event.getAsJsonObject("content_block")
                                val blockType = contentBlock?.get("type")?.asString

                                if (blockType == "tool_use") {
                                    toolUseBlockIndices.add(contentBlockIndex)
                                    val toolId = contentBlock.get("id")?.asString ?: ""
                                    val toolName = contentBlock.get("name")?.asString ?: ""
                                    emit(StreamEvent.ToolUseStart(contentBlockIndex, toolId, toolName))
                                }
                                // text blocks don't need an explicit start event
                            }

                            "content_block_delta" -> {
                                val delta = event.getAsJsonObject("delta")
                                val deltaType = delta?.get("type")?.asString

                                when (deltaType) {
                                    "text_delta" -> {
                                        val text = delta.get("text")?.asString
                                        if (text != null) {
                                            emit(StreamEvent.ContentDelta(text))
                                        }
                                    }
                                    "input_json_delta" -> {
                                        val partialJson = delta.get("partial_json")?.asString
                                        if (partialJson != null) {
                                            emit(StreamEvent.ToolInputDelta(partialJson))
                                        }
                                    }
                                }
                            }

                            "content_block_stop" -> {
                                val blockIndex = event.get("index")?.asInt ?: contentBlockIndex
                                // Only emit ToolUseEnd for tool_use blocks, not text blocks
                                if (blockIndex in toolUseBlockIndices) {
                                    emit(StreamEvent.ToolUseEnd(blockIndex))
                                }
                            }

                            "message_delta" -> {
                                val delta = event.getAsJsonObject("delta")
                                val stopReasonStr = delta?.get("stop_reason")?.asString
                                val stopReason = parseStopReason(stopReasonStr)
                                emit(StreamEvent.MessageDelta(stopReason))
                            }

                            "message_stop" -> {
                                emit(StreamEvent.MessageStop)
                            }

                            "error" -> {
                                val error = event.getAsJsonObject("error")
                                val errorMsg = error?.get("message")?.asString ?: "Unknown streaming error"
                                emit(StreamEvent.Error(errorMsg))
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Skipping unparseable SSE event: {}", data)
                    }
                }
            } finally {
                reader.close()
                response.close()
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun supportedModels(): List<ModelInfo> = customModels ?: SUPPORTED_MODELS

    override suspend fun healthCheck(): Boolean {
        if (apiKey.isBlank()) return false
        return try {
            val result = complete(
                "Reply with 'ok'.",
                CompletionOptions(maxTokens = 10, temperature = 0.0)
            )
            result.content.isNotBlank()
        } catch (e: Exception) {
            logger.warn("Claude health check failed: {}", e.message)
            false
        }
    }

    // ---- Internal helpers ----

    private fun validateApiKey() {
        if (apiKey.isBlank()) {
            throw AuthenticationException(
                "Anthropic API key not set. Set ANTHROPIC_API_KEY environment variable or pass it to ClaudeAdapter."
            )
        }
    }

    /**
     * Build request body for simple prompt-based calls (complete / streamComplete).
     */
    private fun buildRequestBody(
        prompt: String,
        options: CompletionOptions,
        model: String,
        stream: Boolean
    ): String {
        val messages = mutableListOf<Map<String, String>>()

        for (msg in options.messages) {
            messages.add(
                mapOf(
                    "role" to when (msg.role) {
                        Message.Role.USER -> "user"
                        Message.Role.ASSISTANT -> "assistant"
                        Message.Role.SYSTEM -> "user"
                        Message.Role.TOOL -> "user"
                    },
                    "content" to msg.content
                )
            )
        }

        messages.add(mapOf("role" to "user", "content" to prompt))

        val body = mutableMapOf<String, Any>(
            "model" to model,
            "max_tokens" to options.maxTokens,
            "messages" to messages
        )

        if (options.systemPrompt != null) {
            body["system"] = options.systemPrompt
        }
        if (options.temperature != 0.7) {
            body["temperature"] = options.temperature
        }
        if (options.topP != 1.0) {
            body["top_p"] = options.topP
        }
        if (options.stopSequences.isNotEmpty()) {
            body["stop_sequences"] = options.stopSequences
        }
        if (stream) {
            body["stream"] = true
        }

        return gson.toJson(body)
    }

    /**
     * Build request body for messages-based calls with tool support (streamWithTools).
     *
     * Handles complex message content (text blocks, tool_use blocks, tool_result blocks)
     * as required by the Claude Messages API.
     */
    internal fun buildMessagesRequestBody(
        messages: List<Message>,
        options: CompletionOptions,
        model: String,
        tools: List<ToolDefinition>,
        stream: Boolean
    ): String {
        val apiMessages = JsonArray()

        for (msg in messages) {
            val msgObj = JsonObject()

            when (msg.role) {
                Message.Role.USER -> {
                    msgObj.addProperty("role", "user")
                    // If the message has tool results, format as content blocks
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        val contentArray = JsonArray()
                        for (result in msg.toolResults) {
                            val resultObj = JsonObject()
                            resultObj.addProperty("type", "tool_result")
                            resultObj.addProperty("tool_use_id", result.toolUseId)
                            resultObj.addProperty("content", result.content)
                            if (result.isError) {
                                resultObj.addProperty("is_error", true)
                            }
                            contentArray.add(resultObj)
                        }
                        msgObj.add("content", contentArray)
                    } else {
                        msgObj.addProperty("content", msg.content)
                    }
                }

                Message.Role.ASSISTANT -> {
                    msgObj.addProperty("role", "assistant")
                    // If the message has tool uses, format as content blocks
                    if (msg.toolUses != null && msg.toolUses.isNotEmpty()) {
                        val contentArray = JsonArray()
                        // Include text if present
                        if (msg.content.isNotBlank()) {
                            val textBlock = JsonObject()
                            textBlock.addProperty("type", "text")
                            textBlock.addProperty("text", msg.content)
                            contentArray.add(textBlock)
                        }
                        for (toolUse in msg.toolUses) {
                            val toolObj = JsonObject()
                            toolObj.addProperty("type", "tool_use")
                            toolObj.addProperty("id", toolUse.id)
                            toolObj.addProperty("name", toolUse.name)
                            toolObj.add("input", gson.toJsonTree(toolUse.input))
                            contentArray.add(toolObj)
                        }
                        msgObj.add("content", contentArray)
                    } else {
                        msgObj.addProperty("content", msg.content)
                    }
                }

                Message.Role.SYSTEM -> {
                    // System messages are handled via the top-level "system" param
                    msgObj.addProperty("role", "user")
                    msgObj.addProperty("content", msg.content)
                }

                Message.Role.TOOL -> {
                    // Tool role messages are sent as user messages with tool_result content
                    msgObj.addProperty("role", "user")
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        val contentArray = JsonArray()
                        for (result in msg.toolResults) {
                            val resultObj = JsonObject()
                            resultObj.addProperty("type", "tool_result")
                            resultObj.addProperty("tool_use_id", result.toolUseId)
                            resultObj.addProperty("content", result.content)
                            if (result.isError) {
                                resultObj.addProperty("is_error", true)
                            }
                            contentArray.add(resultObj)
                        }
                        msgObj.add("content", contentArray)
                    } else {
                        msgObj.addProperty("content", msg.content)
                    }
                }
            }

            apiMessages.add(msgObj)
        }

        val body = JsonObject()
        body.addProperty("model", model)
        body.addProperty("max_tokens", options.maxTokens)
        body.add("messages", apiMessages)

        if (options.systemPrompt != null) {
            val systemArray = JsonArray()
            val systemBlock = JsonObject()
            systemBlock.addProperty("type", "text")
            systemBlock.addProperty("text", options.systemPrompt)
            val cacheControl = JsonObject()
            cacheControl.addProperty("type", "ephemeral")
            systemBlock.add("cache_control", cacheControl)
            systemArray.add(systemBlock)
            body.add("system", systemArray)
        }
        if (options.temperature != 0.7) {
            body.addProperty("temperature", options.temperature)
        }
        if (options.topP != 1.0) {
            body.addProperty("top_p", options.topP)
        }
        if (options.stopSequences.isNotEmpty()) {
            body.add("stop_sequences", gson.toJsonTree(options.stopSequences))
        }

        // Add tools
        if (tools.isNotEmpty()) {
            val toolsArray = JsonArray()
            for (tool in tools) {
                val toolObj = JsonObject()
                toolObj.addProperty("name", tool.name)
                toolObj.addProperty("description", tool.description)
                toolObj.add("input_schema", gson.toJsonTree(tool.inputSchema))
                toolsArray.add(toolObj)
            }
            body.add("tools", toolsArray)
        }

        if (stream) {
            body.addProperty("stream", true)
        }

        return gson.toJson(body)
    }

    private fun buildHttpRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$baseUrl$MESSAGES_PATH")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("anthropic-beta", "prompt-caching-2024-07-31")
            .addHeader("content-type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseCompletionResponse(body: String, model: String, latencyMs: Long): CompletionResult {
        val json = gson.fromJson(body, JsonObject::class.java)

        val contentArray = json.getAsJsonArray("content")
        val textContent = contentArray
            ?.filter { it.asJsonObject.get("type")?.asString == "text" }
            ?.joinToString("") { it.asJsonObject.get("text").asString }
            ?: ""

        val usageObj = json.getAsJsonObject("usage")
        val usage = TokenUsage(
            inputTokens = usageObj?.get("input_tokens")?.asInt ?: 0,
            outputTokens = usageObj?.get("output_tokens")?.asInt ?: 0
        )

        val stopReasonStr = json.get("stop_reason")?.asString
        val stopReason = parseStopReason(stopReasonStr)

        val responseModel = json.get("model")?.asString ?: model

        return CompletionResult(
            content = textContent,
            model = responseModel,
            usage = usage,
            stopReason = stopReason,
            latencyMs = latencyMs
        )
    }

    private fun parseStopReason(stopReasonStr: String?): StopReason {
        return when (stopReasonStr) {
            "end_turn" -> StopReason.END_TURN
            "stop_sequence" -> StopReason.STOP_SEQUENCE
            "max_tokens" -> StopReason.MAX_TOKENS
            "tool_use" -> StopReason.TOOL_USE
            else -> StopReason.END_TURN
        }
    }

    private fun handleErrorResponse(statusCode: Int, body: String): Nothing {
        val message = try {
            val json = gson.fromJson(body, JsonObject::class.java)
            json.getAsJsonObject("error")?.get("message")?.asString ?: body
        } catch (e: Exception) {
            body
        }

        when (statusCode) {
            401 -> throw AuthenticationException("Invalid API key: $message")
            429 -> throw RateLimitException("Rate limited: $message")
            404 -> throw ModelNotAvailableException("Model not found: $message")
            else -> throw ModelAdapterException("API error ($statusCode): $message", statusCode = statusCode)
        }
    }
}
