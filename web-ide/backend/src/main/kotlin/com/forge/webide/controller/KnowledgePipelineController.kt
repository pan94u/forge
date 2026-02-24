package com.forge.webide.controller

import com.forge.webide.repository.SkillQualityLearnedPatternRepository
import com.forge.webide.repository.SkillQualityRecordRepository
import com.forge.webide.service.KnowledgeGapDetectorService
import com.forge.webide.service.learning.AssetExtractorService
import com.forge.webide.service.learning.LearningLoopPipelineService
import com.forge.webide.service.learning.SkillFeedbackService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * REST API for the knowledge pipeline (Phase 8.4).
 * Exposes pipeline status, knowledge gaps, skill quality data, and manual triggers.
 */
@RestController
@RequestMapping("/api/knowledge-pipeline")
class KnowledgePipelineController(
    private val pipelineService: LearningLoopPipelineService,
    private val gapDetectorService: KnowledgeGapDetectorService,
    private val assetExtractorService: AssetExtractorService,
    private val skillFeedbackService: SkillFeedbackService,
    private val qualityRecordRepository: SkillQualityRecordRepository,
    private val learnedPatternRepository: SkillQualityLearnedPatternRepository
) {

    /**
     * Get overall pipeline status: gaps, quality, patterns.
     */
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<Map<String, Any>> {
        val gapStats = gapDetectorService.getGapStatistics()
        val since7d = Instant.now().minus(7, ChronoUnit.DAYS)
        val recentQuality = qualityRecordRepository.findByCreatedAtAfter(since7d)
        val learnedPatterns = learnedPatternRepository.findByStatus("PENDING")

        return ResponseEntity.ok(mapOf(
            "knowledgeGaps" to gapStats,
            "skillQuality" to mapOf(
                "recentRecords" to recentQuality.size,
                "passRate" to if (recentQuality.isNotEmpty())
                    recentQuality.count { it.overallStatus == "PASSED" }.toDouble() / recentQuality.size
                else 0.0
            ),
            "learnedPatterns" to mapOf(
                "pending" to learnedPatterns.size,
                "patterns" to learnedPatterns.map { mapOf(
                    "id" to it.id,
                    "skillName" to it.skillName,
                    "description" to it.patternDescription,
                    "confidence" to it.confidence,
                    "suggestion" to it.suggestion
                )}
            )
        ))
    }

    /**
     * Get knowledge gaps list.
     */
    @GetMapping("/gaps")
    fun getGaps(
        @RequestParam(defaultValue = "false") resolved: Boolean
    ): ResponseEntity<Any> {
        val gaps = if (resolved) gapDetectorService.getAllGaps() else gapDetectorService.getUnresolvedGaps()
        return ResponseEntity.ok(gaps)
    }

    /**
     * Resolve a knowledge gap.
     */
    @PostMapping("/gaps/{id}/resolve")
    fun resolveGap(@PathVariable id: String): ResponseEntity<Any> {
        val resolved = gapDetectorService.resolveGap(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(resolved)
    }

    /**
     * Get skill quality records with optional filtering.
     */
    @GetMapping("/skill-quality")
    fun getSkillQuality(
        @RequestParam(required = false) skillName: String?,
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<Any> {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val records = if (!skillName.isNullOrBlank()) {
            qualityRecordRepository.findBySkillNameOrderByCreatedAtDesc(skillName)
                .filter { it.createdAt.isAfter(since) }
        } else {
            qualityRecordRepository.findByCreatedAtAfter(since)
        }

        val bySkill = records.groupBy { it.skillName }
        val summary = bySkill.map { (skill, recs) ->
            val total = recs.size
            val passed = recs.count { it.overallStatus == "PASSED" }
            mapOf(
                "skillName" to skill,
                "total" to total,
                "passed" to passed,
                "failed" to (total - passed),
                "passRate" to if (total > 0) passed.toDouble() / total else 0.0,
                "avgExecutionTimeMs" to recs.map { it.executionTimeMs }.average().toLong()
            )
        }

        return ResponseEntity.ok(mapOf(
            "summary" to summary,
            "totalRecords" to records.size
        ))
    }

    /**
     * Get self-learned patterns.
     */
    @GetMapping("/learned-patterns")
    fun getLearnedPatterns(
        @RequestParam(required = false) status: String?
    ): ResponseEntity<Any> {
        val patterns = if (!status.isNullOrBlank()) {
            learnedPatternRepository.findByStatus(status.uppercase())
        } else {
            learnedPatternRepository.findAll()
        }
        return ResponseEntity.ok(patterns.map { mapOf(
            "id" to it.id,
            "skillName" to it.skillName,
            "patternType" to it.patternType,
            "description" to it.patternDescription,
            "confidence" to it.confidence,
            "sampleSize" to it.sampleSize,
            "suggestion" to it.suggestion,
            "status" to it.status,
            "createdAt" to it.createdAt.toString()
        )})
    }

    /**
     * Get skill update suggestions from cross-analysis.
     */
    @GetMapping("/skill-suggestions")
    fun getSkillSuggestions(
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<Any> {
        val suggestions = skillFeedbackService.generateSkillUpdateSuggestions(days)
        return ResponseEntity.ok(suggestions)
    }

    /**
     * Manually trigger the full pipeline.
     */
    @PostMapping("/run")
    fun runPipeline(
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<Any> {
        val report = pipelineService.runPipeline(days)
        return ResponseEntity.ok(mapOf(
            "dateRange" to report.dateRange,
            "totalEvaluations" to report.totalEvaluations,
            "insightCount" to report.insights.size,
            "recommendationCount" to report.recommendations.size,
            "skillQualitySummary" to report.skillQualitySummary,
            "learnedPatterns" to report.learnedPatterns,
            "generatedAt" to report.generatedAt
        ))
    }

    /**
     * Manually trigger asset extraction.
     */
    @PostMapping("/extract-assets")
    fun extractAssets(
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<Any> {
        val result = assetExtractorService.extractAssets(days = days)
        return ResponseEntity.ok(result)
    }
}
