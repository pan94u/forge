package com.forge.webide.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

data class OrgMemberId(
    val orgId: String = "",
    val userId: String = ""
) : Serializable

@Entity
@Table(name = "org_members")
@IdClass(OrgMemberId::class)
class OrgMemberEntity(
    @Id
    @Column(name = "org_id")
    val orgId: String,

    @Id
    @Column(name = "user_id")
    val userId: String,

    @Column(nullable = false, length = 20)
    var role: String = "MEMBER",

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now()
)
