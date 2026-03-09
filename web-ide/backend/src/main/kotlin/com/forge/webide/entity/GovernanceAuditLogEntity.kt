package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "governance_audit_logs")
class GovernanceAuditLogEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "org_id", nullable = false)
    val orgId: String,

    @Column(nullable = false, length = 50)
    val domain: String,

    @Column(nullable = false, length = 100)
    val action: String,

    @Column(length = 255)
    val actor: String? = null,

    @Column(columnDefinition = "TEXT")
    val detail: String? = null,

    @Column(name = "ai_recommendation", columnDefinition = "TEXT")
    val aiRecommendation: String? = null,

    @Column(name = "human_decision", length = 50)
    val humanDecision: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
