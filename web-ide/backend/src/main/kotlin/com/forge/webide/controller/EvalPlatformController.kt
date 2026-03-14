package com.forge.webide.controller

import com.forge.webide.service.RbacHelper
import com.forge.webide.service.eval.EvalTaskService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/eval")
class EvalPlatformController(
    private val evalTaskService: EvalTaskService,
    private val rbacHelper: RbacHelper
) {

    @GetMapping("/tasks")
    fun listTasks(
        @RequestParam(required = false) orgId: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) difficulty: String?,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<List<EvalTaskService.EvalTaskDto>> {
        if (orgId != null) rbacHelper.requireOrgAdmin(jwt, orgId)
        val tasks = evalTaskService.listTasks(orgId, type, difficulty)
        return ResponseEntity.ok(tasks)
    }

    @GetMapping("/tasks/{id}")
    fun getTask(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<EvalTaskService.EvalTaskDto> {
        val task = evalTaskService.getTask(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(task)
    }

    @PostMapping("/tasks")
    fun createTask(
        @RequestBody dto: EvalTaskService.EvalTaskDto,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<EvalTaskService.EvalTaskDto> {
        if (dto.orgId != null) rbacHelper.requireOrgAdmin(jwt, dto.orgId)
        val created = evalTaskService.createTask(dto)
        return ResponseEntity.ok(created)
    }

    @PutMapping("/tasks/{id}")
    fun updateTask(
        @PathVariable id: String,
        @RequestBody dto: EvalTaskService.EvalTaskDto,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<EvalTaskService.EvalTaskDto> {
        val existing = evalTaskService.getTask(id) ?: return ResponseEntity.notFound().build()
        if (existing.orgId != null) rbacHelper.requireOrgAdmin(jwt, existing.orgId)
        val updated = evalTaskService.updateTask(id, dto) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/tasks/{id}")
    fun deleteTask(
        @PathVariable id: String,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<Map<String, Boolean>> {
        val existing = evalTaskService.getTask(id) ?: return ResponseEntity.notFound().build()
        if (existing.orgId != null) rbacHelper.requireOrgAdmin(jwt, existing.orgId)
        val deleted = evalTaskService.deleteTask(id)
        return ResponseEntity.ok(mapOf("deleted" to deleted))
    }

    @PostMapping("/tasks/import-yaml")
    fun importYaml(
        @RequestBody request: YamlImportRequest,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<EvalTaskService.ImportResult> {
        if (request.orgId != null) rbacHelper.requireOrgAdmin(jwt, request.orgId)
        val result = if (request.yamlContents.size == 1) {
            evalTaskService.importFromYaml(request.yamlContents[0], request.orgId)
        } else {
            evalTaskService.bulkImportFromDirectory(request.yamlContents, request.orgId)
        }
        return ResponseEntity.ok(result)
    }

    data class YamlImportRequest(
        val yamlContents: List<String>,
        val orgId: String? = null
    )
}
