package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.CompletionResult
import com.forge.adapter.model.ModelAdapter
import com.forge.adapter.model.StopReason
import com.forge.adapter.model.TokenUsage
import com.forge.webide.entity.EvalResultEntity
import com.forge.webide.entity.EvalTaskEntity
import com.forge.webide.repository.EvalResultRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class GraderOrchestratorTest {

    private lateinit var modelAdapter: ModelAdapter
    private lateinit var objectMapper: ObjectMapper
    private lateinit var evalResultRepository: EvalResultRepository
    private lateinit var orchestrator: GraderOrchestrator

    @BeforeEach
    fun setUp() {
        modelAdapter = mockk()
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        evalResultRepository = mockk(relaxed = true)
        orchestrator = GraderOrchestrator(modelAdapter, objectMapper, evalResultRepository)
    }

    private fun makeTask(
        graderConfig: String? = null,
        input: String = "Write code",
        successCriteria: String = "contains:result"
    ) = EvalTaskEntity(
        id = "task-001",
        name = "Test Task",
        input = input,
        successCriteria = successCriteria,
        graderConfig = graderConfig,
        createdAt = Instant.now()
    )

    private fun makeResult() = EvalResultEntity(
        id = "result-001",
        runId = "run-001",
        taskId = "task-001",
        createdAt = Instant.now()
    )

    @Test
    fun `parseGraderConfig returns default for null input`() {
        val config = orchestrator.parseGraderConfig(null)
        assertThat(config.codeGrader).isFalse()
        assertThat(config.modelGrader).isFalse()
    }

    @Test
    fun `parseGraderConfig returns default for blank input`() {
        val config = orchestrator.parseGraderConfig("  ")
        assertThat(config.codeGrader).isFalse()
    }

    @Test
    fun `parseGraderConfig parses valid JSON`() {
        val json = """{"codeGrader":true,"modelGrader":false,"rubric":"quality","assertions":[{"type":"contains","expected":"test","description":"has test"}],"baselinePassRate":0.9}"""
        val config = orchestrator.parseGraderConfig(json)
        assertThat(config.codeGrader).isTrue()
        assertThat(config.modelGrader).isFalse()
        assertThat(config.assertions).hasSize(1)
        assertThat(config.baselinePassRate).isEqualTo(0.9)
    }

    @Test
    fun `computeTotalScore with both graders uses 50-50 weighting`() {
        val codeGrade = CodeGradeResult(passed = true, assertions = emptyList(), passRate = 0.8, detail = "{}")
        val modelGrade = ModelGradeResult(score = BigDecimal("0.6"), rationale = "", detail = "{}")
        val score = orchestrator.computeTotalScore(codeGrade, modelGrade)
        // 0.8 * 0.5 + 0.6 * 0.5 = 0.70
        assertThat(score).isEqualByComparingTo(BigDecimal("0.70"))
    }

    @Test
    fun `computeTotalScore with only code grader uses code score`() {
        val codeGrade = CodeGradeResult(passed = true, assertions = emptyList(), passRate = 0.9, detail = "{}")
        val score = orchestrator.computeTotalScore(codeGrade, null)
        assertThat(score).isEqualByComparingTo(BigDecimal("0.90"))
    }

    @Test
    fun `computeTotalScore with only model grader uses model score`() {
        val modelGrade = ModelGradeResult(score = BigDecimal("0.75"), rationale = "", detail = "{}")
        val score = orchestrator.computeTotalScore(null, modelGrade)
        assertThat(score).isEqualByComparingTo(BigDecimal("0.75"))
    }

    @Test
    fun `computeTotalScore with neither grader returns zero`() {
        val score = orchestrator.computeTotalScore(null, null)
        assertThat(score).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `grade with code grader only runs assertions`() {
        val graderConfig = objectMapper.writeValueAsString(mapOf(
            "codeGrader" to true,
            "modelGrader" to false,
            "assertions" to listOf(mapOf("type" to "contains", "expected" to "hello", "description" to "has hello")),
            "baselinePassRate" to 0.5
        ))
        val task = makeTask(graderConfig = graderConfig)
        val result = orchestrator.grade(task, "hello world")

        assertThat(result.codeGrade).isNotNull
        assertThat(result.codeGrade!!.passed).isTrue()
        assertThat(result.modelGrade).isNull()
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `grade with model grader calls LLM`() {
        val graderConfig = objectMapper.writeValueAsString(mapOf(
            "codeGrader" to false,
            "modelGrader" to true,
            "rubric" to "Check quality",
            "baselinePassRate" to 0.5
        ))
        coEvery { modelAdapter.complete(any(), any<CompletionOptions>()) } returns CompletionResult(
            content = """{"score": 0.8, "dimensions": {}, "rationale": "Good"}""",
            model = "claude-sonnet-4-20250514",
            usage = TokenUsage(100, 50),
            stopReason = StopReason.END_TURN,
            latencyMs = 500
        )

        val task = makeTask(graderConfig = graderConfig)
        val result = orchestrator.grade(task, "some output")

        assertThat(result.codeGrade).isNull()
        assertThat(result.modelGrade).isNotNull
        assertThat(result.modelGrade!!.score).isEqualByComparingTo(BigDecimal("0.80"))
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `gradeAndPersist updates result entity`() {
        val graderConfig = objectMapper.writeValueAsString(mapOf(
            "codeGrader" to true,
            "modelGrader" to false,
            "assertions" to listOf(mapOf("type" to "contains", "expected" to "ok", "description" to "has ok")),
            "baselinePassRate" to 0.5
        ))
        val task = makeTask(graderConfig = graderConfig)
        val resultEntity = makeResult()

        every { evalResultRepository.save(any()) } answers { firstArg() }

        val gradeResult = orchestrator.gradeAndPersist(task, "ok done", resultEntity)

        assertThat(gradeResult.passed).isTrue()
        assertThat(resultEntity.status).isEqualTo("PASSED")
        assertThat(resultEntity.totalScore).isNotNull
        assertThat(resultEntity.codeGradePassed).isTrue()
        verify(exactly = 1) { evalResultRepository.save(resultEntity) }
    }
}
