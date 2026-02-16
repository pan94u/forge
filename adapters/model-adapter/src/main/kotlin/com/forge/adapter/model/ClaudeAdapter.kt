package com.forge.adapter.model

import com.google.gson.Gson
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
    private val baseUrl: String = "https://api.anthropic.com"
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
        private const val DEFAULT_MODEL = "claude-sonnet-4-20250514"
        private const val MESSAGES_PATH = "/v1/messages"

        val SUPPORTED_MODELS = listOf(
            ModelInfo(
                id = "claude-opus-4-20250514",
                displayName = "Claude Opus 4",
                provider = "anthropic",
                contextWindow = 200_000,
                maxOutputTokens = 32_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.HIGH
            ),
            ModelInfo(
                id = "claude-sonnet-4-20250514",
                displayName = "Claude Sonnet 4",
                provider = "anthropic",
                contextWindow = 200_000,
                maxOutputTokens = 16_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.MEDIUM
            ),
            ModelInfo(
                id = "claude-haiku-3-5-20241022",
                displayName = "Claude 3.5 Haiku",
                provider = "anthropic",
                contextWindow = 200_000,
                maxOutputTokens = 8_192,
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

    override fun supportedModels(): List<ModelInfo> = SUPPORTED_MODELS

    override suspend fun healthCheck(): Boolean {
        if (apiKey.isBlank()) return false
        return try {
            // Use a minimal request to verify connectivity
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

    private fun validateApiKey() {
        if (apiKey.isBlank()) {
            throw AuthenticationException(
                "Anthropic API key not set. Set ANTHROPIC_API_KEY environment variable or pass it to ClaudeAdapter."
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

        // Add conversation history
        for (msg in options.messages) {
            messages.add(
                mapOf(
                    "role" to when (msg.role) {
                        Message.Role.USER -> "user"
                        Message.Role.ASSISTANT -> "assistant"
                        Message.Role.SYSTEM -> "user" // System messages handled separately
                    },
                    "content" to msg.content
                )
            )
        }

        // Add the current prompt
        messages.add(mapOf("role" to "user", "content" to prompt))

        val body = mutableMapOf<String, Any>(
            "model" to model,
            "max_tokens" to options.maxTokens,
            "messages" to messages
        )

        if (options.systemPrompt != null) {
            body["system"] = options.systemPrompt!!
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

    private fun buildHttpRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$baseUrl$MESSAGES_PATH")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", API_VERSION)
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
        val stopReason = when (stopReasonStr) {
            "end_turn" -> StopReason.END_TURN
            "stop_sequence" -> StopReason.STOP_SEQUENCE
            "max_tokens" -> StopReason.MAX_TOKENS
            else -> StopReason.END_TURN
        }

        val responseModel = json.get("model")?.asString ?: model

        return CompletionResult(
            content = textContent,
            model = responseModel,
            usage = usage,
            stopReason = stopReason,
            latencyMs = latencyMs
        )
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
