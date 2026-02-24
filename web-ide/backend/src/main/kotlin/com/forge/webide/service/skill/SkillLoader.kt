package com.forge.webide.service.skill

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads Skill and Profile definitions from the plugins directory.
 *
 * Supports 3-level progressive disclosure (Anthropic Agent Skills standard):
 * - Level 1: Metadata (name + description) — injected into system prompt
 * - Level 2: SKILL.md content — served on-demand via read_skill MCP tool
 * - Level 3: Sub-files + scripts — served/executed on-demand
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
     *
     * Phase 4: No longer applies content-based pruning. System prompt only contains
     * metadata (~100 tokens/skill), so all matching skills are returned.
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

        // 3. Filter to enabled + unique skills only
        val filtered = result
            .filter { it.enabled }
            .distinctBy { it.name }

        logger.info("Filtering skills for profile: {}", profile.name)
        logger.info("Loaded {} skills (filtered from {})", filtered.size, totalSkillCount)

        return filtered
    }

    /**
     * Return metadata-only catalog of all skills (Level 1).
     * Used by the list_skills MCP tool.
     */
    fun loadSkillMetadataCatalog(
        profileName: String? = null,
        category: SkillCategory? = null,
        scope: SkillScope? = null
    ): List<SkillDefinition> {
        var skills = skillCache.values.toList()

        if (profileName != null) {
            skills = skills.filter { it.matchesProfile(profileName) }
        }
        if (category != null) {
            skills = skills.filter { it.category == category }
        }
        if (scope != null) {
            skills = skills.filter { it.scope == scope }
        }

        return skills.sortedBy { it.name }
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

    /**
     * Resolve the base plugins directory path.
     */
    fun resolvePluginsPath(): Path {
        val path = Paths.get(basePath)
        return if (path.isAbsolute) path else Paths.get(System.getProperty("user.dir")).resolve(path)
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
                            logger.debug("Loaded skill: {} (category={}, subFiles={}, scripts={})",
                                skill.name, skill.category, skill.subFiles.size, skill.scripts.size)
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
        val version = yamlMap["version"]?.toString() ?: "1.0"
        val author = yamlMap["author"]?.toString() ?: ""

        @Suppress("UNCHECKED_CAST")
        val tags: List<String> = when (val tagsVal = yamlMap["tags"]) {
            is List<*> -> tagsVal.filterIsInstance<String>()
            is String -> tagsVal.split(",").map { it.trim() }
            else -> emptyList()
        }

        // Auto-detect category from frontmatter or source path
        val category = detectCategory(yamlMap["category"]?.toString(), path)

        // Auto-detect scope from frontmatter or source path
        val scope = detectScope(yamlMap["scope"]?.toString(), path)

        // Scan sub-directories for Level 3 content
        val skillDir = path.parent
        val subFiles = scanSubFiles(skillDir)
        val scripts = scanScripts(skillDir)

        return SkillDefinition(
            name = name,
            description = description,
            trigger = trigger,
            tags = tags,
            stage = stage,
            type = type,
            content = body.trim(),
            sourcePath = path.toString(),
            version = version,
            author = author,
            category = category,
            scope = scope,
            subFiles = subFiles,
            scripts = scripts
        )
    }

    /**
     * Detect skill category from frontmatter value or source path.
     */
    private fun detectCategory(frontmatterCategory: String?, path: Path): SkillCategory {
        // Explicit frontmatter value takes precedence
        if (!frontmatterCategory.isNullOrBlank()) {
            return try {
                SkillCategory.valueOf(frontmatterCategory.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Unknown skill category '{}' in {}, falling back to auto-detect", frontmatterCategory, path)
                detectCategoryFromPath(path)
            }
        }
        return detectCategoryFromPath(path)
    }

    /**
     * Detect skill scope from frontmatter value or source path.
     * plugins/ → PLATFORM, workspace/.skills/ → WORKSPACE, else → CUSTOM
     */
    private fun detectScope(frontmatterScope: String?, path: Path): SkillScope {
        if (!frontmatterScope.isNullOrBlank()) {
            return try {
                SkillScope.valueOf(frontmatterScope.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Unknown skill scope '{}' in {}, falling back to auto-detect", frontmatterScope, path)
                detectScopeFromPath(path)
            }
        }
        return detectScopeFromPath(path)
    }

    internal fun detectScopeFromPath(path: Path): SkillScope {
        val pathStr = path.toString()
        return when {
            pathStr.contains("plugins/") || pathStr.contains("plugins${File.separator}") -> SkillScope.PLATFORM
            pathStr.contains(".skills/custom/") || pathStr.contains(".skills${File.separator}custom${File.separator}") -> SkillScope.CUSTOM
            pathStr.contains(".skills/") || pathStr.contains(".skills${File.separator}") -> SkillScope.WORKSPACE
            else -> SkillScope.PLATFORM
        }
    }

    private fun detectCategoryFromPath(path: Path): SkillCategory {
        val pathStr = path.toString()
        return when {
            pathStr.contains("forge-foundation") -> SkillCategory.FOUNDATION
            pathStr.contains("forge-superagent") -> SkillCategory.DELIVERY
            pathStr.contains("forge-deployment") -> SkillCategory.DELIVERY
            pathStr.contains("forge-knowledge") -> SkillCategory.KNOWLEDGE
            else -> SkillCategory.CUSTOM
        }
    }

    /**
     * Scan a skill directory for sub-files in standard directories:
     * examples/, reference/, templates/, patterns/
     */
    private fun scanSubFiles(skillDir: Path): List<SkillSubFile> {
        val subDirMappings = mapOf(
            "examples" to SkillContentType.EXAMPLE,
            "reference" to SkillContentType.REFERENCE,
            "templates" to SkillContentType.TEMPLATE,
            "patterns" to SkillContentType.REFERENCE
        )

        val result = mutableListOf<SkillSubFile>()

        for ((dirName, contentType) in subDirMappings) {
            val subDir = skillDir.resolve(dirName)
            if (Files.isDirectory(subDir)) {
                try {
                    Files.list(subDir)
                        .filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                        .forEach { file ->
                            val relativePath = "$dirName/${file.fileName}"
                            val desc = extractFirstLineDescription(file)
                            result.add(SkillSubFile(relativePath, desc, contentType))
                        }
                } catch (e: Exception) {
                    logger.debug("Failed to scan sub-directory {}: {}", subDir, e.message)
                }
            }
        }

        return result
    }

    /**
     * Scan a skill directory for executable scripts in scripts/.
     */
    private fun scanScripts(skillDir: Path): List<SkillScript> {
        val scriptsDir = skillDir.resolve("scripts")
        if (!Files.isDirectory(scriptsDir)) return emptyList()

        val scriptExtensions = mapOf(
            "py" to "python",
            "sh" to "bash",
            "kt" to "kotlin",
            "kts" to "kotlin"
        )

        val result = mutableListOf<SkillScript>()

        try {
            Files.list(scriptsDir)
                .filter { Files.isRegularFile(it) }
                .forEach { file ->
                    val ext = file.fileName.toString().substringAfterLast('.', "")
                    val language = scriptExtensions[ext]
                    if (language != null) {
                        val relativePath = "scripts/${file.fileName}"
                        val desc = extractFirstLineDescription(file)
                        val scriptType = detectScriptType(file.fileName.toString(), desc)
                        result.add(SkillScript(
                            path = relativePath,
                            description = desc,
                            language = language,
                            scriptType = scriptType
                        ))
                    }
                }
        } catch (e: Exception) {
            logger.debug("Failed to scan scripts directory {}: {}", scriptsDir, e.message)
        }

        return result
    }

    /**
     * Detect script type from filename and description.
     * Names containing "extract", "mine", "scan", "generate" → EXTRACTION
     * Names containing "validate", "check", "baseline", "verify" → VALIDATION
     */
    private fun detectScriptType(fileName: String, description: String): ScriptType {
        val combined = "$fileName $description".lowercase()
        val extractionKeywords = listOf("extract", "mine", "scan", "generate", "discover", "analyze", "profile", "drift")
        val validationKeywords = listOf("validate", "check", "baseline", "verify", "lint", "guard")

        if (extractionKeywords.any { combined.contains(it) }) return ScriptType.EXTRACTION
        if (validationKeywords.any { combined.contains(it) }) return ScriptType.VALIDATION
        return ScriptType.VALIDATION // default to validation
    }

    /**
     * Extract a description from the first comment line of a file.
     * Supports # comments (Python/Bash/YAML) and // comments (Kotlin).
     * Falls back to the filename without extension.
     */
    private fun extractFirstLineDescription(path: Path): String {
        return try {
            val firstLines = Files.readAllLines(path).take(3)
            for (line in firstLines) {
                val trimmed = line.trim()
                // Python/Bash/Markdown: # comment
                if (trimmed.startsWith("# ") && !trimmed.startsWith("# !")) {
                    return trimmed.removePrefix("# ").trim()
                }
                // Kotlin/Java: // comment
                if (trimmed.startsWith("// ")) {
                    return trimmed.removePrefix("// ").trim()
                }
                // Python docstring: """...""" or triple-quoted
                if (trimmed.startsWith("\"\"\"") && trimmed.endsWith("\"\"\"") && trimmed.length > 6) {
                    return trimmed.removeSurrounding("\"\"\"").trim()
                }
            }
            path.fileName.toString().substringBeforeLast('.')
        } catch (e: Exception) {
            path.fileName.toString().substringBeforeLast('.')
        }
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
        val mode = yamlMap["mode"]?.toString() ?: "default"

        return ProfileDefinition(
            name = name,
            description = description,
            skills = skills,
            baselines = baselines,
            hitlCheckpoint = hitlCheckpoint,
            oodaGuidance = body.trim(),
            sourcePath = path.toString(),
            mode = mode
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
