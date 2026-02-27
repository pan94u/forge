package com.forge.webide.controller

import com.forge.webide.model.*
import com.forge.webide.service.OrgConfigService
import com.forge.webide.service.OrganizationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val orgService: OrganizationService,
    private val configService: OrgConfigService
) {

    // =========================================================================
    // Organization CRUD
    // =========================================================================

    @GetMapping("/orgs")
    fun listOrgs(): ResponseEntity<List<Organization>> =
        ResponseEntity.ok(orgService.listOrgs())

    @PostMapping("/orgs")
    fun createOrg(@RequestBody req: CreateOrgRequest): ResponseEntity<Any> {
        return try {
            val org = orgService.createOrg(req)
            ResponseEntity.status(HttpStatus.CREATED).body(org)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/orgs/{orgId}")
    fun getOrg(@PathVariable orgId: String): ResponseEntity<Organization> {
        val org = orgService.getOrg(orgId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(org)
    }

    @PutMapping("/orgs/{orgId}")
    fun updateOrg(
        @PathVariable orgId: String,
        @RequestBody req: UpdateOrgRequest
    ): ResponseEntity<Organization> {
        val org = orgService.updateOrg(orgId, req) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(org)
    }

    @DeleteMapping("/orgs/{orgId}")
    fun deleteOrg(@PathVariable orgId: String): ResponseEntity<Void> {
        val deleted = orgService.deleteOrg(orgId)
        return if (deleted) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }

    // =========================================================================
    // Member Management
    // =========================================================================

    @GetMapping("/orgs/{orgId}/members")
    fun listMembers(@PathVariable orgId: String): ResponseEntity<List<OrgMember>> =
        ResponseEntity.ok(orgService.listMembers(orgId))

    @PostMapping("/orgs/{orgId}/members")
    fun addMember(
        @PathVariable orgId: String,
        @RequestBody req: AddMemberRequest
    ): ResponseEntity<OrgMember> {
        val member = orgService.addMember(orgId, req)
        return ResponseEntity.status(HttpStatus.CREATED).body(member)
    }

    @DeleteMapping("/orgs/{orgId}/members/{userId}")
    fun removeMember(
        @PathVariable orgId: String,
        @PathVariable userId: String
    ): ResponseEntity<Void> {
        val removed = orgService.removeMember(orgId, userId)
        return if (removed) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }

    // =========================================================================
    // Workspace Binding
    // =========================================================================

    @GetMapping("/orgs/{orgId}/workspaces")
    fun listWorkspaces(@PathVariable orgId: String): ResponseEntity<List<Workspace>> =
        ResponseEntity.ok(orgService.listWorkspaces(orgId))

    @PostMapping("/orgs/{orgId}/workspaces/{wsId}/bind")
    fun bindWorkspace(
        @PathVariable orgId: String,
        @PathVariable wsId: String
    ): ResponseEntity<Map<String, Any>> {
        val success = orgService.bindWorkspace(orgId, wsId)
        return if (success) ResponseEntity.ok(mapOf("success" to true))
        else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/orgs/{orgId}/workspaces/{wsId}/bind")
    fun unbindWorkspace(
        @PathVariable orgId: String,
        @PathVariable wsId: String
    ): ResponseEntity<Void> {
        val success = orgService.unbindWorkspace(wsId)
        return if (success) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }

    // =========================================================================
    // Model Configs
    // =========================================================================

    @GetMapping("/orgs/{orgId}/model-configs")
    fun listModelConfigs(@PathVariable orgId: String): ResponseEntity<List<OrgModelConfig>> =
        ResponseEntity.ok(configService.listModelConfigs(orgId))

    @PutMapping("/orgs/{orgId}/model-configs/{provider}")
    fun upsertModelConfig(
        @PathVariable orgId: String,
        @PathVariable provider: String,
        @RequestBody req: UpsertModelConfigRequest
    ): ResponseEntity<OrgModelConfig> {
        val config = configService.upsertModelConfig(orgId, provider, req)
        return ResponseEntity.ok(config)
    }

    // =========================================================================
    // DB Connections
    // =========================================================================

    @GetMapping("/orgs/{orgId}/db-connections")
    fun listDbConnections(@PathVariable orgId: String): ResponseEntity<List<OrgDbConnection>> =
        ResponseEntity.ok(configService.listDbConnections(orgId))

    @PostMapping("/orgs/{orgId}/db-connections")
    fun createDbConnection(
        @PathVariable orgId: String,
        @RequestBody req: CreateDbConnectionRequest
    ): ResponseEntity<OrgDbConnection> {
        val conn = configService.createDbConnection(orgId, req)
        return ResponseEntity.status(HttpStatus.CREATED).body(conn)
    }

    @DeleteMapping("/orgs/{orgId}/db-connections/{id}")
    fun deleteDbConnection(
        @PathVariable orgId: String,
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val deleted = configService.deleteDbConnection(orgId, id)
        return if (deleted) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }

    @PostMapping("/orgs/{orgId}/db-connections/{id}/test")
    fun testDbConnection(
        @PathVariable orgId: String,
        @PathVariable id: String
    ): ResponseEntity<Map<String, Any>> {
        val result = configService.testDbConnection(orgId, id)
        return ResponseEntity.ok(result)
    }

    // =========================================================================
    // Env Configs
    // =========================================================================

    @GetMapping("/orgs/{orgId}/env-configs")
    fun listEnvConfigs(
        @PathVariable orgId: String,
        @RequestParam(required = false) category: String?
    ): ResponseEntity<List<OrgEnvConfig>> =
        ResponseEntity.ok(configService.listEnvConfigs(orgId, category))

    @PutMapping("/orgs/{orgId}/env-configs/{cat}/{key}")
    fun upsertEnvConfig(
        @PathVariable orgId: String,
        @PathVariable cat: String,
        @PathVariable key: String,
        @RequestBody req: UpsertEnvConfigRequest
    ): ResponseEntity<OrgEnvConfig> {
        val config = configService.upsertEnvConfig(orgId, cat, req.copy(configKey = key))
        return ResponseEntity.ok(config)
    }

    @DeleteMapping("/orgs/{orgId}/env-configs/{cat}/{key}")
    fun deleteEnvConfig(
        @PathVariable orgId: String,
        @PathVariable cat: String,
        @PathVariable key: String
    ): ResponseEntity<Void> {
        val deleted = configService.deleteEnvConfig(orgId, cat, key)
        return if (deleted) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }
}
