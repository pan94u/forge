package com.forge.webide.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "eval_results")
class EvalResultEntity(
    @Id val id: String,
    @Column(name = "run_id", nullable = false, length = 36) val runId: String,
    @Column(name = "task_id", nullable = false, length = 36) val taskId: String,
    @Column(name = "attempt_number") val attemptNumber: Int = 1,
    @Column(length = 20) var status: String? = null,
    @Column(name = "total_score", precision = 5, scale = 2) var totalScore: BigDecimal? = null,
    @Column(name = "code_grade_passed") var codeGradePassed: Boolean? = null,
    @Column(name = "code_grade_detail", columnDefinition = "TEXT") var codeGradeDetail: String? = null,
    @Column(name = "model_grade_score", precision = 5, scale = 2) var modelGradeScore: BigDecimal? = null,
    @Column(name = "model_grade_detail", columnDefinition = "TEXT") var modelGradeDetail: String? = null,
    @Column(columnDefinition = "TEXT") var transcript: String? = null,
    @Column(name = "workspace_id", length = 36) var workspaceId: String? = null,
    @Column(name = "duration_ms") var durationMs: Long? = null,
    @Column(name = "error_message", columnDefinition = "TEXT") var errorMessage: String? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
)
