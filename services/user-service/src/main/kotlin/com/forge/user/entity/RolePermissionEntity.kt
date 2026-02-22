package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "role_permissions")
class RolePermissionEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "role_name", nullable = false, length = 64)
    val roleName: String,

    @Column(name = "permission_id", nullable = false)
    val permissionId: UUID,

    @Column(name = "granted_by", nullable = false)
    val grantedBy: UUID,

    @Column(name = "granted_at", nullable = false, updatable = false)
    val grantedAt: Instant = Instant.now()
)