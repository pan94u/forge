package com.forge.eval.engine

import com.forge.eval.protocol.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ReportGeneratorTest {

    private val generator = ReportGenerator()

    @Test
    fun `generateReport - creates report from run result`() {
        val suite = EvalSuite(
            name = "test-suite",
            platform = Platform.FORGE,
            agentType = AgentType.CODING
        )
        val task = EvalTask(
            suiteId = suite.id,
            name = "task-1",
            prompt = "do something",
            difficulty = Difficulty.MEDIUM
        )

        val trialId = UUID.randomUUID()
        val runResult = EvalRunResult(
            run = EvalRun(
                suiteId = suite.id,
                status = RunStatus.COMPLETED,
                summary = RunSummary(
                    totalTasks = 1,
                    totalTrials = 1,
                    passedTrials = 1,
                    failedTrials = 0,
                    errorTrials = 0,
                    overallPassRate = 1.0,
                    averageScore = 1.0,
                    totalDurationMs = 100
                )
            ),
            trials = listOf(
                EvalTrialResult(
                    trial = EvalTrial(
                        id = trialId,
                        runId = UUID.randomUUID(),
                        taskId = task.id,
                        trialNumber = 1,
                        outcome = TrialOutcome.PASS,
                        score = 1.0,
                        durationMs = 100
                    ),
                    grades = listOf(
                        EvalGrade(
                            trialId = trialId,
                            graderType = GraderType.CODE_BASED,
                            score = 1.0,
                            passed = true,
                            assertionResults = listOf(
                                AssertionResult("check1", true, "expected", "actual")
                            ),
                            explanation = "All passed"
                        )
                    )
                )
            )
        )

        val report = generator.generateReport(suite, listOf(task), runResult)
        assertThat(report.suiteName).isEqualTo("test-suite")
        assertThat(report.platform).isEqualTo(Platform.FORGE)
        assertThat(report.taskResults).hasSize(1)
        assertThat(report.taskResults[0].taskName).isEqualTo("task-1")
    }

    @Test
    fun `toMarkdown - generates readable markdown`() {
        val suite = EvalSuite(
            name = "markdown-test",
            platform = Platform.SYNAPSE,
            agentType = AgentType.CONVERSATIONAL
        )
        val task = EvalTask(suiteId = suite.id, name = "md-task", prompt = "test")

        val trialId = UUID.randomUUID()
        val runResult = EvalRunResult(
            run = EvalRun(
                suiteId = suite.id,
                status = RunStatus.COMPLETED,
                summary = RunSummary(1, 1, 1, 0, 0, 1.0, 0.95, 50)
            ),
            trials = listOf(
                EvalTrialResult(
                    trial = EvalTrial(
                        id = trialId,
                        runId = UUID.randomUUID(),
                        taskId = task.id,
                        trialNumber = 1,
                        outcome = TrialOutcome.PASS,
                        score = 0.95,
                        durationMs = 50
                    ),
                    grades = listOf(
                        EvalGrade(
                            trialId = trialId,
                            graderType = GraderType.CODE_BASED,
                            score = 0.95,
                            passed = true,
                            assertionResults = listOf(
                                AssertionResult("assertion 1", true, "exp", "act"),
                                AssertionResult("assertion 2", false, "exp2", "act2")
                            )
                        )
                    )
                )
            )
        )

        val report = generator.generateReport(suite, listOf(task), runResult)
        val md = generator.toMarkdown(report)

        assertThat(md).contains("# Forge Eval Report")
        assertThat(md).contains("markdown-test")
        assertThat(md).contains("SYNAPSE")
        assertThat(md).contains("md-task")
        assertThat(md).contains("[x] assertion 1")
        assertThat(md).contains("[ ] assertion 2")
        assertThat(md).contains("PASS")
    }
}
