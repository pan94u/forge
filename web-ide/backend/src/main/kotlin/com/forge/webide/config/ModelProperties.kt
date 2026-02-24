package com.forge.webide.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 模型提供商的外部化配置。
 *
 * 通过 application.yml 中的 `forge.models` 前缀配置各提供商的连接参数和可用模型列表。
 * 管理员可在 YAML 中启用/禁用提供商、配置 API Key、调整模型清单，
 * 无需修改代码即可控制平台支持的模型范围。
 *
 * 配置示例：
 * ```yaml
 * forge:
 *   models:
 *     default-provider: anthropic
 *     anthropic:
 *       enabled: true
 *       api-key: ${ANTHROPIC_API_KEY:}
 *       models:
 *         - id: claude-sonnet-4-6
 *           display-name: Claude Sonnet 4.6
 *           context-window: 200000
 *           max-output-tokens: 64000
 *           cost-tier: MEDIUM
 * ```
 */
@ConfigurationProperties(prefix = "forge.models")
data class ModelProperties(
    /** 默认提供商 */
    val defaultProvider: String = "anthropic",

    /** Anthropic Claude 直接 API */
    val anthropic: ProviderConfig = ProviderConfig(),

    /** AWS Bedrock */
    val bedrock: BedrockProviderConfig = BedrockProviderConfig(),

    /** Google Gemini */
    val gemini: ProviderConfig = ProviderConfig(),

    /** 阿里云通义千问 (DashScope) */
    val dashscope: ProviderConfig = ProviderConfig(),

    /** MiniMax（兼容 Anthropic 协议） */
    val minimax: ProviderConfig = ProviderConfig(),

    /** OpenAI 兼容 / 本地模型 */
    val openai: OpenAIProviderConfig = OpenAIProviderConfig()
)

/**
 * 通用提供商配置（适用于 Anthropic / Gemini / DashScope）
 */
data class ProviderConfig(
    /** 是否启用该提供商 */
    val enabled: Boolean = true,

    /** API Key（支持 ${ENV_VAR} 引用） */
    val apiKey: String = "",

    /** API 基础 URL（空字符串表示使用默认值） */
    val baseUrl: String = "",

    /** 该提供商下可用的模型列表（为空时使用 Adapter 内置默认列表） */
    val models: List<ModelConfig> = emptyList()
)

/**
 * AWS Bedrock 提供商配置
 */
data class BedrockProviderConfig(
    val enabled: Boolean = true,

    /** AWS 区域 */
    val region: String = "",

    /** AWS Profile 名称 */
    val profile: String = "",

    /** 该提供商下可用的模型列表 */
    val models: List<ModelConfig> = emptyList()
)

/**
 * OpenAI 兼容 / 本地模型提供商配置
 */
data class OpenAIProviderConfig(
    val enabled: Boolean = true,

    /** API Key */
    val apiKey: String = "",

    /** API 基础 URL */
    val baseUrl: String = "",

    /** 默认模型名称 */
    val defaultModel: String = "",

    /** 该提供商下可用的模型列表 */
    val models: List<ModelConfig> = emptyList()
)

/**
 * 单个模型的配置
 */
data class ModelConfig(
    /** 模型 ID（如 claude-sonnet-4-6） */
    val id: String = "",

    /** 显示名称 */
    val displayName: String = "",

    /** 上下文窗口大小 */
    val contextWindow: Int = 128_000,

    /** 最大输出 token 数 */
    val maxOutputTokens: Int = 4_096,

    /** 是否支持流式输出 */
    val supportsStreaming: Boolean = true,

    /** 是否支持视觉/图像 */
    val supportsVision: Boolean = false,

    /** 费用等级: LOW, MEDIUM, HIGH */
    val costTier: String = "MEDIUM"
)
