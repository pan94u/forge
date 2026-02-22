package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "login_logs")
class LoginLogEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    val provider: IdentityProvider,

    @Column(name = "ip_address", nullable = false, length = 45)
    val ipAddress: String,

    @Column(name = "user_agent", columnDefinition = "TEXT")
    val userAgent: String? = null,

    @Column(name = "success", nullable = false)
    val success: Boolean,

    @Column(name = "failure_reason", length = 255)
    val failureReason: String? = null,

    @Column(name = "request_id")
    val requestId: UUID = UUID.randomUUID(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)