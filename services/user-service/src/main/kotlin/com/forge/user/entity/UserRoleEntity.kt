package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_roles")
class UserRoleEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "role_name", nullable = false, length = 64)
    val roleName: String,

    @Column(name = "org_id")
    val orgId: UUID? = null,

    @Column(name = "granted_by", nullable = false)
    val grantedBy: UUID,

    @Column(name = "granted_at", nullable = false, updatable = false)
    val grantedAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    val expiresAt: Instant? = null
)