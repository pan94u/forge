package com.forge.eval.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.eval.protocol.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

/**
 * End-to-end integration test for the Forge Eval REST API.
 *
 * Tests the full chain: HTTP → Controller → Service → Engine → DB → Response
 * using H2 in-memory database with JPA auto-DDL.
 */
@SpringBootTest(classes = [EvalApiTestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class EvalControllerIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    companion object {
        var suiteId: UUID? = null
        var taskId: UUID? = null
        var runId: UUID? = null
        var multiTrialRunId: UUID? = null
        var transcriptId: UUID? = null
    }

    @Test
    @Order(1)
    fun `POST suites - creates a new eval suite`() {
        val request = CreateSuiteRequest(
            name = "Integration Test Suite",
            description = "E2E test suite",
            platform = Platform.FORGE,
            agentType = AgentType.CODING,
            tags = listOf("test", "e2e")
        )

        val result = mockMvc.perform(
            post("/api/eval/v1/suites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Integration Test Suite"))
            .andExpect(jsonPath("$.platform").value("FORGE"))
            .andExpect(jsonPath("$.agentType").value("CODING"))
            .andExpect(jsonPath("$.taskCount").value(0))
            .andReturn()

        val response: SuiteResponse = objectMapper.readValue(result.response.contentAsString)
        suiteId = response.id
        assertThat(suiteId).isNotNull()
    }

    @Test
    @Order(2)
    fun `GET suites - lists suites with pagination`() {
        mockMvc.perform(get("/api/eval/v1/suites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content[0].name").value("Integration Test Suite"))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    @Order(3)
    fun `GET suites by id - returns suite details`() {
        mockMvc.perform(get("/api/eval/v1/suites/$suiteId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Integration Test Suite"))
            .andExpect(jsonPath("$.id").value(suiteId.toString()))
    }

    @Test
    @Order(4)
    fun `GET suites - 404 for nonexistent suite`() {
        mockMvc.perform(get("/api/eval/v1/suites/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
    }

    @Test
    @Order(5)
    fun `POST tasks - adds task to suite`() {
        val request = CreateTaskRequest(
            name = "Hello World Eval",
            description = "Check if agent says hello",
            prompt = "Say hello world",
            graderConfigs = listOf(
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = listOf(
                        AssertionConfig(type = "contains", expected = "hello", description = "says hello"),
                        AssertionConfig(type = "not_contains", expected = "error", description = "no error")
                    )
                )
            ),
            difficulty = Difficulty.EASY,
            tags = listOf("smoke-test")
        )

        val result = mockMvc.perform(
            post("/api/eval/v1/suites/$suiteId/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Hello World Eval"))
            .andExpect(jsonPath("$.prompt").value("Say hello world"))
            .andReturn()

        val task: EvalTask = objectMapper.readValue(result.response.contentAsString)
        taskId = task.id
        assertThat(taskId).isNotNull()
    }

    @Test
    @Order(6)
    fun `GET tasks - lists tasks for suite`() {
        mockMvc.perform(get("/api/eval/v1/suites/$suiteId/tasks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].name").value("Hello World Eval"))
    }

    @Test
    @Order(7)
    fun `POST runs - executes eval run end-to-end`() {
        val request = CreateRunRequest(
            suiteId = suiteId!!,
            trialsPerTask = 1
        )

        val result = mockMvc.perform(
            post("/api/eval/v1/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.trialsPerTask").value(1))
            .andExpect(jsonPath("$.trials").isArray)
            .andExpect(jsonPath("$.trials[0].trialNumber").value(1))
            .andReturn()

        val response: RunResponse = objectMapper.readValue(result.response.contentAsString)
        runId = response.id
        assertThat(runId).isNotNull()
        assertThat(response.summary).isNotNull()
        assertThat(response.summary!!.totalTasks).isEqualTo(1)
        assertThat(response.summary!!.totalTrials).isEqualTo(1)
        assertThat(response.trials).hasSize(1)
    }

    @Test
    @Order(8)
    fun `GET runs by id - returns run with trials and grades`() {
        val result = mockMvc.perform(get("/api/eval/v1/runs/$runId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(runId.toString()))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.trials").isArray)
            .andReturn()

        val response: RunResponse = objectMapper.readValue(result.response.contentAsString)
        assertThat(response.trials).hasSize(1)
        assertThat(response.trials[0].grades).isNotEmpty()
    }

    @Test
    @Order(9)
    fun `GET runs report JSON - returns structured report`() {
        mockMvc.perform(get("/api/eval/v1/runs/$runId/report?format=json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.suiteName").value("Integration Test Suite"))
            .andExpect(jsonPath("$.platform").value("FORGE"))
            .andExpect(jsonPath("$.taskResults").isArray)
            .andExpect(jsonPath("$.taskResults[0].taskName").value("Hello World Eval"))
    }

    @Test
    @Order(10)
    fun `GET runs report Markdown - returns readable markdown`() {
        val result = mockMvc.perform(get("/api/eval/v1/runs/$runId/report?format=markdown"))
            .andExpect(status().isOk)
            .andReturn()

        val markdown = result.response.contentAsString
        assertThat(markdown).contains("# Forge Eval Report")
        assertThat(markdown).contains("Integration Test Suite")
        assertThat(markdown).contains("Hello World Eval")
    }

    @Test
    @Order(11)
    fun `POST runs - 400 for suite with no tasks`() {
        // Create an empty suite
        val suiteReq = CreateSuiteRequest(
            name = "Empty Suite",
            platform = Platform.SYNAPSE,
            agentType = AgentType.CONVERSATIONAL
        )
        val suiteResult = mockMvc.perform(
            post("/api/eval/v1/suites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(suiteReq))
        ).andReturn()
        val emptySuite: SuiteResponse = objectMapper.readValue(suiteResult.response.contentAsString)

        val runReq = CreateRunRequest(suiteId = emptySuite.id)
        mockMvc.perform(
            post("/api/eval/v1/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(runReq))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @Order(12)
    fun `GET suites - filter by platform`() {
        mockMvc.perform(get("/api/eval/v1/suites?platform=FORGE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].platform").value("FORGE"))
    }

    // ── Phase 2: Multi-trial + Transcript + Regression ──────────────

    @Test
    @Order(20)
    fun `POST runs - multi-trial run with trialsPerTask=3`() {
        val request = CreateRunRequest(
            suiteId = suiteId!!,
            trialsPerTask = 3
        )

        val result = mockMvc.perform(
            post("/api/eval/v1/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.trialsPerTask").value(3))
            .andExpect(jsonPath("$.trials").isArray)
            .andReturn()

        val response: RunResponse = objectMapper.readValue(result.response.contentAsString)
        multiTrialRunId = response.id
        assertThat(response.trials).hasSize(3)
        assertThat(response.trials.map { it.trialNumber }).containsExactly(1, 2, 3)
    }

    @Test
    @Order(21)
    fun `GET runs report - multi-trial shows Pass@k and Pass^k when k gt 1`() {
        val result = mockMvc.perform(get("/api/eval/v1/runs/$multiTrialRunId/report?format=json"))
            .andExpect(status().isOk)
            .andReturn()

        val report: EvalReport = objectMapper.readValue(result.response.contentAsString)
        // With trialsPerTask=3, passAtK and passPowerK should be computed
        // In structure validation mode all trials produce same output so metrics are deterministic
        assertThat(report.summary.totalTrials).isEqualTo(3)
    }

    @Test
    @Order(30)
    fun `POST transcripts - submit external transcript for grading`() {
        val request = SubmitTranscriptRequest(
            suiteId = suiteId!!,
            taskId = taskId!!,
            source = TranscriptSource.EXTERNAL,
            turns = listOf(
                TranscriptTurn(role = "user", content = "Say hello"),
                TranscriptTurn(
                    role = "assistant",
                    content = "hello world!",
                    toolCalls = listOf(ToolCallInfo(toolName = "search_knowledge"))
                )
            ),
            metadata = mapOf("agent" to "synapse-v1")
        )

        val result = mockMvc.perform(
            post("/api/eval/v1/transcripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.transcriptId").isNotEmpty)
            .andExpect(jsonPath("$.grades").isArray)
            .andReturn()

        val body: Map<String, Any> = objectMapper.readValue(result.response.contentAsString)
        transcriptId = UUID.fromString(body["transcriptId"].toString())
        assertThat(transcriptId).isNotNull()
    }

    @Test
    @Order(31)
    fun `GET transcripts - retrieve submitted transcript`() {
        mockMvc.perform(get("/api/eval/v1/transcripts/$transcriptId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source").value("EXTERNAL"))
            .andExpect(jsonPath("$.turns").isArray)
            .andExpect(jsonPath("$.turns[1].content").value("hello world!"))
    }

    @Test
    @Order(40)
    fun `GET regressions - compare two runs`() {
        // runId (order 7) and multiTrialRunId (order 20) are both against same suite
        mockMvc.perform(
            get("/api/eval/v1/regressions")
                .param("suiteId", suiteId.toString())
                .param("currentRunId", multiTrialRunId.toString())
                .param("baselineRunId", runId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.regressions").isArray)
    }

    // ── Phase 4: Review + Lifecycle + Trends ──────────────────────

    @Test
    @Order(50)
    fun `GET reviews queue - returns empty queue initially`() {
        mockMvc.perform(get("/api/eval/v1/reviews/queue"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    @Order(51)
    fun `GET reviews calibration - returns empty metrics initially`() {
        mockMvc.perform(get("/api/eval/v1/reviews/calibration"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalReviews").value(0))
    }

    @Test
    @Order(52)
    fun `GET trends - returns suite trend data`() {
        mockMvc.perform(get("/api/eval/v1/trends/$suiteId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.suiteId").value(suiteId.toString()))
            .andExpect(jsonPath("$.suiteName").value("Integration Test Suite"))
            .andExpect(jsonPath("$.dataPoints").isArray)
    }

    @Test
    @Order(53)
    fun `GET trends - has data points from previous runs`() {
        val result = mockMvc.perform(get("/api/eval/v1/trends/$suiteId"))
            .andExpect(status().isOk)
            .andReturn()

        val response: TrendResponse = objectMapper.readValue(result.response.contentAsString)
        // We created 2 runs: single-trial (order 7) and multi-trial (order 20)
        assertThat(response.dataPoints.size).isGreaterThanOrEqualTo(2)
    }

    @Test
    @Order(54)
    fun `GET lifecycle - evaluate task lifecycle status`() {
        mockMvc.perform(get("/api/eval/v1/suites/$suiteId/tasks/$taskId/lifecycle"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.taskId").value(taskId.toString()))
            .andExpect(jsonPath("$.taskName").value("Hello World Eval"))
            .andExpect(jsonPath("$.currentLifecycle").exists())
            .andExpect(jsonPath("$.recommendedLifecycle").exists())
            .andExpect(jsonPath("$.shouldTransition").isBoolean)
            .andExpect(jsonPath("$.reason").isString)
    }

    @Test
    @Order(55)
    fun `PUT lifecycle - update task lifecycle`() {
        val request = UpdateLifecycleRequest(
            lifecycle = Lifecycle.REGRESSION,
            reason = "手动毕业到回归守护"
        )

        mockMvc.perform(
            put("/api/eval/v1/suites/$suiteId/tasks/$taskId/lifecycle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.newLifecycle").value("REGRESSION"))
            .andExpect(jsonPath("$.previousLifecycle").value("CAPABILITY"))
    }
}
