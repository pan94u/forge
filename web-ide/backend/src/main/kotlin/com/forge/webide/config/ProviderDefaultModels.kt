package com.forge.webide.config

import com.forge.adapter.model.CostTier
import com.forge.adapter.model.ModelInfo

/**
 * 各 Provider 的静态默认模型列表。
 *
 * 不依赖 API Key 或环境变量，用于 /api/models 端点和模型选择器。
 * 用户在 ModelSettingsDialog 中配置 API Key 后，可在 ModelSelector 中选择这些模型。
 */
object ProviderDefaultModels {

    val ANTHROPIC: List<ModelInfo> = listOf(
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
            maxOutputTokens = 8_192,
            supportsStreaming = true,
            supportsVision = true,
            costTier = CostTier.MEDIUM
        ),
        ModelInfo(
            id = "claude-haiku-4-5",
            displayName = "Claude Haiku 4.5",
            provider = "anthropic",
            contextWindow = 200_000,
            maxOutputTokens = 8_192,
            supportsStreaming = true,
            supportsVision = false,
            costTier = CostTier.LOW
        ),
    )

    val GOOGLE: List<ModelInfo> = listOf(
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
            maxOutputTokens = 65_536,
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
            supportsVision = false,
            costTier = CostTier.LOW
        ),
    )

    val DASHSCOPE: List<ModelInfo> = listOf(
        ModelInfo(
            id = "qwen3.5-plus",
            displayName = "Qwen 3.5 Plus",
            provider = "dashscope",
            contextWindow = 1_000_000,
            maxOutputTokens = 8_192,
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
            contextWindow = 10_000_000,
            maxOutputTokens = 6_000,
            supportsStreaming = true,
            supportsVision = false,
            costTier = CostTier.LOW
        ),
    )

    val OPENAI: List<ModelInfo> = listOf(
        ModelInfo(
            id = "gpt-4o",
            displayName = "GPT-4o",
            provider = "openai",
            contextWindow = 128_000,
            maxOutputTokens = 16_384,
            supportsStreaming = true,
            supportsVision = true,
            costTier = CostTier.HIGH
        ),
        ModelInfo(
            id = "gpt-4o-mini",
            displayName = "GPT-4o mini",
            provider = "openai",
            contextWindow = 128_000,
            maxOutputTokens = 16_384,
            supportsStreaming = true,
            supportsVision = true,
            costTier = CostTier.LOW
        ),
    )

    val ALL: List<ModelInfo> = ANTHROPIC + GOOGLE + DASHSCOPE + OPENAI

    val BY_PROVIDER: Map<String, List<ModelInfo>> = mapOf(
        "anthropic" to ANTHROPIC,
        "google" to GOOGLE,
        "dashscope" to DASHSCOPE,
        "openai" to OPENAI,
    )

    /** modelId → provider 的静态映射，用于从模型 ID 确定所属 Provider */
    val MODEL_TO_PROVIDER: Map<String, String> =
        ALL.associate { it.id to it.provider }
}
