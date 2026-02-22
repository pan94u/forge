package com.forge.user.repository

import com.forge.user.entity.IdentityProvider
import com.forge.user.entity.UserIdentityEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserIdentityRepository : JpaRepository<UserIdentityEntity, UUID> {

    fun findByUserId(userId: UUID): List<UserIdentityEntity>

    fun findByProviderAndProviderUserId(provider: IdentityProvider, providerUserId: String): UserIdentityEntity?

    fun deleteByUserIdAndProvider(userId: UUID, provider: IdentityProvider)

    fun existsByUserIdAndProvider(userId: UUID, provider: IdentityProvider): Boolean
}