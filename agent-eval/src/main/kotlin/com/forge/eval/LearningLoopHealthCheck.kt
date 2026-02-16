package com.forge.eval

import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Checks the health of the Forge Learning Loop.
 *
 * The Learning Loop is the second feedback cycle in Forge's dual-loop architecture:
 * - Delivery Loop: skill-driven task execution (the "doing" loop)
 * - Learning Loop: continuous improvement of skills, conventions, and knowledge (the "getting better" loop)
 *
 * Health metrics monitored:
 * 1. Knowledge Growth Rate: Are new conventions and patterns being captured?
 * 2. Skill Freshness: Are skills being updated as the codebase evolves?
 * 3. Convention Drift: Do mining results match documented conventions?
 * 4. Eval Trend: Are evaluation scores improving over time?
 * 5. Feedback Integration: Are post-task feedback items being incorporated?
 */
class LearningLoopHealthCheck(
    private val knowledgeBaseDir: File = File("knowledge-base"),
    private val evalReportsDir: File = File("build/eval-reports"),
    private val conventionsDir: File = File("knowledge-base/conventions"),
    private val pluginsDir: File = File("plugins")
) {

    private val logger = LoggerFactory.getLogger(LearningLoopHealthCheck::class.java)

    data class HealthReport(
        val timestamp: String,
        val overallHealth: HealthStatus,
        val metrics: List<HealthMetric>,
        val recommendations: List<String>
    )

    data class HealthMetric(
        val name: String,
        val status: HealthStatus,
        val value: Double,
        val threshold: Double,
        val description: String
    )

    enum class HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }

    /**
     * Run a comprehensive health check on the Learning Loop.
     */
    fun check(): HealthReport {
        logger.info("Running Learning Loop health check...")

        val metrics = mutableListOf<HealthMetric>()
        val recommendations = mutableListOf<String>()

        // 1. Knowledge Growth Rate
        val knowledgeGrowth = checkKnowledgeGrowth()
        metrics.add(knowledgeGrowth)
        if (knowledgeGrowth.status != HealthStatus.HEALTHY) {
            recommendations.add("Knowledge base has not grown recently. Run convention-miner to capture new patterns.")
        }

        // 2. Skill Freshness
        val skillFreshness = checkSkillFreshness()
        metrics.add(skillFreshness)
        if (skillFreshness.status != HealthStatus.HEALTHY) {
            recommendations.add("Some skills may be stale. Review skills that haven't been updated in 30+ days.")
        }

        // 3. Convention Drift
        val conventionDrift = checkConventionDrift()
        metrics.add(conventionDrift)
        if (conventionDrift.status != HealthStatus.HEALTHY) {
            recommendations.add("Convention mining results may have drifted from documented skills. Run convention-sync workflow.")
        }

        // 4. Eval Trend
        val evalTrend = checkEvalTrend()
        metrics.add(evalTrend)
        if (evalTrend.status != HealthStatus.HEALTHY) {
            recommendations.add("Evaluation scores are trending downward. Investigate recent skill or model changes.")
        }

        // 5. Documentation Coverage
        val docCoverage = checkDocCoverage()
        metrics.add(docCoverage)
        if (docCoverage.status != HealthStatus.HEALTHY) {
            recommendations.add("Generated documentation coverage is low. Run doc-generation workflow.")
        }

        // Determine overall health
        val overallHealth = when {
            metrics.any { it.status == HealthStatus.UNHEALTHY } -> HealthStatus.UNHEALTHY
            metrics.any { it.status == HealthStatus.DEGRADED } -> HealthStatus.DEGRADED
            else -> HealthStatus.HEALTHY
        }

        val report = HealthReport(
            timestamp = Instant.now().toString(),
            overallHealth = overallHealth,
            metrics = metrics,
            recommendations = recommendations
        )

        logReport(report)
        return report
    }

    private fun checkKnowledgeGrowth(): HealthMetric {
        val conventionsFiles = if (conventionsDir.exists()) {
            conventionsDir.listFiles()?.filter { it.isFile && it.extension in listOf("md", "yaml", "json") } ?: emptyList()
        } else {
            emptyList()
        }

        val recentCount = conventionsFiles.count { file ->
            val lastModified = Instant.ofEpochMilli(file.lastModified())
            lastModified.isAfter(Instant.now().minus(30, ChronoUnit.DAYS))
        }

        val ratio = if (conventionsFiles.isNotEmpty()) {
            recentCount.toDouble() / conventionsFiles.size
        } else {
            0.0
        }

        val status = when {
            conventionsFiles.isEmpty() -> HealthStatus.UNHEALTHY
            ratio >= 0.3 -> HealthStatus.HEALTHY
            ratio >= 0.1 -> HealthStatus.DEGRADED
            else -> HealthStatus.UNHEALTHY
        }

        return HealthMetric(
            name = "Knowledge Growth Rate",
            status = status,
            value = ratio,
            threshold = 0.3,
            description = "$recentCount of ${conventionsFiles.size} convention files updated in last 30 days"
        )
    }

    private fun checkSkillFreshness(): HealthMetric {
        var totalSkills = 0
        var freshSkills = 0
        val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)

        if (pluginsDir.exists()) {
            pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
                val skillsDir = File(pluginDir, "skills")
                if (skillsDir.exists()) {
                    skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                        val skillMd = File(skillDir, "SKILL.md")
                        if (skillMd.exists()) {
                            totalSkills++
                            val lastModified = Instant.ofEpochMilli(skillMd.lastModified())
                            if (lastModified.isAfter(thirtyDaysAgo)) {
                                freshSkills++
                            }
                        }
                    }
                }
            }
        }

        val ratio = if (totalSkills > 0) freshSkills.toDouble() / totalSkills else 0.0
        val status = when {
            totalSkills == 0 -> HealthStatus.UNHEALTHY
            ratio >= 0.5 -> HealthStatus.HEALTHY
            ratio >= 0.2 -> HealthStatus.DEGRADED
            else -> HealthStatus.UNHEALTHY
        }

        return HealthMetric(
            name = "Skill Freshness",
            status = status,
            value = ratio,
            threshold = 0.5,
            description = "$freshSkills of $totalSkills skills updated in last 30 days"
        )
    }

    private fun checkConventionDrift(): HealthMetric {
        // Check if convention mining reports exist and are recent
        val miningReports = if (conventionsDir.exists()) {
            conventionsDir.listFiles()?.filter {
                it.name.contains("mining") || it.name.contains("report")
            } ?: emptyList()
        } else {
            emptyList()
        }

        val hasRecentReport = miningReports.any { file ->
            val lastModified = Instant.ofEpochMilli(file.lastModified())
            lastModified.isAfter(Instant.now().minus(60, ChronoUnit.DAYS))
        }

        val status = when {
            miningReports.isEmpty() -> HealthStatus.DEGRADED
            hasRecentReport -> HealthStatus.HEALTHY
            else -> HealthStatus.DEGRADED
        }

        return HealthMetric(
            name = "Convention Drift",
            status = status,
            value = if (hasRecentReport) 1.0 else 0.0,
            threshold = 1.0,
            description = if (hasRecentReport) "Convention mining report is current"
            else "No recent convention mining report found"
        )
    }

    private fun checkEvalTrend(): HealthMetric {
        val reportFile = File(evalReportsDir, "eval-report.json")

        if (!reportFile.exists()) {
            return HealthMetric(
                name = "Eval Score Trend",
                status = HealthStatus.DEGRADED,
                value = 0.0,
                threshold = 0.8,
                description = "No evaluation reports found. Run agent-eval to establish baseline."
            )
        }

        // In a full implementation, this would compare multiple historical reports
        // For now, check if the most recent report shows a passing score
        val content = reportFile.readText()
        val passRateMatch = Regex("\"passRate\"\\s*:\\s*([\\d.]+)").find(content)
        val passRate = passRateMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        val status = when {
            passRate >= 0.8 -> HealthStatus.HEALTHY
            passRate >= 0.6 -> HealthStatus.DEGRADED
            else -> HealthStatus.UNHEALTHY
        }

        return HealthMetric(
            name = "Eval Score Trend",
            status = status,
            value = passRate,
            threshold = 0.8,
            description = "Latest eval pass rate: ${"%.1f".format(passRate * 100)}%"
        )
    }

    private fun checkDocCoverage(): HealthMetric {
        val generatedDocsDir = File(knowledgeBaseDir, "generated-docs")
        val docCount = if (generatedDocsDir.exists()) {
            generatedDocsDir.listFiles()?.count { it.isFile && it.extension == "md" } ?: 0
        } else {
            0
        }

        val status = when {
            docCount >= 10 -> HealthStatus.HEALTHY
            docCount >= 3 -> HealthStatus.DEGRADED
            else -> HealthStatus.UNHEALTHY
        }

        return HealthMetric(
            name = "Documentation Coverage",
            status = status,
            value = docCount.toDouble(),
            threshold = 10.0,
            description = "$docCount generated documentation files"
        )
    }

    private fun logReport(report: HealthReport) {
        logger.info("Learning Loop Health: {}", report.overallHealth)
        for (metric in report.metrics) {
            logger.info("  {}: {} (value={}, threshold={})",
                metric.name, metric.status, metric.value, metric.threshold)
        }
        if (report.recommendations.isNotEmpty()) {
            logger.info("Recommendations:")
            for (rec in report.recommendations) {
                logger.info("  - {}", rec)
            }
        }
    }
}
