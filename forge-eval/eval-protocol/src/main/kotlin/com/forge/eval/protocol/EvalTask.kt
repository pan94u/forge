package com.forge.eval.protocol

import java.time.Instant
import java.util.UUID

/** A single evaluation task within a suite */
data class EvalTask(
    val id: UUID = UUID.randomUUID(),
    val suiteId: UUID,
    val name: String,
    val description: String = "",
    val prompt: String,
    val context: Map<String, Any> = emptyMap(),
    val referenceAnswer: String? = null,
    val graderConfigs: List<GraderConfig> = emptyList(),
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val tags: List<String> = emptyList(),
    val baselinePassRate: Double = 0.8,
    val saturationCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/** Configuration for a grader applied to a task */
data class GraderConfig(
    val type: GraderType,
    val assertions: List<AssertionConfig> = emptyList(),
    val model: String? = null,
    val rubric: List<RubricCriterion> = emptyList()
)

/** Single assertion in a code-based grader */
data class AssertionConfig(
    val type: String,
    val expected: String = "",
    val description: String = "",
    val caseSensitive: Boolean = true,
    val extras: Map<String, Any> = emptyMap()
)

/** A rubric criterion for model-based grading */
data class RubricCriterion(
    val criterion: String,
    val weight: Double = 1.0,
    val description: String = "",
    val scale: List<Double> = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
)
