package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.forge.webide.entity.EvalResultEntity
import com.forge.webide.entity.EvalRunEntity
import com.forge.webide.entity.EvalTaskEntity
import com.forge.webide.repository.EvalResultRepository
import com.forge.webide.repository.EvalRunRepository
import com.forge.webide.repository.EvalTaskRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class EvalRunnerServiceTest {

    private lateinit var evalRunRepository: EvalRunRepository
    private lateinit var evalResultRepository: EvalResultRepository
    private lateinit var evalTaskRepository: EvalTaskRepository
    private lateinit var graderOrchestrator: GraderOrchestrator
    private lateinit var forgeAgentAdapter: ForgeAgentAdapter
    private lateinit var externalApiAdapter: ExternalApiAdapter
    private lateinit var objectMapper: ObjectMapper
    private lateinit var service: EvalRunnerService

    @BeforeEach
    fun setUp() {
        evalRunRepository = mockk(relaxed = true)
        evalResultRepository = mockk(relaxed = true)
        evalTaskRepository = mockk(relaxed = true)
        graderOrchestrator = mockk(relaxed = true)
        forgeAgentAdapter = mockk()
        externalApiAdapter = mockk()
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        every { evalRunRepository.save(any<EvalRunEntity>()) } answers { firstArg() }
        every { evalResultRepository.save(any<EvalResultEntity>()) } answers { firstArg() }

        service = EvalRunnerService(
            evalRunRepository, evalResultRepository, evalTaskRepository,
            graderOrchestrator, forgeAgentAdapter, externalApiAdapter, objectMapper
        )
    }

    private fun makeRun(
        taskIds: List<String> = listOf("task-001"),
        passK: Int = 1,
        mode: String = "PASS_AT_K",
        adapter: String = "FORGE_INTERNAL"
    ): EvalRunEntity {
        val taskIdsJson = objectMapper.writeValueAsString(taskIds)
        return EvalRunEntity(
            id = "run-001",
            taskIds = taskIdsJson,
            passK = passK,
            mode = mode,
            agentAdapter = adapter,
            status = "PENDING",
            createdAt = Instant.now()
        )
    }

    private fun makeTask(id: String = "task-001") = EvalTaskEntity(
        id = id,
        name = "Test Task",
        input = "Write a function",
        successCriteria = "contains:function",
        graderConfig = """{"codeGrader":true,"assertions":[{"type":"contains","expected":"function"}]}""",
        createdAt = Instant.now()
    )

    @Test
    fun `startRun transitions status PENDING to RUNNING to COMPLETED`() {
        val run = makeRun()
        val task = makeTask()
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        every { evalTaskRepository.findById("task-001") } returns Optional.of(task)
        every { forgeAgentAdapter.execute(any(), any()) } returns AgentResponse(
            output = "function hello() {}", durationMs = 100
        )
        every { graderOrchestrator.gradeAndPersist(any(), any(), any()) } returns GradeResult(
            totalScore = java.math.BigDecimal("0.90"), codeGrade = null, modelGrade = null, passed = true
        )

        service.startRun("run-001")

        assertThat(run.status).isEqualTo("COMPLETED")
        assertThat(run.startedAt).isNotNull()
        assertThat(run.completedAt).isNotNull()
    }

    @Test
    fun `startRun counts pass correctly`() {
        val run = makeRun()
        val task = makeTask()
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        every { evalTaskRepository.findById("task-001") } returns Optional.of(task)
        every { forgeAgentAdapter.execute(any(), any()) } returns AgentResponse(
            output = "function hello() {}", durationMs = 100
        )
        every { graderOrchestrator.gradeAndPersist(any(), any(), any()) } returns GradeResult(
            totalScore = java.math.BigDecimal("0.90"), codeGrade = null, modelGrade = null, passed = true
        )

        service.startRun("run-001")

        assertThat(run.passCount).isEqualTo(1)
        assertThat(run.failCount).isEqualTo(0)
        assertThat(run.completedTasks).isEqualTo(1)
    }

    @Test
    fun `startRun counts fail correctly`() {
        val run = makeRun()
        val task = makeTask()
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        every { evalTaskRepository.findById("task-001") } returns Optional.of(task)
        every { forgeAgentAdapter.execute(any(), any()) } returns AgentResponse(
            output = "no match", durationMs = 100
        )
        every { graderOrchestrator.gradeAndPersist(any(), any(), any()) } returns GradeResult(
            totalScore = java.math.BigDecimal("0.20"), codeGrade = null, modelGrade = null, passed = false
        )

        service.startRun("run-001")

        assertThat(run.passCount).isEqualTo(0)
        assertThat(run.failCount).isEqualTo(1)
    }

    @Test
    fun `PASS_AT_K mode passes if any attempt succeeds`() {
        val run = makeRun(passK = 3, mode = "PASS_AT_K")
        val task = makeTask()
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        every { evalTaskRepository.findById("task-001") } returns Optional.of(task)

        var callCount = 0
        every { forgeAgentAdapter.execute(any(), any()) } answers {
            callCount++
            AgentResponse(output = "attempt $callCount", durationMs = 100)
        }
        every { graderOrchestrator.gradeAndPersist(any(), any(), any()) } returnsMany listOf(
            GradeResult(java.math.BigDecimal("0.20"), null, null, false),
            GradeResult(java.math.BigDecimal("0.90"), null, null, true),
            GradeResult(java.math.BigDecimal("0.30"), null, null, false)
        )

        service.startRun("run-001")

        assertThat(run.passCount).isEqualTo(1)
        assertThat(run.failCount).isEqualTo(0)
    }

    @Test
    fun `PASS_POW_K mode fails if any attempt fails`() {
        val run = makeRun(passK = 2, mode = "PASS_POW_K")
        val task = makeTask()
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        every { evalTaskRepository.findById("task-001") } returns Optional.of(task)
        every { forgeAgentAdapter.execute(any(), any()) } returns AgentResponse(
            output = "output", durationMs = 100
        )
        every { graderOrchestrator.gradeAndPersist(any(), any(), any()) } returnsMany listOf(
            GradeResult(java.math.BigDecimal("0.90"), null, null, true),
            GradeResult(java.math.BigDecimal("0.20"), null, null, false)
        )

        service.startRun("run-001")

        assertThat(run.passCount).isEqualTo(0)
        assertThat(run.failCount).isEqualTo(1)
    }

    @Test
    fun `PASS_POW_K mode passes when all attempts pass`() {
        val run = makeRun(passK = 2, mode = "PASS_POW_K")
        val task = makeTask()
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        every { evalTaskRepository.findById("task-001") } returns Optional.of(task)
        every { forgeAgentAdapter.execute(any(), any()) } returns AgentResponse(
            output = "output", durationMs = 100
        )
        every { graderOrchestrator.gradeAndPersist(any(), any(), any()) } returns GradeResult(
            java.math.BigDecimal("0.90"), null, null, true
        )

        service.startRun("run-001")

        assertThat(run.passCount).isEqualTo(1)
        assertThat(run.failCount).isEqualTo(0)
    }

    @Test
    fun `resolveAdapter returns ForgeAgentAdapter for FORGE_INTERNAL`() {
        val adapter = service.resolveAdapter("FORGE_INTERNAL")
        assertThat(adapter).isInstanceOf(ForgeAgentAdapter::class.java)
    }

    @Test
    fun `resolveAdapter returns ExternalApiAdapter for EXTERNAL_API`() {
        val adapter = service.resolveAdapter("EXTERNAL_API")
        assertThat(adapter).isInstanceOf(ExternalApiAdapter::class.java)
    }

    @Test
    fun `startRun sets FAILED status on exception`() {
        val run = makeRun()
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        // taskIds JSON parsing will succeed, but findById returns empty
        every { evalTaskRepository.findById(any()) } throws RuntimeException("DB connection lost")

        service.startRun("run-001")

        assertThat(run.status).isEqualTo("FAILED")
        assertThat(run.completedAt).isNotNull()
    }
}
