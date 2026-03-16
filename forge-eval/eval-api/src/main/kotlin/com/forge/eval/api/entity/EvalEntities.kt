package com.forge.eval.api.entity

import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "eval_suites")
class EvalSuiteEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String = "",

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var platform: PlatformEnum = PlatformEnum.FORGE,

    @Column(name = "agent_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var agentType: AgentTypeEnum = AgentTypeEnum.CODING,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var lifecycle: LifecycleEnum = LifecycleEnum.CAPABILITY,

    @Column(columnDefinition = "TEXT")
    var tags: String = "[]",

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "eval_tasks")
class EvalTaskEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "suite_id", nullable = false)
    val suiteId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var prompt: String = "",

    @Column(columnDefinition = "TEXT")
    var context: String = "{}",

    @Column(name = "reference_answer", columnDefinition = "TEXT")
    var referenceAnswer: String? = null,

    @Column(name = "grader_configs", columnDefinition = "TEXT")
    var graderConfigs: String = "[]",

    @Column
    @Enumerated(EnumType.STRING)
    var difficulty: DifficultyEnum = DifficultyEnum.MEDIUM,

    @Column(columnDefinition = "TEXT")
    var tags: String = "[]",

    @Column(name = "baseline_pass_rate")
    var baselinePassRate: Double = 0.8,

    @Column(name = "saturation_count")
    var saturationCount: Int = 0,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "eval_runs")
class EvalRunEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "suite_id", nullable = false)
    val suiteId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: RunStatusEnum = RunStatusEnum.PENDING,

    @Column(name = "trials_per_task")
    var trialsPerTask: Int = 1,

    @Column
    var model: String? = null,

    @Column(columnDefinition = "TEXT")
    var summary: String? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "eval_trials")
class EvalTrialEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "run_id", nullable = false)
    val runId: UUID = UUID.randomUUID(),

    @Column(name = "task_id", nullable = false)
    val taskId: UUID = UUID.randomUUID(),

    @Column(name = "trial_number")
    var trialNumber: Int = 1,

    @Column
    @Enumerated(EnumType.STRING)
    var outcome: OutcomeEnum = OutcomeEnum.ERROR,

    @Column
    var score: Double = 0.0,

    @Column(name = "duration_ms")
    var durationMs: Long = 0,

    @Column(name = "token_usage", columnDefinition = "TEXT")
    var tokenUsage: String? = null,

    @Column(columnDefinition = "TEXT")
    var output: String = "",

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "eval_transcripts")
class EvalTranscriptEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "trial_id")
    var trialId: UUID? = null,

    @Column
    @Enumerated(EnumType.STRING)
    var source: TranscriptSourceEnum = TranscriptSourceEnum.FORGE,

    @Column(columnDefinition = "TEXT")
    var turns: String = "[]",

    @Column(name = "tool_call_summary", columnDefinition = "TEXT")
    var toolCallSummary: String = "[]",

    @Column(columnDefinition = "TEXT")
    var metadata: String = "{}",

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "eval_grades")
class EvalGradeEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "trial_id", nullable = false)
    val trialId: UUID = UUID.randomUUID(),

    @Column(name = "grader_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var graderType: GraderTypeEnum = GraderTypeEnum.CODE_BASED,

    @Column(nullable = false)
    var score: Double = 0.0,

    @Column(nullable = false)
    var passed: Boolean = false,

    @Column(name = "assertion_results", columnDefinition = "TEXT")
    var assertionResults: String = "[]",

    @Column(name = "rubric_scores", columnDefinition = "TEXT")
    var rubricScores: String = "{}",

    @Column(columnDefinition = "TEXT")
    var explanation: String = "",

    @Column
    var confidence: Double = 1.0,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "eval_reviews")
class EvalReviewEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "grade_id", nullable = false)
    val gradeId: UUID = UUID.randomUUID(),

    @Column(name = "trial_id", nullable = false)
    val trialId: UUID = UUID.randomUUID(),

    @Column(name = "task_id", nullable = false)
    val taskId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ReviewStatusEnum = ReviewStatusEnum.PENDING,

    @Column(name = "review_reasons", columnDefinition = "TEXT")
    var reviewReasons: String = "[]",

    @Column(name = "auto_score")
    var autoScore: Double = 0.0,

    @Column(name = "auto_confidence")
    var autoConfidence: Double = 1.0,

    @Column(name = "human_score")
    var humanScore: Double? = null,

    @Column(name = "human_passed")
    var humanPassed: Boolean? = null,

    @Column(columnDefinition = "TEXT")
    var explanation: String = "",

    @Column
    var reviewer: String? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null
)

// ── Enums with @JsonValue for lowercase serialization ────────────────

enum class PlatformEnum(@get:JsonValue val value: String) {
    FORGE("forge"), SYNAPSE("synapse"), APPLICATION("application")
}

enum class AgentTypeEnum(@get:JsonValue val value: String) {
    CODING("coding"), CONVERSATIONAL("conversational"),
    RESEARCH("research"), COMPUTER_USE("computer_use")
}

enum class LifecycleEnum(@get:JsonValue val value: String) {
    CAPABILITY("capability"), REGRESSION("regression"), SATURATED("saturated")
}

enum class RunStatusEnum(@get:JsonValue val value: String) {
    PENDING("pending"), RUNNING("running"), COMPLETED("completed"),
    FAILED("failed"), CANCELLED("cancelled")
}

enum class OutcomeEnum(@get:JsonValue val value: String) {
    PASS("pass"), FAIL("fail"), PARTIAL("partial"), ERROR("error")
}

enum class GraderTypeEnum(@get:JsonValue val value: String) {
    CODE_BASED("code_based"), MODEL_BASED("model_based"), HUMAN("human")
}

enum class TranscriptSourceEnum(@get:JsonValue val value: String) {
    FORGE("forge"), SYNAPSE("synapse"), EXTERNAL("external")
}

enum class DifficultyEnum(@get:JsonValue val value: String) {
    EASY("easy"), MEDIUM("medium"), HARD("hard"), EXPERT("expert")
}

enum class ReviewStatusEnum(@get:JsonValue val value: String) {
    PENDING("pending"), IN_PROGRESS("in_progress"), COMPLETED("completed"), SKIPPED("skipped")
}
