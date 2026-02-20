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
import java.util.concurrent.TimeUnit

/**
 * ModelAdapter implementation for locally-hosted models or any OpenAI-compatible API.
 *
 * Connects to local model servers that expose an OpenAI-compatible API,
 * such as Ollama, llama.cpp server, vLLM, or LocalAI. Also supports
 * corporate/private OpenAI-compatible endpoints with API key authentication.
 *
 * @param baseUrl The model server URL (default: http://localhost:11434 for Ollama)
 * @param defaultModel The default model name on the server
 * @param apiKey Optional API key for authenticated endpoints (falls back to LOCAL_MODEL_API_KEY env var)
 */
class LocalModelAdapter(
    private val baseUrl: String = System.getenv("LOCAL_MODEL_URL") ?: "http://localhost:11434",
    private val defaultModel: String = System.getenv("LOCAL_MODEL_NAME") ?: "llama3.1:8b",
    private val apiKey: String? = System.getenv("LOCAL_MODEL_API_KEY")
) : ModelAdapter {

    private val logger = LoggerFactory.getLogger(LocalModelAdapter::class.java)
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // Local models can be slow
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(prompt: String, options: CompletionOptions): CompletionResult {
        val model = options.model ?: defaultModel
        logger.info("Local model complete: model={}, url={}", model, baseUrl)

        val isOllama = baseUrl.contains("11434")

        return if (isOllama) {
            completeViaOllama(prompt, options, model)
        } else {
            completeViaOpenAICompat(prompt, options, model)
        }
    }

    override suspend fun streamComplete(prompt: String, options: CompletionOptions): Flow<String> {
        val model = options.model ?: defaultModel
        logger.info("Local model stream: model={}, url={}", model, baseUrl)

        return flow {
            val isOllama = baseUrl.contains("11434")

            val requestBody = if (isOllama) {
                gson.toJson(
                    mapOf(
                        "model" to model,
                        "prompt" to prompt,
                        "stream" to true,
                        "options" to mapOf(
                            "temperature" to options.temperature,
                            "num_predict" to options.maxTokens
                        )
                    )
                )
            } else {
                gson.toJson(
                    mapOf(
                        "model" to model,
                        "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                        "stream" to true,
                        "max_tokens" to options.maxTokens,
                        "temperature" to options.temperature
                    )
                )
            }

            val endpoint = if (isOllama) "$baseUrl/api/generate" else "$baseUrl/v1/chat/completions"

            val request = Request.Builder()
                .url(endpoint)
                .addAuthIfPresent()
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                response.close()
                throw ModelAdapterException("Local model error (${response.code}): $body")
            }

            val reader = response.body?.byteStream()?.bufferedReader()
                ?: throw ModelAdapterException("Empty response stream")

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue

                    if (isOllama) {
                        val json = gson.fromJson(currentLine, JsonObject::class.java)
                        val text = json.get("response")?.asString
                        if (text != null) emit(text)
                        if (json.get("done")?.asBoolean == true) break
                    } else {
                        if (!currentLine.startsWith("data: ")) continue
                        val data = currentLine.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        val json = gson.fromJson(data, JsonObject::class.java)
                        val delta = json.getAsJsonArray("choices")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("delta")
                        val text = delta?.get("content")?.asString
                        if (text != null) emit(text)
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
        val model = options.model ?: defaultModel
        logger.info("Local model stream: model={}, url={}", model, baseUrl)

        return flow {
            val apiMessages = messages.map { msg ->
                mapOf(
                    "role" to when (msg.role) {
                        Message.Role.USER, Message.Role.TOOL -> "user"
                        Message.Role.ASSISTANT -> "assistant"
                        Message.Role.SYSTEM -> "system"
                    },
                    "content" to msg.content
                )
            }

            val requestBody = gson.toJson(
                mapOf(
                    "model" to model,
                    "messages" to apiMessages,
                    "stream" to true,
                    "max_tokens" to options.maxTokens,
                    "temperature" to options.temperature
                )
            )

            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addAuthIfPresent()
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            emit(StreamEvent.MessageStart("", model))

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                response.close()
                throw ModelAdapterException("Local model error (${response.code}): $body")
            }

            val reader = response.body?.byteStream()?.bufferedReader()
                ?: throw ModelAdapterException("Empty response stream")

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (!currentLine.startsWith("data: ")) continue
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = gson.fromJson(data, JsonObject::class.java)
                        val delta = json.getAsJsonArray("choices")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("delta")
                        val text = delta?.get("content")?.asString
                        if (text != null) emit(StreamEvent.ContentDelta(text))
                    } catch (e: Exception) {
                        logger.debug("Skipping unparseable SSE event: {}", data)
                    }
                }
            } finally {
                reader.close()
                response.close()
            }

            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }.flowOn(Dispatchers.IO)
    }

    override fun supportedModels(): List<ModelInfo> {
        // Attempt to query the local server for available models
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return defaultModelList()
                val json = gson.fromJson(body, JsonObject::class.java)
                val models = json.getAsJsonArray("models")

                models?.map { modelJson ->
                    val obj = modelJson.asJsonObject
                    val name = obj.get("name")?.asString ?: "unknown"
                    ModelInfo(
                        id = name,
                        displayName = name,
                        provider = "local",
                        contextWindow = 8_192, // Conservative default
                        maxOutputTokens = 4_096,
                        supportsStreaming = true,
                        supportsVision = false,
                        costTier = CostTier.LOW
                    )
                } ?: defaultModelList()
            } else {
                defaultModelList()
            }
        } catch (e: Exception) {
            logger.debug("Could not query local model server: {}", e.message)
            defaultModelList()
        }
    }

    override suspend fun healthCheck(): Boolean {
        return try {
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val healthy = response.isSuccessful
                response.close()
                healthy
            }
        } catch (e: Exception) {
            logger.debug("Local model health check failed: {}", e.message)
            false
        }
    }

    private suspend fun completeViaOllama(
        prompt: String,
        options: CompletionOptions,
        model: String
    ): CompletionResult {
        val requestBody = gson.toJson(
            mapOf(
                "model" to model,
                "prompt" to prompt,
                "stream" to false,
                "options" to mapOf(
                    "temperature" to options.temperature,
                    "num_predict" to options.maxTokens,
                    "top_p" to options.topP
                )
            )
        )

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .addAuthIfPresent()
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            response.use { resp ->
                val body = resp.body?.string() ?: throw ModelAdapterException("Empty response")

                if (!resp.isSuccessful) {
                    throw ModelAdapterException("Ollama error (${resp.code}): $body")
                }

                val json = gson.fromJson(body, JsonObject::class.java)
                val content = json.get("response")?.asString ?: ""

                CompletionResult(
                    content = content,
                    model = model,
                    usage = TokenUsage(
                        inputTokens = json.get("prompt_eval_count")?.asInt ?: 0,
                        outputTokens = json.get("eval_count")?.asInt ?: 0
                    ),
                    stopReason = if (json.get("done")?.asBoolean == true) StopReason.END_TURN else StopReason.MAX_TOKENS,
                    latencyMs = latency
                )
            }
        }
    }

    private suspend fun completeViaOpenAICompat(
        prompt: String,
        options: CompletionOptions,
        model: String
    ): CompletionResult {
        val messages = mutableListOf<Map<String, String>>()
        if (options.systemPrompt != null) {
            messages.add(mapOf("role" to "system", "content" to options.systemPrompt!!))
        }
        messages.add(mapOf("role" to "user", "content" to prompt))

        val requestBody = gson.toJson(
            mapOf(
                "model" to model,
                "messages" to messages,
                "max_tokens" to options.maxTokens,
                "temperature" to options.temperature,
                "stream" to false
            )
        )

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addAuthIfPresent()
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            response.use { resp ->
                val body = resp.body?.string() ?: throw ModelAdapterException("Empty response")

                if (!resp.isSuccessful) {
                    throw ModelAdapterException("Local model error (${resp.code}): $body")
                }

                val json = gson.fromJson(body, JsonObject::class.java)
                val choice = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
                val content = choice?.getAsJsonObject("message")?.get("content")?.asString ?: ""

                val usage = json.getAsJsonObject("usage")

                CompletionResult(
                    content = content,
                    model = model,
                    usage = TokenUsage(
                        inputTokens = usage?.get("prompt_tokens")?.asInt ?: 0,
                        outputTokens = usage?.get("completion_tokens")?.asInt ?: 0
                    ),
                    stopReason = when (choice?.get("finish_reason")?.asString) {
                        "stop" -> StopReason.END_TURN
                        "length" -> StopReason.MAX_TOKENS
                        else -> StopReason.END_TURN
                    },
                    latencyMs = latency
                )
            }
        }
    }

    private fun Request.Builder.addAuthIfPresent(): Request.Builder = apply {
        if (!apiKey.isNullOrBlank()) addHeader("Authorization", "Bearer $apiKey")
    }

    private fun defaultModelList(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                id = defaultModel,
                displayName = "$defaultModel (local)",
                provider = "local",
                contextWindow = 8_192,
                maxOutputTokens = 4_096,
                supportsStreaming = true,
                supportsVision = false,
                costTier = CostTier.LOW
            )
        )
    }
}
