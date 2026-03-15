package com.forge.eval.protocol

import java.time.Instant
import java.util.UUID

/** Response for suite details */
data class SuiteResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val platform: Platform,
    val agentType: AgentType,
    val lifecycle: Lifecycle,
    val tags: List<String>,
    val taskCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** Response for run details */
data class RunResponse(
    val id: UUID,
    val suiteId: UUID,
    val suiteName: String,
    val status: RunStatus,
    val trialsPerTask: Int,
    val model: String?,
    val summary: RunSummary?,
    val trials: List<TrialResponse>,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant
)

/** Response for a single trial */
data class TrialResponse(
    val id: UUID,
    val taskId: UUID,
    val taskName: String,
    val trialNumber: Int,
    val outcome: TrialOutcome,
    val score: Double,
    val durationMs: Long,
    val grades: List<GradeResponse>
)

/** Response for a single grade */
data class GradeResponse(
    val id: UUID,
    val graderType: GraderType,
    val score: Double,
    val passed: Boolean,
    val assertionResults: List<AssertionResult>,
    val explanation: String,
    val confidence: Double
)

/** Paginated list response wrapper */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

/** Report format for a run — can be serialized as JSON or rendered as Markdown */
data class EvalReport(
    val runId: UUID,
    val suiteName: String,
    val platform: Platform,
    val agentType: AgentType,
    val timestamp: Instant,
    val summary: RunSummary,
    val taskResults: List<TaskReport>,
    val baselineCheck: BaselineCheckReport? = null
)

data class TaskReport(
    val taskId: UUID,
    val taskName: String,
    val difficulty: Difficulty,
    val trials: List<TrialReport>
)

data class TrialReport(
    val trialNumber: Int,
    val outcome: TrialOutcome,
    val score: Double,
    val durationMs: Long,
    val grades: List<GradeResponse>
)

data class BaselineCheckReport(
    val allPassed: Boolean,
    val profileResults: Map<String, ProfileBaselineReport>,
    val failures: List<String>
)

data class ProfileBaselineReport(
    val profile: String,
    val baselinePassRate: Double,
    val actualPassRate: Double,
    val passed: Boolean,
    val evalCount: Int
)
