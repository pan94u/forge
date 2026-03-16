package com.forge.eval.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.eval.api.entity.*
import com.forge.eval.api.repository.*
import com.forge.eval.engine.EvalEngine
import com.forge.eval.engine.EvalRunResult
import com.forge.eval.engine.ReportGenerator
import com.forge.eval.engine.TrialOutput
import com.forge.eval.engine.lifecycle.LifecycleManager
import com.forge.eval.engine.review.ReviewTriggerRules
import com.forge.eval.engine.stats.RegressionDetector
import com.forge.eval.protocol.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class EvalService(
    private val suiteRepo: EvalSuiteRepository,
    private val taskRepo: EvalTaskRepository,
    private val runRepo: EvalRunRepository,
    private val trialRepo: EvalTrialRepository,
    private val gradeRepo: EvalGradeRepository,
    private val transcriptRepo: EvalTranscriptRepository,
    private val objectMapper: ObjectMapper,
    private val evalEngine: EvalEngine,
    private val reportGenerator: ReportGenerator,
    private val reviewTriggerRules: ReviewTriggerRules,
    private val reviewService: ReviewService
) {
    private val logger = LoggerFactory.getLogger(EvalService::class.java)

    // ── Suite CRUD ──────────────────────────────────────────────────

    @Transactional
    fun createSuite(request: CreateSuiteRequest): SuiteResponse {
        val entity = EvalSuiteEntity(
            name = request.name,
            description = request.description,
            platform = PlatformEnum.valueOf(request.platform.name),
            agentType = AgentTypeEnum.valueOf(request.agentType.name),
            lifecycle = LifecycleEnum.valueOf(request.lifecycle.name),
            tags = objectMapper.writeValueAsString(request.tags)
        )
        val saved = suiteRepo.save(entity)
        return toSuiteResponse(saved)
    }

    fun listSuites(
        platform: String?,
        agentType: String?,
        page: Int,
        size: Int
    ): PageResponse<SuiteResponse> {
        val pageable = PageRequest.of(page, size)
        val result: Page<EvalSuiteEntity> = when {
            platform != null && agentType != null ->
                suiteRepo.findByPlatformAndAgentType(
                    PlatformEnum.valueOf(platform.uppercase()),
                    AgentTypeEnum.valueOf(agentType.uppercase()),
                    pageable
                )
            platform != null ->
                suiteRepo.findByPlatform(PlatformEnum.valueOf(platform.uppercase()), pageable)
            else ->
                suiteRepo.findAll(pageable)
        }

        return PageResponse(
            content = result.content.map { toSuiteResponse(it) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getSuite(suiteId: UUID): SuiteResponse {
        val entity = suiteRepo.findById(suiteId)
            .orElseThrow { NotFoundException("Suite not found: $suiteId") }
        return toSuiteResponse(entity)
    }

    // ── Task CRUD ───────────────────────────────────────────────────

    @Transactional
    fun createTask(suiteId: UUID, request: CreateTaskRequest): EvalTask {
        suiteRepo.findById(suiteId)
            .orElseThrow { NotFoundException("Suite not found: $suiteId") }

        val entity = EvalTaskEntity(
            suiteId = suiteId,
            name = request.name,
            description = request.description,
            prompt = request.prompt,
            context = objectMapper.writeValueAsString(request.context),
            referenceAnswer = request.referenceAnswer,
            graderConfigs = objectMapper.writeValueAsString(request.graderConfigs),
            difficulty = DifficultyEnum.valueOf(request.difficulty.name),
            tags = objectMapper.writeValueAsString(request.tags),
            baselinePassRate = request.baselinePassRate
        )
        val saved = taskRepo.save(entity)
        return toEvalTask(saved)
    }

    fun getTasksForSuite(suiteId: UUID): List<EvalTask> {
        return taskRepo.findBySuiteId(suiteId).map { toEvalTask(it) }
    }

    // ── Run Execution ───────────────────────────────────────────────

    @Transactional
    fun createRun(request: CreateRunRequest): RunResponse {
        val suite = suiteRepo.findById(request.suiteId)
            .orElseThrow { NotFoundException("Suite not found: ${request.suiteId}") }

        val tasks = taskRepo.findBySuiteId(request.suiteId)
        if (tasks.isEmpty()) {
            throw IllegalStateException("Suite has no tasks")
        }

        val evalTasks = tasks.map { toEvalTask(it) }
        val evalSuite = toEvalSuite(suite)

        // Execute evaluation (Phase 1: structure validation mode — no model calls)
        val runResult = evalEngine.executeRun(
            suite = evalSuite,
            tasks = if (request.taskFilter != null) {
                evalTasks.filter { it.id in request.taskFilter!! }
            } else {
                evalTasks
            },
            trialsPerTask = request.trialsPerTask
        ) { task ->
            // Phase 1: structure validation — empty output
            // Future phases will integrate actual model calls
            TrialOutput(output = "(structure validation mode — no model output)")
        }

        // Persist results
        val savedRun = persistRunResult(runResult, request)
        return buildRunResponse(savedRun, suite.name)
    }

    fun getRun(runId: UUID): RunResponse {
        val run = runRepo.findById(runId)
            .orElseThrow { NotFoundException("Run not found: $runId") }
        val suite = suiteRepo.findById(run.suiteId)
            .orElseThrow { NotFoundException("Suite not found: ${run.suiteId}") }
        return buildRunResponse(run, suite.name)
    }

    fun getRunReport(runId: UUID, format: String?): Any {
        val run = runRepo.findById(runId)
            .orElseThrow { NotFoundException("Run not found: $runId") }
        val suite = suiteRepo.findById(run.suiteId)
            .orElseThrow { NotFoundException("Suite not found: ${run.suiteId}") }
        val tasks = taskRepo.findBySuiteId(run.suiteId)

        val evalSuite = toEvalSuite(suite)
        val evalTasks = tasks.map { toEvalTask(it) }

        // Reconstruct run result from persisted data
        val trials = trialRepo.findByRunId(runId)
        val gradesByTrial = gradeRepo.findByTrialIdIn(trials.map { it.id })
            .groupBy { it.trialId }

        val trialResults = trials.map { trial ->
            com.forge.eval.engine.EvalTrialResult(
                trial = EvalTrial(
                    id = trial.id,
                    runId = trial.runId,
                    taskId = trial.taskId,
                    trialNumber = trial.trialNumber,
                    outcome = TrialOutcome.valueOf(trial.outcome.name),
                    score = trial.score,
                    durationMs = trial.durationMs
                ),
                grades = gradesByTrial[trial.id]?.map { grade ->
                    EvalGrade(
                        id = grade.id,
                        trialId = grade.trialId,
                        graderType = GraderType.valueOf(grade.graderType.name),
                        score = grade.score,
                        passed = grade.passed,
                        assertionResults = objectMapper.readValue(grade.assertionResults),
                        explanation = grade.explanation,
                        confidence = grade.confidence
                    )
                } ?: emptyList()
            )
        }

        val summary: RunSummary = if (run.summary != null) {
            objectMapper.readValue(run.summary!!)
        } else {
            RunSummary(0, 0, 0, 0, 0, 0.0, 0.0, 0)
        }

        val evalRunResult = EvalRunResult(
            run = EvalRun(
                id = run.id,
                suiteId = run.suiteId,
                status = RunStatus.valueOf(run.status.name),
                summary = summary
            ),
            trials = trialResults
        )

        val report = reportGenerator.generateReport(evalSuite, evalTasks, evalRunResult)

        return if (format == "markdown") {
            reportGenerator.toMarkdown(report)
        } else {
            report
        }
    }

    // ── Transcript ───────────────────────────────────────────────────

    @Transactional
    fun submitTranscript(request: SubmitTranscriptRequest): Map<String, Any> {
        // Verify suite and task exist
        suiteRepo.findById(request.suiteId)
            .orElseThrow { NotFoundException("Suite not found: ${request.suiteId}") }
        val taskEntity = taskRepo.findById(request.taskId)
            .orElseThrow { NotFoundException("Task not found: ${request.taskId}") }

        val task = toEvalTask(taskEntity)

        // Build transcript
        val toolCalls = request.turns.flatMap { it.toolCalls }
        val transcript = EvalTranscript(
            source = request.source,
            turns = request.turns,
            toolCallSummary = toolCalls,
            metadata = request.metadata
        )

        // Grade the transcript output
        val output = request.turns
            .filter { it.role == "assistant" }
            .joinToString("\n") { it.content }

        val grades = evalEngine.gradeOutput(task, output, transcript)

        // Persist transcript (no trial association for external transcripts)
        val transcriptEntity = EvalTranscriptEntity(
            id = transcript.id,
            source = TranscriptSourceEnum.valueOf(request.source.name),
            turns = objectMapper.writeValueAsString(request.turns),
            toolCallSummary = objectMapper.writeValueAsString(toolCalls),
            metadata = objectMapper.writeValueAsString(request.metadata)
        )
        transcriptRepo.save(transcriptEntity)

        // Note: grades for external transcripts are returned inline, not persisted
        // to eval_grades table (which requires a trial FK). This is by design —
        // external transcript evaluation is stateless and immediate.
        // For persisted grades, use POST /runs which creates proper trial records.

        return mapOf(
            "transcriptId" to transcript.id,
            "grades" to grades.map {
                mapOf(
                    "score" to it.score,
                    "passed" to it.passed,
                    "explanation" to it.explanation,
                    "assertionResults" to it.assertionResults
                )
            }
        )
    }


    fun getTranscript(transcriptId: UUID): EvalTranscript {
        val entity = transcriptRepo.findById(transcriptId)
            .orElseThrow { NotFoundException("Transcript not found: $transcriptId") }
        return EvalTranscript(
            id = entity.id,
            trialId = entity.trialId,
            source = TranscriptSource.valueOf(entity.source.name),
            turns = objectMapper.readValue(entity.turns),
            toolCallSummary = objectMapper.readValue(entity.toolCallSummary),
            metadata = objectMapper.readValue(entity.metadata),
            createdAt = entity.createdAt
        )
    }

    // ── Regression Detection ────────────────────────────────────────

    fun detectRegressions(suiteId: UUID, currentRunId: UUID, baselineRunId: UUID): Any {
        val currentTrials = trialRepo.findByRunId(currentRunId)
        val baselineTrials = trialRepo.findByRunId(baselineRunId)

        if (currentTrials.isEmpty() || baselineTrials.isEmpty()) {
            throw IllegalStateException("Both runs must have trials")
        }

        val taskNames = taskRepo.findBySuiteId(suiteId).associate { it.id to it.name }

        val currentByTask = currentTrials.groupBy { it.taskId }
            .mapValues { (_, trials) -> trials.map { TrialOutcome.valueOf(it.outcome.name) } }
        val baselineByTask = baselineTrials.groupBy { it.taskId }
            .mapValues { (_, trials) -> trials.map { TrialOutcome.valueOf(it.outcome.name) } }

        val detector = RegressionDetector()
        return detector.detectRegressions(currentByTask, baselineByTask, taskNames)
    }

    // ── Trends ──────────────────────────────────────────────────────

    fun getSuiteTrends(suiteId: UUID): TrendResponse {
        val suite = suiteRepo.findById(suiteId)
            .orElseThrow { NotFoundException("Suite not found: $suiteId") }

        val runs = runRepo.findBySuiteIdOrderByCreatedAtAsc(suiteId)

        val dataPoints = runs.mapNotNull { run ->
            val summary: RunSummary? = run.summary?.let { objectMapper.readValue(it) }
            summary?.let {
                TrendDataPoint(
                    runId = run.id,
                    timestamp = run.createdAt,
                    passRate = it.overallPassRate,
                    averageScore = it.averageScore,
                    passAtK = it.passAtK,
                    passPowerK = it.passPowerK,
                    totalTrials = it.totalTrials,
                    lifecycle = suite.lifecycle.name
                )
            }
        }

        return TrendResponse(
            suiteId = suiteId,
            suiteName = suite.name,
            dataPoints = dataPoints
        )
    }

    // ── Lifecycle ──────────────────────────────────────────────────

    fun evaluateTaskLifecycle(suiteId: UUID, taskId: UUID): LifecycleEvalResponse {
        val suite = suiteRepo.findById(suiteId)
            .orElseThrow { NotFoundException("Suite not found: $suiteId") }
        val task = taskRepo.findById(taskId)
            .orElseThrow { NotFoundException("Task not found: $taskId") }

        // Get all runs for this suite, ordered by time
        val runs = runRepo.findBySuiteIdOrderByCreatedAtAsc(suiteId)

        // Compute pass rates per run for this specific task
        val runHistory = mutableListOf<Double>()
        val passPowerKHistory = mutableListOf<Double>()

        for (run in runs) {
            val trials = trialRepo.findByRunId(run.id).filter { it.taskId == taskId }
            if (trials.isEmpty()) continue

            val passRate = trials.count { it.outcome == OutcomeEnum.PASS }.toDouble() / trials.size
            runHistory.add(passRate)

            // Pass^k: all trials pass
            val allPass = if (trials.size > 1) {
                if (trials.all { it.outcome == OutcomeEnum.PASS }) 1.0 else 0.0
            } else {
                passRate
            }
            passPowerKHistory.add(allPass)
        }

        val lifecycleManager = LifecycleManager()
        val currentLifecycle = Lifecycle.valueOf(suite.lifecycle.name)
        val decision = lifecycleManager.evaluate(currentLifecycle, runHistory, passPowerKHistory)

        return LifecycleEvalResponse(
            taskId = taskId,
            taskName = task.name,
            currentLifecycle = decision.currentLifecycle.name,
            recommendedLifecycle = decision.recommendedLifecycle.name,
            shouldTransition = decision.shouldTransition,
            reason = decision.reason,
            consecutivePassingRuns = decision.metrics.consecutivePassingRuns,
            recentPassRate = decision.metrics.recentPassRate,
            recentPassPowerK = decision.metrics.recentPassPowerK
        )
    }

    @Transactional
    fun updateTaskLifecycle(suiteId: UUID, taskId: UUID, request: UpdateLifecycleRequest): Map<String, Any> {
        val suite = suiteRepo.findById(suiteId)
            .orElseThrow { NotFoundException("Suite not found: $suiteId") }

        val oldLifecycle = suite.lifecycle.name
        suite.lifecycle = LifecycleEnum.valueOf(request.lifecycle.name)
        suite.updatedAt = Instant.now()
        suiteRepo.save(suite)

        logger.info("Lifecycle transition: suite {} ({}) {} → {} — {}",
            suiteId, suite.name, oldLifecycle, request.lifecycle.name, request.reason)

        return mapOf(
            "suiteId" to suiteId,
            "previousLifecycle" to oldLifecycle,
            "newLifecycle" to request.lifecycle.name,
            "reason" to request.reason,
            "updatedAt" to Instant.now().toString()
        )
    }

    // ── Persistence helpers ─────────────────────────────────────────

    private fun persistRunResult(result: EvalRunResult, request: CreateRunRequest): EvalRunEntity {
        val runEntity = EvalRunEntity(
            id = result.run.id,
            suiteId = request.suiteId,
            status = RunStatusEnum.COMPLETED,
            trialsPerTask = request.trialsPerTask,
            model = request.model,
            summary = objectMapper.writeValueAsString(result.run.summary),
            startedAt = result.run.startedAt,
            completedAt = result.run.completedAt
        )
        val savedRun = runRepo.save(runEntity)

        for (trialResult in result.trials) {
            val trialEntity = EvalTrialEntity(
                id = trialResult.trial.id,
                runId = result.run.id,
                taskId = trialResult.trial.taskId,
                trialNumber = trialResult.trial.trialNumber,
                outcome = OutcomeEnum.valueOf(trialResult.trial.outcome.name),
                score = trialResult.trial.score,
                durationMs = trialResult.trial.durationMs,
                tokenUsage = trialResult.trial.tokenUsage?.let { objectMapper.writeValueAsString(it) },
                output = trialResult.trial.output,
                errorMessage = trialResult.trial.errorMessage
            )
            trialRepo.save(trialEntity)

            for (grade in trialResult.grades) {
                val gradeEntity = EvalGradeEntity(
                    id = grade.id,
                    trialId = trialResult.trial.id,
                    graderType = GraderTypeEnum.valueOf(grade.graderType.name),
                    score = grade.score,
                    passed = grade.passed,
                    assertionResults = objectMapper.writeValueAsString(grade.assertionResults),
                    rubricScores = objectMapper.writeValueAsString(grade.rubricScores),
                    explanation = grade.explanation,
                    confidence = grade.confidence
                )
                gradeRepo.save(gradeEntity)
            }

            if (trialResult.transcript != null) {
                val transcriptEntity = EvalTranscriptEntity(
                    trialId = trialResult.trial.id,
                    source = TranscriptSourceEnum.valueOf(trialResult.transcript!!.source.name),
                    turns = objectMapper.writeValueAsString(trialResult.transcript!!.turns),
                    toolCallSummary = objectMapper.writeValueAsString(trialResult.transcript!!.toolCallSummary),
                    metadata = objectMapper.writeValueAsString(trialResult.transcript!!.metadata)
                )
                transcriptRepo.save(transcriptEntity)
            }

            // Auto-trigger human review based on ReviewTriggerRules
            val taskRunCount = trialRepo.findByTaskId(trialResult.trial.taskId).size
            val decision = reviewTriggerRules.shouldReview(trialResult.grades, taskRunCount)
            if (decision.shouldReview) {
                for (grade in trialResult.grades) {
                    reviewService.createReview(
                        gradeId = grade.id,
                        trialId = trialResult.trial.id,
                        taskId = trialResult.trial.taskId,
                        autoScore = grade.score,
                        autoConfidence = grade.confidence,
                        reasons = decision.reasons.map { it.description }
                    )
                }
            }
        }

        return savedRun
    }

    private fun buildRunResponse(run: EvalRunEntity, suiteName: String): RunResponse {
        val trials = trialRepo.findByRunId(run.id)
        val gradesByTrial = if (trials.isNotEmpty()) {
            gradeRepo.findByTrialIdIn(trials.map { it.id }).groupBy { it.trialId }
        } else {
            emptyMap()
        }

        val taskIds = trials.map { it.taskId }.distinct()
        val taskNames = if (taskIds.isNotEmpty()) {
            taskRepo.findAllById(taskIds).associate { it.id to it.name }
        } else {
            emptyMap()
        }

        return RunResponse(
            id = run.id,
            suiteId = run.suiteId,
            suiteName = suiteName,
            status = RunStatus.valueOf(run.status.name),
            trialsPerTask = run.trialsPerTask,
            model = run.model,
            summary = run.summary?.let { objectMapper.readValue(it) },
            trials = trials.map { trial ->
                TrialResponse(
                    id = trial.id,
                    taskId = trial.taskId,
                    taskName = taskNames[trial.taskId] ?: "unknown",
                    trialNumber = trial.trialNumber,
                    outcome = TrialOutcome.valueOf(trial.outcome.name),
                    score = trial.score,
                    durationMs = trial.durationMs,
                    grades = gradesByTrial[trial.id]?.map { grade ->
                        GradeResponse(
                            id = grade.id,
                            graderType = GraderType.valueOf(grade.graderType.name),
                            score = grade.score,
                            passed = grade.passed,
                            assertionResults = objectMapper.readValue(grade.assertionResults),
                            explanation = grade.explanation,
                            confidence = grade.confidence
                        )
                    } ?: emptyList()
                )
            },
            startedAt = run.startedAt,
            completedAt = run.completedAt,
            createdAt = run.createdAt
        )
    }

    // ── Conversion helpers ──────────────────────────────────────────

    private fun toSuiteResponse(entity: EvalSuiteEntity): SuiteResponse {
        val taskCount = taskRepo.countBySuiteId(entity.id)
        return SuiteResponse(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            platform = Platform.valueOf(entity.platform.name),
            agentType = AgentType.valueOf(entity.agentType.name),
            lifecycle = Lifecycle.valueOf(entity.lifecycle.name),
            tags = objectMapper.readValue(entity.tags),
            taskCount = taskCount.toInt(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun toEvalSuite(entity: EvalSuiteEntity): EvalSuite {
        return EvalSuite(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            platform = Platform.valueOf(entity.platform.name),
            agentType = AgentType.valueOf(entity.agentType.name),
            lifecycle = Lifecycle.valueOf(entity.lifecycle.name),
            tags = objectMapper.readValue(entity.tags)
        )
    }

    private fun toEvalTask(entity: EvalTaskEntity): EvalTask {
        return EvalTask(
            id = entity.id,
            suiteId = entity.suiteId,
            name = entity.name,
            description = entity.description,
            prompt = entity.prompt,
            context = objectMapper.readValue(entity.context),
            referenceAnswer = entity.referenceAnswer,
            graderConfigs = objectMapper.readValue(entity.graderConfigs),
            difficulty = Difficulty.valueOf(entity.difficulty.name),
            tags = objectMapper.readValue(entity.tags),
            baselinePassRate = entity.baselinePassRate,
            saturationCount = entity.saturationCount
        )
    }
}

class NotFoundException(message: String) : RuntimeException(message)
