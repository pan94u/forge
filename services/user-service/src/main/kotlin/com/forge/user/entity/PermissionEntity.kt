package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "permissions")
class PermissionEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "resource", nullable = false, length = 64)
    val resource: String,

    @Column(name = "action", nullable = false, length = 64)
    val action: String,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "conditions", columnDefinition = "jsonb")
    val conditions: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    init {
        require(resource.isNotBlank()) { "Resource cannot be blank" }
        require(action.isNotBlank()) { "Action cannot be blank" }
    }
}