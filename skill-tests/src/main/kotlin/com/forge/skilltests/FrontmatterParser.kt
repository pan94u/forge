package com.forge.skilltests

import org.yaml.snakeyaml.Yaml

/**
 * Parses YAML frontmatter from SKILL.md files.
 *
 * SKILL.md files use Jekyll-style YAML frontmatter delimited by `---` markers:
 * ```
 * ---
 * name: my-skill
 * version: 1.0.0
 * profile: development
 * description: "A skill for doing things"
 * required_tools:
 *   - forge-knowledge
 * tags:
 *   - kotlin
 *   - spring-boot
 * ---
 *
 * ## Purpose
 * ...
 * ```
 *
 * This parser extracts the frontmatter into a structured [SkillFrontmatter] object
 * and provides the remaining Markdown body separately.
 */
class FrontmatterParser {

    private val yaml = Yaml()

    /**
     * Parsed result containing both frontmatter metadata and the Markdown body.
     */
    data class ParseResult(
        val frontmatter: SkillFrontmatter,
        val body: String,
        val raw: String
    )

    /**
     * Structured representation of SKILL.md frontmatter fields.
     */
    data class SkillFrontmatter(
        /** Skill name (kebab-case, e.g., "code-review") */
        val name: String,
        /** Semantic version (e.g., "1.2.0") */
        val version: String,
        /** Skill profile: planning, development, testing, design, review, etc. */
        val profile: String,
        /** Human-readable description */
        val description: String = "",
        /** Tags for categorization and search */
        val tags: List<String> = emptyList(),
        /** MCP tools this skill requires */
        val requiredTools: List<String> = emptyList(),
        /** Baseline scripts associated with this skill */
        val baselineRefs: List<String> = emptyList(),
        /** Skills that must be loaded before this one */
        val dependsOn: List<String> = emptyList(),
        /** Additional metadata key-value pairs */
        val extras: Map<String, Any> = emptyMap()
    )

    /**
     * Parse a SKILL.md file content into frontmatter and body.
     *
     * @param content The full SKILL.md file content
     * @return ParseResult with structured frontmatter and body text
     * @throws FrontmatterParseException if the frontmatter is invalid
     */
    fun parse(content: String): ParseResult {
        if (!content.trimStart().startsWith("---")) {
            throw FrontmatterParseException("File does not start with YAML frontmatter delimiter (---)")
        }

        val trimmed = content.trimStart()
        val secondDelimiter = trimmed.indexOf("---", 3)
        if (secondDelimiter < 0) {
            throw FrontmatterParseException("Missing closing frontmatter delimiter (---)")
        }

        val frontmatterYaml = trimmed.substring(3, secondDelimiter).trim()
        val body = trimmed.substring(secondDelimiter + 3).trim()

        val parsed = parseFrontmatterYaml(frontmatterYaml)

        return ParseResult(
            frontmatter = parsed,
            body = body,
            raw = frontmatterYaml
        )
    }

    /**
     * Extract just the frontmatter without parsing the body.
     * Useful for quick metadata lookups across many files.
     */
    fun extractFrontmatter(content: String): SkillFrontmatter? {
        return try {
            parse(content).frontmatter
        } catch (e: FrontmatterParseException) {
            null
        }
    }

    /**
     * Check if a string contains valid frontmatter.
     */
    fun hasFrontmatter(content: String): Boolean {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) return false
        return trimmed.indexOf("---", 3) > 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFrontmatterYaml(yamlContent: String): SkillFrontmatter {
        val data: Map<String, Any> = try {
            yaml.load(yamlContent) as? Map<String, Any>
                ?: throw FrontmatterParseException("Frontmatter is not a valid YAML mapping")
        } catch (e: ClassCastException) {
            throw FrontmatterParseException("Frontmatter is not a valid YAML mapping: ${e.message}")
        } catch (e: Exception) {
            if (e is FrontmatterParseException) throw e
            throw FrontmatterParseException("Failed to parse YAML frontmatter: ${e.message}")
        }

        val name = data["name"] as? String
            ?: throw FrontmatterParseException("Required field 'name' is missing")
        val version = data["version"]?.toString()
            ?: throw FrontmatterParseException("Required field 'version' is missing")
        val profile = data["profile"] as? String
            ?: throw FrontmatterParseException("Required field 'profile' is missing")

        val knownFields = setOf(
            "name", "version", "profile", "description",
            "tags", "required_tools", "baseline_refs", "depends_on"
        )
        val extras = data.filterKeys { it !in knownFields }

        return SkillFrontmatter(
            name = name,
            version = version,
            profile = profile,
            description = data["description"]?.toString() ?: "",
            tags = toStringList(data["tags"]),
            requiredTools = toStringList(data["required_tools"]),
            baselineRefs = toStringList(data["baseline_refs"]),
            dependsOn = toStringList(data["depends_on"]),
            extras = extras
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun toStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.filterNotNull().map { it.toString() }
            is String -> listOf(value)
            else -> emptyList()
        }
    }
}

/**
 * Exception thrown when SKILL.md frontmatter cannot be parsed.
 */
class FrontmatterParseException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
