package com.forge.skilltests

import org.slf4j.LoggerFactory

/**
 * Validates the structure and content of SKILL.md files.
 *
 * Enforces the Forge skill format specification:
 * - Valid YAML frontmatter with required fields
 * - Required Markdown sections present
 * - Semantic versioning format
 * - Valid profile names
 * - Consistent naming conventions
 * - No empty required sections
 */
class SkillValidator(
    private val frontmatterParser: FrontmatterParser = FrontmatterParser()
) {

    private val logger = LoggerFactory.getLogger(SkillValidator::class.java)

    companion object {
        val VALID_PROFILES = setOf(
            "planning", "development", "testing", "design", "review",
            "deployment", "debugging", "documentation", "knowledge", "general"
        )

        val REQUIRED_SECTIONS = listOf(
            "## Purpose",
            "## Instructions"
        )

        val RECOMMENDED_SECTIONS = listOf(
            "## Quality Criteria",
            "## Examples",
            "## Anti-patterns"
        )

        val SEMVER_PATTERN = Regex("""^\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?$""")
        val KEBAB_CASE_PATTERN = Regex("""^[a-z][a-z0-9]*(-[a-z0-9]+)*$""")

        const val MIN_PURPOSE_LENGTH = 20
        const val MIN_INSTRUCTIONS_LENGTH = 50
    }

    /**
     * Result of validating a single SKILL.md file.
     */
    data class ValidationResult(
        val skillPath: String,
        val skillName: String,
        val valid: Boolean,
        val errors: List<ValidationError>,
        val warnings: List<ValidationWarning>
    ) {
        val errorCount: Int get() = errors.size
        val warningCount: Int get() = warnings.size
    }

    data class ValidationError(
        val code: String,
        val message: String,
        val line: Int? = null
    )

    data class ValidationWarning(
        val code: String,
        val message: String,
        val suggestion: String? = null
    )

    /**
     * Validate a SKILL.md file's content.
     *
     * @param content The full file content
     * @param filePath The file path (for error reporting)
     * @return Validation result with errors and warnings
     */
    fun validate(content: String, filePath: String = "SKILL.md"): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        var skillName = "unknown"

        // 1. Check frontmatter exists and is parseable
        if (!frontmatterParser.hasFrontmatter(content)) {
            errors.add(
                ValidationError(
                    "MISSING_FRONTMATTER",
                    "SKILL.md must start with YAML frontmatter delimited by ---"
                )
            )
            return ValidationResult(filePath, skillName, false, errors, warnings)
        }

        val parseResult = try {
            frontmatterParser.parse(content)
        } catch (e: FrontmatterParseException) {
            errors.add(
                ValidationError(
                    "INVALID_FRONTMATTER",
                    "Failed to parse frontmatter: ${e.message}"
                )
            )
            return ValidationResult(filePath, skillName, false, errors, warnings)
        }

        val fm = parseResult.frontmatter
        skillName = fm.name

        // 2. Validate frontmatter fields
        validateFrontmatter(fm, errors, warnings)

        // 3. Validate required sections
        validateSections(parseResult.body, errors, warnings)

        // 4. Validate section content quality
        validateSectionContent(parseResult.body, errors, warnings)

        // 5. Check naming consistency
        validateNamingConsistency(fm, filePath, errors, warnings)

        return ValidationResult(
            skillPath = filePath,
            skillName = skillName,
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun validateFrontmatter(
        fm: FrontmatterParser.SkillFrontmatter,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        // Name format
        if (!KEBAB_CASE_PATTERN.matches(fm.name)) {
            errors.add(
                ValidationError(
                    "INVALID_NAME_FORMAT",
                    "Skill name '${fm.name}' must be kebab-case (e.g., 'code-review')"
                )
            )
        }

        // Version format
        if (!SEMVER_PATTERN.matches(fm.version)) {
            errors.add(
                ValidationError(
                    "INVALID_VERSION",
                    "Version '${fm.version}' must follow semantic versioning (e.g., '1.0.0')"
                )
            )
        }

        // Profile value
        if (fm.profile !in VALID_PROFILES) {
            errors.add(
                ValidationError(
                    "INVALID_PROFILE",
                    "Profile '${fm.profile}' is not valid. Must be one of: ${VALID_PROFILES.joinToString(", ")}"
                )
            )
        }

        // Description
        if (fm.description.isBlank()) {
            warnings.add(
                ValidationWarning(
                    "MISSING_DESCRIPTION",
                    "Skill has no description",
                    "Add a 'description' field to the frontmatter"
                )
            )
        }

        // Tags
        if (fm.tags.isEmpty()) {
            warnings.add(
                ValidationWarning(
                    "NO_TAGS",
                    "Skill has no tags",
                    "Add tags for better discoverability"
                )
            )
        }
    }

    private fun validateSections(
        body: String,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        for (section in REQUIRED_SECTIONS) {
            if (!body.contains(section)) {
                errors.add(
                    ValidationError(
                        "MISSING_SECTION",
                        "Required section '$section' is missing"
                    )
                )
            }
        }

        for (section in RECOMMENDED_SECTIONS) {
            if (!body.contains(section)) {
                warnings.add(
                    ValidationWarning(
                        "MISSING_RECOMMENDED_SECTION",
                        "Recommended section '$section' is missing",
                        "Consider adding '$section' to improve skill quality"
                    )
                )
            }
        }
    }

    private fun validateSectionContent(
        body: String,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        val sections = parseSections(body)

        // Check Purpose section content
        val purposeContent = sections["Purpose"]
        if (purposeContent != null && purposeContent.trim().length < MIN_PURPOSE_LENGTH) {
            warnings.add(
                ValidationWarning(
                    "SHORT_PURPOSE",
                    "Purpose section is very short (${purposeContent.trim().length} chars)",
                    "Purpose should clearly explain what this skill does and when to use it"
                )
            )
        }

        // Check Instructions section content
        val instructionsContent = sections["Instructions"]
        if (instructionsContent != null && instructionsContent.trim().length < MIN_INSTRUCTIONS_LENGTH) {
            warnings.add(
                ValidationWarning(
                    "SHORT_INSTRUCTIONS",
                    "Instructions section is very short (${instructionsContent.trim().length} chars)",
                    "Instructions should provide detailed, actionable guidance"
                )
            )
        }
    }

    private fun validateNamingConsistency(
        fm: FrontmatterParser.SkillFrontmatter,
        filePath: String,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        // Check if the skill name matches its directory name
        val dirName = filePath.substringBeforeLast("/SKILL.md").substringAfterLast("/")
        if (dirName != "SKILL.md" && dirName != fm.name && dirName.isNotBlank()) {
            warnings.add(
                ValidationWarning(
                    "NAME_DIR_MISMATCH",
                    "Skill name '${fm.name}' does not match directory name '$dirName'",
                    "Rename the directory to '${fm.name}' or update the frontmatter name"
                )
            )
        }
    }

    /**
     * Parse Markdown body into sections by ## headings.
     */
    private fun parseSections(body: String): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        var currentSection: String? = null
        val currentContent = StringBuilder()

        for (line in body.lines()) {
            if (line.startsWith("## ")) {
                if (currentSection != null) {
                    sections[currentSection] = currentContent.toString()
                }
                currentSection = line.removePrefix("## ").trim()
                currentContent.clear()
            } else {
                currentContent.appendLine(line)
            }
        }

        if (currentSection != null) {
            sections[currentSection] = currentContent.toString()
        }

        return sections
    }
}
