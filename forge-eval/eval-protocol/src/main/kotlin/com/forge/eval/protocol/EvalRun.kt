package com.forge.eval.protocol

import java.time.Instant
import java.util.UUID

/** A single evaluation run — executes all tasks in a suite with configurable trials */
data class EvalRun(
    val id: UUID = UUID.randomUUID(),
    val suiteId: UUID,
    val status: RunStatus = RunStatus.PENDING,
    val trialsPerTask: Int = 1,
    val model: String? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val summary: RunSummary? = null,
    val createdAt: Instant = Instant.now()
)

/** Summary statistics for a completed run */
data class RunSummary(
    val totalTasks: Int,
    val totalTrials: Int,
    val passedTrials: Int,
    val failedTrials: Int,
    val errorTrials: Int,
    val overallPassRate: Double,
    val averageScore: Double,
    val totalDurationMs: Long,
    val passAtK: Double? = null,
    val passPowerK: Double? = null
)
