package com.forge.adapter.model

/**
 * Options for a completion request to any model provider.
 */
data class CompletionOptions(
    /** The model identifier (e.g., "claude-opus-4-20250514", "claude-sonnet-4-20250514") */
    val model: String? = null,

    /** Maximum tokens to generate in the response */
    val maxTokens: Int = 4096,

    /** Temperature for sampling (0.0 = deterministic, 1.0 = creative) */
    val temperature: Double = 0.7,

    /** Top-p nucleus sampling threshold */
    val topP: Double = 1.0,

    /** Stop sequences that terminate generation */
    val stopSequences: List<String> = emptyList(),

    /** System prompt / instructions */
    val systemPrompt: String? = null,

    /** Previous conversation messages for multi-turn context */
    val messages: List<Message> = emptyList(),

    /** Request timeout in milliseconds */
    val timeoutMs: Long = 120_000,

    /** Additional provider-specific parameters */
    val extras: Map<String, Any> = emptyMap()
)

/**
 * A message in a conversation history.
 */
data class Message(
    val role: Role,
    val content: String
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/**
 * Result of a completion request.
 */
data class CompletionResult(
    /** The generated text content */
    val content: String,

    /** The model that actually generated the response */
    val model: String,

    /** Token usage information */
    val usage: TokenUsage,

    /** The reason generation stopped */
    val stopReason: StopReason,

    /** Latency in milliseconds */
    val latencyMs: Long,

    /** Provider-specific metadata */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Token usage breakdown.
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

/**
 * Reason generation stopped.
 */
enum class StopReason {
    /** Model reached a natural end point */
    END_TURN,
    /** Hit a stop sequence */
    STOP_SEQUENCE,
    /** Hit max tokens limit */
    MAX_TOKENS,
    /** Error occurred during generation */
    ERROR
}

/**
 * Information about a supported model.
 */
data class ModelInfo(
    /** Model identifier */
    val id: String,

    /** Human-readable display name */
    val displayName: String,

    /** Provider name (e.g., "anthropic", "aws-bedrock", "local") */
    val provider: String,

    /** Maximum context window size in tokens */
    val contextWindow: Int,

    /** Maximum output tokens */
    val maxOutputTokens: Int,

    /** Whether the model supports streaming */
    val supportsStreaming: Boolean = true,

    /** Whether the model supports vision/images */
    val supportsVision: Boolean = false,

    /** Relative cost tier: LOW, MEDIUM, HIGH */
    val costTier: CostTier = CostTier.MEDIUM
)

enum class CostTier {
    LOW, MEDIUM, HIGH
}
