package com.forge.user.service

import com.forge.user.entity.IdentityProvider
import com.forge.user.entity.UserIdentityEntity
import com.forge.user.entity.UserEntity
import com.forge.user.exception.UserException
import com.forge.user.repository.UserIdentityRepository
import com.forge.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 第三方身份服务
 * 支持 GitHub、Google、微信、钉钉等 OAuth 登录
 */
@Service
class IdentityService(
    private val userRepository: UserRepository,
    private val userIdentityRepository: UserIdentityRepository
) {
    /**
     * 通过第三方身份查找用户
     * 如果用户不存在，返回 null
     */
    fun findUserByIdentity(provider: IdentityProvider, providerUserId: String): UserEntity? {
        return userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId)
            ?.let { identity ->
                userRepository.findById(identity.userId).orElse(null)
            }
    }

    /**
     * 绑定第三方账号到现有用户
     */
    @Transactional
    fun bindIdentity(userId: UUID, provider: IdentityProvider, providerUserId: String): UserIdentityEntity {
        // 检查是否已绑定
        val existing = userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId)
        if (existing != null && existing.userId != userId) {
            throw UserException("该账号已被其他用户绑定")
        }

        // 检查用户是否已绑定该提供商
        val userIdentities = userIdentityRepository.findByUserId(userId)
        if (userIdentities.any { it.provider == provider }) {
            throw UserException("该账号已绑定${provider.name}，请先解绑")
        }

        val identity = UserIdentityEntity(
            userId = userId,
            provider = provider,
            providerUserId = providerUserId,
            linkedAt = Instant.now()
        )

        return userIdentityRepository.save(identity)
    }

    /**
     * 解绑第三方账号
     */
    @Transactional
    fun unbindIdentity(userId: UUID, provider: IdentityProvider) {
        val identities = userIdentityRepository.findByUserId(userId)
        val identity = identities.find { it.provider == provider }
            ?: throw UserException("未绑定该账号")

        userIdentityRepository.delete(identity)
    }

    /**
     * 获取用户绑定的所有第三方账号
     */
    fun getUserIdentities(userId: UUID): List<UserIdentityEntity> {
        return userIdentityRepository.findByUserId(userId)
    }

    /**
     * 检查用户是否绑定了指定提供商
     */
    fun isBound(userId: UUID, provider: IdentityProvider): Boolean {
        return userIdentityRepository.findByUserId(userId)
            .any { it.provider == provider }
    }
}