package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Stores user preferences for skill enable/disable state.
 * Platform skills are enabled by default; this table overrides defaults.
 */
@Entity
@Table(
    name = "skill_preferences",
    uniqueConstraints = [UniqueConstraint(columnNames = ["workspace_id", "skill_name"])]
)
class SkillPreferenceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: String,

    @Column(name = "skill_name", nullable = false)
    val skillName: String,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
