package com.forge.webide.service.learning

import com.forge.webide.repository.InteractionEvaluationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    private val skillFeedbackService: SkillFeedbackService
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

        val report = PipelineReport(
            dateRange = "${LocalDate.now().minusDays(days.toLong())} to ${LocalDate.now()}",
            totalEvaluations = evaluations.size,
            insights = insights.sortedBy { it.score },
            recommendations = recommendations,
            generatedAt = Instant.now().toString()
        )

        // Save report to filesystem
        saveReport(report)

        return report
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
        val generatedAt: String
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
