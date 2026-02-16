package com.forge.webide.controller

import com.forge.webide.model.*
import com.forge.webide.service.WorkspaceService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController(
    private val workspaceService: WorkspaceService
) {

    @PostMapping
    fun createWorkspace(
        @RequestBody request: CreateWorkspaceRequest,
        principal: Principal?
    ): ResponseEntity<Workspace> {
        val userId = principal?.name ?: "anonymous"
        val workspace = workspaceService.createWorkspace(request, userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(workspace)
    }

    @GetMapping
    fun listWorkspaces(principal: Principal?): ResponseEntity<List<Workspace>> {
        val userId = principal?.name ?: "anonymous"
        val workspaces = workspaceService.listWorkspaces(userId)
        return ResponseEntity.ok(workspaces)
    }

    @GetMapping("/{id}")
    fun getWorkspace(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Workspace> {
        val workspace = workspaceService.getWorkspace(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workspace)
    }

    @DeleteMapping("/{id}")
    fun deleteWorkspace(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Void> {
        val userId = principal?.name ?: "anonymous"
        workspaceService.deleteWorkspace(id, userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/activate")
    fun activateWorkspace(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Workspace> {
        val workspace = workspaceService.activateWorkspace(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workspace)
    }

    @PostMapping("/{id}/suspend")
    fun suspendWorkspace(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Workspace> {
        val workspace = workspaceService.suspendWorkspace(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workspace)
    }

    @GetMapping("/{id}/files")
    fun getFileTree(
        @PathVariable id: String
    ): ResponseEntity<List<FileNode>> {
        val files = workspaceService.getFileTree(id)
        return ResponseEntity.ok(files)
    }

    @GetMapping("/{id}/files/content")
    fun getFileContent(
        @PathVariable id: String,
        @RequestParam path: String
    ): ResponseEntity<String> {
        val content = workspaceService.getFileContent(id, path)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(content)
    }

    @PutMapping("/{id}/files/content")
    fun saveFileContent(
        @PathVariable id: String,
        @RequestBody request: FileContentRequest
    ): ResponseEntity<Void> {
        workspaceService.saveFileContent(id, request.path, request.content)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{id}/files")
    fun createFile(
        @PathVariable id: String,
        @RequestBody request: FileContentRequest
    ): ResponseEntity<Void> {
        workspaceService.createFile(id, request.path, request.content)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{id}/files")
    fun deleteFile(
        @PathVariable id: String,
        @RequestParam path: String
    ): ResponseEntity<Void> {
        workspaceService.deleteFile(id, path)
        return ResponseEntity.noContent().build()
    }
}
