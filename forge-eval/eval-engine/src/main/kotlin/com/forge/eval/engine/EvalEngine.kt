package com.forge.eval.engine

import com.forge.eval.engine.grader.CodeBasedGrader
import com.forge.eval.protocol.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Core evaluation orchestrator.
 *
 * Takes an EvalRun definition, executes trials against tasks in the suite,
 * applies graders, and produces results. In Phase 1, only CodeBasedGrader
 * is supported and model calls are handled externally (or via passthrough).
 */
class EvalEngine(
    private val codeGrader: CodeBasedGrader = CodeBasedGrader()
) {

    private val logger = LoggerFactory.getLogger(EvalEngine::class.java)

    /**
     * Execute a complete evaluation run.
     *
     * @param suite The evaluation suite
     * @param tasks The tasks to evaluate
     * @param trialsPerTask Number of trials per task
     * @param outputProvider Function that produces output for a given task (e.g., model call)
     * @return Completed EvalRun with summary
     */
    fun executeRun(
        suite: EvalSuite,
        tasks: List<EvalTask>,
        trialsPerTask: Int = 1,
        outputProvider: (EvalTask) -> TrialOutput
    ): EvalRunResult {
        val runId = UUID.randomUUID()
        logger.info("Starting eval run {} for suite '{}' ({} tasks, {} trials/task)",
            runId, suite.name, tasks.size, trialsPerTask)

        val startTime = Instant.now()
        val allTrials = mutableListOf<EvalTrialResult>()

        for (task in tasks) {
            for (trialNum in 1..trialsPerTask) {
                val trialResult = executeTrial(runId, task, trialNum, outputProvider)
                allTrials.add(trialResult)

                val status = trialResult.trial.outcome
                logger.info("  Task '{}' trial #{}: {} (score={:.2f})",
                    task.name, trialNum, status, trialResult.grades.firstOrNull()?.score ?: 0.0)
            }
        }

        val completedAt = Instant.now()
        val summary = buildSummary(allTrials, trialsPerTask)

        logger.info("Run {} completed: {} trials, pass rate={:.1f}%",
            runId, allTrials.size, summary.overallPassRate * 100)

        return EvalRunResult(
            run = EvalRun(
                id = runId,
                suiteId = suite.id,
                status = RunStatus.COMPLETED,
                trialsPerTask = trialsPerTask,
                startedAt = startTime,
                completedAt = completedAt,
                summary = summary
            ),
            trials = allTrials
        )
    }

    /**
     * Grade a single output against a task's grader configs.
     * Useful for external transcript grading (Synapse/App integration).
     */
    fun gradeOutput(
        task: EvalTask,
        output: String,
        transcript: EvalTranscript? = null
    ): List<EvalGrade> {
        val trialId = UUID.randomUUID()
        return task.graderConfigs
            .filter { it.type == GraderType.CODE_BASED }
            .map { config ->
                codeGrader.grade(trialId, output, config.assertions, transcript)
            }
    }

    private fun executeTrial(
        runId: UUID,
        task: EvalTask,
        trialNumber: Int,
        outputProvider: (EvalTask) -> TrialOutput
    ): EvalTrialResult {
        val trialId = UUID.randomUUID()
        val startTime = System.currentTimeMillis()

        val trialOutput = try {
            outputProvider(task)
        } catch (e: Exception) {
            logger.error("Trial execution failed for task '{}': {}", task.name, e.message)
            TrialOutput(
                output = "(execution error: ${e.message})",
                error = e.message
            )
        }

        val durationMs = System.currentTimeMillis() - startTime

        // Apply code-based graders
        val grades = task.graderConfigs
            .filter { it.type == GraderType.CODE_BASED }
            .map { config ->
                codeGrader.grade(trialId, trialOutput.output, config.assertions, trialOutput.transcript)
            }

        // Determine outcome from grades
        val outcome = when {
            trialOutput.error != null -> TrialOutcome.ERROR
            grades.isEmpty() -> TrialOutcome.PASS
            grades.all { it.passed } -> TrialOutcome.PASS
            grades.any { it.passed } -> TrialOutcome.PARTIAL
            else -> TrialOutcome.FAIL
        }

        val avgScore = if (grades.isNotEmpty()) grades.map { it.score }.average() else 0.0

        return EvalTrialResult(
            trial = EvalTrial(
                id = trialId,
                runId = runId,
                taskId = task.id,
                trialNumber = trialNumber,
                outcome = outcome,
                score = avgScore,
                durationMs = durationMs,
                tokenUsage = trialOutput.tokenUsage,
                output = trialOutput.output,
                errorMessage = trialOutput.error
            ),
            grades = grades,
            transcript = trialOutput.transcript
        )
    }

    private fun buildSummary(trials: List<EvalTrialResult>, trialsPerTask: Int): RunSummary {
        val taskIds = trials.map { it.trial.taskId }.distinct()
        val totalTrials = trials.size
        val passedTrials = trials.count { it.trial.outcome == TrialOutcome.PASS }
        val failedTrials = trials.count { it.trial.outcome == TrialOutcome.FAIL }
        val errorTrials = trials.count { it.trial.outcome == TrialOutcome.ERROR }
        val overallPassRate = if (totalTrials > 0) passedTrials.toDouble() / totalTrials else 0.0
        val averageScore = if (trials.isNotEmpty()) trials.map { it.trial.score }.average() else 0.0
        val totalDurationMs = trials.sumOf { it.trial.durationMs }

        return RunSummary(
            totalTasks = taskIds.size,
            totalTrials = totalTrials,
            passedTrials = passedTrials,
            failedTrials = failedTrials,
            errorTrials = errorTrials,
            overallPassRate = overallPassRate,
            averageScore = averageScore,
            totalDurationMs = totalDurationMs
        )
    }
}

/** Output produced by running a task (model call or passthrough) */
data class TrialOutput(
    val output: String,
    val transcript: EvalTranscript? = null,
    val tokenUsage: TokenUsage? = null,
    val error: String? = null
)

/** Complete result for a single trial including grades and transcript */
data class EvalTrialResult(
    val trial: EvalTrial,
    val grades: List<EvalGrade>,
    val transcript: EvalTranscript? = null
)

/** Complete result for a run */
data class EvalRunResult(
    val run: EvalRun,
    val trials: List<EvalTrialResult>
)
