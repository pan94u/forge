package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.adapter.model.ModelAdapter
import com.forge.webide.entity.EvalResultEntity
import com.forge.webide.entity.EvalTaskEntity
import com.forge.webide.repository.EvalResultRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class GraderOrchestrator(
    private val claudeAdapter: ModelAdapter,
    private val objectMapper: ObjectMapper,
    private val evalResultRepository: EvalResultRepository
) {

    private val log = LoggerFactory.getLogger(GraderOrchestrator::class.java)

    fun parseGraderConfig(graderConfigJson: String?): GraderConfig {
        if (graderConfigJson.isNullOrBlank()) return GraderConfig()
        return try {
            objectMapper.readValue(graderConfigJson)
        } catch (e: Exception) {
            log.warn("Failed to parse graderConfig: {}", e.message)
            GraderConfig()
        }
    }

    fun grade(task: EvalTaskEntity, actualOutput: String): GradeResult {
        val config = parseGraderConfig(task.graderConfig)

        val codeGrade = if (config.codeGrader && config.assertions.isNotEmpty()) {
            CodeGrader.grade(config.assertions, actualOutput, objectMapper)
        } else null

        val modelGrade = if (config.modelGrader) {
            val modelGrader = ModelGrader(claudeAdapter, objectMapper)
            modelGrader.grade(config.rubric, task.successCriteria, task.input, actualOutput)
        } else null

        val totalScore = computeTotalScore(codeGrade, modelGrade)
        val passed = totalScore >= BigDecimal.valueOf(config.baselinePassRate)

        return GradeResult(
            totalScore = totalScore,
            codeGrade = codeGrade,
            modelGrade = modelGrade,
            passed = passed
        )
    }

    @Transactional
    fun gradeAndPersist(task: EvalTaskEntity, actualOutput: String, resultEntity: EvalResultEntity): GradeResult {
        val result = grade(task, actualOutput)

        resultEntity.totalScore = result.totalScore
        resultEntity.codeGradePassed = result.codeGrade?.passed
        resultEntity.codeGradeDetail = result.codeGrade?.detail
        resultEntity.modelGradeScore = result.modelGrade?.score
        resultEntity.modelGradeDetail = result.modelGrade?.detail
        resultEntity.status = if (result.passed) "PASSED" else "FAILED"

        evalResultRepository.save(resultEntity)
        log.info("Graded task={} result={} score={} passed={}",
            task.id, resultEntity.id, result.totalScore, result.passed)

        return result
    }

    internal fun computeTotalScore(codeGrade: CodeGradeResult?, modelGrade: ModelGradeResult?): BigDecimal {
        return when {
            codeGrade != null && modelGrade != null -> {
                val codeScore = BigDecimal.valueOf(codeGrade.passRate)
                val weighted = codeScore.multiply(BigDecimal("0.5"))
                    .add(modelGrade.score.multiply(BigDecimal("0.5")))
                weighted.setScale(2, RoundingMode.HALF_UP)
            }
            codeGrade != null -> BigDecimal.valueOf(codeGrade.passRate).setScale(2, RoundingMode.HALF_UP)
            modelGrade != null -> modelGrade.score.setScale(2, RoundingMode.HALF_UP)
            else -> BigDecimal.ZERO
        }
    }
}
