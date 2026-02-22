package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.config.ProviderDefaultModels
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 动态 Adapter 工厂服务。
 *
 * 根据用户在 ModelSettingsDialog 中保存的 Provider 配置，
 * 按需（per-request）创建对应的 ModelAdapter 实例。
 *
 * 完全依赖用户配置，不读取任何系统级环境变量。
 */
@Service
class DynamicAdapterFactory(
    private val userModelConfigService: UserModelConfigService
) {
    private val logger = LoggerFactory.getLogger(DynamicAdapterFactory::class.java)

    /**
     * 根据 modelId 确定所属 provider。
     * 优先查静态模型列表，找不到则用 model ID 前缀推断。
     */
    fun providerForModel(modelId: String): String {
        return ProviderDefaultModels.MODEL_TO_PROVIDER[modelId]
            ?: inferProviderFromId(modelId)
    }

    /**
     * 为指定用户和 provider 创建 ModelAdapter。
     * 从数据库读取用户配置的 API Key，动态实例化对应 Adapter。
     *
     * @throws ProviderNotConfiguredException 当用户未配置该 Provider 时
     */
    fun createForUser(userId: String, provider: String): ModelAdapter {
        val config = userModelConfigService.getDecryptedConfig(userId, provider)
            ?: throw ProviderNotConfiguredException(
                "Provider '$provider' 未配置。请在 Model Settings 中添加 API Key。"
            )
        if (!config.enabled) {
            throw ProviderNotConfiguredException(
                "Provider '$provider' 已禁用。请在 Model Settings 中启用它。"
            )
        }

        logger.debug("为用户 {} 创建 {} adapter", userId, provider)

        return when (provider) {
            "anthropic" -> {
                val apiKey = config.apiKey
                    ?: throw ProviderNotConfiguredException("Anthropic API Key 未设置")
                ClaudeAdapter(
                    apiKey = apiKey,
                    baseUrl = config.baseUrl.ifBlank { "https://api.anthropic.com" }
                )
            }
            "google" -> {
                val apiKey = config.apiKey
                    ?: throw ProviderNotConfiguredException("Google API Key 未设置")
                GeminiAdapter(
                    apiKey = apiKey,
                    baseUrl = config.baseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
                )
            }
            "dashscope" -> {
                val apiKey = config.apiKey
                    ?: throw ProviderNotConfiguredException("DashScope API Key 未设置")
                QwenAdapter(
                    apiKey = apiKey,
                    baseUrl = config.baseUrl.ifBlank { "https://dashscope.aliyuncs.com/compatible-mode/v1" }
                )
            }
            "openai" -> {
                val baseUrl = config.baseUrl.ifBlank {
                    throw ProviderNotConfiguredException("OpenAI Compatible 需要配置 Base URL")
                }
                LocalModelAdapter(
                    baseUrl = baseUrl,
                    apiKey = config.apiKey
                )
            }
            "aws-bedrock" -> {
                val region = config.region.ifBlank { "us-east-1" }
                BedrockAdapter(region = region)
            }
            else -> throw IllegalArgumentException("未知的 Provider: $provider")
        }
    }

    private fun inferProviderFromId(modelId: String): String = when {
        modelId.startsWith("claude") -> "anthropic"
        modelId.startsWith("gemini") -> "google"
        modelId.startsWith("qwen") -> "dashscope"
        modelId.startsWith("gpt") || modelId.startsWith("o1") || modelId.startsWith("o3") -> "openai"
        modelId.startsWith("anthropic.") -> "aws-bedrock"
        else -> "openai"  // 未知的默认走 openai-compatible
    }
}

class ProviderNotConfiguredException(message: String) : RuntimeException(message)
