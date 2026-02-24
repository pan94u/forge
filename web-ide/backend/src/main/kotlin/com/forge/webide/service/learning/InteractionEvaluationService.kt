package com.forge.webide.service.learning

import com.forge.webide.entity.InteractionEvaluationEntity
import com.forge.webide.repository.InteractionEvaluationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Records and scores interaction evaluations for the Learning Loop (Phase 7).
 *
 * Auto-scores 4 dimensions after each agentic interaction:
 * - Intent accuracy: Was the routing correct?
 * - Completion: Did all tools succeed?
 * - Quality: Did baselines pass?
 * - Experience: Was the interaction smooth?
 *
 * Also supports manual user ratings (0-5 scale).
 */
@Service
class InteractionEvaluationService(
    private val repository: InteractionEvaluationRepository
) {
    private val logger = LoggerFactory.getLogger(InteractionEvaluationService::class.java)

    companion object {
        private const val MAX_TURNS_BASELINE = 8
    }

    /**
     * Record an evaluation after an agentic loop completes.
     */
    fun recordEvaluation(
        sessionId: String,
        workspaceId: String,
        profile: String,
        mode: String,
        routingConfidence: Double,
        intentConfirmed: Boolean,
        toolCallCount: Int,
        toolSuccessCount: Int,
        turnCount: Int,
        durationMs: Long,
        baselinePassRate: Double = 1.0,
        userCorrectionCount: Int = 0
    ): InteractionEvaluationEntity {
        val capabilityCategory = inferCapabilityCategory(profile, mode)

        // Auto-score 4 dimensions
        val intentScore = computeIntentScore(routingConfidence, intentConfirmed)
        val completionScore = computeCompletionScore(toolCallCount, toolSuccessCount)
        val qualityScore = baselinePassRate.coerceIn(0.0, 1.0)
        val experienceScore = computeExperienceScore(turnCount, userCorrectionCount)

        val entity = InteractionEvaluationEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            workspaceId = workspaceId,
            profile = profile,
            mode = mode,
            capabilityCategory = capabilityCategory,
            routingConfidence = routingConfidence,
            intentConfirmed = intentConfirmed,
            intentScore = intentScore,
            completionScore = completionScore,
            qualityScore = qualityScore,
            experienceScore = experienceScore,
            toolCallCount = toolCallCount,
            toolSuccessCount = toolSuccessCount,
            turnCount = turnCount,
            durationMs = durationMs
        )

        return try {
            repository.save(entity).also {
                logger.info(
                    "Recorded evaluation: session={}, profile={}, scores=[intent={}, completion={}, quality={}, experience={}]",
                    sessionId, profile,
                    "%.2f".format(intentScore), "%.2f".format(completionScore),
                    "%.2f".format(qualityScore), "%.2f".format(experienceScore)
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to save evaluation: {}", e.message)
            entity
        }
    }

    /**
     * Apply manual user ratings to an evaluation.
     */
    fun rateEvaluation(
        evaluationId: String,
        intentScore: Int?,
        completionScore: Int?,
        qualityScore: Int?,
        experienceScore: Int?,
        feedback: String?
    ): InteractionEvaluationEntity? {
        val entity = repository.findById(evaluationId).orElse(null) ?: return null
        if (intentScore != null) entity.manualIntentScore = intentScore.coerceIn(0, 5)
        if (completionScore != null) entity.manualCompletionScore = completionScore.coerceIn(0, 5)
        if (qualityScore != null) entity.manualQualityScore = qualityScore.coerceIn(0, 5)
        if (experienceScore != null) entity.manualExperienceScore = experienceScore.coerceIn(0, 5)
        if (!feedback.isNullOrBlank()) entity.userFeedback = feedback
        return repository.save(entity)
    }

    /**
     * Get evaluation summary grouped by profile.
     */
    fun getSummaryByProfile(days: Int = 30): List<EvaluationSummary> {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return repository.avgScoresByProfileSince(since).map { row ->
            EvaluationSummary(
                groupKey = row[0] as String,
                groupType = "profile",
                avgIntentScore = (row[1] as Number).toDouble(),
                avgCompletionScore = (row[2] as Number).toDouble(),
                avgQualityScore = (row[3] as Number).toDouble(),
                avgExperienceScore = (row[4] as Number).toDouble(),
                count = (row[5] as Number).toLong()
            )
        }
    }

    /**
     * Get evaluation summary grouped by capability category.
     */
    fun getSummaryByCategory(days: Int = 30): List<EvaluationSummary> {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return repository.avgScoresByCategorySince(since).map { row ->
            EvaluationSummary(
                groupKey = row[0] as String,
                groupType = "category",
                avgIntentScore = (row[1] as Number).toDouble(),
                avgCompletionScore = (row[2] as Number).toDouble(),
                avgQualityScore = (row[3] as Number).toDouble(),
                avgExperienceScore = (row[4] as Number).toDouble(),
                count = (row[5] as Number).toLong()
            )
        }
    }

    /**
     * Get recent evaluations.
     */
    fun getRecentEvaluations(days: Int = 7): List<InteractionEvaluationEntity> {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return repository.findByCreatedAtAfterOrderByCreatedAtDesc(since)
    }

    fun getEvaluation(id: String): InteractionEvaluationEntity? {
        return repository.findById(id).orElse(null)
    }

    // --- Scoring algorithms ---

    private fun computeIntentScore(confidence: Double, confirmed: Boolean): Double {
        val base = confidence.coerceIn(0.0, 1.0)
        return if (confirmed) (base * 1.2).coerceAtMost(1.0) else (base * 0.8).coerceAtMost(1.0)
    }

    private fun computeCompletionScore(totalCalls: Int, successCalls: Int): Double {
        if (totalCalls == 0) return 1.0
        return (successCalls.toDouble() / totalCalls).coerceIn(0.0, 1.0)
    }

    private fun computeExperienceScore(turnCount: Int, userCorrectionCount: Int): Double {
        val turnFactor = ((MAX_TURNS_BASELINE - turnCount).toDouble() / MAX_TURNS_BASELINE).coerceIn(0.0, 1.0)
        val correctionPenalty = (userCorrectionCount * 0.2).coerceAtMost(1.0)
        return (turnFactor * (1.0 - correctionPenalty)).coerceIn(0.0, 1.0)
    }

    private fun inferCapabilityCategory(profile: String, mode: String): String {
        return when {
            profile.contains("evaluation") && mode == "read-only" -> "A" // ANALYZE
            profile.contains("evaluation") -> "B" // GENERATE
            profile.contains("development") -> "E" // DELIVER
            profile.contains("planning") -> "E"
            profile.contains("design") -> "E"
            profile.contains("testing") -> "E"
            profile.contains("ops") -> "E"
            else -> ""
        }
    }

    data class EvaluationSummary(
        val groupKey: String,
        val groupType: String,
        val avgIntentScore: Double,
        val avgCompletionScore: Double,
        val avgQualityScore: Double,
        val avgExperienceScore: Double,
        val count: Long
    )
}
