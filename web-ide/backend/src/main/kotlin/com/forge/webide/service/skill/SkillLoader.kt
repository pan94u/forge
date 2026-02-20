package com.forge.webide.service.skill

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads Skill and Profile definitions from the plugins directory.
 *
 * Scans:
 * - `{base-path}/forge-foundation/skills/{name}/SKILL.md` (Foundation Skills)
 * - `{base-path}/forge-superagent/skills/{name}/SKILL.md` (Delivery Skills)
 * - `{base-path}/forge-superagent/skill-profiles/{name}.md` (Profiles)
 * - `{base-path}/forge-superagent/CLAUDE.md` (SuperAgent system instructions)
 */
@Service
class SkillLoader(
    @Value("\${forge.plugins.base-path:plugins}")
    private val basePath: String
) {
    private val logger = LoggerFactory.getLogger(SkillLoader::class.java)
    private val yamlMapper = ObjectMapper(YAMLFactory())

    private val skillCache = ConcurrentHashMap<String, SkillDefinition>()
    private val profileCache = ConcurrentHashMap<String, ProfileDefinition>()

    @Volatile
    private var _superAgentInstructions: String = ""
    val superAgentInstructions: String get() = _superAgentInstructions

    @PostConstruct
    fun init() {
        reloadAll()
    }

    fun loadSkill(name: String): SkillDefinition? = skillCache[name]

    fun loadProfile(name: String): ProfileDefinition? = profileCache[name]

    fun loadAllSkills(): List<SkillDefinition> = skillCache.values.toList()

    fun loadAllProfiles(): List<ProfileDefinition> = profileCache.values.toList()

    fun loadAllFoundationSkills(): List<SkillDefinition> {
        return skillCache.values.filter { it.sourcePath.contains("forge-foundation") }
    }

    /**
     * Load all skills referenced by a profile, with optional message-based keyword triggering.
     * Handles special tokens:
     * - "foundation-skills-all" → all foundation skills
     * - "domain-skills-contextual" → skipped (runtime context-dependent)
     *
     * Additionally loads skills whose tags match keywords in the user message,
     * filtered by stage compatibility with the current profile.
     */
    fun loadSkillsForProfile(profile: ProfileDefinition, message: String = ""): List<SkillDefinition> {
        val totalSkillCount = skillCache.size
        val result = mutableListOf<SkillDefinition>()

        // 1. Load explicitly referenced skills
        for (skillRef in profile.skills) {
            when (skillRef) {
                "foundation-skills-all" -> result.addAll(loadAllFoundationSkills())
                "domain-skills-contextual" -> {
                    // Domain skills are loaded at runtime based on context; skip here
                }
                else -> {
                    val skill = skillCache[skillRef]
                    if (skill != null) {
                        result.add(skill)
                    } else {
                        logger.warn("Skill '{}' referenced by profile '{}' not found", skillRef, profile.name)
                    }
                }
            }
        }

        // 2. Load keyword-triggered skills from message content
        if (message.isNotBlank()) {
            val keywordTriggered = loadKeywordTriggeredSkills(message, profile.name)
            if (keywordTriggered.isNotEmpty()) {
                logger.info("Keyword-triggered skills for profile '{}': {}",
                    profile.name, keywordTriggered.map { it.name })
                result.addAll(keywordTriggered)
            }
        }

        val filtered = result.distinctBy { it.name }

        logger.info("Filtering skills for profile: {}", profile.name)
        logger.info("Loaded {} skills (filtered from {})", filtered.size, totalSkillCount)

        return filtered
    }

    /**
     * Find skills whose tags match keywords in the user message.
     * Only returns skills compatible with the given profile (by stage).
     */
    fun loadKeywordTriggeredSkills(message: String, profileName: String): List<SkillDefinition> {
        val messageLower = message.lowercase()
        return skillCache.values.filter { skill ->
            val tagMatch = skill.tags.any { tag ->
                tag.length >= 2 && messageLower.contains(tag.lowercase())
            }
            tagMatch && skill.matchesProfile(profileName)
        }
    }

    fun reloadAll() {
        skillCache.clear()
        profileCache.clear()

        val pluginsDir = resolvePluginsPath()
        if (!Files.isDirectory(pluginsDir)) {
            logger.warn("Plugins directory not found: {}", pluginsDir)
            return
        }

        // Load skills from all plugin directories
        val skillDirs = listOf(
            pluginsDir.resolve("forge-foundation/skills"),
            pluginsDir.resolve("forge-superagent/skills"),
            pluginsDir.resolve("forge-deployment/skills"),
            pluginsDir.resolve("forge-knowledge/skills")
        )

        for (skillsRoot in skillDirs) {
            if (Files.isDirectory(skillsRoot)) {
                loadSkillsFromDirectory(skillsRoot)
            }
        }

        // Load profiles
        val profilesDir = pluginsDir.resolve("forge-superagent/skill-profiles")
        if (Files.isDirectory(profilesDir)) {
            loadProfilesFromDirectory(profilesDir)
        }

        // Load SuperAgent CLAUDE.md
        val claudeMd = pluginsDir.resolve("forge-superagent/CLAUDE.md")
        if (Files.isRegularFile(claudeMd)) {
            _superAgentInstructions = Files.readString(claudeMd)
            logger.info("Loaded SuperAgent instructions from {}", claudeMd)
        }

        logger.info(
            "Skill loading complete: {} skills, {} profiles",
            skillCache.size, profileCache.size
        )
    }

    private fun resolvePluginsPath(): Path {
        val path = Paths.get(basePath)
        return if (path.isAbsolute) path else Paths.get(System.getProperty("user.dir")).resolve(path)
    }

    private fun loadSkillsFromDirectory(skillsRoot: Path) {
        try {
            Files.list(skillsRoot)
                .filter { Files.isDirectory(it) }
                .forEach { skillDir ->
                    val skillFile = skillDir.resolve("SKILL.md")
                    if (Files.isRegularFile(skillFile)) {
                        try {
                            val skill = parseSkillFile(skillFile)
                            skillCache[skill.name] = skill
                            logger.debug("Loaded skill: {} from {}", skill.name, skillFile)
                        } catch (e: Exception) {
                            logger.warn("Failed to parse skill file {}: {}", skillFile, e.message)
                        }
                    }
                }
        } catch (e: Exception) {
            logger.warn("Failed to scan skills directory {}: {}", skillsRoot, e.message)
        }
    }

    private fun loadProfilesFromDirectory(profilesDir: Path) {
        try {
            Files.list(profilesDir)
                .filter { it.toString().endsWith(".md") }
                .forEach { profileFile ->
                    try {
                        val profile = parseProfileFile(profileFile)
                        profileCache[profile.name] = profile
                        logger.debug("Loaded profile: {} from {}", profile.name, profileFile)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse profile file {}: {}", profileFile, e.message)
                    }
                }
        } catch (e: Exception) {
            logger.warn("Failed to scan profiles directory {}: {}", profilesDir, e.message)
        }
    }

    internal fun parseSkillFile(path: Path): SkillDefinition {
        val content = Files.readString(path)
        val (frontmatter, body) = splitFrontmatter(content)

        val yamlMap = parseYamlMap(frontmatter)

        val name = yamlMap["name"]?.toString()
            ?: path.parent.fileName.toString()

        val description = yamlMap["description"]?.toString() ?: ""
        val trigger = yamlMap["trigger"]?.toString()
        val stage = yamlMap["stage"]?.toString()
        val type = yamlMap["type"]?.toString()

        @Suppress("UNCHECKED_CAST")
        val tags: List<String> = when (val tagsVal = yamlMap["tags"]) {
            is List<*> -> tagsVal.filterIsInstance<String>()
            is String -> tagsVal.split(",").map { it.trim() }
            else -> emptyList()
        }

        return SkillDefinition(
            name = name,
            description = description,
            trigger = trigger,
            tags = tags,
            stage = stage,
            type = type,
            content = body.trim(),
            sourcePath = path.toString()
        )
    }

    internal fun parseProfileFile(path: Path): ProfileDefinition {
        val content = Files.readString(path)
        val (frontmatter, body) = splitFrontmatter(content)

        val yamlMap = parseYamlMap(frontmatter)

        val name = yamlMap["name"]?.toString()
            ?: path.fileName.toString().removeSuffix(".md")

        val description = yamlMap["description"]?.toString() ?: ""

        @Suppress("UNCHECKED_CAST")
        val skills: List<String> = when (val skillsVal = yamlMap["skills"]) {
            is List<*> -> skillsVal.filterIsInstance<String>()
            else -> emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        val baselines: List<String> = when (val baselinesVal = yamlMap["baselines"]) {
            is List<*> -> baselinesVal.filterIsInstance<String>()
            else -> emptyList()
        }

        val hitlCheckpoint = yamlMap["hitl-checkpoint"]?.toString() ?: ""

        return ProfileDefinition(
            name = name,
            description = description,
            skills = skills,
            baselines = baselines,
            hitlCheckpoint = hitlCheckpoint,
            oodaGuidance = body.trim(),
            sourcePath = path.toString()
        )
    }

    /**
     * Split a Markdown file into YAML frontmatter and body.
     * Frontmatter is delimited by --- at the start and end.
     */
    internal fun splitFrontmatter(content: String): Pair<String, String> {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) {
            return "" to content
        }

        val endIndex = trimmed.indexOf("---", 3)
        if (endIndex < 0) {
            return "" to content
        }

        val frontmatter = trimmed.substring(3, endIndex).trim()
        val body = trimmed.substring(endIndex + 3)
        return frontmatter to body
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYamlMap(yaml: String): Map<String, Any?> {
        if (yaml.isBlank()) return emptyMap()
        return try {
            yamlMapper.readValue(yaml, Map::class.java) as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) {
            logger.warn("Failed to parse YAML frontmatter: {}", e.message)
            emptyMap()
        }
    }
}
