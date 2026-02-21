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
 * ModelAdapter implementation for Alibaba Cloud's Qwen models via DashScope API.
 *
 * DashScope exposes an OpenAI-compatible endpoint, so this adapter follows the
 * OpenAI Chat Completions format with Qwen-specific model IDs and tool calling.
 *
 * @param apiKey DashScope API key. Falls back to DASHSCOPE_API_KEY environment variable.
 * @param baseUrl DashScope API base URL (default: OpenAI-compatible endpoint)
 */
class QwenAdapter(
    private val apiKey: String = System.getenv("DASHSCOPE_API_KEY") ?: "",
    private val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    private val customModels: List<ModelInfo>? = null
) : ModelAdapter {

    private val logger = LoggerFactory.getLogger(QwenAdapter::class.java)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val DEFAULT_MODEL = "qwen-plus"

        val SUPPORTED_MODELS = listOf(
            ModelInfo(
                id = "qwen3.5-plus",
                displayName = "Qwen 3.5 Plus",
                provider = "dashscope",
                contextWindow = 1_000_000,
                maxOutputTokens = 16_384,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.HIGH
            ),
            ModelInfo(
                id = "qwen-plus",
                displayName = "Qwen Plus",
                provider = "dashscope",
                contextWindow = 131_072,
                maxOutputTokens = 8_192,
                supportsStreaming = true,
                supportsVision = false,
                costTier = CostTier.MEDIUM
            ),
            ModelInfo(
                id = "qwen-turbo",
                displayName = "Qwen Turbo",
                provider = "dashscope",
                contextWindow = 131_072,
                maxOutputTokens = 8_192,
                supportsStreaming = true,
                supportsVision = false,
                costTier = CostTier.LOW
            ),
            ModelInfo(
                id = "qwen-long",
                displayName = "Qwen Long",
                provider = "dashscope",
                contextWindow = 1_000_000,
                maxOutputTokens = 6_000,
                supportsStreaming = true,
                supportsVision = false,
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
                        val delta = event.getAsJsonArray("choices")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("delta")
                        val text = delta?.get("content")?.asString
                        if (text != null) emit(text)
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
            logger.debug("Calling Qwen API: model={}, messages={}, tools={}", model, messages.size, tools.size)
            val response = client.newCall(request).execute()
            logger.debug("Qwen API response: status={}", response.code)

            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                response.close()
                handleErrorResponse(response.code, body)
            }

            val reader: BufferedReader = response.body?.byteStream()?.bufferedReader()
                ?: throw ModelAdapterException("Empty response stream")

            try {
                var toolCallIndex = 0
                var currentToolId = ""
                var line: String?

                emit(StreamEvent.MessageStart("", model))

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (!currentLine.startsWith("data: ")) continue
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    try {
                        val event = gson.fromJson(data, JsonObject::class.java)
                        val choice = event.getAsJsonArray("choices")
                            ?.get(0)?.asJsonObject ?: continue
                        val delta = choice.getAsJsonObject("delta") ?: continue

                        // Handle text content
                        val content = delta.get("content")?.asString
                        if (!content.isNullOrEmpty()) {
                            emit(StreamEvent.ContentDelta(content))
                        }

                        // Handle tool calls (OpenAI function calling format)
                        val toolCalls = delta.getAsJsonArray("tool_calls")
                        if (toolCalls != null && toolCalls.size() > 0) {
                            for (tc in toolCalls) {
                                val toolCall = tc.asJsonObject
                                val function = toolCall.getAsJsonObject("function") ?: continue

                                val name = function.get("name")?.asString
                                val arguments = function.get("arguments")?.asString

                                if (name != null) {
                                    val id = toolCall.get("id")?.asString ?: "call_$toolCallIndex"
                                    currentToolId = id
                                    emit(StreamEvent.ToolUseStart(toolCallIndex, id, name))
                                }

                                if (arguments != null) {
                                    emit(StreamEvent.ToolInputDelta(arguments))
                                }
                            }
                        }

                        // Handle finish_reason
                        val finishReason = choice.get("finish_reason")?.asString
                        if (finishReason != null) {
                            if (currentToolId.isNotEmpty()) {
                                emit(StreamEvent.ToolUseEnd(toolCallIndex))
                                toolCallIndex++
                                currentToolId = ""
                            }
                            emit(StreamEvent.MessageDelta(parseFinishReason(finishReason)))
                        }
                    } catch (e: Exception) {
                        logger.debug("Skipping unparseable SSE event: {}", data)
                    }
                }

                emit(StreamEvent.MessageStop)
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
            logger.warn("Qwen health check failed: {}", e.message)
            false
        }
    }

    // ---- Internal helpers ----

    private fun validateApiKey() {
        if (apiKey.isBlank()) {
            throw AuthenticationException(
                "DashScope API key not set. Set DASHSCOPE_API_KEY environment variable or pass it to QwenAdapter."
            )
        }
    }

    private fun buildRequestBody(
        prompt: String,
        options: CompletionOptions,
        model: String,
        stream: Boolean
    ): String {
        val messages = mutableListOf<Map<String, String>>()

        if (options.systemPrompt != null) {
            messages.add(mapOf("role" to "system", "content" to options.systemPrompt))
        }

        for (msg in options.messages) {
            messages.add(
                mapOf(
                    "role" to when (msg.role) {
                        Message.Role.USER -> "user"
                        Message.Role.ASSISTANT -> "assistant"
                        Message.Role.SYSTEM -> "system"
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
            "messages" to messages,
            "stream" to stream
        )

        if (options.temperature != 0.7) {
            body["temperature"] = options.temperature
        }
        if (options.topP != 1.0) {
            body["top_p"] = options.topP
        }
        if (options.stopSequences.isNotEmpty()) {
            body["stop"] = options.stopSequences
        }

        return gson.toJson(body)
    }

    internal fun buildMessagesRequestBody(
        messages: List<Message>,
        options: CompletionOptions,
        model: String,
        tools: List<ToolDefinition>,
        stream: Boolean
    ): String {
        val apiMessages = JsonArray()

        if (options.systemPrompt != null) {
            val sysMsg = JsonObject()
            sysMsg.addProperty("role", "system")
            sysMsg.addProperty("content", options.systemPrompt)
            apiMessages.add(sysMsg)
        }

        for (msg in messages) {
            val msgObj = JsonObject()

            when (msg.role) {
                Message.Role.USER -> {
                    msgObj.addProperty("role", "user")
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        // Send tool results back (OpenAI format: role=tool)
                        for (result in msg.toolResults) {
                            val toolMsg = JsonObject()
                            toolMsg.addProperty("role", "tool")
                            toolMsg.addProperty("tool_call_id", result.toolUseId)
                            toolMsg.addProperty("content", result.content)
                            apiMessages.add(toolMsg)
                        }
                        continue
                    } else {
                        msgObj.addProperty("content", msg.content)
                    }
                }

                Message.Role.ASSISTANT -> {
                    msgObj.addProperty("role", "assistant")
                    if (msg.toolUses != null && msg.toolUses.isNotEmpty()) {
                        if (msg.content.isNotBlank()) {
                            msgObj.addProperty("content", msg.content)
                        }
                        val toolCalls = JsonArray()
                        for (toolUse in msg.toolUses) {
                            val tc = JsonObject()
                            tc.addProperty("id", toolUse.id)
                            tc.addProperty("type", "function")
                            val fn = JsonObject()
                            fn.addProperty("name", toolUse.name)
                            fn.addProperty("arguments", gson.toJson(toolUse.input))
                            tc.add("function", fn)
                            toolCalls.add(tc)
                        }
                        msgObj.add("tool_calls", toolCalls)
                    } else {
                        msgObj.addProperty("content", msg.content)
                    }
                }

                Message.Role.SYSTEM -> {
                    msgObj.addProperty("role", "system")
                    msgObj.addProperty("content", msg.content)
                }

                Message.Role.TOOL -> {
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        for (result in msg.toolResults) {
                            val toolMsg = JsonObject()
                            toolMsg.addProperty("role", "tool")
                            toolMsg.addProperty("tool_call_id", result.toolUseId)
                            toolMsg.addProperty("content", result.content)
                            apiMessages.add(toolMsg)
                        }
                        continue
                    } else {
                        msgObj.addProperty("role", "user")
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
        body.addProperty("stream", stream)

        if (options.temperature != 0.7) {
            body.addProperty("temperature", options.temperature)
        }
        if (options.topP != 1.0) {
            body.addProperty("top_p", options.topP)
        }
        if (options.stopSequences.isNotEmpty()) {
            body.add("stop", gson.toJsonTree(options.stopSequences))
        }

        // Add tools in OpenAI function calling format
        if (tools.isNotEmpty()) {
            val toolsArray = JsonArray()
            for (tool in tools) {
                val toolObj = JsonObject()
                toolObj.addProperty("type", "function")
                val fn = JsonObject()
                fn.addProperty("name", tool.name)
                fn.addProperty("description", tool.description)
                fn.add("parameters", gson.toJsonTree(tool.inputSchema))
                toolObj.add("function", fn)
                toolsArray.add(toolObj)
            }
            body.add("tools", toolsArray)
        }

        return gson.toJson(body)
    }

    private fun buildHttpRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseCompletionResponse(body: String, model: String, latencyMs: Long): CompletionResult {
        val json = gson.fromJson(body, JsonObject::class.java)

        val choice = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
        val content = choice?.getAsJsonObject("message")?.get("content")?.asString ?: ""

        val usage = json.getAsJsonObject("usage")

        return CompletionResult(
            content = content,
            model = json.get("model")?.asString ?: model,
            usage = TokenUsage(
                inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0,
                outputTokens = usage?.get("completion_tokens")?.asInt ?: 0
            ),
            stopReason = parseFinishReason(choice?.get("finish_reason")?.asString),
            latencyMs = latencyMs
        )
    }

    private fun parseFinishReason(reason: String?): StopReason {
        return when (reason) {
            "stop" -> StopReason.END_TURN
            "length" -> StopReason.MAX_TOKENS
            "tool_calls" -> StopReason.TOOL_USE
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
            401, 403 -> throw AuthenticationException("DashScope auth failed: $message")
            429 -> throw RateLimitException("DashScope rate limited: $message")
            404 -> throw ModelNotAvailableException("Model not found: $message")
            else -> throw ModelAdapterException("DashScope API error ($statusCode): $message", statusCode = statusCode)
        }
    }
}
