package com.forge.eval.protocol

import java.time.Instant
import java.util.UUID

/** A grade produced by a grader for a specific trial */
data class EvalGrade(
    val id: UUID = UUID.randomUUID(),
    val trialId: UUID,
    val graderType: GraderType,
    val score: Double,
    val passed: Boolean,
    val assertionResults: List<AssertionResult> = emptyList(),
    val rubricScores: Map<String, Double> = emptyMap(),
    val explanation: String = "",
    val confidence: Double = 1.0,
    val createdAt: Instant = Instant.now()
)

/** Result of evaluating a single assertion */
data class AssertionResult(
    val description: String,
    val passed: Boolean,
    val expected: String,
    val actual: String = "",
    val assertionType: String = ""
)
