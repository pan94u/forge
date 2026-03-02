package com.forge.webide.service.skill

/**
 * Skill category determines management behavior:
 * - SYSTEM: core agent instructions, cannot be disabled
 * - FOUNDATION: language/framework conventions, can be disabled
 * - DELIVERY: delivery stage skills, can be disabled
 * - KNOWLEDGE: domain knowledge skills, can be disabled
 * - CUSTOM: user-created skills, can be created/modified/deleted
 */
enum class SkillCategory { SYSTEM, FOUNDATION, DELIVERY, KNOWLEDGE, CUSTOM }

/**
 * Skill scope determines ownership and mutability:
 * - PLATFORM: built-in, read-only (plugins/ directory)
 * - WORKSPACE: workspace-level, editable (workspace/.skills/)
 * - CUSTOM: user-created, full CRUD
 */
enum class SkillScope { PLATFORM, WORKSPACE, CUSTOM, MARKETPLACE }

/**
 * Script type determines the script's role in the dual-loop architecture:
 * - VALIDATION: quality gate scripts (compile check, layer violation, etc.)
 * - EXTRACTION: knowledge extraction scripts (convention mining, rule extraction, etc.)
 */
enum class ScriptType { VALIDATION, EXTRACTION }

enum class SkillContentType { REFERENCE, EXAMPLE, TEMPLATE, SCRIPT }

/**
 * A sub-file within a Skill directory (Level 3 content).
 */
data class SkillSubFile(
    val path: String,
    val description: String,
    val type: SkillContentType
)

/**
 * An executable script within a Skill's scripts/ directory.
 */
data class SkillScript(
    val path: String,
    val description: String,
    val language: String,
    val scriptType: ScriptType = ScriptType.VALIDATION,
    val executionHint: String = "run"
)

/**
 * A loaded Skill definition parsed from a SKILL.md file.
 *
 * Supports 3-level progressive disclosure (Anthropic Agent Skills standard):
 * - Level 1: Metadata (name + description) — always in system prompt
 * - Level 2: SKILL.md content — read on-demand via read_skill tool
 * - Level 3: Sub-files + scripts — read/executed on-demand
 */
/**
 * Quality configuration from SKILL.md frontmatter (Layer 2 rules).
 */
data class SkillQualityConfig(
    val requiredSections: List<String> = emptyList(),
    val forbiddenPatterns: List<String> = emptyList(),
    val minOutputLength: Int = 0,
    val skipDefaultChecks: List<String> = emptyList(),
    val customValidator: String? = null
)

data class SkillDefinition(
    val name: String,
    val description: String,
    val trigger: String? = null,
    val tags: List<String> = emptyList(),
    val stage: String? = null,
    val type: String? = null,
    val content: String,
    val sourcePath: String,
    // Progressive disclosure extensions
    val version: String = "1.0",
    val author: String = "",
    val category: SkillCategory = SkillCategory.CUSTOM,
    val scope: SkillScope = SkillScope.PLATFORM,
    val subFiles: List<SkillSubFile> = emptyList(),
    val scripts: List<SkillScript> = emptyList(),
    val enabled: Boolean = true,
    val isUserCreated: Boolean = false,
    // Phase 8.2: Quality config from frontmatter
    val quality: SkillQualityConfig? = null
) {
    /** Check if this skill matches a given profile by stage mapping. */
    fun matchesProfile(profileName: String): Boolean {
        // Foundation skills match all profiles
        if (type == "foundation-skill" || sourcePath.contains("forge-foundation")) return true
        // If no stage defined, include in all profiles
        if (stage.isNullOrBlank()) return true
        // Map profile name to expected stage
        val expectedStage = when {
            profileName.contains("planning") -> "planning"
            profileName.contains("design") -> "design"
            profileName.contains("development") -> "development"
            profileName.contains("testing") -> "testing"
            profileName.contains("ops") -> "operations"
            profileName.contains("evaluation") -> "evaluation"
            else -> return true // unknown profile → include all
        }
        return stage == expectedStage
    }
}

/**
 * A loaded Profile definition parsed from a profile .md file.
 */
data class ProfileDefinition(
    val name: String,
    val description: String,
    val skills: List<String> = emptyList(),
    val baselines: List<String> = emptyList(),
    val hitlCheckpoint: String = "",
    val oodaGuidance: String,
    val sourcePath: String,
    /** Profile mode: "default" for delivery profiles, "read-only" for analysis/evaluation profiles */
    val mode: String = "default"
)

// ---- HITL (Human-In-The-Loop) types ----

enum class HitlStatus { PENDING, APPROVED, REJECTED, TIMEOUT }

enum class HitlAction { APPROVE, REJECT, MODIFY }

data class HitlDecision(
    val action: HitlAction,
    val feedback: String? = null,
    val modifiedPrompt: String? = null
)
