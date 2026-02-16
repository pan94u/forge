package com.forge.superagent.learning

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Asset Extractor — Analyzes execution logs to extract reusable assets and identify improvements.
 *
 * This component implements the "outer learning loop" of the dual-loop architecture.
 * It processes execution logs to:
 * 1. Detect new code conventions that should become Foundation Skills
 * 2. Identify knowledge gaps where the SuperAgent struggled
 * 3. Flag poorly performing skills that need revision
 * 4. Discover domain patterns that should become Domain Skills
 *
 * The extracted assets feed back into the skill system, improving future executions.
 */

// --- Data model for extracted assets ---

enum class AssetType {
    CODE_CONVENTION,
    DOMAIN_PATTERN,
    KNOWLEDGE_GAP,
    SKILL_DEFICIENCY,
    BEST_PRACTICE,
    ANTI_PATTERN
}

enum class AssetPriority {
    CRITICAL,   // Caused failures, must address immediately
    HIGH,       // Recurring issue, should address soon
    MEDIUM,     // Improvement opportunity
    LOW         // Nice to have
}

data class ExtractedAsset(
    val type: AssetType,
    val priority: AssetPriority,
    val title: String,
    val description: String,
    val evidence: List<String>,
    val suggestedAction: String,
    val sourceExecutions: List<String>,
    val detectedDate: LocalDate = LocalDate.now()
) {
    fun toReport(): String {
        val sb = StringBuilder()
        sb.appendLine("### ${priority.name}: $title")
        sb.appendLine("**Type**: ${type.name}")
        sb.appendLine("**Description**: $description")
        sb.appendLine("**Evidence**:")
        evidence.forEach { sb.appendLine("  - $it") }
        sb.appendLine("**Suggested Action**: $suggestedAction")
        sb.appendLine("**Source Executions**: ${sourceExecutions.size} execution(s)")
        return sb.toString()
    }
}

data class ExtractionReport(
    val dateRange: Pair<LocalDate, LocalDate>,
    val executionsAnalyzed: Int,
    val assets: List<ExtractedAsset>,
    val summary: ExtractionSummary
) {
    fun toMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# Asset Extraction Report")
        sb.appendLine()
        sb.appendLine("**Date Range**: ${dateRange.first} to ${dateRange.second}")
        sb.appendLine("**Executions Analyzed**: $executionsAnalyzed")
        sb.appendLine()
        sb.appendLine("## Summary")
        sb.appendLine("- New conventions detected: ${summary.newConventions}")
        sb.appendLine("- Knowledge gaps identified: ${summary.knowledgeGaps}")
        sb.appendLine("- Skill deficiencies found: ${summary.skillDeficiencies}")
        sb.appendLine("- Domain patterns discovered: ${summary.domainPatterns}")
        sb.appendLine("- Best practices identified: ${summary.bestPractices}")
        sb.appendLine("- Anti-patterns detected: ${summary.antiPatterns}")
        sb.appendLine()

        // Group by priority
        val byPriority = assets.groupBy { it.priority }
        for (priority in AssetPriority.entries) {
            val priorityAssets = byPriority[priority] ?: continue
            sb.appendLine("## ${priority.name} Priority (${priorityAssets.size})")
            sb.appendLine()
            priorityAssets.forEach { asset ->
                sb.appendLine(asset.toReport())
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}

data class ExtractionSummary(
    val newConventions: Int,
    val knowledgeGaps: Int,
    val skillDeficiencies: Int,
    val domainPatterns: Int,
    val bestPractices: Int,
    val antiPatterns: Int
)

// --- Asset Extractor implementation ---

class AssetExtractor(
    private val executionLogger: ExecutionLogger,
    private val projectRoot: String = System.getProperty("user.dir") ?: "."
) {
    /**
     * Run a full extraction analysis over a date range.
     *
     * @param startDate Start of analysis period (inclusive)
     * @param endDate End of analysis period (inclusive)
     * @return ExtractionReport with all discovered assets
     */
    fun extract(startDate: LocalDate, endDate: LocalDate): ExtractionReport {
        val executionPaths = executionLogger.getExecutionsByDateRange(startDate, endDate)
        val executionContents = executionPaths.map { path ->
            path to File(path).readText()
        }

        val assets = mutableListOf<ExtractedAsset>()

        // Run all extraction passes
        assets.addAll(detectNewConventions(executionContents))
        assets.addAll(identifyKnowledgeGaps(executionContents))
        assets.addAll(flagPoorlyPerformingSkills(executionContents))
        assets.addAll(discoverDomainPatterns(executionContents))
        assets.addAll(detectBestPractices(executionContents))
        assets.addAll(detectAntiPatterns(executionContents))

        val summary = ExtractionSummary(
            newConventions = assets.count { it.type == AssetType.CODE_CONVENTION },
            knowledgeGaps = assets.count { it.type == AssetType.KNOWLEDGE_GAP },
            skillDeficiencies = assets.count { it.type == AssetType.SKILL_DEFICIENCY },
            domainPatterns = assets.count { it.type == AssetType.DOMAIN_PATTERN },
            bestPractices = assets.count { it.type == AssetType.BEST_PRACTICE },
            antiPatterns = assets.count { it.type == AssetType.ANTI_PATTERN }
        )

        return ExtractionReport(
            dateRange = Pair(startDate, endDate),
            executionsAnalyzed = executionContents.size,
            assets = assets.sortedBy { it.priority.ordinal },
            summary = summary
        )
    }

    /**
     * Detect new code conventions from execution patterns.
     *
     * Looks for:
     * - Code style baseline failures that reveal unwritten conventions
     * - Consistent patterns in successful code generation that are not yet documented
     * - HITL feedback that mentions coding standards
     */
    private fun detectNewConventions(executions: List<Pair<String, String>>): List<ExtractedAsset> {
        val assets = mutableListOf<ExtractedAsset>()

        // Analyze code-style baseline failures for recurring patterns
        val styleFailures = mutableMapOf<String, MutableList<String>>()

        for ((path, content) in executions) {
            if (content.contains("code-style-baseline") && content.contains("\"passed\": false")) {
                // Extract failure details
                val failureDetail = extractFieldValue(content, "failureDetails")
                if (failureDetail != null) {
                    // Group similar failures
                    val category = categorizeStyleFailure(failureDetail)
                    styleFailures.getOrPut(category) { mutableListOf() }.add(path)
                }
            }
        }

        // If the same style failure appears 3+ times, it's likely a missing convention
        for ((category, sources) in styleFailures) {
            if (sources.size >= 3) {
                assets.add(
                    ExtractedAsset(
                        type = AssetType.CODE_CONVENTION,
                        priority = AssetPriority.HIGH,
                        title = "Undocumented convention: $category",
                        description = "Code style baseline has repeatedly failed for the same pattern, " +
                                "suggesting a convention that is not documented in Foundation Skills.",
                        evidence = sources.take(5).map { "Execution: ${File(it).name}" },
                        suggestedAction = "Add this convention to the appropriate Foundation Skill " +
                                "(e.g., kotlin-conventions or java-conventions).",
                        sourceExecutions = sources
                    )
                )
            }
        }

        // Analyze HITL feedback for convention-related comments
        for ((path, content) in executions) {
            val feedback = extractFieldValue(content, "hitlFeedback")
            if (feedback != null && (
                        feedback.contains("convention", ignoreCase = true) ||
                                feedback.contains("style", ignoreCase = true) ||
                                feedback.contains("naming", ignoreCase = true) ||
                                feedback.contains("format", ignoreCase = true)
                        )) {
                assets.add(
                    ExtractedAsset(
                        type = AssetType.CODE_CONVENTION,
                        priority = AssetPriority.MEDIUM,
                        title = "Convention feedback from code review",
                        description = "HITL reviewer provided feedback about coding conventions: $feedback",
                        evidence = listOf("HITL feedback: $feedback"),
                        suggestedAction = "Review the feedback and update Foundation Skills if the convention is general, " +
                                "or Domain Skills if it is project-specific.",
                        sourceExecutions = listOf(path)
                    )
                )
            }
        }

        return assets
    }

    /**
     * Identify knowledge gaps — areas where the SuperAgent lacks information.
     *
     * Looks for:
     * - MCP server queries that returned empty results
     * - Multiple OODA loop iterations on the same task (suggesting confusion)
     * - Explicit error messages about missing context
     */
    private fun identifyKnowledgeGaps(executions: List<Pair<String, String>>): List<ExtractedAsset> {
        val assets = mutableListOf<ExtractedAsset>()

        // Find executions with multiple OODA loop iterations (suggests difficulty)
        val highIterationExecutions = executions.filter { (_, content) ->
            val iterations = extractFieldValue(content, "loopIterations")?.toIntOrNull() ?: 1
            iterations >= 3
        }

        if (highIterationExecutions.size >= 2) {
            assets.add(
                ExtractedAsset(
                    type = AssetType.KNOWLEDGE_GAP,
                    priority = AssetPriority.HIGH,
                    title = "Frequent OODA loop cycling (3+ iterations)",
                    description = "Multiple executions required 3 or more OODA loop iterations, " +
                            "indicating the SuperAgent struggled to pass baselines or produce correct output.",
                    evidence = highIterationExecutions.take(5).map { (path, _) ->
                        "Execution: ${File(path).name}"
                    },
                    suggestedAction = "Analyze the specific baseline failures in these executions. " +
                            "Update relevant skills with more specific guidance for the failing scenarios.",
                    sourceExecutions = highIterationExecutions.map { it.first }
                )
            )
        }

        // Find executions with error messages indicating missing context
        for ((path, content) in executions) {
            val errorMessage = extractFieldValue(content, "errorMessage")
            if (errorMessage != null && (
                        errorMessage.contains("not found", ignoreCase = true) ||
                                errorMessage.contains("missing", ignoreCase = true) ||
                                errorMessage.contains("unavailable", ignoreCase = true) ||
                                errorMessage.contains("no data", ignoreCase = true)
                        )) {
                assets.add(
                    ExtractedAsset(
                        type = AssetType.KNOWLEDGE_GAP,
                        priority = AssetPriority.MEDIUM,
                        title = "Missing context: $errorMessage",
                        description = "Execution failed or was degraded due to missing information or context.",
                        evidence = listOf("Error: $errorMessage"),
                        suggestedAction = "Ensure the relevant knowledge is available via MCP servers " +
                                "or add it to the appropriate skill documentation.",
                        sourceExecutions = listOf(path)
                    )
                )
            }
        }

        return assets
    }

    /**
     * Flag poorly performing skills based on baseline failure patterns.
     *
     * Looks for:
     * - Skills whose associated baselines fail more than 30% of the time
     * - Skills that consistently require multiple iterations to pass
     */
    private fun flagPoorlyPerformingSkills(executions: List<Pair<String, String>>): List<ExtractedAsset> {
        val assets = mutableListOf<ExtractedAsset>()

        // Track baseline pass/fail rates per stage
        val stageBaselineStats = mutableMapOf<String, Pair<Int, Int>>() // stage -> (passes, failures)

        for ((_, content) in executions) {
            val stage = extractFieldValue(content, "stage") ?: continue

            // Count baseline results
            val passCount = Regex("\"passed\":\\s*true").findAll(content).count()
            val failCount = Regex("\"passed\":\\s*false").findAll(content).count()

            val current = stageBaselineStats.getOrPut(stage) { Pair(0, 0) }
            stageBaselineStats[stage] = Pair(current.first + passCount, current.second + failCount)
        }

        // Flag stages with high failure rates
        for ((stage, stats) in stageBaselineStats) {
            val (passes, failures) = stats
            val total = passes + failures
            if (total >= 5) { // Need enough data points
                val failRate = failures.toDouble() / total
                if (failRate > 0.30) {
                    assets.add(
                        ExtractedAsset(
                            type = AssetType.SKILL_DEFICIENCY,
                            priority = if (failRate > 0.50) AssetPriority.CRITICAL else AssetPriority.HIGH,
                            title = "High baseline failure rate in $stage stage",
                            description = "Baselines in the $stage stage fail ${
                                "%.0f".format(failRate * 100)
                            }% of the time ($failures failures out of $total runs).",
                            evidence = listOf(
                                "Pass rate: ${"%.0f".format((1 - failRate) * 100)}%",
                                "Total runs: $total",
                                "Failures: $failures"
                            ),
                            suggestedAction = "Review the $stage skill profile and its associated skills. " +
                                    "Add more specific guidance for the commonly failing baseline checks.",
                            sourceExecutions = emptyList()
                        )
                    )
                }
            }
        }

        return assets
    }

    /**
     * Discover domain patterns from successful executions.
     *
     * Looks for:
     * - Domain skills that are frequently loaded together (suggesting a composite pattern)
     * - Recurring task descriptions in specific domains
     */
    private fun discoverDomainPatterns(executions: List<Pair<String, String>>): List<ExtractedAsset> {
        val assets = mutableListOf<ExtractedAsset>()

        // Track domain skill co-occurrence
        val skillCoOccurrence = mutableMapOf<Pair<String, String>, Int>()

        for ((_, content) in executions) {
            // Extract domain skills list
            val domainSkillsMatch = Regex("\"domainSkills\":\\s*\\[([^\\]]*)\\]").find(content)
            if (domainSkillsMatch != null) {
                val skills = domainSkillsMatch.groupValues[1]
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() }

                // Record all pairs
                for (i in skills.indices) {
                    for (j in i + 1 until skills.size) {
                        val pair = if (skills[i] < skills[j]) {
                            Pair(skills[i], skills[j])
                        } else {
                            Pair(skills[j], skills[i])
                        }
                        skillCoOccurrence[pair] = (skillCoOccurrence[pair] ?: 0) + 1
                    }
                }
            }
        }

        // If two domain skills are always loaded together, suggest a composite pattern
        for ((pair, count) in skillCoOccurrence) {
            if (count >= 5) {
                assets.add(
                    ExtractedAsset(
                        type = AssetType.DOMAIN_PATTERN,
                        priority = AssetPriority.LOW,
                        title = "Frequently co-loaded domain skills: ${pair.first} + ${pair.second}",
                        description = "These two domain skills are loaded together in $count executions. " +
                                "Consider creating a composite skill or documenting their relationship.",
                        evidence = listOf(
                            "${pair.first} and ${pair.second} co-loaded $count times"
                        ),
                        suggestedAction = "Review if these skills should be merged or if a composite " +
                                "skill pattern document should be created.",
                        sourceExecutions = emptyList()
                    )
                )
            }
        }

        return assets
    }

    /**
     * Detect best practices from successful executions.
     */
    private fun detectBestPractices(executions: List<Pair<String, String>>): List<ExtractedAsset> {
        val assets = mutableListOf<ExtractedAsset>()

        // Find executions that passed all baselines on first iteration
        val firstPassSuccesses = executions.filter { (_, content) ->
            val iterations = extractFieldValue(content, "loopIterations")?.toIntOrNull() ?: 1
            val success = content.contains("\"success\": true")
            val allBaselinesPassed = !content.contains("\"passed\": false")
            iterations == 1 && success && allBaselinesPassed
        }

        if (firstPassSuccesses.size >= 10) {
            assets.add(
                ExtractedAsset(
                    type = AssetType.BEST_PRACTICE,
                    priority = AssetPriority.LOW,
                    title = "High first-pass success rate",
                    description = "${firstPassSuccesses.size} out of ${executions.size} executions " +
                            "passed all baselines on the first iteration.",
                    evidence = listOf(
                        "First-pass success rate: ${"%.0f".format(
                            firstPassSuccesses.size.toDouble() / executions.size * 100
                        )}%"
                    ),
                    suggestedAction = "Analyze the skill configurations used in successful executions " +
                            "to identify patterns that contribute to first-pass success.",
                    sourceExecutions = firstPassSuccesses.take(5).map { it.first }
                )
            )
        }

        return assets
    }

    /**
     * Detect anti-patterns from failed executions.
     */
    private fun detectAntiPatterns(executions: List<Pair<String, String>>): List<ExtractedAsset> {
        val assets = mutableListOf<ExtractedAsset>()

        // Detect security baseline failures (anti-patterns in code)
        val securityFailures = executions.filter { (_, content) ->
            content.contains("security-baseline") && content.contains("\"passed\": false")
        }

        if (securityFailures.size >= 3) {
            assets.add(
                ExtractedAsset(
                    type = AssetType.ANTI_PATTERN,
                    priority = AssetPriority.CRITICAL,
                    title = "Recurring security baseline failures",
                    description = "Security baseline has failed in ${securityFailures.size} executions. " +
                            "The SuperAgent may be generating code with security vulnerabilities.",
                    evidence = securityFailures.take(5).map { (path, _) ->
                        "Execution: ${File(path).name}"
                    },
                    suggestedAction = "Review security skill guidance. Add explicit examples of secure " +
                            "patterns for the most common failure types (hardcoded credentials, SQL injection, XSS).",
                    sourceExecutions = securityFailures.map { it.first }
                )
            )
        }

        return assets
    }

    // --- Utility methods ---

    private fun extractFieldValue(json: String, fieldName: String): String? {
        val pattern = Regex("\"$fieldName\":\\s*\"([^\"]*)\"|\"$fieldName\":\\s*(\\d+|true|false|null)")
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.ifEmpty { null }
    }

    private fun categorizeStyleFailure(failureDetail: String): String {
        return when {
            failureDetail.contains("import", ignoreCase = true) -> "import-ordering"
            failureDetail.contains("indent", ignoreCase = true) -> "indentation"
            failureDetail.contains("naming", ignoreCase = true) -> "naming-convention"
            failureDetail.contains("whitespace", ignoreCase = true) -> "whitespace"
            failureDetail.contains("line length", ignoreCase = true) -> "line-length"
            failureDetail.contains("wildcard", ignoreCase = true) -> "wildcard-import"
            else -> "other-style-issue"
        }
    }
}

// --- Main entry point for standalone execution ---

fun main(args: Array<String>) {
    val projectRoot = args.getOrNull(0) ?: System.getProperty("user.dir") ?: "."
    val daysBack = args.getOrNull(1)?.toLongOrNull() ?: 7

    val logger = ExecutionLogger(projectRoot = projectRoot)
    val extractor = AssetExtractor(executionLogger = logger, projectRoot = projectRoot)

    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(daysBack)

    println("Extracting assets from executions between $startDate and $endDate...")
    val report = extractor.extract(startDate, endDate)

    println(report.toMarkdown())

    // Write report to file
    val reportDir = File(projectRoot).resolve("reports")
    reportDir.mkdirs()
    val reportFile = reportDir.resolve(
        "asset-extraction-${endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}.md"
    )
    reportFile.writeText(report.toMarkdown())
    println("Report written to: ${reportFile.absolutePath}")
}
