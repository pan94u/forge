package com.forge.webide.service.skill

import com.forge.webide.entity.SkillPreferenceEntity
import com.forge.webide.repository.SkillPreferenceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Manages skill lifecycle: listing, enabling/disabling, CRUD for custom skills.
 * Coordinates between SkillLoader (file-based) and SkillPreferenceRepository (DB-based).
 */
@Service
class SkillManagementService(
    private val skillLoader: SkillLoader,
    private val preferenceRepository: SkillPreferenceRepository
) {
    private val logger = LoggerFactory.getLogger(SkillManagementService::class.java)

    /**
     * List all skills with their current enabled state for a workspace.
     */
    fun listSkills(
        workspaceId: String? = null,
        scope: SkillScope? = null,
        category: SkillCategory? = null
    ): List<SkillView> {
        val allSkills = skillLoader.loadSkillMetadataCatalog(scope = scope, category = category)
        val preferences = workspaceId?.let { preferenceRepository.findByWorkspaceId(it) } ?: emptyList()
        val prefMap = preferences.associateBy { it.skillName }

        return allSkills.map { skill ->
            val pref = prefMap[skill.name]
            SkillView(
                name = skill.name,
                description = skill.description,
                tags = skill.tags,
                scope = skill.scope,
                category = skill.category,
                version = skill.version,
                author = skill.author,
                enabled = pref?.enabled ?: skill.enabled,
                subFileCount = skill.subFiles.size,
                scriptCount = skill.scripts.size,
                subFiles = skill.subFiles.map { SubFileView(it.path, it.description, it.type.name) },
                scripts = skill.scripts.map { ScriptView(it.path, it.description, it.language, it.scriptType.name) }
            )
        }
    }

    /**
     * Get a single skill's full details.
     */
    fun getSkill(name: String, workspaceId: String? = null): SkillDetailView? {
        val skill = skillLoader.loadSkill(name) ?: return null
        val pref = workspaceId?.let { preferenceRepository.findByWorkspaceIdAndSkillName(it, name) }

        return SkillDetailView(
            name = skill.name,
            description = skill.description,
            tags = skill.tags,
            scope = skill.scope,
            category = skill.category,
            version = skill.version,
            author = skill.author,
            enabled = pref?.enabled ?: skill.enabled,
            content = skill.content,
            subFiles = skill.subFiles.map { SubFileView(it.path, it.description, it.type.name) },
            scripts = skill.scripts.map { ScriptView(it.path, it.description, it.language, it.scriptType.name) }
        )
    }

    /**
     * Read a skill's sub-file content.
     */
    fun readSkillContent(name: String, subPath: String): String? {
        val skill = skillLoader.loadSkill(name) ?: return null
        if (subPath.contains("..")) return null

        val skillDir = Path.of(skill.sourcePath).parent
        val targetFile = skillDir.resolve(subPath)
        val normalized = targetFile.normalize()
        if (!normalized.startsWith(skillDir.normalize())) return null
        if (!Files.isRegularFile(targetFile)) return null

        return Files.readString(targetFile)
    }

    /**
     * Enable a skill for a workspace.
     */
    @Transactional
    fun enableSkill(workspaceId: String, skillName: String): Boolean {
        val skill = skillLoader.loadSkill(skillName) ?: return false
        setPreference(workspaceId, skillName, true)
        logger.info("Skill '{}' enabled for workspace '{}'", skillName, workspaceId)
        return true
    }

    /**
     * Disable a skill for a workspace.
     */
    @Transactional
    fun disableSkill(workspaceId: String, skillName: String): Boolean {
        val skill = skillLoader.loadSkill(skillName) ?: return false
        setPreference(workspaceId, skillName, false)
        logger.info("Skill '{}' disabled for workspace '{}'", skillName, workspaceId)
        return true
    }

    /**
     * Create a custom skill in the workspace.
     */
    fun createCustomSkill(workspaceId: String, request: CreateSkillRequest): SkillView? {
        val skillsDir = resolveWorkspaceSkillsDir(workspaceId)
        val skillDir = skillsDir.resolve("custom/${request.name}")

        if (Files.exists(skillDir)) {
            logger.warn("Custom skill '{}' already exists in workspace '{}'", request.name, workspaceId)
            return null
        }

        Files.createDirectories(skillDir)
        val content = buildString {
            appendLine("---")
            appendLine("name: ${request.name}")
            appendLine("description: \"${request.description}\"")
            appendLine("tags: [${request.tags.joinToString(", ")}]")
            appendLine("version: \"1.0\"")
            appendLine("scope: custom")
            appendLine("category: custom")
            appendLine("---")
            appendLine()
            appendLine(request.content)
        }
        Files.writeString(skillDir.resolve("SKILL.md"), content)

        // Reload skills to pick up the new one
        skillLoader.reloadAll()

        logger.info("Custom skill '{}' created in workspace '{}'", request.name, workspaceId)

        // Return the view directly since SkillLoader may not scan workspace directories yet
        return listSkills(workspaceId).find { it.name == request.name }
            ?: SkillView(
                name = request.name,
                description = request.description,
                tags = request.tags,
                scope = SkillScope.CUSTOM,
                category = SkillCategory.CUSTOM,
                version = "1.0",
                author = "",
                enabled = true,
                subFileCount = 0,
                scriptCount = 0,
                subFiles = emptyList(),
                scripts = emptyList()
            )
    }

    /**
     * Update a custom skill.
     */
    fun updateCustomSkill(workspaceId: String, name: String, request: UpdateSkillRequest): Boolean {
        val skill = skillLoader.loadSkill(name) ?: return false
        if (skill.scope != SkillScope.CUSTOM) {
            logger.warn("Cannot update non-custom skill '{}'", name)
            return false
        }

        val skillFile = Path.of(skill.sourcePath)
        if (!Files.isRegularFile(skillFile)) return false

        val content = buildString {
            appendLine("---")
            appendLine("name: $name")
            appendLine("description: \"${request.description ?: skill.description}\"")
            appendLine("tags: [${(request.tags ?: skill.tags).joinToString(", ")}]")
            appendLine("version: \"${skill.version}\"")
            appendLine("scope: custom")
            appendLine("category: custom")
            appendLine("---")
            appendLine()
            appendLine(request.content ?: skill.content)
        }
        Files.writeString(skillFile, content)
        skillLoader.reloadAll()

        logger.info("Custom skill '{}' updated", name)
        return true
    }

    /**
     * Delete a custom skill. Platform skills cannot be deleted.
     */
    fun deleteCustomSkill(workspaceId: String, name: String): Boolean {
        val skill = skillLoader.loadSkill(name) ?: return false
        if (skill.scope == SkillScope.PLATFORM) {
            logger.warn("Cannot delete platform skill '{}'", name)
            return false
        }

        val skillDir = Path.of(skill.sourcePath).parent
        deleteDirectory(skillDir)
        skillLoader.reloadAll()

        logger.info("Skill '{}' deleted", name)
        return true
    }

    /**
     * Execute a skill script and return the result.
     */
    fun runScript(name: String, scriptPath: String, args: List<String> = emptyList()): ScriptResultView? {
        if (scriptPath.contains("..")) return null

        val skill = skillLoader.loadSkill(name) ?: return null
        // URL extracts 'adr_template.py' but definition stores 'scripts/adr_template.py'
        val scriptDef = skill.scripts.find { it.path == scriptPath || it.path == "scripts/$scriptPath" }
            ?: return null

        val skillDir = Path.of(skill.sourcePath).parent
        val scriptFile = skillDir.resolve(scriptDef.path)
        if (!scriptFile.normalize().startsWith(skillDir.normalize())) return null
        if (!Files.isRegularFile(scriptFile)) return null

        val command = when (scriptDef.language) {
            "python" -> listOf("python3", scriptFile.toString())
            "bash" -> listOf("bash", scriptFile.toString())
            "kotlin" -> listOf("kotlin", scriptFile.toString())
            else -> return null
        } + args

        return try {
            logger.info("Executing skill script: {} (skill={})", scriptPath, name)
            val process = ProcessBuilder(command)
                .directory(skillDir.toFile())
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(60, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return ScriptResultView(exitCode = -1, stdout = "", stderr = "Timed out after 60s")
            }

            ScriptResultView(
                exitCode = process.exitValue(),
                stdout = process.inputStream.bufferedReader().readText().take(50_000),
                stderr = process.errorStream.bufferedReader().readText().take(10_000)
            )
        } catch (e: Exception) {
            logger.error("Script execution failed: {}", e.message, e)
            ScriptResultView(exitCode = -1, stdout = "", stderr = e.message ?: "Execution failed")
        }
    }

    private fun setPreference(workspaceId: String, skillName: String, enabled: Boolean) {
        val existing = preferenceRepository.findByWorkspaceIdAndSkillName(workspaceId, skillName)
        if (existing != null) {
            existing.enabled = enabled
            existing.updatedAt = Instant.now()
            preferenceRepository.save(existing)
        } else {
            preferenceRepository.save(
                SkillPreferenceEntity(
                    workspaceId = workspaceId,
                    skillName = skillName,
                    enabled = enabled
                )
            )
        }
    }

    private fun resolveWorkspaceSkillsDir(workspaceId: String): Path {
        val pluginsPath = skillLoader.resolvePluginsPath()
        return pluginsPath.parent.resolve("workspace/$workspaceId/.skills")
    }

    private fun deleteDirectory(dir: Path) {
        if (Files.isDirectory(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }
}

// ---- View models for REST API ----

data class SkillView(
    val name: String,
    val description: String,
    val tags: List<String>,
    val scope: SkillScope,
    val category: SkillCategory,
    val version: String,
    val author: String,
    val enabled: Boolean,
    val subFileCount: Int,
    val scriptCount: Int,
    val subFiles: List<SubFileView>,
    val scripts: List<ScriptView>
)

data class SkillDetailView(
    val name: String,
    val description: String,
    val tags: List<String>,
    val scope: SkillScope,
    val category: SkillCategory,
    val version: String,
    val author: String,
    val enabled: Boolean,
    val content: String,
    val subFiles: List<SubFileView>,
    val scripts: List<ScriptView>
)

data class SubFileView(
    val path: String,
    val description: String,
    val type: String
)

data class ScriptView(
    val path: String,
    val description: String,
    val language: String,
    val scriptType: String
)

data class CreateSkillRequest(
    val name: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val content: String
)

data class UpdateSkillRequest(
    val description: String? = null,
    val tags: List<String>? = null,
    val content: String? = null
)

data class ScriptResultView(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

data class RunScriptRequest(
    val args: List<String> = emptyList()
)
