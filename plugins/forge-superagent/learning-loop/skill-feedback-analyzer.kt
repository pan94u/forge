package com.forge.superagent.learning

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Skill Feedback Analyzer — Measures skill effectiveness and generates optimization suggestions.
 *
 * This component completes the outer learning loop by analyzing how well each skill
 * performs across multiple executions. It:
 * 1. Tracks baseline pass rates per skill profile and per individual skill
 * 2. Generates optimization suggestions based on failure patterns
 * 3. Produces reports for human review to guide skill improvements
 *
 * The analysis results inform which skills need updating, what new skills should be
 * created, and how the SuperAgent's behavior should evolve over time.
 */

// --- Data model ---

data class SkillMetrics(
    val skillName: String,
    val totalUsages: Int,
    val successfulUsages: Int,
    val averageDurationMs: Long,
    val baselinePassRate: Double,
    val firstPassRate: Double,
    val averageIterations: Double,
    val commonFailures: List<FailurePattern>
) {
    val successRate: Double
        get() = if (totalUsages > 0) successfulUsages.toDouble() / totalUsages else 0.0

    val healthStatus: SkillHealth
        get() = when {
            totalUsages < 3 -> SkillHealth.INSUFFICIENT_DATA
            baselinePassRate >= 0.90 && firstPassRate >= 0.80 -> SkillHealth.EXCELLENT
            baselinePassRate >= 0.75 && firstPassRate >= 0.60 -> SkillHealth.GOOD
            baselinePassRate >= 0.50 -> SkillHealth.NEEDS_IMPROVEMENT
            else -> SkillHealth.CRITICAL
        }
}

enum class SkillHealth {
    EXCELLENT,
    GOOD,
    NEEDS_IMPROVEMENT,
    CRITICAL,
    INSUFFICIENT_DATA
}

data class FailurePattern(
    val baselineName: String,
    val failureCategory: String,
    val occurrences: Int,
    val sampleDetails: List<String>
)

data class OptimizationSuggestion(
    val targetSkill: String,
    val priority: SuggestionPriority,
    val category: SuggestionCategory,
    val title: String,
    val description: String,
    val evidence: String,
    val proposedChange: String
)

enum class SuggestionPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

enum class SuggestionCategory {
    ADD_GUIDANCE,
    UPDATE_EXAMPLES,
    ADD_BASELINE,
    REMOVE_BASELINE,
    SPLIT_SKILL,
    MERGE_SKILLS,
    ADD_FOUNDATION_SKILL,
    ADD_DOMAIN_SKILL
}

data class ProfileMetrics(
    val profileName: String,
    val totalExecutions: Int,
    val successRate: Double,
    val averageDurationMs: Long,
    val baselinePassRate: Double,
    val firstPassRate: Double,
    val averageIterations: Double,
    val skillBreakdown: List<SkillMetrics>
)

data class FeedbackReport(
    val dateRange: Pair<LocalDate, LocalDate>,
    val profileMetrics: List<ProfileMetrics>,
    val skillMetrics: List<SkillMetrics>,
    val suggestions: List<OptimizationSuggestion>,
    val trends: TrendAnalysis
) {
    fun toMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# Skill Feedback Analysis Report")
        sb.appendLine()
        sb.appendLine("**Date Range**: ${dateRange.first} to ${dateRange.second}")
        sb.appendLine("**Generated**: ${LocalDate.now()}")
        sb.appendLine()

        // Executive Summary
        sb.appendLine("## Executive Summary")
        sb.appendLine()
        val totalExecutions = profileMetrics.sumOf { it.totalExecutions }
        val avgSuccess = if (profileMetrics.isNotEmpty()) {
            profileMetrics.map { it.successRate }.average()
        } else 0.0
        val avgBaseline = if (profileMetrics.isNotEmpty()) {
            profileMetrics.map { it.baselinePassRate }.average()
        } else 0.0
        sb.appendLine("- Total executions analyzed: $totalExecutions")
        sb.appendLine("- Average success rate: ${"%.1f".format(avgSuccess * 100)}%")
        sb.appendLine("- Average baseline pass rate: ${"%.1f".format(avgBaseline * 100)}%")
        sb.appendLine("- Optimization suggestions: ${suggestions.size}")
        sb.appendLine()

        // Profile Metrics
        sb.appendLine("## Profile Performance")
        sb.appendLine()
        sb.appendLine("| Profile | Executions | Success Rate | Baseline Pass | First Pass | Avg Iterations |")
        sb.appendLine("|---------|-----------|-------------|---------------|------------|----------------|")
        for (pm in profileMetrics) {
            sb.appendLine("| ${pm.profileName} | ${pm.totalExecutions} | ${
                "%.0f".format(pm.successRate * 100)
            }% | ${"%.0f".format(pm.baselinePassRate * 100)}% | ${
                "%.0f".format(pm.firstPassRate * 100)
            }% | ${"%.1f".format(pm.averageIterations)} |")
        }
        sb.appendLine()

        // Skill Health Dashboard
        sb.appendLine("## Skill Health Dashboard")
        sb.appendLine()
        sb.appendLine("| Skill | Usages | Health | Pass Rate | Baseline Pass | Common Failures |")
        sb.appendLine("|-------|--------|--------|-----------|---------------|-----------------|")
        for (sm in skillMetrics.sortedBy { it.healthStatus.ordinal }) {
            val healthIcon = when (sm.healthStatus) {
                SkillHealth.EXCELLENT -> "Excellent"
                SkillHealth.GOOD -> "Good"
                SkillHealth.NEEDS_IMPROVEMENT -> "Needs Work"
                SkillHealth.CRITICAL -> "CRITICAL"
                SkillHealth.INSUFFICIENT_DATA -> "No Data"
            }
            val topFailure = sm.commonFailures.firstOrNull()?.failureCategory ?: "-"
            sb.appendLine("| ${sm.skillName} | ${sm.totalUsages} | $healthIcon | ${
                "%.0f".format(sm.successRate * 100)
            }% | ${"%.0f".format(sm.baselinePassRate * 100)}% | $topFailure |")
        }
        sb.appendLine()

        // Optimization Suggestions
        if (suggestions.isNotEmpty()) {
            sb.appendLine("## Optimization Suggestions")
            sb.appendLine()

            val byPriority = suggestions.groupBy { it.priority }
            for (priority in SuggestionPriority.entries) {
                val prioritySuggestions = byPriority[priority] ?: continue
                sb.appendLine("### ${priority.name} Priority")
                sb.appendLine()
                for ((index, suggestion) in prioritySuggestions.withIndex()) {
                    sb.appendLine("#### ${index + 1}. ${suggestion.title}")
                    sb.appendLine("- **Target Skill**: ${suggestion.targetSkill}")
                    sb.appendLine("- **Category**: ${suggestion.category.name}")
                    sb.appendLine("- **Description**: ${suggestion.description}")
                    sb.appendLine("- **Evidence**: ${suggestion.evidence}")
                    sb.appendLine("- **Proposed Change**: ${suggestion.proposedChange}")
                    sb.appendLine()
                }
            }
        }

        // Trends
        sb.appendLine("## Trends")
        sb.appendLine()
        sb.appendLine("- Success rate trend: ${trends.successRateTrend}")
        sb.appendLine("- Baseline pass rate trend: ${trends.baselinePassRateTrend}")
        sb.appendLine("- Average iteration trend: ${trends.iterationTrend}")
        sb.appendLine("- Duration trend: ${trends.durationTrend}")
        sb.appendLine()

        return sb.toString()
    }
}

data class TrendAnalysis(
    val successRateTrend: String,
    val baselinePassRateTrend: String,
    val iterationTrend: String,
    val durationTrend: String
)

// --- Skill Feedback Analyzer implementation ---

class SkillFeedbackAnalyzer(
    private val executionLogger: ExecutionLogger,
    private val projectRoot: String = System.getProperty("user.dir") ?: "."
) {
    /**
     * Analyze skill effectiveness over a date range and generate a feedback report.
     *
     * @param startDate Start of analysis period (inclusive)
     * @param endDate End of analysis period (inclusive)
     * @return FeedbackReport with metrics, suggestions, and trends
     */
    fun analyze(startDate: LocalDate, endDate: LocalDate): FeedbackReport {
        val executionPaths = executionLogger.getExecutionsByDateRange(startDate, endDate)
        val executions = executionPaths.map { path ->
            ExecutionData(path, File(path).readText())
        }

        val profileMetrics = computeProfileMetrics(executions)
        val skillMetrics = computeSkillMetrics(executions)
        val suggestions = generateSuggestions(profileMetrics, skillMetrics, executions)
        val trends = analyzeTrends(executions, startDate, endDate)

        return FeedbackReport(
            dateRange = Pair(startDate, endDate),
            profileMetrics = profileMetrics,
            skillMetrics = skillMetrics,
            suggestions = suggestions,
            trends = trends
        )
    }

    /**
     * Compute metrics per skill profile.
     */
    private fun computeProfileMetrics(executions: List<ExecutionData>): List<ProfileMetrics> {
        val byProfile = executions.groupBy { it.stage }

        return byProfile.map { (profile, execs) ->
            val totalExecutions = execs.size
            val successfulExecutions = execs.count { it.isSuccessful }
            val baselinePasses = execs.sumOf { it.baselinePassCount }
            val baselineTotal = execs.sumOf { it.baselineTotalCount }
            val firstPassCount = execs.count { it.iterations == 1 && it.isSuccessful }
            val avgIterations = if (execs.isNotEmpty()) {
                execs.map { it.iterations.toDouble() }.average()
            } else 0.0
            val avgDuration = if (execs.isNotEmpty()) {
                execs.map { it.durationMs }.average().toLong()
            } else 0L

            ProfileMetrics(
                profileName = profile,
                totalExecutions = totalExecutions,
                successRate = if (totalExecutions > 0) successfulExecutions.toDouble() / totalExecutions else 0.0,
                averageDurationMs = avgDuration,
                baselinePassRate = if (baselineTotal > 0) baselinePasses.toDouble() / baselineTotal else 1.0,
                firstPassRate = if (totalExecutions > 0) firstPassCount.toDouble() / totalExecutions else 0.0,
                averageIterations = avgIterations,
                skillBreakdown = emptyList()
            )
        }
    }

    /**
     * Compute metrics per individual skill.
     */
    private fun computeSkillMetrics(executions: List<ExecutionData>): List<SkillMetrics> {
        // Collect all unique skills mentioned across executions
        val skillUsages = mutableMapOf<String, MutableList<ExecutionData>>()

        for (exec in executions) {
            for (skill in exec.allSkills) {
                skillUsages.getOrPut(skill) { mutableListOf() }.add(exec)
            }
        }

        return skillUsages.map { (skillName, usages) ->
            val successfulUsages = usages.count { it.isSuccessful }
            val avgDuration = if (usages.isNotEmpty()) {
                usages.map { it.durationMs }.average().toLong()
            } else 0L
            val baselinePasses = usages.sumOf { it.baselinePassCount }
            val baselineTotal = usages.sumOf { it.baselineTotalCount }
            val firstPassCount = usages.count { it.iterations == 1 && it.isSuccessful }
            val avgIterations = if (usages.isNotEmpty()) {
                usages.map { it.iterations.toDouble() }.average()
            } else 0.0

            // Identify common failure patterns
            val failurePatterns = identifyFailurePatterns(skillName, usages)

            SkillMetrics(
                skillName = skillName,
                totalUsages = usages.size,
                successfulUsages = successfulUsages,
                averageDurationMs = avgDuration,
                baselinePassRate = if (baselineTotal > 0) baselinePasses.toDouble() / baselineTotal else 1.0,
                firstPassRate = if (usages.isNotEmpty()) firstPassCount.toDouble() / usages.size else 0.0,
                averageIterations = avgIterations,
                commonFailures = failurePatterns
            )
        }
    }

    /**
     * Identify common failure patterns for a specific skill.
     */
    private fun identifyFailurePatterns(
        skillName: String,
        usages: List<ExecutionData>
    ): List<FailurePattern> {
        val failureCategories = mutableMapOf<String, MutableList<String>>()

        for (usage in usages) {
            val content = usage.rawContent
            // Find failed baselines
            val failedBaselines = Regex("\"name\":\\s*\"([^\"]+)\"[^}]*\"passed\":\\s*false")
                .findAll(content)
            for (match in failedBaselines) {
                val baselineName = match.groupValues[1]
                val details = extractNearbyDetails(content, match.range.first)
                failureCategories.getOrPut(baselineName) { mutableListOf() }.add(details)
            }
        }

        return failureCategories.map { (baseline, details) ->
            FailurePattern(
                baselineName = baseline,
                failureCategory = categorizeFailure(baseline, details),
                occurrences = details.size,
                sampleDetails = details.take(3)
            )
        }.sortedByDescending { it.occurrences }
    }

    /**
     * Generate optimization suggestions based on metrics analysis.
     */
    private fun generateSuggestions(
        profileMetrics: List<ProfileMetrics>,
        skillMetrics: List<SkillMetrics>,
        executions: List<ExecutionData>
    ): List<OptimizationSuggestion> {
        val suggestions = mutableListOf<OptimizationSuggestion>()

        // Suggestion: Skills with critical health need immediate attention
        for (skill in skillMetrics.filter { it.healthStatus == SkillHealth.CRITICAL }) {
            suggestions.add(
                OptimizationSuggestion(
                    targetSkill = skill.skillName,
                    priority = SuggestionPriority.CRITICAL,
                    category = SuggestionCategory.ADD_GUIDANCE,
                    title = "Skill '${skill.skillName}' has critical health status",
                    description = "This skill has a baseline pass rate of ${
                        "%.0f".format(skill.baselinePassRate * 100)
                    }% and a success rate of ${"%.0f".format(skill.successRate * 100)}%.",
                    evidence = "Based on ${skill.totalUsages} usages with ${
                        skill.commonFailures.firstOrNull()?.occurrences ?: 0
                    } failures in the most common failure category.",
                    proposedChange = "Review the skill documentation and add specific guidance for: " +
                            (skill.commonFailures.take(3).joinToString(", ") { it.failureCategory })
                )
            )
        }

        // Suggestion: Profiles with low first-pass rates need better Observe/Orient guidance
        for (profile in profileMetrics.filter { it.firstPassRate < 0.50 && it.totalExecutions >= 5 }) {
            suggestions.add(
                OptimizationSuggestion(
                    targetSkill = "${profile.profileName}-profile",
                    priority = SuggestionPriority.HIGH,
                    category = SuggestionCategory.ADD_GUIDANCE,
                    title = "Low first-pass rate in ${profile.profileName} profile",
                    description = "Only ${"%.0f".format(profile.firstPassRate * 100)}% of executions " +
                            "pass on the first OODA iteration. Most tasks require ${
                                "%.1f".format(profile.averageIterations)
                            } iterations on average.",
                    evidence = "Based on ${profile.totalExecutions} executions.",
                    proposedChange = "Strengthen the Observe and Orient phases in the skill profile " +
                            "with more specific checklists and decision criteria to reduce rework."
                )
            )
        }

        // Suggestion: Skills with high iteration counts need better examples
        for (skill in skillMetrics.filter { it.averageIterations > 2.0 && it.totalUsages >= 3 }) {
            suggestions.add(
                OptimizationSuggestion(
                    targetSkill = skill.skillName,
                    priority = SuggestionPriority.MEDIUM,
                    category = SuggestionCategory.UPDATE_EXAMPLES,
                    title = "High iteration count for '${skill.skillName}'",
                    description = "Tasks using this skill average ${
                        "%.1f".format(skill.averageIterations)
                    } OODA iterations. Adding concrete examples may reduce iterations.",
                    evidence = "Average of ${skill.totalUsages} usages.",
                    proposedChange = "Add 2-3 concrete, detailed examples to the skill SKILL.md file " +
                            "showing the expected output format and common patterns."
                )
            )
        }

        // Suggestion: Security baseline failures suggest need for security-focused guidance
        val securityFailureRate = skillMetrics
            .flatMap { it.commonFailures }
            .filter { it.baselineName.contains("security") }
            .sumOf { it.occurrences }
        if (securityFailureRate >= 5) {
            suggestions.add(
                OptimizationSuggestion(
                    targetSkill = "code-generation",
                    priority = SuggestionPriority.HIGH,
                    category = SuggestionCategory.ADD_GUIDANCE,
                    title = "Recurring security baseline failures",
                    description = "Security baselines have failed $securityFailureRate times across all skills.",
                    evidence = "Aggregated from all skill execution data.",
                    proposedChange = "Add a dedicated 'Security Patterns' section to the code-generation skill " +
                            "with specific examples of secure coding patterns (parameterized queries, " +
                            "output encoding, secrets management)."
                )
            )
        }

        // Suggestion: If a stage has no executions, it may need better routing
        val activeStages = profileMetrics.map { it.profileName }.toSet()
        val allStages = listOf("PLANNING", "DESIGN", "DEVELOPMENT", "TESTING", "OPERATIONS")
        for (stage in allStages) {
            if (stage !in activeStages && executions.size >= 20) {
                suggestions.add(
                    OptimizationSuggestion(
                        targetSkill = "${stage.lowercase()}-profile",
                        priority = SuggestionPriority.LOW,
                        category = SuggestionCategory.ADD_GUIDANCE,
                        title = "No executions for $stage profile",
                        description = "The $stage profile has not been used despite ${executions.size} total executions.",
                        evidence = "Zero executions in the analysis period.",
                        proposedChange = "Review whether this profile is being correctly routed to. " +
                                "Consider adding more routing keywords or improving auto-detection."
                    )
                )
            }
        }

        return suggestions.sortedBy { it.priority.ordinal }
    }

    /**
     * Analyze trends over the date range.
     */
    private fun analyzeTrends(
        executions: List<ExecutionData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): TrendAnalysis {
        if (executions.size < 4) {
            return TrendAnalysis(
                successRateTrend = "Insufficient data",
                baselinePassRateTrend = "Insufficient data",
                iterationTrend = "Insufficient data",
                durationTrend = "Insufficient data"
            )
        }

        // Split executions into first half and second half
        val midpoint = executions.size / 2
        val firstHalf = executions.take(midpoint)
        val secondHalf = executions.drop(midpoint)

        val firstSuccessRate = firstHalf.count { it.isSuccessful }.toDouble() / firstHalf.size
        val secondSuccessRate = secondHalf.count { it.isSuccessful }.toDouble() / secondHalf.size
        val successTrend = trendDescription(firstSuccessRate, secondSuccessRate)

        val firstBaselineRate = computeBaselineRate(firstHalf)
        val secondBaselineRate = computeBaselineRate(secondHalf)
        val baselineTrend = trendDescription(firstBaselineRate, secondBaselineRate)

        val firstAvgIter = firstHalf.map { it.iterations.toDouble() }.average()
        val secondAvgIter = secondHalf.map { it.iterations.toDouble() }.average()
        val iterTrend = trendDescription(1.0 / firstAvgIter, 1.0 / secondAvgIter) // Invert: lower is better

        val firstAvgDuration = firstHalf.map { it.durationMs.toDouble() }.average()
        val secondAvgDuration = secondHalf.map { it.durationMs.toDouble() }.average()
        val durationTrend = trendDescription(1.0 / firstAvgDuration, 1.0 / secondAvgDuration) // Invert: lower is better

        return TrendAnalysis(
            successRateTrend = successTrend,
            baselinePassRateTrend = baselineTrend,
            iterationTrend = iterTrend,
            durationTrend = durationTrend
        )
    }

    // --- Utility methods ---

    private fun computeBaselineRate(executions: List<ExecutionData>): Double {
        val passes = executions.sumOf { it.baselinePassCount }
        val total = executions.sumOf { it.baselineTotalCount }
        return if (total > 0) passes.toDouble() / total else 1.0
    }

    private fun trendDescription(first: Double, second: Double): String {
        val change = second - first
        return when {
            change > 0.10 -> "Improving (+${"%.0f".format(change * 100)}%)"
            change > 0.02 -> "Slightly improving (+${"%.0f".format(change * 100)}%)"
            change < -0.10 -> "Declining (${"%.0f".format(change * 100)}%)"
            change < -0.02 -> "Slightly declining (${"%.0f".format(change * 100)}%)"
            else -> "Stable"
        }
    }

    private fun categorizeFailure(baselineName: String, details: List<String>): String {
        return when {
            baselineName.contains("code-style") -> "code-style-violation"
            baselineName.contains("security") -> "security-vulnerability"
            baselineName.contains("test-coverage") -> "insufficient-test-coverage"
            baselineName.contains("api-contract") -> "api-spec-mismatch"
            baselineName.contains("architecture") -> "architecture-violation"
            else -> "unknown-failure"
        }
    }

    private fun extractNearbyDetails(content: String, position: Int): String {
        val start = maxOf(0, position - 100)
        val end = minOf(content.length, position + 200)
        return content.substring(start, end).replace("\n", " ").trim()
    }

    /**
     * Internal data class for parsed execution data.
     */
    private data class ExecutionData(
        val path: String,
        val rawContent: String
    ) {
        val stage: String by lazy {
            Regex("\"stage\":\\s*\"(\\w+)\"").find(rawContent)?.groupValues?.get(1) ?: "UNKNOWN"
        }

        val isSuccessful: Boolean by lazy {
            rawContent.contains("\"success\": true")
        }

        val iterations: Int by lazy {
            Regex("\"loopIterations\":\\s*(\\d+)").find(rawContent)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        }

        val durationMs: Long by lazy {
            Regex("\"totalDurationMs\":\\s*(\\d+)").find(rawContent)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        }

        val baselinePassCount: Int by lazy {
            Regex("\"passed\":\\s*true").findAll(rawContent).count()
        }

        val baselineTotalCount: Int by lazy {
            Regex("\"passed\":\\s*(true|false)").findAll(rawContent).count()
        }

        val allSkills: List<String> by lazy {
            val skills = mutableListOf<String>()
            for (field in listOf("foundationSkills", "domainSkills", "deliverySkills")) {
                val match = Regex("\"$field\":\\s*\\[([^\\]]*)\\]").find(rawContent)
                if (match != null) {
                    skills.addAll(
                        match.groupValues[1]
                            .split(",")
                            .map { it.trim().removeSurrounding("\"") }
                            .filter { it.isNotEmpty() }
                    )
                }
            }
            skills
        }
    }
}

// --- Main entry point for standalone execution ---

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0) ?: System.getProperty("user.dir") ?: "."
    val daysBack = args.getOrNull(1)?.toLongOrNull() ?: 14

    val logger = ExecutionLogger(projectRoot = projectRoot)
    val analyzer = SkillFeedbackAnalyzer(executionLogger = logger, projectRoot = projectRoot)

    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(daysBack)

    println("Analyzing skill effectiveness from $startDate to $endDate...")
    val report = analyzer.analyze(startDate, endDate)

    println(report.toMarkdown())

    // Write report to file
    val reportDir = File(projectRoot).resolve("reports")
    reportDir.mkdirs()
    val reportFile = reportDir.resolve(
        "skill-feedback-${endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}.md"
    )
    reportFile.writeText(report.toMarkdown())
    println("Report written to: ${reportFile.absolutePath}")
}
