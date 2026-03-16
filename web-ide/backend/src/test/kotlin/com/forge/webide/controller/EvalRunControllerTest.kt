package com.forge.webide.controller

import com.forge.webide.entity.EvalResultEntity
import com.forge.webide.entity.EvalRunEntity
import com.forge.webide.repository.EvalResultRepository
import com.forge.webide.repository.EvalRunRepository
import com.forge.webide.service.eval.EvalRunnerService
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class EvalRunControllerTest {

    private lateinit var evalRunRepository: EvalRunRepository
    private lateinit var evalResultRepository: EvalResultRepository
    private lateinit var evalRunnerService: EvalRunnerService
    private lateinit var controller: EvalRunController

    @BeforeEach
    fun setUp() {
        evalRunRepository = mockk(relaxed = true)
        evalResultRepository = mockk(relaxed = true)
        evalRunnerService = mockk(relaxed = true)
        controller = EvalRunController(evalRunRepository, evalResultRepository, evalRunnerService)
    }

    @Test
    fun `createRun saves entity and starts run`() {
        every { evalRunRepository.save(any()) } answers { firstArg() }

        val request = EvalRunController.CreateRunRequest(
            taskIds = listOf("task-001", "task-002"),
            passK = 3,
            mode = "PASS_AT_K"
        )
        val response = controller.createRun(request)

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body!!.passK).isEqualTo(3)
        assertThat(response.body!!.totalTasks).isEqualTo(2)
        assertThat(response.body!!.status).isEqualTo("PENDING")
        verify(exactly = 1) { evalRunnerService.startRun(any()) }
    }

    @Test
    fun `listRuns returns all runs when no orgId`() {
        val runs = listOf(
            EvalRunEntity(id = "run-1", taskIds = "[]", createdAt = Instant.now()),
            EvalRunEntity(id = "run-2", taskIds = "[]", createdAt = Instant.now())
        )
        every { evalRunRepository.findAllByOrderByCreatedAtDesc() } returns runs

        val response = controller.listRuns(null)

        assertThat(response.body).hasSize(2)
    }

    @Test
    fun `getRun returns 404 for missing run`() {
        every { evalRunRepository.findById("nonexistent") } returns Optional.empty()

        val response = controller.getRun("nonexistent")

        assertThat(response.statusCode.value()).isEqualTo(404)
    }

    @Test
    fun `getRunResults returns results for run`() {
        every { evalRunRepository.existsById("run-001") } returns true
        every { evalResultRepository.findByRunIdOrderByCreatedAtAsc("run-001") } returns listOf(
            EvalResultEntity(id = "r-1", runId = "run-001", taskId = "t-1", createdAt = Instant.now()),
            EvalResultEntity(id = "r-2", runId = "run-001", taskId = "t-2", createdAt = Instant.now())
        )

        val response = controller.getRunResults("run-001")

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body).hasSize(2)
    }

    @Test
    fun `cancelRun sets status to CANCELLED`() {
        val run = EvalRunEntity(
            id = "run-001", taskIds = "[]", status = "RUNNING", createdAt = Instant.now()
        )
        every { evalRunRepository.findById("run-001") } returns Optional.of(run)
        every { evalRunRepository.save(any()) } answers { firstArg() }

        val response = controller.cancelRun("run-001")

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body!!.status).isEqualTo("CANCELLED")
        assertThat(run.completedAt).isNotNull()
    }
}
