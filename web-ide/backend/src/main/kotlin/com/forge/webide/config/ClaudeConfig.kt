package com.forge.webide.config

import com.forge.adapter.model.*
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Spring configuration for model adapters and the ModelRegistry.
 *
 * 从 [ModelProperties] 读取 application.yml 配置，自动创建各提供商的 Adapter。
 * 提供商启用条件：enabled=true 且 API Key 非空（Bedrock 检查 region）。
 * 模型列表优先使用 YAML 配置，为空时回退到 Adapter 内置默认清单。
 */
@Configuration
@EnableConfigurationProperties(ModelProperties::class)
class ClaudeConfig(
    private val modelProperties: ModelProperties
) {

    private val logger = LoggerFactory.getLogger(ClaudeConfig::class.java)

    @Bean
    fun modelRegistry(): ModelRegistry {
        val adapters = mutableMapOf<String, ModelAdapter>()

        // Anthropic Claude (直接 API)
        val anthropicConfig = modelProperties.anthropic
        val claudeKey = anthropicConfig.apiKey.ifBlank { System.getenv("ANTHROPIC_API_KEY") ?: "" }
        if (anthropicConfig.enabled && claudeKey.isNotBlank()) {
            val baseUrl = anthropicConfig.baseUrl.ifBlank { "https://api.anthropic.com" }
            val models = toModelInfoList(anthropicConfig.models, "anthropic")
            adapters["anthropic"] = ClaudeAdapter(
                apiKey = claudeKey,
                baseUrl = baseUrl,
                customModels = models
            )
            logger.info("注册 adapter: anthropic (Claude API), {} 个模型", models?.size ?: "默认")
        }

        // AWS Bedrock
        val bedrockConfig = modelProperties.bedrock
        val awsRegion = bedrockConfig.region.ifBlank { System.getenv("AWS_REGION") ?: "" }
        val awsCredentials = System.getenv("AWS_ACCESS_KEY_ID")?.isNotBlank() == true ||
                (bedrockConfig.profile.ifBlank { System.getenv("AWS_PROFILE") ?: "" }).isNotBlank()
        if (bedrockConfig.enabled && awsRegion.isNotBlank() && awsCredentials) {
            val profile = bedrockConfig.profile.ifBlank { System.getenv("AWS_PROFILE") }
            val models = toModelInfoList(bedrockConfig.models, "aws-bedrock")
            adapters["aws-bedrock"] = BedrockAdapter(
                region = awsRegion,
                profileName = profile,
                customModels = models
            )
            logger.info("注册 adapter: aws-bedrock (region={}), {} 个模型", awsRegion, models?.size ?: "默认")
        }

        // Google Gemini
        val geminiConfig = modelProperties.gemini
        val geminiKey = geminiConfig.apiKey.ifBlank { System.getenv("GEMINI_API_KEY") ?: "" }
        if (geminiConfig.enabled && geminiKey.isNotBlank()) {
            val baseUrl = geminiConfig.baseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
            val models = toModelInfoList(geminiConfig.models, "google")
            adapters["google"] = GeminiAdapter(
                apiKey = geminiKey,
                baseUrl = baseUrl,
                customModels = models
            )
            logger.info("注册 adapter: google (Gemini), {} 个模型", models?.size ?: "默认")
        }

        // Alibaba Qwen (DashScope)
        val dashscopeConfig = modelProperties.dashscope
        val qwenKey = dashscopeConfig.apiKey.ifBlank { System.getenv("DASHSCOPE_API_KEY") ?: "" }
        if (dashscopeConfig.enabled && qwenKey.isNotBlank()) {
            val baseUrl = dashscopeConfig.baseUrl.ifBlank {
                "https://dashscope.aliyuncs.com/compatible-mode/v1"
            }
            val models = toModelInfoList(dashscopeConfig.models, "dashscope")
            adapters["dashscope"] = QwenAdapter(
                apiKey = qwenKey,
                baseUrl = baseUrl,
                customModels = models
            )
            logger.info("注册 adapter: dashscope (Qwen), {} 个模型", models?.size ?: "默认")
        }

        // MiniMax（兼容 Anthropic 协议，复用 ClaudeAdapter）
        // 即使没有系统级 API Key 也注册 adapter，用户可通过 Settings 配置自己的 key
        val minimaxConfig = modelProperties.minimax
        val minimaxKey = minimaxConfig.apiKey.ifBlank { System.getenv("MINIMAX_API_KEY") ?: "" }
        if (minimaxConfig.enabled) {
            val baseUrl = minimaxConfig.baseUrl.ifBlank { "https://api.minimaxi.com/anthropic" }
            val models = toModelInfoList(minimaxConfig.models, "minimax")
            adapters["minimax"] = ClaudeAdapter(
                apiKey = minimaxKey.ifBlank { "placeholder" },
                baseUrl = baseUrl,
                customModels = models
            )
            logger.info("注册 adapter: minimax (MiniMax, Anthropic 兼容), {} 个模型, hasSystemKey={}",
                models?.size ?: "默认", minimaxKey.isNotBlank())
        }

        // OpenAI 兼容 / 本地模型
        val openaiConfig = modelProperties.openai
        val localUrl = openaiConfig.baseUrl.ifBlank { System.getenv("LOCAL_MODEL_URL") ?: "" }
        if (openaiConfig.enabled && localUrl.isNotBlank()) {
            val defaultModel = openaiConfig.defaultModel.ifBlank {
                System.getenv("LOCAL_MODEL_NAME") ?: ""
            }
            val localApiKey = openaiConfig.apiKey.ifBlank { System.getenv("LOCAL_MODEL_API_KEY") }
            adapters["openai"] = LocalModelAdapter(
                baseUrl = localUrl,
                defaultModel = defaultModel,
                apiKey = localApiKey
            )
            logger.info("注册 adapter: openai (本地/OpenAI 兼容)")
        }

        // Fallback: 无 adapter 时创建默认 Claude（运行时可能失败）
        if (adapters.isEmpty()) {
            adapters["anthropic"] = ClaudeAdapter(apiKey = claudeKey, baseUrl = "https://api.anthropic.com")
            logger.warn("未检测到任何 API Key，注册默认 anthropic adapter（运行时可能失败）")
        }

        val registry = ModelRegistry(
            adapters = adapters,
            defaultProvider = modelProperties.defaultProvider
        )
        val summary = registry.summary()
        logger.info(
            "ModelRegistry 初始化: {} 个提供商, {} 个模型, 默认={}",
            summary.providers.size, summary.totalModels, summary.defaultProvider
        )
        return registry
    }

    /**
     * 默认 ModelAdapter bean，用于向后兼容。
     * 注入 ModelAdapter 的 service 会得到 registry 中的默认 adapter。
     */
    @Bean
    fun claudeAdapter(modelRegistry: ModelRegistry): ModelAdapter {
        return modelRegistry.defaultAdapter()
    }

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()

    /**
     * 将 YAML ModelConfig 列表转换为 ModelInfo 列表。
     * 如果配置列表为空，返回 null（让 Adapter 使用内置默认清单）。
     */
    private fun toModelInfoList(configs: List<ModelConfig>, provider: String): List<ModelInfo>? {
        if (configs.isEmpty()) return null
        return configs.mapNotNull { config ->
            if (config.id.isBlank()) {
                logger.warn("跳过无效模型配置: id 为空")
                return@mapNotNull null
            }
            ModelInfo(
                id = config.id,
                displayName = config.displayName.ifBlank { config.id },
                provider = provider,
                contextWindow = config.contextWindow,
                maxOutputTokens = config.maxOutputTokens,
                supportsStreaming = config.supportsStreaming,
                supportsVision = config.supportsVision,
                costTier = try {
                    CostTier.valueOf(config.costTier.uppercase())
                } catch (e: IllegalArgumentException) {
                    CostTier.MEDIUM
                }
            )
        }
    }
}
