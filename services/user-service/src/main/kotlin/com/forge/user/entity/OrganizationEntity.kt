package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "organizations")
class OrganizationEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 128)
    val name: String,

    @Column(name = "slug", nullable = false, unique = true, length = 128)
    val slug: String,

    @Column(name = "avatar", length = 512)
    var avatar: String? = null,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "owner_id", nullable = false)
    val ownerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    var plan: OrgPlan = OrgPlan.FREE,

    @Column(name = "settings")
    var settings: String = "{}",

    @Column(name = "metadata")
    var metadata: String = "{}",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}

enum class OrgPlan {
    FREE,
    PRO,
    ENTERPRISE
}