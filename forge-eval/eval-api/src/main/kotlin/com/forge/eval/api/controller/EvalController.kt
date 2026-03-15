package com.forge.eval.api.controller

import com.forge.eval.api.service.EvalService
import com.forge.eval.api.service.NotFoundException
import com.forge.eval.protocol.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/eval/v1")
class EvalController(
    private val evalService: EvalService
) {

    // ── Suite endpoints ─────────────────────────────────────────────

    @PostMapping("/suites")
    fun createSuite(@RequestBody request: CreateSuiteRequest): ResponseEntity<SuiteResponse> {
        val suite = evalService.createSuite(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(suite)
    }

    @GetMapping("/suites")
    fun listSuites(
        @RequestParam(required = false) platform: String?,
        @RequestParam(required = false) agentType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<SuiteResponse> {
        return evalService.listSuites(platform, agentType, page, size)
    }

    @GetMapping("/suites/{suiteId}")
    fun getSuite(@PathVariable suiteId: UUID): SuiteResponse {
        return evalService.getSuite(suiteId)
    }

    // ── Task endpoints ──────────────────────────────────────────────

    @PostMapping("/suites/{suiteId}/tasks")
    fun createTask(
        @PathVariable suiteId: UUID,
        @RequestBody request: CreateTaskRequest
    ): ResponseEntity<EvalTask> {
        val task = evalService.createTask(suiteId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(task)
    }

    @GetMapping("/suites/{suiteId}/tasks")
    fun listTasks(@PathVariable suiteId: UUID): List<EvalTask> {
        return evalService.getTasksForSuite(suiteId)
    }

    // ── Run endpoints ───────────────────────────────────────────────

    @PostMapping("/runs")
    fun createRun(@RequestBody request: CreateRunRequest): ResponseEntity<RunResponse> {
        val run = evalService.createRun(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(run)
    }

    @GetMapping("/runs/{runId}")
    fun getRun(@PathVariable runId: UUID): RunResponse {
        return evalService.getRun(runId)
    }

    @GetMapping("/runs/{runId}/report")
    fun getRunReport(
        @PathVariable runId: UUID,
        @RequestParam(defaultValue = "json") format: String
    ): Any {
        return evalService.getRunReport(runId, format)
    }

    // ── Transcript endpoints ────────────────────────────────────────

    @PostMapping("/transcripts")
    fun submitTranscript(@RequestBody request: SubmitTranscriptRequest): ResponseEntity<Map<String, Any>> {
        val result = evalService.submitTranscript(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @GetMapping("/transcripts/{transcriptId}")
    fun getTranscript(@PathVariable transcriptId: UUID): EvalTranscript {
        return evalService.getTranscript(transcriptId)
    }

    // ── Regression endpoints ────────────────────────────────────────

    @GetMapping("/regressions")
    fun detectRegressions(
        @RequestParam suiteId: UUID,
        @RequestParam currentRunId: UUID,
        @RequestParam baselineRunId: UUID
    ): Any {
        return evalService.detectRegressions(suiteId, currentRunId, baselineRunId)
    }

    // ── Error handling ──────────────────────────────────────────────

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (e.message ?: "Not found")))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleBadState(e: IllegalStateException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (e.message ?: "Bad request")))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (e.message ?: "Invalid argument")))
    }
}
