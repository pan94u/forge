package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "org_members")
class OrgMemberEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "org_id", nullable = false)
    val orgId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: OrgRole,

    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "invited_by")
    val invitedBy: UUID? = null
)

enum class OrgRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER
}