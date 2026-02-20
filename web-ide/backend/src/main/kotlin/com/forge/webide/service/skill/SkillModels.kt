package com.forge.webide.service.skill

/**
 * A loaded Skill definition parsed from a SKILL.md file.
 */
data class SkillDefinition(
    val name: String,
    val description: String,
    val trigger: String? = null,
    val tags: List<String> = emptyList(),
    val stage: String? = null,
    val type: String? = null,
    val content: String,
    val sourcePath: String
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
    val sourcePath: String
)

/**
 * Result of routing a user message to a specific Profile.
 */
data class ProfileRoutingResult(
    val profile: ProfileDefinition,
    val confidence: Double,
    val reason: String
)
