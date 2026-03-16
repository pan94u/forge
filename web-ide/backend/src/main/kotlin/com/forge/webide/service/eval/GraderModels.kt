package com.forge.webide.service.eval

import java.math.BigDecimal

/**
 * Grader 体系共享数据类。
 * 解析 EvalTaskEntity.graderConfig JSON，承载断言配置与评分结果。
 */

data class GraderConfig(
    val codeGrader: Boolean = false,
    val modelGrader: Boolean = false,
    val rubric: String = "",
    val assertions: List<AssertionConfig> = emptyList(),
    val baselinePassRate: Double = 0.8
)

data class AssertionConfig(
    val type: String,
    val expected: String = "",
    val description: String = ""
)

data class AssertionResult(
    val passed: Boolean,
    val expected: String,
    val actual: String,
    val description: String = ""
)

data class CodeGradeResult(
    val passed: Boolean,
    val assertions: List<AssertionResult>,
    val passRate: Double,
    val detail: String
)

data class ModelGradeResult(
    val score: BigDecimal,
    val rationale: String,
    val detail: String
)

data class GradeResult(
    val totalScore: BigDecimal,
    val codeGrade: CodeGradeResult?,
    val modelGrade: ModelGradeResult?,
    val passed: Boolean
)
