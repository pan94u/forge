package com.forge.webide.service

import com.forge.webide.entity.SkillUsageEntity
import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import com.forge.webide.repository.SkillUsageRepository
import com.forge.webide.service.skill.SkillCategory
import com.forge.webide.service.skill.SkillLoader
import com.forge.webide.service.skill.SkillScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Handles skill-related MCP tools: read_skill, run_skill_script, list_skills.
 */
@Service
class SkillToolHandler(
    private val skillLoader: SkillLoader,
    private val skillUsageRepository: SkillUsageRepository,
    private val skillQualityHookService: com.forge.webide.service.skill.SkillQualityHookService
) {

    private val logger = LoggerFactory.getLogger(SkillToolHandler::class.java)

    fun handle(toolName: String, args: Map<String, Any?>, workspaceId: String?): McpToolCallResponse {
        return when (toolName) {
            "read_skill" -> handleReadSkill(args)
            "run_skill_script" -> handleRunSkillScript(args, workspaceId)
            "list_skills" -> handleListSkills(args)
            else -> McpProxyService.errorResponse("Unknown skill tool: $toolName")
        }
    }

    /**
     * Read a skill's SKILL.md or sub-file content (Level 2 + Level 3).
     */
    private fun handleReadSkill(arguments: Map<String, Any?>): McpToolCallResponse {
        val skillName = arguments["skill_name"] as? String
            ?: return McpProxyService.errorResponse("'skill_name' parameter is required")

        val file = arguments["file"] as? String ?: "SKILL.md"

        if (file.contains("..")) {
            return McpProxyService.errorResponse("Path traversal not allowed")
        }

        val skill = skillLoader.loadSkill(skillName)
            ?: return McpProxyService.errorResponse("Skill '$skillName' not found. Use list_skills to see available skills.")

        if (file == "SKILL.md") {
            trackSkillUsage(skillName, "READ")
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = skill.content)),
                isError = false
            )
        }

        val skillDir = Path.of(skill.sourcePath).parent
        val targetFile = skillDir.resolve(file)

        val normalizedTarget = targetFile.normalize()
        val normalizedSkillDir = skillDir.normalize()
        if (!normalizedTarget.startsWith(normalizedSkillDir)) {
            return McpProxyService.errorResponse("Path traversal not allowed: file must be within skill directory")
        }

        if (!Files.isRegularFile(targetFile)) {
            val availableFiles = skill.subFiles.map { it.path } + skill.scripts.map { it.path }
            return McpProxyService.errorResponse(
                "File '$file' not found in skill '$skillName'. " +
                    "Available files: ${availableFiles.joinToString(", ").ifEmpty { "none" }}"
            )
        }

        val content = try {
            val raw = Files.readString(targetFile)
            if (raw.length > 50_000) {
                raw.take(50_000) + "\n\n[... truncated at 50KB ...]"
            } else {
                raw
            }
        } catch (e: Exception) {
            return McpProxyService.errorResponse("Failed to read file: ${e.message}")
        }

        trackSkillUsage(skillName, "READ")
        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = content)),
            isError = false
        )
    }

    /**
     * Execute a skill's script and return stdout + exitCode (Level 3).
     */
    private fun handleRunSkillScript(arguments: Map<String, Any?>, workspaceId: String? = null): McpToolCallResponse {
        val skillName = arguments["skill_name"] as? String
            ?: return McpProxyService.errorResponse("'skill_name' parameter is required")

        val scriptPath = arguments["script"] as? String
            ?: return McpProxyService.errorResponse("'script' parameter is required (e.g. 'scripts/validate.py')")

        if (scriptPath.contains("..")) {
            return McpProxyService.errorResponse("Path traversal not allowed")
        }

        val skill = skillLoader.loadSkill(skillName)
            ?: return McpProxyService.errorResponse("Skill '$skillName' not found")

        val scriptDef = skill.scripts.find { it.path == scriptPath }
            ?: return McpProxyService.errorResponse(
                "Script '$scriptPath' not found in skill '$skillName'. " +
                    "Available scripts: ${skill.scripts.joinToString(", ") { it.path }.ifEmpty { "none" }}"
            )

        val skillDir = Path.of(skill.sourcePath).parent
        val scriptFile = skillDir.resolve(scriptPath)

        val normalizedScript = scriptFile.normalize()
        val normalizedSkillDir = skillDir.normalize()
        if (!normalizedScript.startsWith(normalizedSkillDir)) {
            return McpProxyService.errorResponse("Path traversal not allowed")
        }

        if (!Files.isRegularFile(scriptFile)) {
            return McpProxyService.errorResponse("Script file not found: $scriptPath")
        }

        val command = when (scriptDef.language) {
            "python" -> listOf("python3", scriptFile.toString())
            "bash" -> listOf("bash", scriptFile.toString())
            "kotlin" -> listOf("kotlin", scriptFile.toString())
            else -> return McpProxyService.errorResponse("Unsupported script language: ${scriptDef.language}")
        }

        @Suppress("UNCHECKED_CAST")
        val args = when (val argsVal = arguments["args"]) {
            is List<*> -> argsVal.filterIsInstance<String>()
            is String -> argsVal.split(" ")
            else -> emptyList()
        }

        val fullCommand = command + args

        return try {
            logger.info("Executing skill script: {} (skill={})", scriptPath, skillName)
            val startTime = System.currentTimeMillis()
            val process = ProcessBuilder(fullCommand)
                .directory(skillDir.toFile())
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(60, TimeUnit.SECONDS)
            val executionTimeMs = System.currentTimeMillis() - startTime
            if (!completed) {
                process.destroyForcibly()
                return McpProxyService.errorResponse("Script execution timed out after 60 seconds")
            }

            val stdout = process.inputStream.bufferedReader().readText().take(50_000)
            val stderr = process.errorStream.bufferedReader().readText().take(10_000)
            val exitCode = process.exitValue()

            // Phase 8.2: Run quality hook
            val qualityResult = try {
                skillQualityHookService.checkQuality(
                    skillName = skillName,
                    scriptPath = scriptPath,
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                    executionTimeMs = executionTimeMs,
                    workspaceId = workspaceId,
                    qualityConfig = skill.quality?.let {
                        com.forge.webide.service.skill.SkillQualityConfig(
                            requiredSections = it.requiredSections,
                            forbiddenPatterns = it.forbiddenPatterns,
                            minOutputLength = it.minOutputLength,
                            skipDefaultChecks = it.skipDefaultChecks,
                            customValidator = it.customValidator
                        )
                    }
                )
            } catch (e: Exception) {
                logger.debug("Quality hook failed for skill {}: {}", skillName, e.message)
                null
            }

            val output = buildString {
                appendLine("Script: $scriptPath")
                appendLine("Exit code: $exitCode")
                if (qualityResult != null) {
                    appendLine("Quality: ${qualityResult.overallStatus}")
                }
                if (stdout.isNotBlank()) {
                    appendLine()
                    appendLine("Output:")
                    appendLine(stdout)
                }
                if (stderr.isNotBlank()) {
                    appendLine()
                    appendLine("Errors:")
                    appendLine(stderr)
                }
                if (qualityResult?.autoFixInstruction != null) {
                    appendLine()
                    appendLine("[Quality Hook] ${qualityResult.autoFixInstruction}")
                }
            }

            trackSkillUsage(skillName, "SCRIPT_RUN", scriptDef.scriptType.name, exitCode == 0)
            McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = output)),
                isError = exitCode != 0 && qualityResult?.overallStatus != "PARTIAL_SUCCESS"
            )
        } catch (e: Exception) {
            trackSkillUsage(skillName, "SCRIPT_RUN", scriptDef.scriptType.name, false)
            logger.error("Script execution failed: skill={}, script={}: {}", skillName, scriptPath, e.message)
            McpProxyService.errorResponse("Script execution failed: ${e.message}")
        }
    }

    /**
     * List all available skills with metadata (Level 1 query).
     */
    private fun handleListSkills(arguments: Map<String, Any?>): McpToolCallResponse {
        val profileFilter = arguments["profile"] as? String
        val categoryFilter = arguments["category"] as? String
        val scopeFilter = arguments["scope"] as? String

        val category = if (!categoryFilter.isNullOrBlank()) {
            try {
                SkillCategory.valueOf(categoryFilter.uppercase())
            } catch (e: IllegalArgumentException) {
                return McpProxyService.errorResponse("Invalid category: $categoryFilter. Valid: ${SkillCategory.entries.joinToString()}")
            }
        } else null

        val scope = if (!scopeFilter.isNullOrBlank()) {
            try {
                SkillScope.valueOf(scopeFilter.uppercase())
            } catch (e: IllegalArgumentException) {
                return McpProxyService.errorResponse("Invalid scope: $scopeFilter. Valid: ${SkillScope.entries.joinToString()}")
            }
        } else null

        val skills = skillLoader.loadSkillMetadataCatalog(profileFilter, category, scope)

        val output = buildString {
            appendLine("Available Skills (${skills.size}):")
            appendLine()
            for (skill in skills) {
                appendLine("- **${skill.name}** [${skill.scope.name.lowercase()}/${skill.category.name.lowercase()}]")
                appendLine("  ${skill.description}")
                if (skill.tags.isNotEmpty()) {
                    appendLine("  Tags: ${skill.tags.joinToString(", ")}")
                }
                if (skill.subFiles.isNotEmpty()) {
                    appendLine("  Sub-files: ${skill.subFiles.joinToString(", ") { it.path }}")
                }
                if (skill.scripts.isNotEmpty()) {
                    appendLine("  Scripts: ${skill.scripts.joinToString(", ") { "${it.path} (${it.scriptType.name.lowercase()}/${it.language})" }}")
                }
                appendLine("  Version: ${skill.version} | Scope: ${skill.scope.name.lowercase()} | Enabled: ${skill.enabled}")
                appendLine()
            }
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = output)),
            isError = false
        )
    }

    // ---- Internal ----

    private fun trackSkillUsage(
        skillName: String,
        action: String,
        scriptType: String? = null,
        success: Boolean = true
    ) {
        try {
            skillUsageRepository.save(
                SkillUsageEntity(
                    sessionId = "",
                    skillName = skillName,
                    action = action,
                    scriptType = scriptType,
                    success = success
                )
            )
        } catch (e: Exception) {
            logger.debug("Failed to track skill usage: {}", e.message)
        }
    }
}
