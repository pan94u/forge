package com.forge.webide.service.learning

import com.forge.webide.repository.ExecutionRecordRepository
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Spring-managed skill feedback analysis service.
 * Runs daily analysis of execution records and generates improvement reports.
 */
@Service
class SkillFeedbackService(
    private val executionRecordRepository: ExecutionRecordRepository,
    private val evaluationRepository: com.forge.webide.repository.InteractionEvaluationRepository
) {
    private val logger = LoggerFactory.getLogger(SkillFeedbackService::class.java)
    private val gson = Gson()
    private val feedbackDir = "logs/feedback"

    /**
     * Daily analysis job: run at 2:00 AM every day.
     */
    @Scheduled(cron = "0 0 2 * * *")
    fun runDailyAnalysis() {
        logger.info("Starting daily skill feedback analysis...")
        try {
            val report = analyzeLastNDays(7)
            saveReport(report)
            logger.info("Skill feedback analysis complete: {} profiles analyzed", report.profileSummaries.size)
        } catch (e: Exception) {
            logger.error("Skill feedback analysis failed: {}", e.message, e)
        }
    }

    /**
     * Analyze execution records from the last N days.
     */
    fun analyzeLastNDays(days: Int): FeedbackReport {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val records = executionRecordRepository.findByCreatedAtAfter(since)

        // Profile summary
        val byProfile = records.groupBy { it.profile }
        val profileSummaries = byProfile.map { (profile, recs) ->
            val avgDuration = if (recs.isNotEmpty()) recs.map { it.totalDurationMs }.average().toLong() else 0L
            val avgTurns = if (recs.isNotEmpty()) recs.map { it.totalTurns }.average() else 0.0

            // Parse tool calls to count tools
            val toolCounts = mutableMapOf<String, Int>()
            for (rec in recs) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val tools = gson.fromJson(rec.toolCalls, List::class.java) as? List<Map<String, Any>> ?: continue
                    for (tool in tools) {
                        val name = tool["name"] as? String ?: continue
                        toolCounts[name] = (toolCounts[name] ?: 0) + 1
                    }
                } catch (_: Exception) { }
            }

            ProfileSummary(
                name = profile,
                executionCount = recs.size,
                avgDurationMs = avgDuration,
                avgTurns = avgTurns,
                topTools = toolCounts.entries.sortedByDescending { it.value }
                    .take(5)
                    .map { "${it.key}: ${it.value}" }
            )
        }

        // Overall stats
        val totalExecutions = records.size
        val avgDuration = if (records.isNotEmpty()) records.map { it.totalDurationMs }.average().toLong() else 0L

        // Phase 7: Enrich with evaluation scores
        val evaluationScores = try {
            val evalSince = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
            val evals = evaluationRepository.findByCreatedAtAfterOrderByCreatedAtDesc(evalSince)
            val byProfile = evals.groupBy { it.profile }
            byProfile.mapValues { (_, recs) ->
                mapOf(
                    "avgIntent" to "%.2f".format(recs.map { it.intentScore }.average()),
                    "avgCompletion" to "%.2f".format(recs.map { it.completionScore }.average()),
                    "avgQuality" to "%.2f".format(recs.map { it.qualityScore }.average()),
                    "avgExperience" to "%.2f".format(recs.map { it.experienceScore }.average()),
                    "count" to recs.size.toString()
                )
            }
        } catch (_: Exception) { emptyMap() }

        return FeedbackReport(
            dateRange = "${LocalDate.now().minusDays(days.toLong())} to ${LocalDate.now()}",
            totalExecutions = totalExecutions,
            avgDurationMs = avgDuration,
            profileSummaries = profileSummaries,
            evaluationScores = evaluationScores,
            generatedAt = Instant.now().toString()
        )
    }

    /**
     * Save analysis report to file system.
     */
    private fun saveReport(report: FeedbackReport) {
        try {
            val dir = File(feedbackDir)
            dir.mkdirs()

            val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val reportFile = dir.resolve("feedback-$date.md")

            val markdown = buildString {
                appendLine("# Skill Feedback Analysis Report")
                appendLine()
                appendLine("**Date Range**: ${report.dateRange}")
                appendLine("**Generated**: ${report.generatedAt}")
                appendLine("**Total Executions**: ${report.totalExecutions}")
                appendLine("**Average Duration**: ${report.avgDurationMs}ms")
                appendLine()
                appendLine("## Profile Performance")
                appendLine()
                appendLine("| Profile | Executions | Avg Duration | Avg Turns | Top Tools |")
                appendLine("|---------|-----------|-------------|-----------|-----------|")
                for (p in report.profileSummaries) {
                    appendLine("| ${p.name} | ${p.executionCount} | ${p.avgDurationMs}ms | ${"%.1f".format(p.avgTurns)} | ${p.topTools.take(3).joinToString(", ")} |")
                }
                // Phase 7: Evaluation scores section
                if (report.evaluationScores.isNotEmpty()) {
                    appendLine()
                    appendLine("## Evaluation Scores (4D)")
                    appendLine()
                    appendLine("| Profile | Intent | Completion | Quality | Experience | Samples |")
                    appendLine("|---------|--------|------------|---------|------------|---------|")
                    for ((profile, scores) in report.evaluationScores) {
                        appendLine("| $profile | ${scores["avgIntent"]} | ${scores["avgCompletion"]} | ${scores["avgQuality"]} | ${scores["avgExperience"]} | ${scores["count"]} |")
                    }
                }
            }

            reportFile.writeText(markdown)
            logger.info("Feedback report saved to {}", reportFile.absolutePath)
        } catch (e: Exception) {
            logger.warn("Failed to save feedback report: {}", e.message)
        }
    }

    /**
     * Get the latest feedback report content.
     */
    fun getLatestReport(): String? {
        return try {
            val dir = File(feedbackDir)
            if (!dir.exists()) return null
            dir.listFiles()
                ?.filter { it.name.startsWith("feedback-") && it.name.endsWith(".md") }
                ?.maxByOrNull { it.name }
                ?.readText()
        } catch (e: Exception) {
            logger.warn("Failed to read feedback report: {}", e.message)
            null
        }
    }

    data class FeedbackReport(
        val dateRange: String,
        val totalExecutions: Int,
        val avgDurationMs: Long,
        val profileSummaries: List<ProfileSummary>,
        val evaluationScores: Map<String, Map<String, String>> = emptyMap(),
        val generatedAt: String
    )

    data class ProfileSummary(
        val name: String,
        val executionCount: Int,
        val avgDurationMs: Long,
        val avgTurns: Double,
        val topTools: List<String>
    )
}
