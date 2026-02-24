package com.forge.webide.service.skill

import com.forge.webide.entity.SkillQualityRecordEntity
import com.forge.webide.repository.SkillQualityRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 3-layer quality hook for Skill script executions (Phase 8.2).
 *
 * Layer 1: Platform-wide default checks (exit code, output, error patterns, timeout)
 * Layer 2: Skill-specific rules from SKILL.md frontmatter (required_sections, forbidden_patterns, etc.)
 * Layer 3: Self-learned patterns from historical execution data (read-only suggestions)
 */
@Service
class SkillQualityHookService(
    private val qualityRecordRepository: SkillQualityRecordRepository
) {
    private val logger = LoggerFactory.getLogger(SkillQualityHookService::class.java)

    companion object {
        const val MAX_EXECUTION_TIME_MS = 60_000L

        val ERROR_PATTERNS = listOf(
            "Exception", "Error:", "Traceback", "stack trace",
            "permission denied", "FATAL", "PANIC", "segfault"
        )
    }

    /**
     * Run quality checks on a script execution result.
     * Returns a [QualityCheckResult] with status and details.
     */
    fun checkQuality(
        skillName: String,
        scriptPath: String?,
        exitCode: Int,
        stdout: String,
        stderr: String,
        executionTimeMs: Long,
        workspaceId: String? = null,
        sessionId: String? = null,
        qualityConfig: SkillQualityConfig? = null
    ): QualityCheckResult {
        val layer1 = runLayer1Checks(exitCode, stdout, stderr, executionTimeMs, qualityConfig)
        val layer2 = if (qualityConfig != null) runLayer2Checks(stdout, qualityConfig) else null

        val overallStatus = when {
            layer1.passed && (layer2?.passed != false) -> "PASSED"
            !layer1.passed && stdout.isNotBlank() -> "PARTIAL_SUCCESS"
            else -> "FAILED"
        }

        val autoFixType = when {
            overallStatus == "PARTIAL_SUCCESS" -> "output_with_errors"
            layer2?.passed == false && layer2.missingSection != null -> "missing_section"
            else -> null
        }

        // Persist quality record
        try {
            qualityRecordRepository.save(
                SkillQualityRecordEntity(
                    id = UUID.randomUUID().toString(),
                    skillName = skillName,
                    scriptPath = scriptPath,
                    workspaceId = workspaceId,
                    sessionId = sessionId,
                    exitCode = exitCode,
                    executionTimeMs = executionTimeMs,
                    outputLength = stdout.length,
                    outputSnippet = stdout.take(2000),
                    overallStatus = overallStatus,
                    layer1Passed = layer1.passed,
                    layer1Details = layer1.details.joinToString("; "),
                    layer2Passed = layer2?.passed,
                    layer2Details = layer2?.details?.joinToString("; "),
                    autoFixApplied = autoFixType != null,
                    autoFixType = autoFixType
                )
            )
        } catch (e: Exception) {
            logger.warn("Failed to persist quality record for skill {}: {}", skillName, e.message)
        }

        logger.info(
            "Skill quality check: skill={}, script={}, status={}, layer1={}, layer2={}",
            skillName, scriptPath, overallStatus, layer1.passed, layer2?.passed
        )

        return QualityCheckResult(
            overallStatus = overallStatus,
            layer1 = layer1,
            layer2 = layer2,
            autoFixType = autoFixType,
            autoFixInstruction = generateAutoFixInstruction(autoFixType, layer2)
        )
    }

    /**
     * Layer 1: Platform-wide default checks.
     */
    private fun runLayer1Checks(
        exitCode: Int,
        stdout: String,
        stderr: String,
        executionTimeMs: Long,
        qualityConfig: SkillQualityConfig?
    ): LayerCheckResult {
        val details = mutableListOf<String>()
        var passed = true

        val skipChecks = qualityConfig?.skipDefaultChecks ?: emptyList()

        // Check 1: exit code == 0
        if ("exit_code" !in skipChecks && exitCode != 0) {
            details.add("Exit code $exitCode (expected 0)")
            passed = false
        }

        // Check 2: output not empty
        if ("empty_output" !in skipChecks && stdout.isBlank()) {
            details.add("Output is empty")
            passed = false
        }

        // Check 3: no common error patterns
        if ("error_patterns" !in skipChecks) {
            val combined = stdout + "\n" + stderr
            val matchedPatterns = ERROR_PATTERNS.filter { pattern ->
                combined.contains(pattern, ignoreCase = true)
            }
            if (matchedPatterns.isNotEmpty()) {
                details.add("Error patterns found: ${matchedPatterns.joinToString(", ")}")
                passed = false
            }
        }

        // Check 4: execution time < 60s
        if ("timeout" !in skipChecks && executionTimeMs > MAX_EXECUTION_TIME_MS) {
            details.add("Execution time ${executionTimeMs}ms exceeds ${MAX_EXECUTION_TIME_MS}ms")
            passed = false
        }

        if (details.isEmpty()) details.add("All platform checks passed")

        return LayerCheckResult(passed = passed, details = details)
    }

    /**
     * Layer 2: Skill-specific rules from SKILL.md frontmatter.
     */
    private fun runLayer2Checks(
        stdout: String,
        config: SkillQualityConfig
    ): LayerCheckResult {
        val details = mutableListOf<String>()
        var passed = true
        var missingSection: String? = null

        // Check required sections
        for (section in config.requiredSections) {
            if (!stdout.contains(section)) {
                details.add("Missing required section: '$section'")
                passed = false
                if (missingSection == null) missingSection = section
            }
        }

        // Check forbidden patterns
        for (pattern in config.forbiddenPatterns) {
            if (stdout.contains(pattern, ignoreCase = true)) {
                details.add("Forbidden pattern found: '$pattern'")
                passed = false
            }
        }

        // Check minimum output length
        if (config.minOutputLength > 0 && stdout.length < config.minOutputLength) {
            details.add("Output length ${stdout.length} below minimum ${config.minOutputLength}")
            passed = false
        }

        if (details.isEmpty()) details.add("All skill-specific checks passed")

        return LayerCheckResult(passed = passed, details = details, missingSection = missingSection)
    }

    private fun generateAutoFixInstruction(
        autoFixType: String?,
        layer2: LayerCheckResult?
    ): String? {
        return when (autoFixType) {
            "missing_section" -> {
                val section = layer2?.missingSection ?: return null
                "请在输出中补充缺失的「$section」段落。"
            }
            "output_with_errors" -> "脚本执行有错误但产生了部分输出，请检查并修复错误后重新执行。"
            else -> null
        }
    }
}

// ---- Data classes ----

data class QualityCheckResult(
    val overallStatus: String,
    val layer1: LayerCheckResult,
    val layer2: LayerCheckResult?,
    val autoFixType: String?,
    val autoFixInstruction: String?
)

data class LayerCheckResult(
    val passed: Boolean,
    val details: List<String>,
    val missingSection: String? = null
)
