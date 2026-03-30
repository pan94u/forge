package com.forge.eval.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.eval.api.entity.*
import com.forge.eval.api.repository.*
import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.ModelAdapter
import com.forge.eval.engine.EvalEngine
import org.springframework.beans.factory.ObjectProvider
import com.forge.eval.engine.EvalRunResult
import com.forge.eval.engine.ReportGenerator
import com.forge.eval.engine.TrialOutput
import com.forge.eval.engine.lifecycle.LifecycleManager
import com.forge.eval.engine.review.ReviewTriggerRules
import com.forge.eval.engine.stats.RegressionDetector
import com.forge.eval.protocol.*
import kotlinx.coroutines.runBlocking
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
    private val reviewService: ReviewService,
    private val externalAgentCaller: ExternalAgentCaller,
    modelAdapterProvider: ObjectProvider<ModelAdapter>
) {
    private val modelAdapter: ModelAdapter? = modelAdapterProvider.ifAvailable
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
            tags = objectMapper.writeValueAsString(request.tags),
            agentEndpoint = request.agentEndpoint,
            agentConfig = request.agentConfig?.let { objectMapper.writeValueAsString(it) }
        )
        val saved = suiteRepo.save(entity)
        return toSuiteResponse(saved)
    }

    @Transactional
    fun updateSuite(suiteId: UUID, fields: Map<String, Any?>): SuiteResponse {
        val entity = suiteRepo.findById(suiteId)
            .orElseThrow { NotFoundException("Suite not found: $suiteId") }

        fields["name"]?.let { entity.name = it as String }
        fields["description"]?.let { entity.description = it as String }
        if (fields.containsKey("agentEndpoint")) {
            entity.agentEndpoint = fields["agentEndpoint"] as? String
        }
        if (fields.containsKey("agentConfig")) {
            entity.agentConfig = fields["agentConfig"]?.let { objectMapper.writeValueAsString(it) }
        }
        entity.updatedAt = Instant.now()

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

    // ── Run Listing ──────────────────────────────────────────────────

    fun getRunsForSuite(suiteId: UUID): List<RunResponse> {
        val suite = suiteRepo.findById(suiteId)
            .orElseThrow { NotFoundException("Suite not found: $suiteId") }
        val runs = runRepo.findBySuiteIdOrderByCreatedAtAsc(suiteId)
        return runs.map { buildRunResponse(it, suite.name) }
    }

    // ── Run Execution ───────────────────────────────────────────────

    fun createRun(request: CreateRunRequest, authHeader: String? = null): RunResponse {
        val suite = suiteRepo.findById(request.suiteId)
            .orElseThrow { NotFoundException("Suite not found: ${request.suiteId}") }

        val tasks = taskRepo.findBySuiteId(request.suiteId)
        if (tasks.isEmpty()) {
            throw IllegalStateException("Suite has no tasks")
        }

        val evalTasks = if (request.taskFilter != null) {
            tasks.filter { it.id in request.taskFilter!! }.map { toEvalTask(it) }
        } else {
            tasks.map { toEvalTask(it) }
        }

        // 1. Create Run entity with RUNNING status
        val runId = UUID.randomUUID()
        val now = Instant.now()
        val runEntity = EvalRunEntity(
            id = runId,
            suiteId = request.suiteId,
            status = RunStatusEnum.RUNNING,
            trialsPerTask = request.trialsPerTask,
            model = request.model,
            totalTasks = evalTasks.size,
            startedAt = now
        )
        runRepo.save(runEntity)

        // 2. Prepare output provider
        val agentEndpoint = suite.agentEndpoint
        val agentConfig: AgentEndpointConfig? = suite.agentConfig?.let {
            try { objectMapper.readValue(it, AgentEndpointConfig::class.java) } catch (_: Exception) { null }
        }
        // Inject caller's JWT into agent config headers for cross-gateway auth
        val effectiveConfig = if (!authHeader.isNullOrBlank() && agentConfig != null) {
            agentConfig.copy(headers = agentConfig.headers + ("Authorization" to authHeader))
        } else if (!authHeader.isNullOrBlank()) {
            AgentEndpointConfig(headers = mapOf("Authorization" to authHeader))
        } else {
            agentConfig
        }
        val outputProvider: (EvalTask) -> TrialOutput = if (!agentEndpoint.isNullOrBlank()) {
            { task -> externalAgentCaller.call(agentEndpoint, effectiveConfig, task) }
        } else {
            { task -> callModel(task, request.model) }
        }

        // 3. Launch async execution
        Thread {
            try {
                executeRunAsync(runId, request.suiteId, evalTasks, request.trialsPerTask, outputProvider, suite.name)
            } catch (e: Exception) {
                logger.error("Async run {} failed: {}", runId, e.message, e)
                finalizeRunOnError(runId, request.trialsPerTask)
            }
        }.apply { isDaemon = true; name = "eval-run-$runId" }.start()

        // 4. Return immediately
        return buildRunResponse(runEntity, suite.name)
    }

    private fun executeRunAsync(
        runId: UUID,
        suiteId: UUID,
        tasks: List<EvalTask>,
        trialsPerTask: Int,
        outputProvider: (EvalTask) -> TrialOutput,
        suiteName: String
    ) {
        logger.info("Async run {} started: {} tasks × {} trials", runId, tasks.size, trialsPerTask)

        val allTrialResults = mutableListOf<com.forge.eval.engine.EvalTrialResult>()

        for (task in tasks) {
            for (trialNum in 1..trialsPerTask) {
                val trialId = UUID.randomUUID()
                val startTime = System.currentTimeMillis()

                // Execute
                val trialOutput = try {
                    outputProvider(task)
                } catch (e: Exception) {
                    logger.error("Trial execution failed for task '{}': {}", task.name, e.message)
                    TrialOutput(output = "(execution error: ${e.message})", error = e.message)
                }

                val durationMs = System.currentTimeMillis() - startTime

                // Grade via CompositeGrader
                val grades = evalEngine.gradeOutput(task, trialOutput.output, trialOutput.transcript)

                // Determine outcome
                val outcome = when {
                    trialOutput.error != null -> TrialOutcome.ERROR
                    grades.isEmpty() -> TrialOutcome.PASS
                    grades.all { it.passed } -> TrialOutcome.PASS
                    grades.any { it.passed } -> TrialOutcome.PARTIAL
                    else -> TrialOutcome.FAIL
                }
                val avgScore = if (grades.isNotEmpty()) grades.map { it.score }.average() else 0.0

                val status = outcome
                logger.info("  Task '{}' trial #{}: {} (score={:.2f})", task.name, trialNum, status, avgScore)

                // Persist trial immediately
                val trialEntity = EvalTrialEntity(
                    id = trialId,
                    runId = runId,
                    taskId = task.id,
                    trialNumber = trialNum,
                    outcome = OutcomeEnum.valueOf(outcome.name),
                    score = avgScore,
                    durationMs = durationMs,
                    tokenUsage = trialOutput.tokenUsage?.let { objectMapper.writeValueAsString(it) },
                    output = trialOutput.output,
                    errorMessage = trialOutput.error
                )
                trialRepo.save(trialEntity)

                // Persist grades
                for (grade in grades) {
                    val gradeEntity = EvalGradeEntity(
                        id = grade.id,
                        trialId = trialId,
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

                // Persist transcript
                if (trialOutput.transcript != null) {
                    val transcriptEntity = EvalTranscriptEntity(
                        trialId = trialId,
                        source = TranscriptSourceEnum.valueOf(trialOutput.transcript!!.source.name),
                        turns = objectMapper.writeValueAsString(trialOutput.transcript!!.turns),
                        toolCallSummary = objectMapper.writeValueAsString(trialOutput.transcript!!.toolCallSummary),
                        metadata = objectMapper.writeValueAsString(trialOutput.transcript!!.metadata)
                    )
                    transcriptRepo.save(transcriptEntity)
                }

                // Auto-trigger human review
                val taskRunCount = trialRepo.findByTaskId(task.id).size
                val decision = reviewTriggerRules.shouldReview(grades, taskRunCount)
                if (decision.shouldReview) {
                    for (grade in grades) {
                        reviewService.createReview(
                            gradeId = grade.id,
                            trialId = trialId,
                            taskId = task.id,
                            autoScore = grade.score,
                            autoConfidence = grade.confidence,
                            reasons = decision.reasons.map { it.description }
                        )
                    }
                }

                // Collect for summary computation
                allTrialResults.add(com.forge.eval.engine.EvalTrialResult(
                    trial = EvalTrial(
                        id = trialId, runId = runId, taskId = task.id,
                        trialNumber = trialNum, outcome = outcome, score = avgScore,
                        durationMs = durationMs, tokenUsage = trialOutput.tokenUsage,
                        output = trialOutput.output, errorMessage = trialOutput.error
                    ),
                    grades = grades,
                    transcript = trialOutput.transcript
                ))
            }
        }

        // Compute final summary
        val totalTrials = allTrialResults.size
        val passedTrials = allTrialResults.count { it.trial.outcome == TrialOutcome.PASS }
        val failedTrials = allTrialResults.count { it.trial.outcome == TrialOutcome.FAIL }
        val errorTrials = allTrialResults.count { it.trial.outcome == TrialOutcome.ERROR }
        val overallPassRate = if (totalTrials > 0) passedTrials.toDouble() / totalTrials else 0.0
        val averageScore = if (allTrialResults.isNotEmpty()) allTrialResults.map { it.trial.score }.average() else 0.0
        val totalDurationMs = allTrialResults.sumOf { it.trial.durationMs }

        // Pass@k and Pass^k
        val trialsByTask = allTrialResults.groupBy { it.trial.taskId }
        val (passAtK, passPowerK) = if (trialsPerTask > 1 && trialsByTask.isNotEmpty()) {
            val taskPassAtK = trialsByTask.values.map { taskTrials ->
                val outcomes = taskTrials.map { it.trial.outcome }
                com.forge.eval.engine.stats.PassMetrics.passAtK(outcomes, trialsPerTask)
            }
            val taskPassPowerK = trialsByTask.values.map { taskTrials ->
                val outcomes = taskTrials.map { it.trial.outcome }
                com.forge.eval.engine.stats.PassMetrics.passPowerK(outcomes, trialsPerTask)
            }
            Pair(taskPassAtK.average(), taskPassPowerK.average())
        } else {
            Pair(null, null)
        }

        val summary = RunSummary(
            totalTasks = trialsByTask.size,
            totalTrials = totalTrials,
            passedTrials = passedTrials,
            failedTrials = failedTrials,
            errorTrials = errorTrials,
            overallPassRate = overallPassRate,
            averageScore = averageScore,
            totalDurationMs = totalDurationMs,
            passAtK = passAtK,
            passPowerK = passPowerK
        )

        // Update Run to COMPLETED
        val runEntity = runRepo.findById(runId).orElseThrow()
        runEntity.status = RunStatusEnum.COMPLETED
        runEntity.completedAt = Instant.now()
        runEntity.summary = objectMapper.writeValueAsString(summary)
        runRepo.save(runEntity)

        logger.info("Async run {} completed: {} trials, pass rate={:.1f}%", runId, totalTrials, overallPassRate * 100)
    }

    /**
     * 异常中断后，基于已入库的 trials 生成 summary，标记 FAILED 而非丢弃数据。
     */
    private fun finalizeRunOnError(runId: UUID, trialsPerTask: Int) {
        try {
            val runEntity = runRepo.findById(runId).orElse(null) ?: return
            val trials = trialRepo.findByRunId(runId)

            val totalTrials = trials.size
            val passedTrials = trials.count { it.outcome == OutcomeEnum.PASS }
            val failedTrials = trials.count { it.outcome == OutcomeEnum.FAIL }
            val errorTrials = trials.count { it.outcome == OutcomeEnum.ERROR }
            val overallPassRate = if (totalTrials > 0) passedTrials.toDouble() / totalTrials else 0.0
            val averageScore = if (trials.isNotEmpty()) trials.map { it.score }.average() else 0.0
            val totalDurationMs = trials.sumOf { it.durationMs }

            val trialsByTask = trials.groupBy { it.taskId }
            val (passAtK, passPowerK) = if (trialsPerTask > 1 && trialsByTask.isNotEmpty()) {
                val taskPassAtK = trialsByTask.values.map { taskTrials ->
                    val outcomes = taskTrials.map { TrialOutcome.valueOf(it.outcome.name) }
                    com.forge.eval.engine.stats.PassMetrics.passAtK(outcomes, trialsPerTask)
                }
                val taskPassPowerK = trialsByTask.values.map { taskTrials ->
                    val outcomes = taskTrials.map { TrialOutcome.valueOf(it.outcome.name) }
                    com.forge.eval.engine.stats.PassMetrics.passPowerK(outcomes, trialsPerTask)
                }
                Pair(taskPassAtK.average(), taskPassPowerK.average())
            } else {
                Pair(null, null)
            }

            val summary = RunSummary(
                totalTasks = trialsByTask.size,
                totalTrials = totalTrials,
                passedTrials = passedTrials,
                failedTrials = failedTrials,
                errorTrials = errorTrials,
                overallPassRate = overallPassRate,
                averageScore = averageScore,
                totalDurationMs = totalDurationMs,
                passAtK = passAtK,
                passPowerK = passPowerK
            )

            runEntity.status = RunStatusEnum.FAILED
            runEntity.completedAt = Instant.now()
            runEntity.summary = objectMapper.writeValueAsString(summary)
            runRepo.save(runEntity)

            logger.warn("Run {} marked FAILED with {} completed trials (summary preserved)", runId, totalTrials)
        } catch (e: Exception) {
            logger.error("Failed to finalize run {} on error: {}", runId, e.message)
        }
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
        val suite = suiteRepo.findById(request.suiteId)
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

        // Extract agent output from assistant turns
        val output = request.turns
            .filter { it.role == "assistant" }
            .joinToString("\n") { it.content }

        // Grade via CompositeGrader (Code-Based + Model-Based)
        val grades = evalEngine.gradeOutput(task, output, transcript)

        // Create a Manual Run + Trial to persist the evaluation
        val runId = UUID.randomUUID()
        val trialId = UUID.randomUUID()
        val now = Instant.now()

        // Determine outcome
        val outcome = when {
            grades.isEmpty() -> TrialOutcome.PASS
            grades.all { it.passed } -> TrialOutcome.PASS
            grades.any { it.passed } -> TrialOutcome.PARTIAL
            else -> TrialOutcome.FAIL
        }
        val avgScore = if (grades.isNotEmpty()) grades.map { it.score }.average() else 0.0

        // Persist Run (manual type)
        val runSummary = RunSummary(
            totalTasks = 1, totalTrials = 1,
            passedTrials = if (outcome == TrialOutcome.PASS) 1 else 0,
            failedTrials = if (outcome == TrialOutcome.FAIL) 1 else 0,
            errorTrials = 0,
            overallPassRate = if (outcome == TrialOutcome.PASS) 1.0 else 0.0,
            averageScore = avgScore,
            totalDurationMs = 0
        )
        val runEntity = EvalRunEntity(
            id = runId,
            suiteId = request.suiteId,
            status = RunStatusEnum.COMPLETED,
            trialsPerTask = 1,
            model = (request.metadata["model"] as? String),
            summary = objectMapper.writeValueAsString(runSummary),
            startedAt = now,
            completedAt = now
        )
        runRepo.save(runEntity)

        // Persist Trial
        val trialEntity = EvalTrialEntity(
            id = trialId,
            runId = runId,
            taskId = request.taskId,
            trialNumber = 1,
            outcome = OutcomeEnum.valueOf(outcome.name),
            score = avgScore,
            durationMs = 0,
            output = output
        )
        trialRepo.save(trialEntity)

        // Persist Transcript (linked to trial)
        val transcriptEntity = EvalTranscriptEntity(
            id = transcript.id,
            trialId = trialId,
            source = TranscriptSourceEnum.valueOf(request.source.name),
            turns = objectMapper.writeValueAsString(request.turns),
            toolCallSummary = objectMapper.writeValueAsString(toolCalls),
            metadata = objectMapper.writeValueAsString(request.metadata)
        )
        transcriptRepo.save(transcriptEntity)

        // Persist Grades
        for (grade in grades) {
            val gradeEntity = EvalGradeEntity(
                id = grade.id,
                trialId = trialId,
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

        // Auto-trigger human review
        val taskRunCount = trialRepo.findByTaskId(request.taskId).size
        val reviewDecision = reviewTriggerRules.shouldReview(grades, taskRunCount)
        if (reviewDecision.shouldReview) {
            for (grade in grades) {
                reviewService.createReview(
                    gradeId = grade.id,
                    trialId = trialId,
                    taskId = request.taskId,
                    autoScore = grade.score,
                    autoConfidence = grade.confidence,
                    reasons = reviewDecision.reasons.map { it.description }
                )
            }
        }

        return mapOf(
            "runId" to runId,
            "trialId" to trialId,
            "transcriptId" to transcript.id,
            "outcome" to outcome.name,
            "score" to avgScore,
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

    // ── Model calling ─────────────────────────────────────────────

    private fun callModel(task: EvalTask, model: String?): TrialOutput {
        if (modelAdapter == null) {
            logger.warn("No ModelAdapter available — returning structure validation output")
            return TrialOutput(output = "(no model adapter configured)")
        }

        logger.info("Calling model '{}' for task '{}'", model ?: "(default)", task.name)
        return try {
            val result = runBlocking {
                modelAdapter.complete(
                    prompt = task.prompt,
                    options = CompletionOptions(
                        model = model,
                        maxTokens = 4096,
                        temperature = 0.7,
                        systemPrompt = "You are an AI agent being evaluated. Complete the task described in the prompt."
                    )
                )
            }

            val transcript = EvalTranscript(
                source = TranscriptSource.FORGE,
                turns = listOf(
                    TranscriptTurn(role = "user", content = task.prompt),
                    TranscriptTurn(role = "assistant", content = result.content)
                ),
                metadata = mapOf("model" to result.model, "latencyMs" to result.latencyMs.toString())
            )

            TrialOutput(
                output = result.content,
                transcript = transcript,
                tokenUsage = TokenUsage(
                    inputTokens = result.usage.inputTokens,
                    outputTokens = result.usage.outputTokens
                )
            )
        } catch (e: Exception) {
            logger.error("Model call failed for task '{}': {}", task.name, e.message)
            TrialOutput(
                output = "(model call failed: ${e.message})",
                error = e.message
            )
        }
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
            totalExpectedTrials = run.totalTasks * run.trialsPerTask,
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
        val runCount = runRepo.countBySuiteId(entity.id)
        return SuiteResponse(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            platform = Platform.valueOf(entity.platform.name),
            agentType = AgentType.valueOf(entity.agentType.name),
            lifecycle = Lifecycle.valueOf(entity.lifecycle.name),
            tags = objectMapper.readValue(entity.tags),
            taskCount = taskCount.toInt(),
            runCount = runCount.toInt(),
            agentEndpoint = entity.agentEndpoint,
            agentConfig = entity.agentConfig?.let {
                try { objectMapper.readValue(it, AgentEndpointConfig::class.java) } catch (_: Exception) { null }
            },
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
