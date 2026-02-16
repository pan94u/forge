package com.forge.superagent.learning

import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Execution Logger — Records every SuperAgent execution for the outer learning loop.
 *
 * This logger captures the complete context of each SuperAgent OODA cycle:
 * inputs, outputs, skill selections, baseline results, MCP calls, and timing.
 * The data feeds into the asset-extractor and skill-feedback-analyzer for
 * continuous improvement.
 *
 * Log files are written to a structured directory hierarchy:
 *   logs/
 *     2025-01-15/
 *       exec-{uuid}.json
 *       exec-{uuid}.json
 *     2025-01-16/
 *       exec-{uuid}.json
 */

// --- Data model for execution records ---

enum class DeliveryStage {
    PLANNING,
    DESIGN,
    DEVELOPMENT,
    TESTING,
    OPERATIONS
}

enum class OodaPhase {
    OBSERVE,
    ORIENT,
    DECIDE,
    ACT
}

data class SkillSelection(
    val profileName: String,
    val foundationSkills: List<String>,
    val domainSkills: List<String>,
    val deliverySkills: List<String>,
    val selectionReason: String
)

data class McpCall(
    val server: String,
    val method: String,
    val parameters: Map<String, String>,
    val responseSize: Int,
    val duration: Duration,
    val success: Boolean,
    val errorMessage: String? = null
)

data class BaselineResultRecord(
    val name: String,
    val passed: Boolean,
    val duration: Duration,
    val failureDetails: String? = null
)

data class OodaCycleRecord(
    val phase: OodaPhase,
    val startTime: Instant,
    val endTime: Instant,
    val inputSummary: String,
    val outputSummary: String,
    val mcpCalls: List<McpCall> = emptyList(),
    val filesRead: List<String> = emptyList(),
    val filesWritten: List<String> = emptyList(),
    val decisions: List<String> = emptyList()
) {
    val duration: Duration get() = Duration.between(startTime, endTime)
}

data class ExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Instant = Instant.now(),
    val stage: DeliveryStage,
    val taskDescription: String,
    val userContext: String? = null,
    val skillSelection: SkillSelection,
    val oodaCycles: List<OodaCycleRecord>,
    val baselineResults: List<BaselineResultRecord>,
    val hitlCheckpointReached: Boolean,
    val hitlApproved: Boolean? = null,
    val hitlFeedback: String? = null,
    val totalDuration: Duration,
    val success: Boolean,
    val errorMessage: String? = null,
    val artifactsProduced: List<String> = emptyList(),
    val loopIterations: Int = 1
) {
    /**
     * Serialize to a JSON-like string format.
     * In a production system, this would use kotlinx.serialization or Jackson.
     */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("""  "id": "$id",""")
        sb.appendLine("""  "timestamp": "$timestamp",""")
        sb.appendLine("""  "stage": "${stage.name}",""")
        sb.appendLine("""  "taskDescription": "${escapeJson(taskDescription)}",""")
        sb.appendLine("""  "userContext": ${if (userContext != null) "\"${escapeJson(userContext)}\"" else "null"},""")
        sb.appendLine("""  "skillSelection": {""")
        sb.appendLine("""    "profileName": "${skillSelection.profileName}",""")
        sb.appendLine("""    "foundationSkills": [${skillSelection.foundationSkills.joinToString(", ") { "\"$it\"" }}],""")
        sb.appendLine("""    "domainSkills": [${skillSelection.domainSkills.joinToString(", ") { "\"$it\"" }}],""")
        sb.appendLine("""    "deliverySkills": [${skillSelection.deliverySkills.joinToString(", ") { "\"$it\"" }}],""")
        sb.appendLine("""    "selectionReason": "${escapeJson(skillSelection.selectionReason)}" """)
        sb.appendLine("""  },""")
        sb.appendLine("""  "oodaCycles": [""")
        oodaCycles.forEachIndexed { index, cycle ->
            sb.appendLine("""    {""")
            sb.appendLine("""      "phase": "${cycle.phase.name}",""")
            sb.appendLine("""      "startTime": "${cycle.startTime}",""")
            sb.appendLine("""      "endTime": "${cycle.endTime}",""")
            sb.appendLine("""      "durationMs": ${cycle.duration.toMillis()},""")
            sb.appendLine("""      "inputSummary": "${escapeJson(cycle.inputSummary)}",""")
            sb.appendLine("""      "outputSummary": "${escapeJson(cycle.outputSummary)}",""")
            sb.appendLine("""      "mcpCallCount": ${cycle.mcpCalls.size},""")
            sb.appendLine("""      "filesRead": ${cycle.filesRead.size},""")
            sb.appendLine("""      "filesWritten": ${cycle.filesWritten.size},""")
            sb.appendLine("""      "decisions": [${cycle.decisions.joinToString(", ") { "\"${escapeJson(it)}\"" }}]""")
            sb.appendLine("""    }${if (index < oodaCycles.size - 1) "," else ""}""")
        }
        sb.appendLine("""  ],""")
        sb.appendLine("""  "baselineResults": [""")
        baselineResults.forEachIndexed { index, result ->
            sb.appendLine("""    {""")
            sb.appendLine("""      "name": "${result.name}",""")
            sb.appendLine("""      "passed": ${result.passed},""")
            sb.appendLine("""      "durationMs": ${result.duration.toMillis()},""")
            sb.appendLine("""      "failureDetails": ${if (result.failureDetails != null) "\"${escapeJson(result.failureDetails)}\"" else "null"}""")
            sb.appendLine("""    }${if (index < baselineResults.size - 1) "," else ""}""")
        }
        sb.appendLine("""  ],""")
        sb.appendLine("""  "hitlCheckpointReached": $hitlCheckpointReached,""")
        sb.appendLine("""  "hitlApproved": ${hitlApproved ?: "null"},""")
        sb.appendLine("""  "hitlFeedback": ${if (hitlFeedback != null) "\"${escapeJson(hitlFeedback)}\"" else "null"},""")
        sb.appendLine("""  "totalDurationMs": ${totalDuration.toMillis()},""")
        sb.appendLine("""  "success": $success,""")
        sb.appendLine("""  "errorMessage": ${if (errorMessage != null) "\"${escapeJson(errorMessage)}\"" else "null"},""")
        sb.appendLine("""  "artifactsProduced": [${artifactsProduced.joinToString(", ") { "\"${escapeJson(it)}\"" }}],""")
        sb.appendLine("""  "loopIterations": $loopIterations""")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

// --- Logger implementation ---

class ExecutionLogger(
    private val logBaseDir: String = "logs",
    private val projectRoot: String = System.getProperty("user.dir") ?: "."
) {
    private val logDir: File
        get() = File(projectRoot).resolve(logBaseDir)

    /**
     * Log a complete execution record to disk.
     *
     * @param record The execution record to persist
     * @return The file path where the log was written
     */
    fun logExecution(record: ExecutionRecord): String {
        val dateDir = getDateDirectory(record.timestamp)
        dateDir.mkdirs()

        val logFile = dateDir.resolve("exec-${record.id}.json")
        logFile.writeText(record.toJson())

        println("[LOG] Execution logged: ${logFile.absolutePath}")
        return logFile.absolutePath
    }

    /**
     * Retrieve execution records for a specific date.
     *
     * @param date The date to retrieve records for
     * @return List of execution record file paths
     */
    fun getExecutionsByDate(date: LocalDate): List<String> {
        val dateDir = logDir.resolve(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        if (!dateDir.exists()) return emptyList()

        return dateDir.listFiles()
            ?.filter { it.name.startsWith("exec-") && it.name.endsWith(".json") }
            ?.map { it.absolutePath }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Retrieve execution records for a date range.
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of execution record file paths
     */
    fun getExecutionsByDateRange(startDate: LocalDate, endDate: LocalDate): List<String> {
        val result = mutableListOf<String>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            result.addAll(getExecutionsByDate(current))
            current = current.plusDays(1)
        }
        return result
    }

    /**
     * Get summary statistics for a date.
     */
    fun getDailySummary(date: LocalDate): DailySummary {
        val executions = getExecutionsByDate(date)
        var totalExecutions = 0
        var successfulExecutions = 0
        var failedExecutions = 0
        var totalBaselineRuns = 0
        var passedBaselines = 0
        var failedBaselines = 0
        var totalDurationMs = 0L
        val stageDistribution = mutableMapOf<String, Int>()

        for (path in executions) {
            totalExecutions++
            val content = File(path).readText()

            // Parse key fields from JSON (simplified parsing)
            if (content.contains("\"success\": true")) {
                successfulExecutions++
            } else {
                failedExecutions++
            }

            // Count baselines
            val baselineMatches = Regex("\"passed\":\\s*(true|false)").findAll(content)
            for (match in baselineMatches) {
                totalBaselineRuns++
                if (match.groupValues[1] == "true") passedBaselines++
                else failedBaselines++
            }

            // Extract duration
            val durationMatch = Regex("\"totalDurationMs\":\\s*(\\d+)").find(content)
            if (durationMatch != null) {
                totalDurationMs += durationMatch.groupValues[1].toLong()
            }

            // Extract stage
            val stageMatch = Regex("\"stage\":\\s*\"(\\w+)\"").find(content)
            if (stageMatch != null) {
                val stage = stageMatch.groupValues[1]
                stageDistribution[stage] = (stageDistribution[stage] ?: 0) + 1
            }
        }

        return DailySummary(
            date = date,
            totalExecutions = totalExecutions,
            successfulExecutions = successfulExecutions,
            failedExecutions = failedExecutions,
            totalBaselineRuns = totalBaselineRuns,
            passedBaselines = passedBaselines,
            failedBaselines = failedBaselines,
            totalDurationMs = totalDurationMs,
            stageDistribution = stageDistribution
        )
    }

    private fun getDateDirectory(timestamp: Instant): File {
        val date = timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
        return logDir.resolve(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
}

data class DailySummary(
    val date: LocalDate,
    val totalExecutions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val totalBaselineRuns: Int,
    val passedBaselines: Int,
    val failedBaselines: Int,
    val totalDurationMs: Long,
    val stageDistribution: Map<String, Int>
) {
    val successRate: Double
        get() = if (totalExecutions > 0) successfulExecutions.toDouble() / totalExecutions else 0.0

    val baselinePassRate: Double
        get() = if (totalBaselineRuns > 0) passedBaselines.toDouble() / totalBaselineRuns else 0.0

    val avgDurationMs: Long
        get() = if (totalExecutions > 0) totalDurationMs / totalExecutions else 0

    override fun toString(): String {
        return """
            |Daily Summary for $date:
            |  Total executions: $totalExecutions
            |  Success rate: ${"%.1f".format(successRate * 100)}%
            |  Baseline pass rate: ${"%.1f".format(baselinePassRate * 100)}%
            |  Avg duration: ${avgDurationMs}ms
            |  Stage distribution: $stageDistribution
        """.trimMargin()
    }
}

// --- Builder for convenient record creation ---

class ExecutionRecordBuilder {
    private var stage: DeliveryStage = DeliveryStage.DEVELOPMENT
    private var taskDescription: String = ""
    private var userContext: String? = null
    private var skillSelection: SkillSelection? = null
    private val oodaCycles = mutableListOf<OodaCycleRecord>()
    private val baselineResults = mutableListOf<BaselineResultRecord>()
    private var hitlCheckpointReached: Boolean = false
    private var hitlApproved: Boolean? = null
    private var hitlFeedback: String? = null
    private var startTime: Instant = Instant.now()
    private var success: Boolean = true
    private var errorMessage: String? = null
    private val artifactsProduced = mutableListOf<String>()
    private var loopIterations: Int = 1

    fun stage(stage: DeliveryStage) = apply { this.stage = stage }
    fun task(description: String) = apply { this.taskDescription = description }
    fun userContext(context: String?) = apply { this.userContext = context }
    fun skills(selection: SkillSelection) = apply { this.skillSelection = selection }
    fun addOodaCycle(cycle: OodaCycleRecord) = apply { oodaCycles.add(cycle) }
    fun addBaselineResult(result: BaselineResultRecord) = apply { baselineResults.add(result) }
    fun hitlReached(approved: Boolean?, feedback: String? = null) = apply {
        hitlCheckpointReached = true
        hitlApproved = approved
        hitlFeedback = feedback
    }
    fun failed(message: String) = apply { success = false; errorMessage = message }
    fun addArtifact(path: String) = apply { artifactsProduced.add(path) }
    fun iterations(count: Int) = apply { loopIterations = count }

    fun build(): ExecutionRecord {
        val endTime = Instant.now()
        return ExecutionRecord(
            stage = stage,
            taskDescription = taskDescription,
            userContext = userContext,
            skillSelection = skillSelection ?: SkillSelection(
                profileName = "unknown",
                foundationSkills = emptyList(),
                domainSkills = emptyList(),
                deliverySkills = emptyList(),
                selectionReason = "not specified"
            ),
            oodaCycles = oodaCycles.toList(),
            baselineResults = baselineResults.toList(),
            hitlCheckpointReached = hitlCheckpointReached,
            hitlApproved = hitlApproved,
            hitlFeedback = hitlFeedback,
            totalDuration = Duration.between(startTime, endTime),
            success = success,
            errorMessage = errorMessage,
            artifactsProduced = artifactsProduced.toList(),
            loopIterations = loopIterations
        )
    }
}

fun executionRecord(block: ExecutionRecordBuilder.() -> Unit): ExecutionRecord {
    return ExecutionRecordBuilder().apply(block).build()
}
