package com.forge.webide.service.skill

import com.forge.webide.repository.SkillUsageRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Provides skill usage analytics, trigger suggestions, and evolution recommendations.
 *
 * - Tracks which skills are heavily/rarely used
 * - Suggests extraction scripts to run at key milestones
 * - Identifies skills that may need improvement or deprecation
 */
@Service
class SkillAnalyticsService(
    private val skillUsageRepository: SkillUsageRepository,
    private val skillLoader: SkillLoader
) {
    private val logger = LoggerFactory.getLogger(SkillAnalyticsService::class.java)

    /**
     * Get usage stats for a specific skill.
     */
    fun getSkillStats(skillName: String): SkillStatsView {
        val totalUsage = skillUsageRepository.countBySkillName(skillName)
        val successCount = skillUsageRepository.countSuccessBySkillName(skillName)
        val successRate = if (totalUsage > 0) successCount.toDouble() / totalUsage else 0.0

        return SkillStatsView(
            skillName = skillName,
            totalUsage = totalUsage,
            successCount = successCount,
            successRate = successRate
        )
    }

    /**
     * Get ranked skill usage across all skills.
     */
    fun getSkillRanking(days: Int = 30): List<SkillRankingEntry> {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val counts = skillUsageRepository.countBySkillNameSince(since)

        return counts.map { row ->
            SkillRankingEntry(
                skillName = row[0] as String,
                usageCount = (row[1] as Long)
            )
        }
    }

    /**
     * Identify skills that may need attention (never used, high failure rate).
     */
    fun getEvolutionSuggestions(): List<SkillSuggestion> {
        val suggestions = mutableListOf<SkillSuggestion>()
        val allSkills = skillLoader.loadSkillMetadataCatalog()
        val since30d = Instant.now().minus(30, ChronoUnit.DAYS)
        val usageCounts = skillUsageRepository.countBySkillNameSince(since30d)
            .associate { it[0] as String to (it[1] as Long) }

        for (skill in allSkills) {
            val usage = usageCounts[skill.name] ?: 0L

            if (usage == 0L) {
                suggestions.add(
                    SkillSuggestion(
                        skillName = skill.name,
                        type = "UNUSED",
                        message = "Skill '${skill.name}' has not been used in the last 30 days. Consider reviewing if it's still relevant."
                    )
                )
            } else {
                val stats = getSkillStats(skill.name)
                if (stats.successRate < 0.5 && stats.totalUsage > 5) {
                    suggestions.add(
                        SkillSuggestion(
                            skillName = skill.name,
                            type = "LOW_SUCCESS_RATE",
                            message = "Skill '${skill.name}' has a ${(stats.successRate * 100).toInt()}% success rate. Scripts may need updating."
                        )
                    )
                }
            }
        }

        return suggestions
    }

    /**
     * Get trigger suggestions based on context (workspace creation, milestone, etc.).
     */
    fun getTriggerSuggestions(trigger: String): List<TriggerSuggestionView> {
        return when (trigger) {
            "workspace_init" -> listOf(
                TriggerSuggestionView(
                    skillName = "codebase-profiler",
                    scriptPath = "scripts/scan_modules.py",
                    reason = "Run codebase profiler to understand project structure"
                ),
                TriggerSuggestionView(
                    skillName = "codebase-profiler",
                    scriptPath = "scripts/scan_endpoints.py",
                    reason = "Scan API endpoints to generate API inventory"
                )
            )
            "milestone_complete" -> listOf(
                TriggerSuggestionView(
                    skillName = "convention-miner",
                    scriptPath = "scripts/mine_naming.py",
                    reason = "Check for convention drift after milestone"
                ),
                TriggerSuggestionView(
                    skillName = "delivery-methodology",
                    scriptPath = "scripts/session_summary.py",
                    reason = "Generate structured session summary"
                )
            )
            else -> emptyList()
        }
    }
}

data class SkillStatsView(
    val skillName: String,
    val totalUsage: Long,
    val successCount: Long,
    val successRate: Double
)

data class SkillRankingEntry(
    val skillName: String,
    val usageCount: Long
)

data class SkillSuggestion(
    val skillName: String,
    val type: String,
    val message: String
)

data class TriggerSuggestionView(
    val skillName: String,
    val scriptPath: String,
    val reason: String
)
