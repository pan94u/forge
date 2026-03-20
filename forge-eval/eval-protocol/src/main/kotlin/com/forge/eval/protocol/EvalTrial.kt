package com.forge.eval.protocol

import java.time.Instant
import java.util.UUID

/** A single trial — one execution of one task within a run */
data class EvalTrial(
    val id: UUID = UUID.randomUUID(),
    val runId: UUID,
    val taskId: UUID,
    val trialNumber: Int,
    val outcome: TrialOutcome = TrialOutcome.ERROR,
    val score: Double = 0.0,
    val durationMs: Long = 0,
    val tokenUsage: TokenUsage? = null,
    val output: String = "",
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now()
)

/** Token usage for a trial */
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}
