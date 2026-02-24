package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Records evaluation metrics for each AI interaction (Phase 7 — Learning Loop).
 *
 * Tracks 4-dimensional scores (intent, completion, quality, experience) both
 * automatically computed and manually rated by users.
 */
@Entity
@Table(name = "interaction_evaluations")
class InteractionEvaluationEntity(
    @Id
    val id: String,

    @Column(name = "session_id", nullable = false)
    val sessionId: String,

    @Column(name = "workspace_id")
    val workspaceId: String = "",

    @Column(nullable = false)
    val profile: String,

    /** Profile mode: "default" or "read-only" */
    @Column(nullable = false)
    val mode: String = "default",

    /** Capability category: A=ANALYZE, B=GENERATE, C=FIX, D=KNOWLEDGE, E=DELIVER */
    @Column(name = "capability_category")
    val capabilityCategory: String = "",

    /** Scenario ID: A1, B3, C1, etc. */
    @Column(name = "scenario_id")
    val scenarioId: String = "",

    /** ProfileRouter confidence score (0.0 - 1.0) */
    @Column(name = "routing_confidence")
    val routingConfidence: Double = 0.0,

    /** Whether user confirmed the intent via confirmation card */
    @Column(name = "intent_confirmed")
    val intentConfirmed: Boolean = false,

    // -- Auto-computed 4D scores (0.0 - 1.0) --

    @Column(name = "intent_score")
    var intentScore: Double = 0.0,

    @Column(name = "completion_score")
    var completionScore: Double = 0.0,

    @Column(name = "quality_score")
    var qualityScore: Double = 0.0,

    @Column(name = "experience_score")
    var experienceScore: Double = 0.0,

    // -- Manual 4D scores (0 - 5, set by user) --

    @Column(name = "manual_intent_score")
    var manualIntentScore: Int? = null,

    @Column(name = "manual_completion_score")
    var manualCompletionScore: Int? = null,

    @Column(name = "manual_quality_score")
    var manualQualityScore: Int? = null,

    @Column(name = "manual_experience_score")
    var manualExperienceScore: Int? = null,

    // -- Metrics --

    @Column(name = "tool_call_count")
    val toolCallCount: Int = 0,

    @Column(name = "tool_success_count")
    val toolSuccessCount: Int = 0,

    @Column(name = "turn_count")
    val turnCount: Int = 0,

    @Column(name = "duration_ms")
    val durationMs: Long = 0,

    /** Free-text user feedback */
    @Column(name = "user_feedback", columnDefinition = "TEXT")
    var userFeedback: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
