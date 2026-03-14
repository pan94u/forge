package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "eval_runs")
class EvalRunEntity(
    @Id val id: String,
    @Column var name: String? = null,
    @Column(name = "task_ids", nullable = false, columnDefinition = "TEXT") val taskIds: String,
    @Column(name = "model_provider", length = 50) val modelProvider: String? = null,
    @Column(name = "model_name", length = 100) val modelName: String? = null,
    @Column(name = "skill_profile", length = 100) val skillProfile: String? = null,
    @Column(name = "pass_k") val passK: Int = 1,
    @Column(length = 20) val mode: String = "PASS_AT_K",
    @Column(name = "agent_adapter", length = 50) val agentAdapter: String = "FORGE_INTERNAL",
    @Column(name = "agent_endpoint", length = 500) val agentEndpoint: String? = null,
    @Column(length = 20) var status: String = "PENDING",
    @Column(name = "org_id", length = 36) val orgId: String? = null,
    @Column(name = "triggered_by", length = 50) val triggeredBy: String = "MANUAL",
    @Column(name = "total_tasks") var totalTasks: Int = 0,
    @Column(name = "completed_tasks") var completedTasks: Int = 0,
    @Column(name = "pass_count") var passCount: Int = 0,
    @Column(name = "fail_count") var failCount: Int = 0,
    @Column(name = "started_at") var startedAt: Instant? = null,
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
)
