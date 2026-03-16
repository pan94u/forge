package com.forge.webide.controller

import com.forge.webide.entity.EvalRunEntity
import com.forge.webide.repository.EvalResultRepository
import com.forge.webide.repository.EvalRunRepository
import com.forge.webide.service.eval.EvalRunnerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/eval/runs")
class EvalRunController(
    private val evalRunRepository: EvalRunRepository,
    private val evalResultRepository: EvalResultRepository,
    private val evalRunnerService: EvalRunnerService
) {

    @PostMapping
    fun createRun(@RequestBody request: CreateRunRequest): ResponseEntity<EvalRunDto> {
        val runId = UUID.randomUUID().toString()
        val taskIdsJson = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request.taskIds)

        val entity = EvalRunEntity(
            id = runId,
            name = request.name,
            taskIds = taskIdsJson,
            modelProvider = request.modelProvider,
            modelName = request.modelName,
            skillProfile = request.skillProfile,
            passK = request.passK ?: 1,
            mode = request.mode ?: "PASS_AT_K",
            agentAdapter = request.agentAdapter ?: "FORGE_INTERNAL",
            status = "PENDING",
            orgId = request.orgId,
            totalTasks = request.taskIds.size
        )
        evalRunRepository.save(entity)

        evalRunnerService.startRun(runId)

        return ResponseEntity.ok(EvalRunDto.from(entity))
    }

    @GetMapping
    fun listRuns(@RequestParam(required = false) orgId: String?): ResponseEntity<List<EvalRunDto>> {
        val runs = if (!orgId.isNullOrBlank()) {
            evalRunRepository.findByOrgIdOrderByCreatedAtDesc(orgId)
        } else {
            evalRunRepository.findAllByOrderByCreatedAtDesc()
        }
        return ResponseEntity.ok(runs.map { EvalRunDto.from(it) })
    }

    @GetMapping("/{id}")
    fun getRun(@PathVariable id: String): ResponseEntity<EvalRunDto> {
        val run = evalRunRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(EvalRunDto.from(run))
    }

    @GetMapping("/{id}/results")
    fun getRunResults(@PathVariable id: String): ResponseEntity<List<EvalResultDto>> {
        if (!evalRunRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        val results = evalResultRepository.findByRunIdOrderByCreatedAtAsc(id)
        return ResponseEntity.ok(results.map { EvalResultDto.from(it) })
    }

    @PostMapping("/{id}/cancel")
    fun cancelRun(@PathVariable id: String): ResponseEntity<EvalRunDto> {
        val run = evalRunRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (run.status != "RUNNING" && run.status != "PENDING") {
            return ResponseEntity.badRequest().build()
        }
        run.status = "CANCELLED"
        run.completedAt = Instant.now()
        evalRunRepository.save(run)
        return ResponseEntity.ok(EvalRunDto.from(run))
    }

    // --- DTOs ---

    data class CreateRunRequest(
        val taskIds: List<String>,
        val name: String? = null,
        val modelProvider: String? = null,
        val modelName: String? = null,
        val skillProfile: String? = null,
        val passK: Int? = null,
        val mode: String? = null,
        val agentAdapter: String? = null,
        val orgId: String? = null
    )

    data class EvalRunDto(
        val id: String,
        val name: String?,
        val taskIds: String,
        val modelProvider: String?,
        val modelName: String?,
        val skillProfile: String?,
        val passK: Int,
        val mode: String,
        val agentAdapter: String,
        val status: String,
        val orgId: String?,
        val totalTasks: Int,
        val completedTasks: Int,
        val passCount: Int,
        val failCount: Int,
        val startedAt: Instant?,
        val completedAt: Instant?,
        val createdAt: Instant
    ) {
        companion object {
            fun from(e: EvalRunEntity) = EvalRunDto(
                id = e.id,
                name = e.name,
                taskIds = e.taskIds,
                modelProvider = e.modelProvider,
                modelName = e.modelName,
                skillProfile = e.skillProfile,
                passK = e.passK,
                mode = e.mode,
                agentAdapter = e.agentAdapter,
                status = e.status,
                orgId = e.orgId,
                totalTasks = e.totalTasks,
                completedTasks = e.completedTasks,
                passCount = e.passCount,
                failCount = e.failCount,
                startedAt = e.startedAt,
                completedAt = e.completedAt,
                createdAt = e.createdAt
            )
        }
    }

    data class EvalResultDto(
        val id: String,
        val runId: String,
        val taskId: String,
        val attemptNumber: Int,
        val status: String?,
        val totalScore: Double?,
        val codeGradePassed: Boolean?,
        val modelGradeScore: Double?,
        val durationMs: Long?,
        val errorMessage: String?,
        val createdAt: Instant
    ) {
        companion object {
            fun from(e: com.forge.webide.entity.EvalResultEntity) = EvalResultDto(
                id = e.id,
                runId = e.runId,
                taskId = e.taskId,
                attemptNumber = e.attemptNumber,
                status = e.status,
                totalScore = e.totalScore?.toDouble(),
                codeGradePassed = e.codeGradePassed,
                modelGradeScore = e.modelGradeScore?.toDouble(),
                durationMs = e.durationMs,
                errorMessage = e.errorMessage,
                createdAt = e.createdAt
            )
        }
    }
}
