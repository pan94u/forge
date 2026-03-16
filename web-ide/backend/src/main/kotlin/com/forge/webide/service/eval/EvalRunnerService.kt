package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.webide.entity.EvalResultEntity
import com.forge.webide.entity.EvalRunEntity
import com.forge.webide.repository.EvalResultRepository
import com.forge.webide.repository.EvalRunRepository
import com.forge.webide.repository.EvalTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class EvalRunnerService(
    private val evalRunRepository: EvalRunRepository,
    private val evalResultRepository: EvalResultRepository,
    private val evalTaskRepository: EvalTaskRepository,
    private val graderOrchestrator: GraderOrchestrator,
    private val forgeAgentAdapter: ForgeAgentAdapter,
    private val externalApiAdapter: ExternalApiAdapter,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(EvalRunnerService::class.java)

    @Async("evalExecutor")
    fun startRun(runId: String) {
        val run = evalRunRepository.findById(runId).orElse(null)
        if (run == null) {
            log.error("EvalRun not found: {}", runId)
            return
        }

        try {
            run.status = "RUNNING"
            run.startedAt = Instant.now()
            evalRunRepository.save(run)
            log.info("Starting eval run {} with mode={}, passK={}", runId, run.mode, run.passK)

            val taskIds: List<String> = objectMapper.readValue(run.taskIds)
            val tasks = taskIds.mapNotNull { id ->
                evalTaskRepository.findById(id).orElse(null).also {
                    if (it == null) log.warn("Task not found: {} in run {}", id, runId)
                }
            }

            run.totalTasks = tasks.size
            evalRunRepository.save(run)

            val adapter = resolveAdapter(run.agentAdapter)
            val config = AgentExecutionConfig(
                modelProvider = run.modelProvider,
                modelName = run.modelName,
                skillProfile = run.skillProfile
            )

            for (task in tasks) {
                try {
                    executeTaskWithRetries(run, task.id, task, adapter, config)
                } catch (e: Exception) {
                    log.error("Task {} failed in run {}: {}", task.id, runId, e.message)
                    run.failCount++
                }
                run.completedTasks++
                evalRunRepository.save(run)
            }

            run.status = "COMPLETED"
            run.completedAt = Instant.now()
            evalRunRepository.save(run)
            log.info("Eval run {} completed: total={}, pass={}, fail={}",
                runId, run.totalTasks, run.passCount, run.failCount)

        } catch (e: Exception) {
            log.error("Eval run {} failed: {}", runId, e.message, e)
            run.status = "FAILED"
            run.completedAt = Instant.now()
            evalRunRepository.save(run)
        }
    }

    private fun executeTaskWithRetries(
        run: EvalRunEntity,
        taskId: String,
        task: com.forge.webide.entity.EvalTaskEntity,
        adapter: AgentAdapter,
        config: AgentExecutionConfig
    ) {
        val k = run.passK
        val results = mutableListOf<Boolean>()

        for (attempt in 1..k) {
            val resultEntity = EvalResultEntity(
                id = UUID.randomUUID().toString(),
                runId = run.id,
                taskId = taskId,
                attemptNumber = attempt,
                status = "RUNNING"
            )
            evalResultRepository.save(resultEntity)

            try {
                val response = adapter.execute(task.input, config)

                resultEntity.transcript = objectMapper.writeValueAsString(response.transcript)
                resultEntity.durationMs = response.durationMs
                resultEntity.workspaceId = config.workspaceId

                if (!response.success) {
                    resultEntity.status = "ERROR"
                    resultEntity.errorMessage = response.errorMessage
                    evalResultRepository.save(resultEntity)
                    results.add(false)
                    continue
                }

                val gradeResult = graderOrchestrator.gradeAndPersist(task, response.output, resultEntity)
                results.add(gradeResult.passed)

            } catch (e: Exception) {
                log.error("Attempt {} for task {} in run {} failed: {}",
                    attempt, taskId, run.id, e.message)
                resultEntity.status = "ERROR"
                resultEntity.errorMessage = e.message
                evalResultRepository.save(resultEntity)
                results.add(false)
            }
        }

        val taskPassed = when (run.mode) {
            "PASS_AT_K" -> results.any { it }
            "PASS_POW_K" -> results.all { it } && results.isNotEmpty()
            else -> results.any { it }
        }

        if (taskPassed) run.passCount++ else run.failCount++
    }

    internal fun resolveAdapter(adapterName: String): AgentAdapter {
        return when (adapterName) {
            "FORGE_INTERNAL" -> forgeAgentAdapter
            "EXTERNAL_API" -> externalApiAdapter
            else -> {
                log.warn("Unknown adapter: {}, falling back to FORGE_INTERNAL", adapterName)
                forgeAgentAdapter
            }
        }
    }
}
