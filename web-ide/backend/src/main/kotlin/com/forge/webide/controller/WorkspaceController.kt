package com.forge.webide.controller

import com.forge.webide.model.*
import com.forge.webide.service.GitStatus
import com.forge.webide.service.WorkspaceRuntimeService
import com.forge.webide.service.WorkspaceService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController(
    private val workspaceService: WorkspaceService,
    private val runtimeService: WorkspaceRuntimeService
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

    // =========================================================================
    // Running services management
    // =========================================================================

    @GetMapping("/{id}/services")
    fun listServices(
        @PathVariable id: String
    ): ResponseEntity<List<ServiceInfo>> {
        val services = runtimeService.getServices(id).map { svc ->
            ServiceInfo(
                port = svc.port,
                command = svc.command,
                status = if (svc.process.isAlive) "running" else "stopped",
                startTime = svc.startTime.toString(),
                proxyUrl = "/api/workspaces/$id/proxy/${svc.port}/"
            )
        }
        return ResponseEntity.ok(services)
    }

    @DeleteMapping("/{id}/services/{port}")
    fun stopService(
        @PathVariable id: String,
        @PathVariable port: Int
    ): ResponseEntity<Void> {
        val stopped = runtimeService.stopService(id, port)
        return if (stopped) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }

    // =========================================================================
    // File preview (serves files with proper Content-Type for browser rendering)
    // =========================================================================

    @GetMapping("/{id}/preview/**")
    fun previewFile(
        @PathVariable id: String,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        // Extract file path from URL: /api/workspaces/{id}/preview/path/to/file.html
        val prefix = "/api/workspaces/$id/preview/"
        val path = request.requestURI.removePrefix(prefix)
        if (path.isBlank()) return ResponseEntity.notFound().build()

        val content = workspaceService.getFileBytes(id, path)
            ?: return ResponseEntity.notFound().build()

        val contentType = getMediaType(path)
        return ResponseEntity.ok()
            .contentType(contentType)
            .body(content)
    }

    private fun getMediaType(path: String): MediaType {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> MediaType.parseMediaType("text/html; charset=utf-8")
            "css" -> MediaType.parseMediaType("text/css; charset=utf-8")
            "js", "mjs" -> MediaType.parseMediaType("application/javascript; charset=utf-8")
            "json" -> MediaType.APPLICATION_JSON
            "png" -> MediaType.IMAGE_PNG
            "jpg", "jpeg" -> MediaType.IMAGE_JPEG
            "gif" -> MediaType.IMAGE_GIF
            "svg" -> MediaType.parseMediaType("image/svg+xml")
            "ico" -> MediaType.parseMediaType("image/x-icon")
            "txt" -> MediaType.TEXT_PLAIN
            "xml" -> MediaType.APPLICATION_XML
            "woff" -> MediaType.parseMediaType("font/woff")
            "woff2" -> MediaType.parseMediaType("font/woff2")
            "ttf" -> MediaType.parseMediaType("font/ttf")
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
    }

    // =========================================================================
    // Git operations
    // =========================================================================

    @PostMapping("/{id}/git/pull")
    fun pullWorkspace(
        @PathVariable id: String
    ): ResponseEntity<Map<String, String>> {
        val result = workspaceService.pullWorkspace(id)
        return ResponseEntity.ok(mapOf("output" to result))
    }

    @GetMapping("/{id}/git/status")
    fun getGitStatus(
        @PathVariable id: String
    ): ResponseEntity<GitStatus> {
        val status = workspaceService.getGitStatus(id)
        return ResponseEntity.ok(status)
    }
}
