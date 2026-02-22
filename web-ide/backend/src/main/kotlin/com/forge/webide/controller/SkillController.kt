package com.forge.webide.controller

import com.forge.webide.service.skill.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/skills")
class SkillController(
    private val skillManagementService: SkillManagementService,
    private val skillAnalyticsService: SkillAnalyticsService
) {

    /**
     * GET /api/skills — list all skills with metadata and enabled state.
     */
    @GetMapping
    fun listSkills(
        @RequestParam(required = false) workspaceId: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) category: String?
    ): ResponseEntity<List<SkillView>> {
        val skillScope = scope?.let {
            try { SkillScope.valueOf(it.uppercase()) } catch (_: IllegalArgumentException) { null }
        }
        val skillCategory = category?.let {
            try { SkillCategory.valueOf(it.uppercase()) } catch (_: IllegalArgumentException) { null }
        }
        val skills = skillManagementService.listSkills(
            workspaceId = workspaceId,
            scope = skillScope,
            category = skillCategory
        )
        return ResponseEntity.ok(skills)
    }

    /**
     * GET /api/skills/{name} — get skill details.
     */
    @GetMapping("/{name}")
    fun getSkill(
        @PathVariable name: String,
        @RequestParam(required = false) workspaceId: String?
    ): ResponseEntity<SkillDetailView> {
        val skill = skillManagementService.getSkill(name, workspaceId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(skill)
    }

    /**
     * GET /api/skills/{name}/content/{*path} — read a skill's sub-file content.
     */
    @GetMapping("/{name}/content/**")
    fun readSkillContent(
        @PathVariable name: String,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        val prefix = "/api/skills/$name/content/"
        val subPath = request.requestURI.removePrefix(prefix)
        if (subPath.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Sub-path is required"))
        }
        val content = skillManagementService.readSkillContent(name, subPath)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("path" to subPath, "content" to content))
    }

    /**
     * POST /api/skills — create a custom skill.
     */
    @PostMapping
    fun createSkill(
        @RequestParam workspaceId: String,
        @RequestBody request: CreateSkillRequest
    ): ResponseEntity<SkillView> {
        val skill = skillManagementService.createCustomSkill(workspaceId, request)
            ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(skill)
    }

    /**
     * PUT /api/skills/{name} — update a custom skill.
     */
    @PutMapping("/{name}")
    fun updateSkill(
        @PathVariable name: String,
        @RequestParam workspaceId: String,
        @RequestBody request: UpdateSkillRequest
    ): ResponseEntity<Void> {
        val success = skillManagementService.updateCustomSkill(workspaceId, name, request)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.badRequest().build()
    }

    /**
     * DELETE /api/skills/{name} — delete a skill (PLATFORM skills cannot be deleted).
     */
    @DeleteMapping("/{name}")
    fun deleteSkill(
        @PathVariable name: String,
        @RequestParam workspaceId: String
    ): ResponseEntity<Void> {
        val success = skillManagementService.deleteCustomSkill(workspaceId, name)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.badRequest().build()
    }

    /**
     * POST /api/skills/{name}/enable — enable a skill for a workspace.
     */
    @PostMapping("/{name}/enable")
    fun enableSkill(
        @PathVariable name: String,
        @RequestParam workspaceId: String
    ): ResponseEntity<Void> {
        val success = skillManagementService.enableSkill(workspaceId, name)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.notFound().build()
    }

    /**
     * POST /api/skills/{name}/disable — disable a skill for a workspace.
     */
    @PostMapping("/{name}/disable")
    fun disableSkill(
        @PathVariable name: String,
        @RequestParam workspaceId: String
    ): ResponseEntity<Void> {
        val success = skillManagementService.disableSkill(workspaceId, name)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.notFound().build()
    }

    /**
     * POST /api/skills/{name}/scripts/{script} — execute a skill script.
     */
    @PostMapping("/{name}/scripts/**")
    fun runScript(
        @PathVariable name: String,
        request: HttpServletRequest,
        @RequestBody(required = false) body: RunScriptRequest?
    ): ResponseEntity<ScriptResultView> {
        val prefix = "/api/skills/$name/scripts/"
        val scriptPath = request.requestURI.removePrefix(prefix)
        if (scriptPath.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val result = skillManagementService.runScript(name, scriptPath, body?.args ?: emptyList())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    /**
     * GET /api/skills/{name}/stats — usage statistics for a skill.
     */
    @GetMapping("/{name}/stats")
    fun getSkillStats(@PathVariable name: String): ResponseEntity<SkillStatsView> {
        return ResponseEntity.ok(skillAnalyticsService.getSkillStats(name))
    }

}

/**
 * Separate controller for skill analytics endpoints
 * to avoid path conflicts with /api/skills/{name}.
 */
@RestController
@RequestMapping("/api/skill-analytics")
class SkillAnalyticsController(
    private val skillAnalyticsService: SkillAnalyticsService
) {

    /**
     * GET /api/dashboard/skill-ranking — skills ranked by usage.
     */
    @GetMapping("/skill-ranking")
    fun getSkillRanking(
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<List<SkillRankingEntry>> {
        return ResponseEntity.ok(skillAnalyticsService.getSkillRanking(days))
    }

    /**
     * GET /api/dashboard/skill-suggestions — evolution suggestions.
     */
    @GetMapping("/skill-suggestions")
    fun getSuggestions(): ResponseEntity<List<SkillSuggestion>> {
        return ResponseEntity.ok(skillAnalyticsService.getEvolutionSuggestions())
    }

    /**
     * GET /api/dashboard/skill-triggers — trigger suggestions for a context.
     */
    @GetMapping("/skill-triggers")
    fun getTriggerSuggestions(
        @RequestParam trigger: String
    ): ResponseEntity<List<TriggerSuggestionView>> {
        return ResponseEntity.ok(skillAnalyticsService.getTriggerSuggestions(trigger))
    }
}
