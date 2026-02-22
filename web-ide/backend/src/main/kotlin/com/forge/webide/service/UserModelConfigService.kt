package com.forge.webide.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.adapter.model.ModelInfo
import com.forge.webide.config.ProviderDefaultModels
import com.forge.webide.entity.UserModelConfigEntity
import com.forge.webide.repository.UserModelConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 用户模型配置服务。
 *
 * 管理用户级别的模型提供商配置（API Key、Base URL 等）。
 * API Key 通过 [EncryptionService] 加密存储。
 *
 * 系统完全依赖用户配置，不读取系统级环境变量。
 */
@Service
class UserModelConfigService(
    private val repository: UserModelConfigRepository,
    private val encryptionService: EncryptionService
) {

    private val logger = LoggerFactory.getLogger(UserModelConfigService::class.java)
    private val mapper = jacksonObjectMapper()

    /**
     * 获取用户的所有提供商配置（API Key 脱敏返回）。
     */
    fun getUserConfigs(userId: String): List<UserModelConfigView> {
        return repository.findByUserId(userId).map { it.toView() }
    }

    /**
     * 获取用户指定提供商的配置（API Key 脱敏）。
     */
    fun getUserConfig(userId: String, provider: String): UserModelConfigView? {
        return repository.findByUserIdAndProvider(userId, provider)?.toView()
    }

    /**
     * 保存或更新用户的提供商配置。
     */
    @Transactional
    fun saveUserConfig(userId: String, request: UserModelConfigRequest): UserModelConfigView {
        val existing = repository.findByUserIdAndProvider(userId, request.provider)

        val customModelsJson = mapper.writeValueAsString(request.customModels)

        val entity = if (existing != null) {
            existing.apply {
                apiKeyEncrypted = if (request.apiKey.isNotBlank()) {
                    encryptionService.encrypt(request.apiKey)
                } else {
                    apiKeyEncrypted // 不更新空 key
                }
                baseUrl = request.baseUrl
                region = request.region
                enabled = request.enabled
                customModels = customModelsJson
                updatedAt = Instant.now()
            }
        } else {
            UserModelConfigEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                provider = request.provider,
                apiKeyEncrypted = if (request.apiKey.isNotBlank()) encryptionService.encrypt(request.apiKey) else "",
                baseUrl = request.baseUrl,
                region = request.region,
                enabled = request.enabled,
                customModels = customModelsJson
            )
        }

        val saved = repository.save(entity)
        logger.info("用户 {} 保存模型配置: provider={}", userId, request.provider)
        return saved.toView()
    }

    /**
     * 删除用户指定提供商的配置。
     */
    @Transactional
    fun deleteUserConfig(userId: String, provider: String) {
        repository.deleteByUserIdAndProvider(userId, provider)
        logger.info("用户 {} 删除模型配置: provider={}", userId, provider)
    }

    /**
     * 获取用户指定提供商的解密配置（内部使用，不暴露给前端）。
     * 返回 null 表示用户未配置该 Provider 或配置已禁用。
     */
    fun getDecryptedConfig(userId: String, provider: String): DecryptedUserConfig? {
        val entity = repository.findByUserIdAndProvider(userId, provider) ?: return null
        if (!entity.enabled) return null
        val apiKey = if (entity.apiKeyEncrypted.isNotBlank()) {
            try { encryptionService.decrypt(entity.apiKeyEncrypted) } catch (e: Exception) {
                logger.warn("解密 {} 的 {} API Key 失败: {}", userId, provider, e.message)
                null
            }
        } else null
        return DecryptedUserConfig(
            provider = entity.provider,
            apiKey = apiKey,
            baseUrl = entity.baseUrl,
            region = entity.region,
            enabled = entity.enabled,
            customModels = parseCustomModels(entity.customModels)
        )
    }

    /**
     * 获取用户已配置且启用的所有 Provider 的可用模型列表。
     * 包含各 Provider 默认模型 + 用户自定义的 model ID。
     */
    fun getModelsForUser(userId: String): List<ModelInfo> {
        val configs = repository.findByUserId(userId)
            .filter { it.enabled && it.apiKeyEncrypted.isNotBlank() }

        return configs.flatMap { entity ->
            val provider = entity.provider
            val defaults = ProviderDefaultModels.BY_PROVIDER[provider] ?: emptyList()
            val customIds = parseCustomModels(entity.customModels)
            val customModels = customIds.map { modelId ->
                ModelInfo(
                    id = modelId,
                    displayName = modelId,
                    provider = provider,
                    contextWindow = 128_000,
                    maxOutputTokens = 8_192
                )
            }
            defaults + customModels
        }
    }

    // --- Private helpers ---

    private fun UserModelConfigEntity.toView() = UserModelConfigView(
        provider = provider,
        hasApiKey = apiKeyEncrypted.isNotBlank(),
        apiKeyMasked = maskApiKey(apiKeyEncrypted),
        baseUrl = baseUrl,
        region = region,
        enabled = enabled,
        customModels = parseCustomModels(customModels),
        updatedAt = updatedAt.toString()
    )

    private fun maskApiKey(encrypted: String): String {
        if (encrypted.isBlank()) return ""
        return try {
            val decrypted = encryptionService.decrypt(encrypted)
            if (decrypted.length <= 8) "****"
            else "${decrypted.take(4)}****${decrypted.takeLast(4)}"
        } catch (e: Exception) {
            "****"
        }
    }

    private fun parseCustomModels(json: String): List<String> = try {
        if (json.isBlank() || json == "null") emptyList()
        else mapper.readValue<List<String>>(json)
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 用户模型配置请求
 */
data class UserModelConfigRequest(
    val provider: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val region: String = "",
    val enabled: Boolean = true,
    val customModels: List<String> = emptyList()
)

/**
 * 用户模型配置视图（API Key 脱敏）
 */
data class UserModelConfigView(
    val provider: String,
    val hasApiKey: Boolean,
    val apiKeyMasked: String,
    val baseUrl: String,
    val region: String,
    val enabled: Boolean,
    val customModels: List<String> = emptyList(),
    val updatedAt: String
)

/**
 * 解密后的用户配置（仅内部使用）
 */
data class DecryptedUserConfig(
    val provider: String,
    val apiKey: String?,
    val baseUrl: String,
    val region: String,
    val enabled: Boolean,
    val customModels: List<String> = emptyList()
)
