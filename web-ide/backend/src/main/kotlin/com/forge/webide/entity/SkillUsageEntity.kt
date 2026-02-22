package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Tracks skill usage events (reads, script executions) for analytics.
 */
@Entity
@Table(name = "skill_usage")
class SkillUsageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "session_id", nullable = false)
    val sessionId: String,

    @Column(name = "skill_name", nullable = false)
    val skillName: String,

    @Column(name = "action", nullable = false)
    val action: String,

    @Column(name = "script_type")
    val scriptType: String? = null,

    @Column(name = "profile", nullable = false)
    val profile: String = "",

    @Column(name = "success", nullable = false)
    val success: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
