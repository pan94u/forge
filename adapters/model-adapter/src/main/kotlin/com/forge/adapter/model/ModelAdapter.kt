package com.forge.adapter.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Unified interface for interacting with language model providers.
 *
 * The ModelAdapter abstracts away provider-specific API details, allowing
 * the Forge platform to swap between Claude API, AWS Bedrock, local models,
 * or any future provider without changing skill/baseline code.
 *
 * Usage:
 * ```kotlin
 * val adapter: ModelAdapter = ClaudeAdapter(apiKey)
 * val result = adapter.complete("Explain microservices", CompletionOptions(maxTokens = 1024))
 * println(result.content)
 * ```
 */
interface ModelAdapter {

    /**
     * Send a completion request and receive the full response.
     *
     * @param prompt The user prompt text
     * @param options Completion parameters (model, temperature, max tokens, etc.)
     * @return The complete response with content, usage stats, and metadata
     * @throws ModelAdapterException if the request fails
     */
    suspend fun complete(prompt: String, options: CompletionOptions = CompletionOptions()): CompletionResult

    /**
     * Send a completion request and receive the response as a stream of text chunks.
     *
     * Useful for real-time display or processing of long outputs.
     *
     * @param prompt The user prompt text
     * @param options Completion parameters
     * @return A Flow emitting text chunks as they arrive
     * @throws ModelAdapterException if the connection fails
     */
    suspend fun streamComplete(prompt: String, options: CompletionOptions = CompletionOptions()): Flow<String>

    /**
     * Return the list of models supported by this adapter.
     */
    fun supportedModels(): List<ModelInfo>

    /**
     * Send a completion request with tool definitions and receive structured streaming events.
     *
     * Supports tool_use content blocks, partial JSON input streaming, and stop_reason detection.
     * Default implementation falls back to streamComplete() (text-only, no tool support).
     *
     * @param messages Conversation messages including tool results
     * @param options Completion parameters
     * @param tools Tool definitions available to the model
     * @return A Flow emitting StreamEvent instances as they arrive
     */
    suspend fun streamWithTools(
        messages: List<Message>,
        options: CompletionOptions = CompletionOptions(),
        tools: List<ToolDefinition> = emptyList()
    ): Flow<StreamEvent> {
        // Default: fall back to text-only streaming
        val prompt = messages.lastOrNull { it.role == Message.Role.USER }?.content ?: ""
        return flow {
            emit(StreamEvent.MessageStart("", options.model ?: "unknown"))
            streamComplete(prompt, options).collect { chunk ->
                emit(StreamEvent.ContentDelta(chunk))
            }
            emit(StreamEvent.MessageDelta(StopReason.END_TURN))
            emit(StreamEvent.MessageStop)
        }
    }

    /**
     * Check whether this adapter is properly configured and can reach its backend.
     *
     * @return true if the adapter can make requests
     */
    suspend fun healthCheck(): Boolean
}

/**
 * Base exception for model adapter errors.
 */
open class ModelAdapterException(
    message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null,
    val retryable: Boolean = false
) : RuntimeException(message, cause)

/**
 * Thrown when authentication fails (invalid API key, expired token, etc.).
 */
class AuthenticationException(message: String, cause: Throwable? = null) :
    ModelAdapterException(message, cause, statusCode = 401, retryable = false)

/**
 * Thrown when the provider rate-limits the request.
 */
class RateLimitException(
    message: String,
    val retryAfterMs: Long? = null,
    cause: Throwable? = null
) : ModelAdapterException(message, cause, statusCode = 429, retryable = true)

/**
 * Thrown when the requested model is not available.
 */
class ModelNotAvailableException(message: String, cause: Throwable? = null) :
    ModelAdapterException(message, cause, statusCode = 404, retryable = false)
