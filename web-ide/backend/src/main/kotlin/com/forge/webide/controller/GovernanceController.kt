package com.forge.webide.controller

import com.forge.webide.entity.GovernanceSnapshotEntity
import com.forge.webide.repository.GovernanceSnapshotRepository
import com.forge.webide.service.RbacHelper
import com.forge.webide.service.governance.*
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/governance")
class GovernanceController(
    private val budgetService: BudgetGovernanceService,
    private val teamService: TeamGovernanceService,
    private val securityService: SecurityGovernanceService,
    private val knowledgeService: KnowledgeGovernanceService,
    private val processMiningService: ProcessMiningService,
    private val snapshotRepo: GovernanceSnapshotRepository,
    private val rbacHelper: RbacHelper
) {

    @GetMapping("/{orgId}/budget")
    fun getBudget(
        @PathVariable orgId: String,
        @RequestParam(defaultValue = "30") days: Int,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<BudgetSummary> {
        rbacHelper.requireOrgAdmin(jwt, orgId)
        return ResponseEntity.ok(budgetService.getOrgCostSummary(orgId, days))
    }

    @GetMapping("/{orgId}/team")
    fun getTeam(
        @PathVariable orgId: String,
        @RequestParam(defaultValue = "30") days: Int,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<TeamActivity> {
        rbacHelper.requireOrgAdmin(jwt, orgId)
        return ResponseEntity.ok(teamService.getTeamActivity(orgId, days))
    }

    @GetMapping("/{orgId}/security")
    fun getSecurity(
        @PathVariable orgId: String,
        @RequestParam(defaultValue = "30") days: Int,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<SecurityPosture> {
        rbacHelper.requireOrgAdmin(jwt, orgId)
        return ResponseEntity.ok(securityService.getSecurityPosture(orgId, days))
    }

    @GetMapping("/{orgId}/data")
    fun getKnowledge(
        @PathVariable orgId: String,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<KnowledgeHealth> {
        rbacHelper.requireOrgAdmin(jwt, orgId)
        return ResponseEntity.ok(knowledgeService.getKnowledgeHealth(orgId))
    }

    @GetMapping("/{orgId}/process")
    fun getProcessFlows(
        @PathVariable orgId: String,
        @RequestParam(required = false) workspaceId: String? = null,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<List<ProcessFlowDto>> {
        rbacHelper.requireOrgAdmin(jwt, orgId)
        return ResponseEntity.ok(processMiningService.getProcessFlows(orgId, workspaceId))
    }

    @GetMapping("/{orgId}/snapshot")
    fun getSnapshot(
        @PathVariable orgId: String,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<List<GovernanceSnapshotEntity>> {
        rbacHelper.requireOrgAdmin(jwt, orgId)
        val snapshots = snapshotRepo.findByOrgIdOrderByCreatedAtDesc(orgId)
        return ResponseEntity.ok(snapshots)
    }

    @PostMapping("/{orgId}/snapshot")
    fun createSnapshot(
        @PathVariable orgId: String,
        @AuthenticationPrincipal jwt: Jwt? = null
    ): ResponseEntity<Map<String, String>> {
        rbacHelper.requireOrgAdmin(jwt, orgId)

        val now = Instant.now()
        val periodStart = now.minus(30, ChronoUnit.DAYS)
        val domains = listOf("budget", "team", "security", "data", "process")

        val created = mutableListOf<String>()
        for (domain in domains) {
            val snapshotData = generateSnapshotData(orgId, domain)
            val snapshot = GovernanceSnapshotEntity(
                id = UUID.randomUUID().toString(),
                orgId = orgId,
                domain = domain,
                snapshotData = snapshotData,
                periodStart = periodStart,
                periodEnd = now
            )
            snapshotRepo.save(snapshot)
            created.add(domain)
        }

        return ResponseEntity.ok(mapOf(
            "status" to "created",
            "domains" to created.joinToString(","),
            "snapshotCount" to created.size.toString()
        ))
    }

    private fun generateSnapshotData(orgId: String, domain: String): String {
        return try {
            when (domain) {
                "budget" -> com.google.gson.Gson().toJson(budgetService.getOrgCostSummary(orgId, 30))
                "team" -> com.google.gson.Gson().toJson(teamService.getTeamActivity(orgId, 30))
                "security" -> com.google.gson.Gson().toJson(securityService.getSecurityPosture(orgId, 30))
                "data" -> com.google.gson.Gson().toJson(knowledgeService.getKnowledgeHealth(orgId))
                "process" -> com.google.gson.Gson().toJson(processMiningService.getProcessFlows(orgId))
                else -> "{}"
            }
        } catch (t: Throwable) {
            "{\"error\": \"${t.message}\"}"
        }
    }
}
