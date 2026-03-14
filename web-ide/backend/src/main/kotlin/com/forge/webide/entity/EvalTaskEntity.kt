package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "eval_tasks")
class EvalTaskEntity(
    @Id val id: String,
    @Column(nullable = false) val name: String,
    @Column(columnDefinition = "TEXT") val description: String? = null,
    @Column(nullable = false, columnDefinition = "TEXT") val input: String,
    @Column(name = "success_criteria", nullable = false, columnDefinition = "TEXT") val successCriteria: String,
    @Column(name = "grader_config", columnDefinition = "TEXT") val graderConfig: String? = null,
    @Column(name = "task_type", length = 50) val taskType: String? = null,
    @Column(name = "skill_tags", columnDefinition = "TEXT") val skillTags: String? = null,
    @Column(length = 20) val difficulty: String = "MEDIUM",
    @Column(length = 50) val source: String = "MANUAL",
    @Column(name = "org_id", length = 36) val orgId: String? = null,
    @Column(name = "is_active") var isActive: Boolean = true,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
)
