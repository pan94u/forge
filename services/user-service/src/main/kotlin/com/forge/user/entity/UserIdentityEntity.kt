package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_identities")
class UserIdentityEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    val provider: IdentityProvider,

    @Column(name = "provider_user_id", nullable = false, length = 255)
    val providerUserId: String,

    @Column(name = "access_token", columnDefinition = "TEXT")
    var accessToken: String? = null,

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    var refreshToken: String? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: String = "{}",

    @Column(name = "linked_at", nullable = false, updatable = false)
    val linkedAt: Instant = Instant.now()
)

enum class IdentityProvider {
    GITHUB,
    GOOGLE,
    WECHAT,
    DINGTALK,
    EMAIL,
    PHONE
}