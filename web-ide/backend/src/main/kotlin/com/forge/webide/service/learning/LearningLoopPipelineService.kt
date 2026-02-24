package com.forge.webide.service.learning

import com.forge.webide.entity.SkillQualityLearnedPatternEntity
import com.forge.webide.repository.InteractionEvaluationRepository
import com.forge.webide.repository.SkillQualityLearnedPatternRepository
import com.forge.webide.repository.SkillQualityRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Learning Loop Pipeline — the asset-extractor component of the Forge evolution loop.
 *
 * Periodically scans interaction evaluations to identify low-performing areas
 * and generate improvement suggestions. Corresponds to Baseline §4.6 进化环设计.
 *
 * Pipeline flow:
 * 1. Scan recent InteractionEvaluations
 * 2. Identify low-score capability dimensions (avg < 0.6)
 * 3. Analyze root causes (routing errors, tool failures, skill gaps)
 * 4. Generate improvement report
 * 5. Feed into SkillFeedbackService for continuous improvement
 */
@Service
class LearningLoopPipelineService(
    private val evaluationRepository: InteractionEvaluationRepository,
    private val skillFeedbackService: SkillFeedbackService,
    private val qualityRecordRepository: SkillQualityRecordRepository,
    private val learnedPatternRepository: SkillQualityLearnedPatternRepository,
    private val assetExtractorService: AssetExtractorService
) {
    private val logger = LoggerFactory.getLogger(LearningLoopPipelineService::class.java)

    companion object {
        const val LOW_SCORE_THRESHOLD = 0.6
        const val MIN_SAMPLE_SIZE = 3
    }

    /**
     * Weekly analysis job: run at 3:00 AM every Monday.
     */
    @Scheduled(cron = "0 0 3 * * MON")
    fun runWeeklyAnalysis() {
        logger.info("Starting weekly learning loop pipeline analysis...")
        try {
            val report = runPipeline(days = 7)
            logger.info("Learning loop analysis complete: {} insights generated", report.insights.size)
        } catch (e: Exception) {
            logger.error("Learning loop pipeline failed: {}", e.message, e)
        }
    }

    /**
     * Run the learning loop pipeline manually or on schedule.
     */
    fun runPipeline(days: Int = 7): PipelineReport {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val evaluations = evaluationRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since)

        if (evaluations.size < MIN_SAMPLE_SIZE) {
            logger.info("Insufficient data for pipeline analysis ({} < {})", evaluations.size, MIN_SAMPLE_SIZE)
            return PipelineReport(
                dateRange = "${LocalDate.now().minusDays(days.toLong())} to ${LocalDate.now()}",
                totalEvaluations = evaluations.size,
                insights = emptyList(),
                recommendations = listOf("Insufficient data — need at least $MIN_SAMPLE_SIZE interactions"),
                generatedAt = Instant.now().toString()
            )
        }

        val insights = mutableListOf<PipelineInsight>()
        val recommendations = mutableListOf<String>()

        // 1. Analyze by profile
        val byProfile = evaluations.groupBy { it.profile }
        for ((profile, records) in byProfile) {
            val avgIntent = records.map { it.intentScore }.average()
            val avgCompletion = records.map { it.completionScore }.average()
            val avgQuality = records.map { it.qualityScore }.average()
            val avgExperience = records.map { it.experienceScore }.average()

            if (avgIntent < LOW_SCORE_THRESHOLD) {
                val routingIssues = records.count { it.routingConfidence < 0.5 }
                val confirmed = records.count { it.intentConfirmed }
                insights.add(PipelineInsight(
                    dimension = "intent",
                    scope = "profile:$profile",
                    score = avgIntent,
                    sampleSize = records.size,
                    rootCause = "Low routing confidence ($routingIssues/${records.size} below 0.5, $confirmed confirmed by user)",
                    suggestion = "Add more keywords for $profile in ProfileRouter or review keyword overlap with other profiles"
                ))
                recommendations.add("ProfileRouter: Review keyword coverage for $profile")
            }

            if (avgCompletion < LOW_SCORE_THRESHOLD) {
                val failedTools = records.filter { it.toolSuccessCount < it.toolCallCount }
                insights.add(PipelineInsight(
                    dimension = "completion",
                    scope = "profile:$profile",
                    score = avgCompletion,
                    sampleSize = records.size,
                    rootCause = "${failedTools.size}/${records.size} interactions had tool failures",
                    suggestion = "Review tool reliability for $profile; check MCP server health"
                ))
            }

            if (avgQuality < LOW_SCORE_THRESHOLD) {
                insights.add(PipelineInsight(
                    dimension = "quality",
                    scope = "profile:$profile",
                    score = avgQuality,
                    sampleSize = records.size,
                    rootCause = "Baseline pass rate below threshold",
                    suggestion = "Review baseline scripts and skill guidance for $profile"
                ))
            }

            if (avgExperience < LOW_SCORE_THRESHOLD) {
                val avgTurns = records.map { it.turnCount }.average()
                insights.add(PipelineInsight(
                    dimension = "experience",
                    scope = "profile:$profile",
                    score = avgExperience,
                    sampleSize = records.size,
                    rootCause = "High average turns (${"%.1f".format(avgTurns)}) indicating excessive iteration",
                    suggestion = "Optimize OODA guidance for $profile to reduce iteration count"
                ))
            }
        }

        // 2. Analyze by capability category
        val byCategory = evaluations.filter { it.capabilityCategory.isNotBlank() }.groupBy { it.capabilityCategory }
        for ((category, records) in byCategory) {
            val avgOverall = records.map { (it.intentScore + it.completionScore + it.qualityScore + it.experienceScore) / 4 }.average()
            if (avgOverall < LOW_SCORE_THRESHOLD && records.size >= MIN_SAMPLE_SIZE) {
                val categoryName = mapCategory(category)
                insights.add(PipelineInsight(
                    dimension = "overall",
                    scope = "category:$categoryName",
                    score = avgOverall,
                    sampleSize = records.size,
                    rootCause = "Category $categoryName consistently underperforming",
                    suggestion = "Review skills and OODA guidance for $categoryName capability dimension"
                ))
                recommendations.add("Skill Enhancement: Improve $categoryName skills")
            }
        }

        // 3. Overall trend
        val overallAvg = evaluations.map { (it.intentScore + it.completionScore + it.qualityScore + it.experienceScore) / 4 }.average()
        if (overallAvg < LOW_SCORE_THRESHOLD) {
            recommendations.add("Overall platform score (${"%.0f".format(overallAvg * 100)}%) is below target — prioritize the top insight")
        }

        // 4. Skill quality trend analysis (Phase 8.2)
        val skillQualityInsights = analyzeSkillQuality(since)
        insights.addAll(skillQualityInsights)

        // 5. Self-learning pattern mining (Phase 8.2)
        val learnedPatterns = mineLearnedPatterns()
        if (learnedPatterns.isNotEmpty()) {
            recommendations.add("Self-learning: ${learnedPatterns.size} new patterns discovered from skill executions")
        }

        // 6. Asset extraction (Phase 8.3 — Pipeline 1)
        try {
            val extraction = assetExtractorService.extractAssets(days = days)
            if (extraction.documentsCreated > 0) {
                recommendations.add("Asset extraction: ${extraction.documentsCreated} knowledge documents created, ${extraction.gapStubsCreated} gap stubs")
            }
        } catch (e: Exception) {
            logger.warn("Asset extraction failed: {}", e.message)
        }

        // 7. Skill update suggestions (Phase 8.3 — Pipeline 3)
        try {
            val skillSuggestions = skillFeedbackService.generateSkillUpdateSuggestions(days)
            for (s in skillSuggestions) {
                recommendations.add("Skill update: ${s.suggestion}")
            }
        } catch (e: Exception) {
            logger.warn("Skill suggestions failed: {}", e.message)
        }

        val report = PipelineReport(
            dateRange = "${LocalDate.now().minusDays(days.toLong())} to ${LocalDate.now()}",
            totalEvaluations = evaluations.size,
            insights = insights.sortedBy { it.score },
            recommendations = recommendations,
            skillQualitySummary = buildSkillQualitySummary(since),
            learnedPatterns = learnedPatterns.map { it.patternDescription },
            generatedAt = Instant.now().toString()
        )

        // Save report to filesystem
        saveReport(report)

        return report
    }

    /**
     * Analyze skill quality records for trend insights.
     */
    private fun analyzeSkillQuality(since: Instant): List<PipelineInsight> {
        val insights = mutableListOf<PipelineInsight>()
        try {
            val records = qualityRecordRepository.findByCreatedAtAfter(since)
            if (records.isEmpty()) return insights

            val bySkill = records.groupBy { it.skillName }
            for ((skillName, recs) in bySkill) {
                val total = recs.size
                val passed = recs.count { it.overallStatus == "PASSED" }
                val passRate = passed.toDouble() / total

                if (passRate < LOW_SCORE_THRESHOLD && total >= MIN_SAMPLE_SIZE) {
                    val failedDetails = recs.filter { it.overallStatus != "PASSED" }
                        .mapNotNull { it.layer1Details }
                        .flatMap { it.split("; ") }
                        .groupBy { it }
                        .mapValues { it.value.size }
                        .entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .joinToString(", ") { "${it.key} (${it.value}x)" }

                    insights.add(PipelineInsight(
                        dimension = "skill_quality",
                        scope = "skill:$skillName",
                        score = passRate,
                        sampleSize = total,
                        rootCause = "Pass rate ${"%.0f".format(passRate * 100)}%: $failedDetails",
                        suggestion = "Review and fix skill '$skillName' — consider adding quality rules to SKILL.md"
                    ))
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to analyze skill quality: {}", e.message)
        }
        return insights
    }

    /**
     * Mine self-learning patterns from skills with 20+ executions.
     * Compares success vs failure patterns to generate suggestions.
     */
    private fun mineLearnedPatterns(): List<SkillQualityLearnedPatternEntity> {
        val newPatterns = mutableListOf<SkillQualityLearnedPatternEntity>()
        try {
            val allRecords = qualityRecordRepository.findAll()
            val bySkill = allRecords.groupBy { it.skillName }

            for ((skillName, recs) in bySkill) {
                if (recs.size < 20) continue

                // Check if we already have patterns for this skill
                val existing = learnedPatternRepository.findBySkillName(skillName)
                if (existing.isNotEmpty()) continue

                val passed = recs.filter { it.overallStatus == "PASSED" }
                val failed = recs.filter { it.overallStatus != "PASSED" }

                if (failed.isEmpty()) continue

                // Analyze failure details
                val failureReasons = failed
                    .mapNotNull { it.layer1Details }
                    .flatMap { it.split("; ") }
                    .filter { it != "All platform checks passed" }
                    .groupBy { it }
                    .mapValues { it.value.size }
                    .entries
                    .sortedByDescending { it.value }

                for (reason in failureReasons.take(3)) {
                    val confidence = reason.value.toDouble() / failed.size
                    if (confidence < 0.3) continue

                    val pattern = SkillQualityLearnedPatternEntity(
                        id = UUID.randomUUID().toString(),
                        skillName = skillName,
                        patternType = "failure_pattern",
                        patternDescription = reason.key,
                        confidence = confidence,
                        sampleSize = recs.size,
                        suggestion = "建议为 Skill '$skillName' 增加针对「${reason.key}」的质量规则（基于 ${passed.size} 次成功 vs ${failed.size} 次失败的模式分析）"
                    )
                    learnedPatternRepository.save(pattern)
                    newPatterns.add(pattern)
                }

                // Analyze output length patterns
                if (passed.isNotEmpty() && failed.isNotEmpty()) {
                    val avgPassedLen = passed.map { it.outputLength }.average()
                    val avgFailedLen = failed.map { it.outputLength }.average()
                    if (avgPassedLen > avgFailedLen * 2 && avgFailedLen < 100) {
                        val pattern = SkillQualityLearnedPatternEntity(
                            id = UUID.randomUUID().toString(),
                            skillName = skillName,
                            patternType = "output_length",
                            patternDescription = "成功执行平均输出 ${"%.0f".format(avgPassedLen)} 字符，失败平均 ${"%.0f".format(avgFailedLen)} 字符",
                            confidence = 0.7,
                            sampleSize = recs.size,
                            suggestion = "建议为 Skill '$skillName' 设置 min_output_length: ${(avgPassedLen * 0.3).toInt()}"
                        )
                        learnedPatternRepository.save(pattern)
                        newPatterns.add(pattern)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to mine learned patterns: {}", e.message)
        }
        return newPatterns
    }

    private fun buildSkillQualitySummary(since: Instant): List<SkillQualitySummary> {
        return try {
            val statusCounts = qualityRecordRepository.countBySkillNameAndStatusSince(since)
            val bySkill = mutableMapOf<String, MutableMap<String, Long>>()
            for (row in statusCounts) {
                val skill = row[0] as String
                val status = row[1] as String
                val count = (row[2] as Number).toLong()
                bySkill.getOrPut(skill) { mutableMapOf() }[status] = count
            }
            bySkill.map { (skill, counts) ->
                val total = counts.values.sum()
                val passed = counts["PASSED"] ?: 0
                SkillQualitySummary(
                    skillName = skill,
                    totalExecutions = total,
                    passRate = if (total > 0) passed.toDouble() / total else 0.0,
                    statusBreakdown = counts
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to build skill quality summary: {}", e.message)
            emptyList()
        }
    }

    private fun saveReport(report: PipelineReport) {
        try {
            val dir = java.io.File("logs/learning-loop")
            dir.mkdirs()

            val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val reportFile = dir.resolve("pipeline-$date.md")

            val markdown = buildString {
                appendLine("# Learning Loop Pipeline Report")
                appendLine()
                appendLine("**Date Range**: ${report.dateRange}")
                appendLine("**Generated**: ${report.generatedAt}")
                appendLine("**Total Evaluations**: ${report.totalEvaluations}")
                appendLine()

                if (report.insights.isNotEmpty()) {
                    appendLine("## Insights (Low-Score Areas)")
                    appendLine()
                    appendLine("| Dimension | Scope | Score | Samples | Root Cause | Suggestion |")
                    appendLine("|-----------|-------|-------|---------|------------|------------|")
                    for (insight in report.insights) {
                        appendLine("| ${insight.dimension} | ${insight.scope} | ${"%.0f".format(insight.score * 100)}% | ${insight.sampleSize} | ${insight.rootCause} | ${insight.suggestion} |")
                    }
                    appendLine()
                }

                if (report.skillQualitySummary.isNotEmpty()) {
                    appendLine("## Skill Quality Summary")
                    appendLine()
                    appendLine("| Skill | Executions | Pass Rate | Status Breakdown |")
                    appendLine("|-------|-----------|-----------|-----------------|")
                    for (s in report.skillQualitySummary) {
                        val breakdown = s.statusBreakdown.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                        appendLine("| ${s.skillName} | ${s.totalExecutions} | ${"%.0f".format(s.passRate * 100)}% | $breakdown |")
                    }
                    appendLine()
                }

                if (report.learnedPatterns.isNotEmpty()) {
                    appendLine("## Self-Learned Patterns")
                    appendLine()
                    for ((i, pattern) in report.learnedPatterns.withIndex()) {
                        appendLine("${i + 1}. $pattern")
                    }
                    appendLine()
                }

                if (report.recommendations.isNotEmpty()) {
                    appendLine("## Recommendations")
                    appendLine()
                    for ((i, rec) in report.recommendations.withIndex()) {
                        appendLine("${i + 1}. $rec")
                    }
                }
            }

            reportFile.writeText(markdown)
            logger.info("Pipeline report saved to {}", reportFile.absolutePath)
        } catch (e: Exception) {
            logger.warn("Failed to save pipeline report: {}", e.message)
        }
    }

    private fun mapCategory(code: String): String = when (code) {
        "A" -> "ANALYZE"
        "B" -> "GENERATE"
        "C" -> "FIX"
        "D" -> "KNOWLEDGE"
        "E" -> "DELIVER"
        else -> code
    }

    data class PipelineReport(
        val dateRange: String,
        val totalEvaluations: Int,
        val insights: List<PipelineInsight>,
        val recommendations: List<String>,
        val skillQualitySummary: List<SkillQualitySummary> = emptyList(),
        val learnedPatterns: List<String> = emptyList(),
        val generatedAt: String
    )

    data class SkillQualitySummary(
        val skillName: String,
        val totalExecutions: Long,
        val passRate: Double,
        val statusBreakdown: Map<String, Long>
    )

    data class PipelineInsight(
        val dimension: String,
        val scope: String,
        val score: Double,
        val sampleSize: Int,
        val rootCause: String,
        val suggestion: String
    )
}
