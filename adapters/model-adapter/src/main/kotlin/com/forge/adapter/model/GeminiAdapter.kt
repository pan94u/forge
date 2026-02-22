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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ModelAdapter implementation for Google's Gemini models via the Generative Language API.
 *
 * Supports Gemini 2.0 Flash, 2.0 Pro, and 1.5 Pro model families with streaming
 * and function calling (tool use) capabilities.
 *
 * @param apiKey Google AI API key. Falls back to GEMINI_API_KEY environment variable.
 * @param baseUrl Gemini API base URL
 */
class GeminiAdapter(
    private val apiKey: String = System.getenv("GEMINI_API_KEY") ?: "",
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val customModels: List<ModelInfo>? = null
) : ModelAdapter {

    private val logger = LoggerFactory.getLogger(GeminiAdapter::class.java)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val DEFAULT_MODEL = "gemini-2.5-flash"

        val SUPPORTED_MODELS = listOf(
            ModelInfo(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                provider = "google",
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.HIGH
            ),
            ModelInfo(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                provider = "google",
                contextWindow = 1_000_000,
                maxOutputTokens = 65_535,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.MEDIUM
            ),
            ModelInfo(
                id = "gemini-2.5-flash-lite",
                displayName = "Gemini 2.5 Flash Lite",
                provider = "google",
                contextWindow = 1_000_000,
                maxOutputTokens = 32_768,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.LOW
            )
        )
    }

    override suspend fun complete(prompt: String, options: CompletionOptions): CompletionResult {
        validateApiKey()
        val model = options.model ?: DEFAULT_MODEL

        val requestBody = buildGenerateContentBody(prompt, options)
        val url = "$baseUrl/models/$model:generateContent?key=$apiKey"
        val request = buildHttpRequest(url, requestBody)

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            response.use { resp ->
                val body = resp.body?.string() ?: throw ModelAdapterException("Empty response body")

                if (!resp.isSuccessful) {
                    handleErrorResponse(resp.code, body)
                }

                parseGenerateContentResponse(body, model, latency)
            }
        }
    }

    override suspend fun streamComplete(prompt: String, options: CompletionOptions): Flow<String> {
        validateApiKey()
        val model = options.model ?: DEFAULT_MODEL

        val requestBody = buildGenerateContentBody(prompt, options)
        val url = "$baseUrl/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val request = buildHttpRequest(url, requestBody)

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
                    if (data.isEmpty()) continue

                    try {
                        val event = gson.fromJson(data, JsonObject::class.java)
                        val text = extractTextFromCandidate(event)
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

        val requestBody = buildMessagesRequestBody(messages, options, tools)
        val url = "$baseUrl/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val request = buildHttpRequest(url, requestBody)

        return flow {
            logger.debug("Calling Gemini API: model={}, messages={}, tools={}", model, messages.size, tools.size)
            val response = client.newCall(request).execute()
            logger.debug("Gemini API response: status={}", response.code)

            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                response.close()
                handleErrorResponse(response.code, body)
            }

            val reader: BufferedReader = response.body?.byteStream()?.bufferedReader()
                ?: throw ModelAdapterException("Empty response stream")

            try {
                emit(StreamEvent.MessageStart("", model))
                var toolCallIndex = 0
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (!currentLine.startsWith("data: ")) continue
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data.isEmpty()) continue

                    try {
                        val event = gson.fromJson(data, JsonObject::class.java)
                        val candidates = event.getAsJsonArray("candidates")
                        val candidate = candidates?.get(0)?.asJsonObject ?: continue
                        val content = candidate.getAsJsonObject("content") ?: continue
                        val parts = content.getAsJsonArray("parts") ?: continue

                        for (part in parts) {
                            val partObj = part.asJsonObject

                            // Text content
                            val text = partObj.get("text")?.asString
                            if (text != null) {
                                emit(StreamEvent.ContentDelta(text))
                            }

                            // Function call (Gemini's tool use)
                            val functionCall = partObj.getAsJsonObject("functionCall")
                            if (functionCall != null) {
                                val name = functionCall.get("name")?.asString ?: ""
                                val args = functionCall.getAsJsonObject("args")
                                val id = UUID.randomUUID().toString()
                                emit(StreamEvent.ToolUseStart(toolCallIndex, id, name))
                                if (args != null) {
                                    emit(StreamEvent.ToolInputDelta(gson.toJson(args)))
                                }
                                emit(StreamEvent.ToolUseEnd(toolCallIndex))
                                toolCallIndex++
                            }
                        }

                        // Check finish reason
                        val finishReason = candidate.get("finishReason")?.asString
                        if (finishReason != null) {
                            emit(StreamEvent.MessageDelta(parseFinishReason(finishReason)))
                        }
                    } catch (e: Exception) {
                        logger.debug("Skipping unparseable Gemini SSE event: {}", data)
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
            logger.warn("Gemini health check failed: {}", e.message)
            false
        }
    }

    // ---- Internal helpers ----

    private fun validateApiKey() {
        if (apiKey.isBlank()) {
            throw AuthenticationException(
                "Gemini API key not set. Set GEMINI_API_KEY environment variable or pass it to GeminiAdapter."
            )
        }
    }

    /**
     * Build request body for simple prompt-based calls.
     * Gemini uses a "contents" array with "parts" structure.
     */
    private fun buildGenerateContentBody(prompt: String, options: CompletionOptions): String {
        val body = JsonObject()

        val contents = JsonArray()

        // System instruction (Gemini supports it as a top-level field)
        if (options.systemPrompt != null) {
            val systemInstruction = JsonObject()
            val parts = JsonArray()
            val part = JsonObject()
            part.addProperty("text", options.systemPrompt)
            parts.add(part)
            systemInstruction.add("parts", parts)
            body.add("systemInstruction", systemInstruction)
        }

        // Previous messages
        for (msg in options.messages) {
            val contentObj = JsonObject()
            contentObj.addProperty("role", geminiRole(msg.role))
            val parts = JsonArray()
            val part = JsonObject()
            part.addProperty("text", msg.content)
            parts.add(part)
            contentObj.add("parts", parts)
            contents.add(contentObj)
        }

        // Current prompt
        val userContent = JsonObject()
        userContent.addProperty("role", "user")
        val userParts = JsonArray()
        val userPart = JsonObject()
        userPart.addProperty("text", prompt)
        userParts.add(userPart)
        userContent.add("parts", userParts)
        contents.add(userContent)

        body.add("contents", contents)

        // Generation config
        val genConfig = JsonObject()
        genConfig.addProperty("maxOutputTokens", options.maxTokens)
        genConfig.addProperty("temperature", options.temperature)
        genConfig.addProperty("topP", options.topP)
        if (options.stopSequences.isNotEmpty()) {
            genConfig.add("stopSequences", gson.toJsonTree(options.stopSequences))
        }
        body.add("generationConfig", genConfig)

        return gson.toJson(body)
    }

    /**
     * Build request body for messages-based calls with tool support.
     */
    internal fun buildMessagesRequestBody(
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>
    ): String {
        val body = JsonObject()

        // System instruction
        if (options.systemPrompt != null) {
            val systemInstruction = JsonObject()
            val parts = JsonArray()
            val part = JsonObject()
            part.addProperty("text", options.systemPrompt)
            parts.add(part)
            systemInstruction.add("parts", parts)
            body.add("systemInstruction", systemInstruction)
        }

        // Contents (messages)
        val contents = JsonArray()
        for (msg in messages) {
            val contentObj = JsonObject()
            val parts = JsonArray()

            when (msg.role) {
                Message.Role.USER -> {
                    contentObj.addProperty("role", "user")
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        // Function response format
                        for (result in msg.toolResults) {
                            val part = JsonObject()
                            val functionResponse = JsonObject()
                            functionResponse.addProperty("name", result.toolUseId)
                            val response = JsonObject()
                            response.addProperty("content", result.content)
                            functionResponse.add("response", response)
                            part.add("functionResponse", functionResponse)
                            parts.add(part)
                        }
                    } else {
                        val part = JsonObject()
                        part.addProperty("text", msg.content)
                        parts.add(part)
                    }
                }

                Message.Role.ASSISTANT -> {
                    contentObj.addProperty("role", "model")
                    if (msg.content.isNotBlank()) {
                        val part = JsonObject()
                        part.addProperty("text", msg.content)
                        parts.add(part)
                    }
                    if (msg.toolUses != null) {
                        for (toolUse in msg.toolUses) {
                            val part = JsonObject()
                            val functionCall = JsonObject()
                            functionCall.addProperty("name", toolUse.name)
                            functionCall.add("args", gson.toJsonTree(toolUse.input))
                            part.add("functionCall", functionCall)
                            parts.add(part)
                        }
                    }
                }

                Message.Role.SYSTEM -> {
                    // Skip — handled via systemInstruction
                    continue
                }

                Message.Role.TOOL -> {
                    contentObj.addProperty("role", "user")
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        for (result in msg.toolResults) {
                            val part = JsonObject()
                            val functionResponse = JsonObject()
                            functionResponse.addProperty("name", result.toolUseId)
                            val response = JsonObject()
                            response.addProperty("content", result.content)
                            functionResponse.add("response", response)
                            part.add("functionResponse", functionResponse)
                            parts.add(part)
                        }
                    } else {
                        val part = JsonObject()
                        part.addProperty("text", msg.content)
                        parts.add(part)
                    }
                }
            }

            contentObj.add("parts", parts)
            contents.add(contentObj)
        }

        body.add("contents", contents)

        // Generation config
        val genConfig = JsonObject()
        genConfig.addProperty("maxOutputTokens", options.maxTokens)
        genConfig.addProperty("temperature", options.temperature)
        genConfig.addProperty("topP", options.topP)
        if (options.stopSequences.isNotEmpty()) {
            genConfig.add("stopSequences", gson.toJsonTree(options.stopSequences))
        }
        body.add("generationConfig", genConfig)

        // Tools (function declarations)
        if (tools.isNotEmpty()) {
            val toolsArray = JsonArray()
            val toolObj = JsonObject()
            val functionDeclarations = JsonArray()
            for (tool in tools) {
                val fd = JsonObject()
                fd.addProperty("name", tool.name)
                fd.addProperty("description", tool.description)
                fd.add("parameters", gson.toJsonTree(tool.inputSchema))
                functionDeclarations.add(fd)
            }
            toolObj.add("functionDeclarations", functionDeclarations)
            toolsArray.add(toolObj)
            body.add("tools", toolsArray)
        }

        return gson.toJson(body)
    }

    private fun buildHttpRequest(url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseGenerateContentResponse(body: String, model: String, latencyMs: Long): CompletionResult {
        val json = gson.fromJson(body, JsonObject::class.java)

        val text = extractTextFromCandidate(json) ?: ""

        val usageMetadata = json.getAsJsonObject("usageMetadata")
        val candidate = json.getAsJsonArray("candidates")?.get(0)?.asJsonObject
        val finishReason = candidate?.get("finishReason")?.asString

        return CompletionResult(
            content = text,
            model = model,
            usage = TokenUsage(
                inputTokens = usageMetadata?.get("promptTokenCount")?.asInt ?: 0,
                outputTokens = usageMetadata?.get("candidatesTokenCount")?.asInt ?: 0
            ),
            stopReason = parseFinishReason(finishReason),
            latencyMs = latencyMs
        )
    }

    private fun extractTextFromCandidate(json: JsonObject): String? {
        val candidates = json.getAsJsonArray("candidates") ?: return null
        val candidate = candidates.get(0)?.asJsonObject ?: return null
        val content = candidate.getAsJsonObject("content") ?: return null
        val parts = content.getAsJsonArray("parts") ?: return null

        return parts.mapNotNull { part ->
            part.asJsonObject.get("text")?.asString
        }.joinToString("").ifEmpty { null }
    }

    private fun geminiRole(role: Message.Role): String {
        return when (role) {
            Message.Role.USER, Message.Role.TOOL -> "user"
            Message.Role.ASSISTANT -> "model"
            Message.Role.SYSTEM -> "user" // Gemini handles system via systemInstruction
        }
    }

    private fun parseFinishReason(reason: String?): StopReason {
        return when (reason) {
            "STOP" -> StopReason.END_TURN
            "MAX_TOKENS" -> StopReason.MAX_TOKENS
            "STOP_SEQUENCE" -> StopReason.STOP_SEQUENCE
            "FUNCTION_CALL" -> StopReason.TOOL_USE
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
            400 -> throw ModelAdapterException("Invalid request: $message", statusCode = 400)
            401, 403 -> throw AuthenticationException("Gemini auth failed: $message")
            429 -> throw RateLimitException("Gemini rate limited: $message")
            404 -> throw ModelNotAvailableException("Model not found: $message")
            else -> throw ModelAdapterException("Gemini API error ($statusCode): $message", statusCode = statusCode)
        }
    }
}
