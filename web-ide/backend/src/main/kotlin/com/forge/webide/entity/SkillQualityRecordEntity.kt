package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Records quality check results for each Skill script execution.
 * Part of the 3-layer quality model (Phase 8.2).
 */
@Entity
@Table(name = "skill_quality_records")
class SkillQualityRecordEntity(
    @Id
    val id: String,

    @Column(name = "skill_name", nullable = false)
    val skillName: String,

    @Column(name = "script_path")
    val scriptPath: String? = null,

    @Column(name = "workspace_id")
    val workspaceId: String? = null,

    @Column(name = "session_id")
    val sessionId: String? = null,

    @Column(name = "exit_code", nullable = false)
    val exitCode: Int = 0,

    @Column(name = "execution_time_ms", nullable = false)
    val executionTimeMs: Long = 0,

    @Column(name = "output_length", nullable = false)
    val outputLength: Int = 0,

    @Column(name = "output_snippet", columnDefinition = "TEXT")
    val outputSnippet: String? = null,

    @Column(name = "overall_status", nullable = false)
    val overallStatus: String = "UNKNOWN",

    @Column(name = "layer1_passed", nullable = false)
    val layer1Passed: Boolean = true,

    @Column(name = "layer1_details", columnDefinition = "TEXT")
    val layer1Details: String? = null,

    @Column(name = "layer2_passed")
    val layer2Passed: Boolean? = null,

    @Column(name = "layer2_details", columnDefinition = "TEXT")
    val layer2Details: String? = null,

    @Column(name = "auto_fix_applied", nullable = false)
    val autoFixApplied: Boolean = false,

    @Column(name = "auto_fix_type")
    val autoFixType: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
