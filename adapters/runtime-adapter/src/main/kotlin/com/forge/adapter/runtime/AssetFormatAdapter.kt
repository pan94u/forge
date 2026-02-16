package com.forge.adapter.runtime

import org.slf4j.LoggerFactory

/**
 * Adapter for skill and asset format versioning.
 *
 * As the Forge platform evolves, the format of SKILL.md files, CLAUDE.md templates,
 * command definitions, and plugin manifests may change. The AssetFormatAdapter
 * handles forward and backward compatibility, allowing older assets to work with
 * newer runtimes and vice versa.
 *
 * Version history:
 * - v1: Initial format (YAML frontmatter + Markdown body)
 * - v2: Added required_tools, baseline_refs, and learning_loop sections
 *
 * Usage:
 * ```kotlin
 * val adapter = AssetFormatAdapter()
 * val normalized = adapter.normalizeSkill(rawContent)
 * val isValid = adapter.validateAsset(normalized, AssetType.SKILL)
 * ```
 */
class AssetFormatAdapter {

    private val logger = LoggerFactory.getLogger(AssetFormatAdapter::class.java)

    companion object {
        const val CURRENT_FORMAT_VERSION = 2
        const val MIN_SUPPORTED_VERSION = 1

        private val REQUIRED_SKILL_SECTIONS_V1 = listOf("## Purpose", "## Instructions")
        private val REQUIRED_SKILL_SECTIONS_V2 = REQUIRED_SKILL_SECTIONS_V1 + listOf("## Quality Criteria")
        private val REQUIRED_SKILL_FRONTMATTER_V1 = listOf("name", "version", "profile")
        private val REQUIRED_SKILL_FRONTMATTER_V2 = REQUIRED_SKILL_FRONTMATTER_V1 + listOf("description")

        private val REQUIRED_COMMAND_SECTIONS = listOf("## Purpose", "## Steps")
        private val REQUIRED_PLUGIN_MANIFEST_FIELDS = listOf("name", "version", "description")
    }

    /**
     * Supported asset types in the Forge platform.
     */
    enum class AssetType {
        SKILL,
        COMMAND,
        CLAUDE_MD,
        PLUGIN_MANIFEST,
        HOOK
    }

    /**
     * Validation result for an asset.
     */
    data class ValidationResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val detectedVersion: Int = CURRENT_FORMAT_VERSION
    )

    /**
     * Detect the format version of a SKILL.md file based on its structure.
     */
    fun detectFormatVersion(content: String): Int {
        if (!content.startsWith("---")) return 1

        val frontmatter = extractFrontmatter(content)

        // V2 indicators: has description field and quality criteria section
        val hasDescription = frontmatter.containsKey("description")
        val hasQualityCriteria = content.contains("## Quality Criteria")
        val hasRequiredTools = frontmatter.containsKey("required_tools")

        return if (hasDescription && (hasQualityCriteria || hasRequiredTools)) 2 else 1
    }

    /**
     * Normalize a skill from any supported version to the current format version.
     * Adds missing sections with placeholder content where needed.
     */
    fun normalizeSkill(content: String): String {
        val version = detectFormatVersion(content)
        if (version == CURRENT_FORMAT_VERSION) return content

        logger.info("Normalizing skill from v{} to v{}", version, CURRENT_FORMAT_VERSION)

        return when (version) {
            1 -> migrateSkillV1toV2(content)
            else -> content
        }
    }

    /**
     * Validate an asset against the rules for its type and detected version.
     */
    fun validateAsset(content: String, type: AssetType): ValidationResult {
        return when (type) {
            AssetType.SKILL -> validateSkill(content)
            AssetType.COMMAND -> validateCommand(content)
            AssetType.CLAUDE_MD -> validateClaudeMd(content)
            AssetType.PLUGIN_MANIFEST -> validatePluginManifest(content)
            AssetType.HOOK -> validateHook(content)
        }
    }

    /**
     * Extract YAML frontmatter from a Markdown file as a key-value map.
     */
    fun extractFrontmatter(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!content.startsWith("---")) return result

        val endIndex = content.indexOf("---", 3)
        if (endIndex < 0) return result

        val frontmatter = content.substring(3, endIndex).trim()
        for (line in frontmatter.lines()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                result[key] = value
            }
        }
        return result
    }

    /**
     * Extract the Markdown body (everything after frontmatter).
     */
    fun extractBody(content: String): String {
        if (!content.startsWith("---")) return content
        val endIndex = content.indexOf("---", 3)
        return if (endIndex >= 0) {
            content.substring(endIndex + 3).trim()
        } else {
            content
        }
    }

    private fun validateSkill(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val version = detectFormatVersion(content)

        // Check frontmatter exists
        if (!content.startsWith("---")) {
            errors.add("Missing YAML frontmatter (file must start with ---)")
            return ValidationResult(false, errors, warnings, version)
        }

        val frontmatter = extractFrontmatter(content)

        // Check required frontmatter fields
        val requiredFields = if (version >= 2) REQUIRED_SKILL_FRONTMATTER_V2 else REQUIRED_SKILL_FRONTMATTER_V1
        for (field in requiredFields) {
            if (!frontmatter.containsKey(field)) {
                if (version >= 2 && field == "description") {
                    warnings.add("Missing recommended frontmatter field: $field")
                } else {
                    errors.add("Missing required frontmatter field: $field")
                }
            }
        }

        // Check required sections
        val requiredSections = if (version >= 2) REQUIRED_SKILL_SECTIONS_V2 else REQUIRED_SKILL_SECTIONS_V1
        for (section in requiredSections) {
            if (!content.contains(section)) {
                if (section == "## Quality Criteria" && version < 2) {
                    warnings.add("Consider adding section: $section (recommended in v2)")
                } else {
                    errors.add("Missing required section: $section")
                }
            }
        }

        // Check version format
        val versionStr = frontmatter["version"]
        if (versionStr != null && !Regex("""^\d+\.\d+\.\d+$""").matches(versionStr)) {
            warnings.add("Version '$versionStr' does not follow semver (expected: X.Y.Z)")
        }

        return ValidationResult(errors.isEmpty(), errors, warnings, version)
    }

    private fun validateCommand(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (section in REQUIRED_COMMAND_SECTIONS) {
            if (!content.contains(section)) {
                errors.add("Missing required section: $section")
            }
        }

        if (!content.startsWith("#")) {
            warnings.add("Command file should start with a top-level heading")
        }

        return ValidationResult(errors.isEmpty(), errors, warnings)
    }

    private fun validateClaudeMd(content: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!content.contains("##")) {
            errors.add("CLAUDE.md must contain at least one section heading")
        }

        if (!content.contains("Build") && !content.contains("Run") && !content.contains("Quick Start")) {
            warnings.add("CLAUDE.md should contain build/run instructions")
        }

        return ValidationResult(errors.isEmpty(), errors, warnings)
    }

    private fun validatePluginManifest(content: String): ValidationResult {
        val errors = mutableListOf<String>()

        for (field in REQUIRED_PLUGIN_MANIFEST_FIELDS) {
            if (!content.contains("\"$field\"")) {
                errors.add("Missing required field: $field")
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    private fun validateHook(content: String): ValidationResult {
        val errors = mutableListOf<String>()

        if (!content.contains("## Trigger") && !content.contains("## When")) {
            errors.add("Hook must define a trigger condition (## Trigger or ## When)")
        }
        if (!content.contains("## Action") && !content.contains("## Steps")) {
            errors.add("Hook must define an action (## Action or ## Steps)")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    private fun migrateSkillV1toV2(content: String): String {
        val frontmatter = extractFrontmatter(content)
        val body = extractBody(content)

        val newFrontmatter = buildString {
            appendLine("---")
            appendLine("name: ${frontmatter["name"] ?: "unknown"}")
            appendLine("version: ${frontmatter["version"] ?: "0.0.0"}")
            appendLine("profile: ${frontmatter["profile"] ?: "general"}")
            appendLine("description: \"${frontmatter["description"] ?: "Migrated from v1 format"}\"")
            // Preserve any additional frontmatter fields
            for ((key, value) in frontmatter) {
                if (key !in listOf("name", "version", "profile", "description")) {
                    appendLine("$key: $value")
                }
            }
            appendLine("---")
        }

        val newBody = if (!body.contains("## Quality Criteria")) {
            "$body\n\n## Quality Criteria\n\n- Meets the stated purpose\n- Follows project coding conventions\n- Passes existing tests\n"
        } else {
            body
        }

        return "$newFrontmatter\n$newBody"
    }
}
