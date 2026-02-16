package com.forge.webide.controller

import com.forge.webide.model.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/workflows")
class WorkflowController {

    private val workflows = ConcurrentHashMap<String, Workflow>()
    private val executionResults = ConcurrentHashMap<String, WorkflowExecutionResult>()

    @PostMapping
    fun createWorkflow(
        @RequestBody request: CreateWorkflowRequest,
        principal: Principal?
    ): ResponseEntity<Workflow> {
        val userId = principal?.name ?: "anonymous"
        val workflow = Workflow(
            name = request.name,
            description = request.description ?: "",
            nodes = request.nodes ?: emptyList(),
            edges = request.edges ?: emptyList(),
            owner = userId
        )
        workflows[workflow.id] = workflow
        return ResponseEntity.status(HttpStatus.CREATED).body(workflow)
    }

    @GetMapping
    fun listWorkflows(principal: Principal?): ResponseEntity<List<Workflow>> {
        val userId = principal?.name ?: "anonymous"
        val userWorkflows = workflows.values
            .filter { it.owner == userId || it.owner.isEmpty() }
            .sortedByDescending { it.updatedAt }
        return ResponseEntity.ok(userWorkflows)
    }

    @GetMapping("/{id}")
    fun getWorkflow(@PathVariable id: String): ResponseEntity<Workflow> {
        val workflow = workflows[id]
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflow)
    }

    @PutMapping("/{id}")
    fun updateWorkflow(
        @PathVariable id: String,
        @RequestBody request: UpdateWorkflowRequest
    ): ResponseEntity<Workflow> {
        val existing = workflows[id]
            ?: return ResponseEntity.notFound().build()

        val updated = existing.copy(
            name = request.name ?: existing.name,
            description = request.description ?: existing.description,
            nodes = request.nodes ?: existing.nodes,
            edges = request.edges ?: existing.edges,
            updatedAt = Instant.now()
        )
        workflows[id] = updated
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{id}")
    fun deleteWorkflow(@PathVariable id: String): ResponseEntity<Void> {
        workflows.remove(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/run")
    fun runWorkflow(
        @PathVariable id: String
    ): ResponseEntity<WorkflowExecutionResult> {
        val workflow = workflows[id]
            ?: return ResponseEntity.notFound().build()

        val executionId = UUID.randomUUID().toString()

        // Execute workflow steps sequentially
        val steps = workflow.nodes.map { node ->
            val startTime = System.currentTimeMillis()
            try {
                // Simulate step execution
                Thread.sleep(100)

                StepResult(
                    nodeId = node.id,
                    status = "success",
                    output = "Step '${node.data["label"] ?: node.type}' completed successfully",
                    duration = System.currentTimeMillis() - startTime
                )
            } catch (e: Exception) {
                StepResult(
                    nodeId = node.id,
                    status = "error",
                    error = e.message,
                    duration = System.currentTimeMillis() - startTime
                )
            }
        }

        val hasError = steps.any { it.status == "error" }
        val result = WorkflowExecutionResult(
            workflowId = id,
            status = if (hasError) "error" else "success",
            steps = steps,
            completedAt = Instant.now()
        )

        executionResults[executionId] = result
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}/runs")
    fun listRuns(@PathVariable id: String): ResponseEntity<List<WorkflowExecutionResult>> {
        val runs = executionResults.values
            .filter { it.workflowId == id }
            .sortedByDescending { it.startedAt }
        return ResponseEntity.ok(runs)
    }
}
