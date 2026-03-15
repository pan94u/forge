package com.forge.eval.engine

import com.forge.eval.protocol.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class EvalEngineTest {

    private val engine = EvalEngine()

    private fun createSuite() = EvalSuite(
        name = "test-suite",
        platform = Platform.FORGE,
        agentType = AgentType.CODING
    )

    private fun createTask(
        suiteId: UUID,
        assertions: List<AssertionConfig> = listOf(
            AssertionConfig(type = "contains", expected = "hello", description = "has hello")
        )
    ) = EvalTask(
        suiteId = suiteId,
        name = "test-task",
        prompt = "Say hello",
        graderConfigs = listOf(GraderConfig(type = GraderType.CODE_BASED, assertions = assertions))
    )

    @Test
    fun `executeRun - single task single trial - pass`() {
        val suite = createSuite()
        val task = createTask(suite.id)

        val result = engine.executeRun(suite, listOf(task), trialsPerTask = 1) {
            TrialOutput(output = "hello world")
        }

        assertThat(result.run.status).isEqualTo(RunStatus.COMPLETED)
        assertThat(result.run.summary?.totalTasks).isEqualTo(1)
        assertThat(result.run.summary?.totalTrials).isEqualTo(1)
        assertThat(result.run.summary?.passedTrials).isEqualTo(1)
        assertThat(result.run.summary?.overallPassRate).isEqualTo(1.0)
        assertThat(result.trials).hasSize(1)
        assertThat(result.trials[0].trial.outcome).isEqualTo(TrialOutcome.PASS)
    }

    @Test
    fun `executeRun - single task single trial - fail`() {
        val suite = createSuite()
        val task = createTask(suite.id)

        val result = engine.executeRun(suite, listOf(task), trialsPerTask = 1) {
            TrialOutput(output = "goodbye world")
        }

        assertThat(result.run.summary?.passedTrials).isEqualTo(0)
        assertThat(result.run.summary?.failedTrials).isEqualTo(1)
        assertThat(result.trials[0].trial.outcome).isEqualTo(TrialOutcome.FAIL)
    }

    @Test
    fun `executeRun - multiple trials per task`() {
        val suite = createSuite()
        val task = createTask(suite.id)
        var callCount = 0

        val result = engine.executeRun(suite, listOf(task), trialsPerTask = 3) {
            callCount++
            if (callCount <= 2) TrialOutput(output = "hello") else TrialOutput(output = "nope")
        }

        assertThat(result.run.summary?.totalTrials).isEqualTo(3)
        assertThat(result.run.summary?.passedTrials).isEqualTo(2)
        assertThat(result.run.summary?.failedTrials).isEqualTo(1)
    }

    @Test
    fun `executeRun - multiple tasks`() {
        val suite = createSuite()
        val task1 = createTask(suite.id, listOf(
            AssertionConfig(type = "contains", expected = "alpha", description = "has alpha")
        ))
        val task2 = createTask(suite.id, listOf(
            AssertionConfig(type = "contains", expected = "beta", description = "has beta")
        ))

        val result = engine.executeRun(suite, listOf(task1, task2), trialsPerTask = 1) {
            TrialOutput(output = "alpha and beta here")
        }

        assertThat(result.run.summary?.totalTasks).isEqualTo(2)
        assertThat(result.run.summary?.totalTrials).isEqualTo(2)
        assertThat(result.run.summary?.passedTrials).isEqualTo(2)
    }

    @Test
    fun `executeRun - error handling`() {
        val suite = createSuite()
        val task = createTask(suite.id)

        val result = engine.executeRun(suite, listOf(task), trialsPerTask = 1) {
            throw RuntimeException("model crashed")
        }

        assertThat(result.trials[0].trial.outcome).isEqualTo(TrialOutcome.ERROR)
        assertThat(result.run.summary?.errorTrials).isEqualTo(1)
    }

    @Test
    fun `executeRun - task with no grader configs passes`() {
        val suite = createSuite()
        val task = EvalTask(
            suiteId = suite.id,
            name = "no-grader-task",
            prompt = "Do something",
            graderConfigs = emptyList()
        )

        val result = engine.executeRun(suite, listOf(task), trialsPerTask = 1) {
            TrialOutput(output = "done")
        }

        assertThat(result.trials[0].trial.outcome).isEqualTo(TrialOutcome.PASS)
    }

    @Test
    fun `gradeOutput - grades external output`() {
        val suite = createSuite()
        val task = createTask(suite.id, listOf(
            AssertionConfig(type = "contains", expected = "success", description = "has success"),
            AssertionConfig(type = "not_contains", expected = "error", description = "no error")
        ))

        val grades = engine.gradeOutput(task, "operation success completed")
        assertThat(grades).hasSize(1)
        assertThat(grades[0].passed).isTrue()
        assertThat(grades[0].score).isEqualTo(1.0)
    }

    @Test
    fun `executeRun - with transcript tool assertions`() {
        val suite = createSuite()
        val task = createTask(suite.id, listOf(
            AssertionConfig(type = "tool_used", expected = "search_knowledge", description = "searched"),
            AssertionConfig(type = "contains", expected = "result", description = "has result")
        ))

        val transcript = EvalTranscript(
            toolCallSummary = listOf(ToolCallInfo(toolName = "search_knowledge"))
        )

        val result = engine.executeRun(suite, listOf(task), trialsPerTask = 1) {
            TrialOutput(output = "here is the result", transcript = transcript)
        }

        assertThat(result.trials[0].trial.outcome).isEqualTo(TrialOutcome.PASS)
    }
}
