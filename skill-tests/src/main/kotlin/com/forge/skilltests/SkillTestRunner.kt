package com.forge.skilltests

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Runs skill validation across all plugins and reports results.
 *
 * The SkillTestRunner discovers all SKILL.md files in the plugins directory,
 * validates each one using SkillValidator, and produces a summary report.
 * This is designed to run in CI pipelines to catch skill quality regressions.
 *
 * Usage:
 * ```kotlin
 * val runner = SkillTestRunner(pluginsDir = File("plugins"))
 * val report = runner.runAll()
 * if (!report.allPassed) exitProcess(1)
 * ```
 */
class SkillTestRunner(
    private val pluginsDir: File = File("plugins"),
    private val validator: SkillValidator = SkillValidator(),
    private val strictMode: Boolean = false
) {

    private val logger = LoggerFactory.getLogger(SkillTestRunner::class.java)

    /**
     * Summary report for a complete validation run.
     */
    data class TestReport(
        val totalSkills: Int,
        val passedSkills: Int,
        val failedSkills: Int,
        val totalErrors: Int,
        val totalWarnings: Int,
        val results: List<SkillValidator.ValidationResult>,
        val allPassed: Boolean
    ) {
        val passRate: Double get() = if (totalSkills > 0) passedSkills.toDouble() / totalSkills else 0.0
    }

    /**
     * Run validation on all skills across all plugins.
     *
     * @return Test report with results for every skill
     */
    fun runAll(): TestReport {
        logger.info("Discovering skills in: {}", pluginsDir.absolutePath)

        if (!pluginsDir.exists()) {
            logger.error("Plugins directory not found: {}", pluginsDir.absolutePath)
            return TestReport(0, 0, 0, 0, 0, emptyList(), true)
        }

        val results = mutableListOf<SkillValidator.ValidationResult>()

        pluginsDir.listFiles()?.filter { it.isDirectory }?.sorted()?.forEach { pluginDir ->
            val skillsDir = File(pluginDir, "skills")
            if (!skillsDir.exists()) return@forEach

            skillsDir.listFiles()?.filter { it.isDirectory }?.sorted()?.forEach { skillDir ->
                val skillMd = File(skillDir, "SKILL.md")
                if (skillMd.exists()) {
                    val relativePath = "${pluginDir.name}/skills/${skillDir.name}/SKILL.md"
                    logger.info("Validating: {}", relativePath)

                    val result = validator.validate(skillMd.readText(), relativePath)
                    results.add(result)

                    if (!result.valid) {
                        logger.error("  FAIL: {} error(s)", result.errorCount)
                        for (error in result.errors) {
                            logger.error("    [{}] {}", error.code, error.message)
                        }
                    } else {
                        logger.info("  PASS ({} warning(s))", result.warningCount)
                    }

                    for (warning in result.warnings) {
                        logger.warn("    [{}] {}", warning.code, warning.message)
                    }
                }
            }
        }

        val passedCount = results.count { it.valid }
        val failedCount = results.count { !it.valid }
        val totalErrors = results.sumOf { it.errorCount }
        val totalWarnings = results.sumOf { it.warningCount }

        val allPassed = if (strictMode) {
            totalErrors == 0 && totalWarnings == 0
        } else {
            totalErrors == 0
        }

        val report = TestReport(
            totalSkills = results.size,
            passedSkills = passedCount,
            failedSkills = failedCount,
            totalErrors = totalErrors,
            totalWarnings = totalWarnings,
            results = results,
            allPassed = allPassed
        )

        printSummary(report)
        return report
    }

    /**
     * Validate a single skill file.
     */
    fun runSingle(skillMdPath: String): SkillValidator.ValidationResult {
        val file = File(skillMdPath)
        if (!file.exists()) {
            return SkillValidator.ValidationResult(
                skillPath = skillMdPath,
                skillName = "unknown",
                valid = false,
                errors = listOf(
                    SkillValidator.ValidationError("FILE_NOT_FOUND", "File not found: $skillMdPath")
                ),
                warnings = emptyList()
            )
        }

        return validator.validate(file.readText(), skillMdPath)
    }

    /**
     * Discover all SKILL.md files without validating them.
     * Useful for listing or counting skills.
     */
    fun discoverSkills(): List<File> {
        if (!pluginsDir.exists()) return emptyList()

        val skills = mutableListOf<File>()

        pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val skillsDir = File(pluginDir, "skills")
            if (!skillsDir.exists()) return@forEach

            skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                val skillMd = File(skillDir, "SKILL.md")
                if (skillMd.exists()) {
                    skills.add(skillMd)
                }
            }
        }

        return skills.sorted()
    }

    private fun printSummary(report: TestReport) {
        println()
        println("=== Skill Validation Summary ===")
        println()
        println("Total skills:  ${report.totalSkills}")
        println("Passed:        ${report.passedSkills}")
        println("Failed:        ${report.failedSkills}")
        println("Total errors:  ${report.totalErrors}")
        println("Total warnings: ${report.totalWarnings}")
        println("Pass rate:     ${"%.1f".format(report.passRate * 100)}%")
        println()

        if (report.failedSkills > 0) {
            println("Failed skills:")
            for (result in report.results.filter { !it.valid }) {
                println("  - ${result.skillPath}")
                for (error in result.errors) {
                    println("    [${error.code}] ${error.message}")
                }
            }
            println()
        }

        if (report.allPassed) {
            println("Result: ALL PASSED")
        } else {
            println("Result: FAILED")
        }
    }
}

/**
 * Entry point for running skill tests from the command line.
 */
fun main(args: Array<String>) {
    val pluginsDir = if (args.isNotEmpty()) File(args[0]) else File("plugins")
    val strict = args.contains("--strict")

    val runner = SkillTestRunner(pluginsDir = pluginsDir, strictMode = strict)
    val report = runner.runAll()

    if (!report.allPassed) {
        System.exit(1)
    }
}
