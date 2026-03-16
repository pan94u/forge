package com.forge.eval.protocol

import java.util.UUID

/** Request to create a new eval suite */
data class CreateSuiteRequest(
    val name: String,
    val description: String = "",
    val platform: Platform,
    val agentType: AgentType,
    val lifecycle: Lifecycle = Lifecycle.CAPABILITY,
    val tags: List<String> = emptyList()
)

/** Request to add a task to a suite */
data class CreateTaskRequest(
    val name: String,
    val description: String = "",
    val prompt: String,
    val context: Map<String, Any> = emptyMap(),
    val referenceAnswer: String? = null,
    val graderConfigs: List<GraderConfig> = emptyList(),
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val tags: List<String> = emptyList(),
    val baselinePassRate: Double = 0.8
)

/** Request to start an eval run */
data class CreateRunRequest(
    val suiteId: UUID,
    val trialsPerTask: Int = 1,
    val model: String? = null,
    val taskFilter: List<UUID>? = null
)

/** Request to submit an external transcript for grading */
data class SubmitTranscriptRequest(
    val suiteId: UUID,
    val taskId: UUID,
    val source: TranscriptSource = TranscriptSource.EXTERNAL,
    val turns: List<TranscriptTurn>,
    val metadata: Map<String, String> = emptyMap()
)

/** Request to grade a single transcript */
data class GradeRequest(
    val trialId: UUID,
    val graderConfigs: List<GraderConfig>? = null
)

/** Request to submit a human review */
data class SubmitReviewRequest(
    val score: Double,
    val passed: Boolean,
    val explanation: String = "",
    val rubricScores: Map<String, Double> = emptyMap(),
    val reviewer: String = "anonymous"
)

/** Request to update task lifecycle */
data class UpdateLifecycleRequest(
    val lifecycle: Lifecycle,
    val reason: String = ""
)
