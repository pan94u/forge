package com.forge.webide.controller

import com.forge.webide.service.learning.InteractionEvaluationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API for interaction evaluation metrics (Phase 7 — Learning Loop Dashboard).
 */
@RestController
@RequestMapping("/api/evaluations")
class EvaluationController(
    private val evaluationService: InteractionEvaluationService
) {

    /**
     * Get aggregated evaluation summary (by profile and by capability category).
     */
    @GetMapping("/summary")
    fun getSummary(@RequestParam(defaultValue = "30") days: Int): ResponseEntity<Map<String, Any>> {
        val byProfile = evaluationService.getSummaryByProfile(days)
        val byCategory = evaluationService.getSummaryByCategory(days)
        return ResponseEntity.ok(mapOf(
            "byProfile" to byProfile,
            "byCategory" to byCategory,
            "days" to days
        ))
    }

    /**
     * Get recent evaluations for trend display.
     */
    @GetMapping("/trend")
    fun getTrend(@RequestParam(defaultValue = "7") days: Int): ResponseEntity<List<Map<String, Any?>>> {
        val evaluations = evaluationService.getRecentEvaluations(days)
        return ResponseEntity.ok(evaluations.map { e ->
            mapOf(
                "id" to e.id,
                "sessionId" to e.sessionId,
                "profile" to e.profile,
                "mode" to e.mode,
                "capabilityCategory" to e.capabilityCategory,
                "intentScore" to e.intentScore,
                "completionScore" to e.completionScore,
                "qualityScore" to e.qualityScore,
                "experienceScore" to e.experienceScore,
                "toolCallCount" to e.toolCallCount,
                "turnCount" to e.turnCount,
                "durationMs" to e.durationMs,
                "manualIntentScore" to e.manualIntentScore,
                "manualCompletionScore" to e.manualCompletionScore,
                "manualQualityScore" to e.manualQualityScore,
                "manualExperienceScore" to e.manualExperienceScore,
                "userFeedback" to e.userFeedback,
                "createdAt" to e.createdAt.toString()
            )
        })
    }

    /**
     * Get a single evaluation detail.
     */
    @GetMapping("/{id}")
    fun getEvaluation(@PathVariable id: String): ResponseEntity<Map<String, Any?>> {
        val evaluation = evaluationService.getEvaluation(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(mapOf(
            "id" to evaluation.id,
            "sessionId" to evaluation.sessionId,
            "workspaceId" to evaluation.workspaceId,
            "profile" to evaluation.profile,
            "mode" to evaluation.mode,
            "capabilityCategory" to evaluation.capabilityCategory,
            "scenarioId" to evaluation.scenarioId,
            "routingConfidence" to evaluation.routingConfidence,
            "intentConfirmed" to evaluation.intentConfirmed,
            "intentScore" to evaluation.intentScore,
            "completionScore" to evaluation.completionScore,
            "qualityScore" to evaluation.qualityScore,
            "experienceScore" to evaluation.experienceScore,
            "manualIntentScore" to evaluation.manualIntentScore,
            "manualCompletionScore" to evaluation.manualCompletionScore,
            "manualQualityScore" to evaluation.manualQualityScore,
            "manualExperienceScore" to evaluation.manualExperienceScore,
            "toolCallCount" to evaluation.toolCallCount,
            "toolSuccessCount" to evaluation.toolSuccessCount,
            "turnCount" to evaluation.turnCount,
            "durationMs" to evaluation.durationMs,
            "userFeedback" to evaluation.userFeedback,
            "createdAt" to evaluation.createdAt.toString()
        ))
    }

    /**
     * Submit manual rating for an evaluation.
     */
    @PostMapping("/{id}/rate")
    fun rateEvaluation(
        @PathVariable id: String,
        @RequestBody body: RatingRequest
    ): ResponseEntity<Map<String, Any?>> {
        val updated = evaluationService.rateEvaluation(
            evaluationId = id,
            intentScore = body.intentScore,
            completionScore = body.completionScore,
            qualityScore = body.qualityScore,
            experienceScore = body.experienceScore,
            feedback = body.feedback
        ) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(mapOf(
            "id" to updated.id,
            "manualIntentScore" to updated.manualIntentScore,
            "manualCompletionScore" to updated.manualCompletionScore,
            "manualQualityScore" to updated.manualQualityScore,
            "manualExperienceScore" to updated.manualExperienceScore,
            "userFeedback" to updated.userFeedback
        ))
    }

    data class RatingRequest(
        val intentScore: Int? = null,
        val completionScore: Int? = null,
        val qualityScore: Int? = null,
        val experienceScore: Int? = null,
        val feedback: String? = null
    )
}
