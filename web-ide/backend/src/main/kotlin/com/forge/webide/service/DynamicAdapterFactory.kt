package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.config.ProviderDefaultModels
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 动态 Adapter 工厂服务。
 *
 * 根据用户在 ModelSettingsDialog 中保存的 Provider 配置，
 * 按需创建对应的 ModelAdapter 实例并缓存，避免每次请求重复实例化。
 *
 * 缓存策略：key = "userId:provider"，API Key 更新时调用 [invalidate] 清除缓存。
 */
@Service
class DynamicAdapterFactory(
    private val userModelConfigService: UserModelConfigService
) {
    private val logger = LoggerFactory.getLogger(DynamicAdapterFactory::class.java)

    // key = "userId:provider", value = cached adapter
    private val adapterCache = ConcurrentHashMap<String, ModelAdapter>()

    /**
     * 根据 modelId 确定所属 provider。
     * 优先查静态模型列表，找不到则用 model ID 前缀推断。
     */
    fun providerForModel(modelId: String): String {
        return ProviderDefaultModels.MODEL_TO_PROVIDER[modelId]
            ?: inferProviderFromId(modelId)
    }

    /**
     * 为指定用户和 provider 获取（或创建）ModelAdapter。
     * 相同 userId+provider 的 Adapter 会被缓存复用。
     *
     * @throws ProviderNotConfiguredException 当用户未配置该 Provider 时
     */
    fun createForUser(userId: String, provider: String): ModelAdapter {
        val cacheKey = "$userId:$provider"
        return adapterCache.getOrPut(cacheKey) {
            buildAdapter(userId, provider)
        }
    }

    /**
     * 清除指定用户某 provider 的缓存（用于 API Key 更新后刷新）。
     */
    fun invalidate(userId: String, provider: String) {
        val removed = adapterCache.remove("$userId:$provider")
        if (removed != null) {
            logger.debug("已清除用户 {} 的 {} adapter 缓存", userId, provider)
        }
    }

    /**
     * 清除指定用户所有 provider 的缓存。
     */
    fun invalidateAll(userId: String) {
        val prefix = "$userId:"
        adapterCache.keys.filter { it.startsWith(prefix) }.forEach { key ->
            adapterCache.remove(key)
        }
        logger.debug("已清除用户 {} 的所有 adapter 缓存", userId)
    }

    private fun buildAdapter(userId: String, provider: String): ModelAdapter {
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
