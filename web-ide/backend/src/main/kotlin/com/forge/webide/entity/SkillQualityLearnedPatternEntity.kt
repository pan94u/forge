package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Stores self-learned quality patterns mined from historical executions.
 * Layer 3 of the quality model (Phase 8.2).
 */
@Entity
@Table(name = "skill_quality_learned_patterns")
class SkillQualityLearnedPatternEntity(
    @Id
    val id: String,

    @Column(name = "skill_name", nullable = false)
    val skillName: String,

    @Column(name = "pattern_type", nullable = false)
    val patternType: String,

    @Column(name = "pattern_description", nullable = false, columnDefinition = "TEXT")
    val patternDescription: String,

    @Column(nullable = false)
    val confidence: Double = 0.0,

    @Column(name = "sample_size", nullable = false)
    val sampleSize: Int = 0,

    @Column(columnDefinition = "TEXT")
    val suggestion: String? = null,

    @Column(nullable = false)
    val status: String = "PENDING",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "confirmed_at")
    val confirmedAt: Instant? = null
)
