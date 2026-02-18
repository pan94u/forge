package com.forge.webide.service.skill

import com.forge.webide.service.McpProxyService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Assembles a dynamic system prompt from a Profile, its loaded Skills,
 * and the SuperAgent instructions.
 *
 * Structure:
 * [1] SuperAgent role definition (core paragraphs from CLAUDE.md)
 * [2] Current Profile OODA guidance
 * [3] Loaded Skills content (each as an independent section)
 * [4] Baseline execution rules
 * [5] HITL checkpoint rules
 * [6] Available MCP tools list
 */
@Service
class SystemPromptAssembler(
    private val skillLoader: SkillLoader,
    private val mcpProxyService: McpProxyService
) {
    private val logger = LoggerFactory.getLogger(SystemPromptAssembler::class.java)

    companion object {
        /**
         * Maximum tokens (estimated at ~4 chars/token) for the assembled prompt.
         * 50K tokens ≈ 200K chars. Claude's 200K context window can handle this.
         */
        private const val MAX_PROMPT_CHARS = 200_000

        private const val FALLBACK_PROMPT = """You are Forge AI, an intelligent development assistant embedded in the Forge Web IDE.

You help developers with:
- Understanding and explaining code
- Answering questions about the codebase and architecture
- Suggesting improvements and best practices
- Debugging issues
- Writing and modifying code
- Navigating the knowledge base

When provided with file context, analyze the code carefully and provide specific, actionable advice.
When using MCP tools, explain what you're doing and why.
Always be concise but thorough in your responses."""
    }

    /**
     * Assemble a complete system prompt for the given profile and skills.
     */
    fun assemble(profile: ProfileDefinition, skills: List<SkillDefinition>): String {
        val sections = mutableListOf<String>()

        // [1] SuperAgent role definition
        val superAgentIntro = extractRoleDefinition(skillLoader.superAgentInstructions)
        if (superAgentIntro.isNotBlank()) {
            sections.add(superAgentIntro)
        }

        // [2] Active profile context
        sections.add(buildProfileSection(profile))

        // [3] Loaded skills
        val skillsSections = buildSkillsSections(skills)
        sections.addAll(skillsSections)

        // [4] Baseline rules
        if (profile.baselines.isNotEmpty()) {
            sections.add(buildBaselineSection(profile))
        }

        // [5] HITL checkpoint
        if (profile.hitlCheckpoint.isNotBlank()) {
            sections.add(buildHitlSection(profile))
        }

        // [6] Available MCP tools
        val toolsSection = buildToolsSection()
        if (toolsSection.isNotBlank()) {
            sections.add(toolsSection)
        }

        val assembled = sections.joinToString("\n\n---\n\n")

        // Truncate if exceeding limit
        if (assembled.length > MAX_PROMPT_CHARS) {
            logger.warn(
                "Assembled prompt exceeds limit ({} chars > {}), truncating skills",
                assembled.length, MAX_PROMPT_CHARS
            )
            return truncatePrompt(profile, skills, superAgentIntro)
        }

        logger.debug(
            "Assembled system prompt: {} chars, profile={}, skills={}",
            assembled.length, profile.name, skills.map { it.name }
        )

        return assembled
    }

    /**
     * Returns the fallback static prompt when skill loading fails.
     */
    fun fallbackPrompt(): String = FALLBACK_PROMPT

    /**
     * Extract the role definition section from CLAUDE.md.
     * Takes the content from start through the "Skill Profile Routing Logic" header.
     */
    internal fun extractRoleDefinition(claudeMd: String): String {
        if (claudeMd.isBlank()) return ""

        // Take the role definition and OODA loop sections
        val lines = claudeMd.lines()
        val sections = mutableListOf<String>()
        var capturing = true

        for (line in lines) {
            if (line.startsWith("## Skill Profile Routing Logic")) {
                // Skip routing logic (handled by ProfileRouter)
                capturing = false
                continue
            }
            if (!capturing && line.startsWith("## ") && line != "## Skill Profile Routing Logic") {
                capturing = true
            }
            if (line.startsWith("## Foundation Skills Loading") ||
                line.startsWith("## Domain Skills Loading") ||
                line.startsWith("## Knowledge Integration") ||
                line.startsWith("## Profile Transitions") ||
                line.startsWith("## Execution Logging")
            ) {
                // Skip sections handled by the platform
                capturing = false
                continue
            }
            if (!capturing && line.startsWith("## ")) {
                capturing = true
            }
            if (capturing) {
                sections.add(line)
            }
        }

        return sections.joinToString("\n").trim()
    }

    private fun buildProfileSection(profile: ProfileDefinition): String {
        val sb = StringBuilder()
        sb.appendLine("## Active Profile: ${profile.name}")
        sb.appendLine()
        sb.appendLine(profile.description)

        if (profile.oodaGuidance.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(profile.oodaGuidance)
        }

        return sb.toString().trim()
    }

    private fun buildSkillsSections(skills: List<SkillDefinition>): List<String> {
        return skills.map { skill ->
            "## Skill: ${skill.name}\n\n${skill.content}"
        }
    }

    private fun buildBaselineSection(profile: ProfileDefinition): String {
        val sb = StringBuilder()
        sb.appendLine("## Baseline Enforcement")
        sb.appendLine()
        sb.appendLine("After every Act phase, run ALL of the following baselines:")
        for (baseline in profile.baselines) {
            sb.appendLine("- **$baseline**")
        }
        sb.appendLine()
        sb.appendLine("On any failure: do NOT deliver the result. Loop back to Observe, fix the issue, and re-run.")
        sb.appendLine("Maximum 3 OODA loops for baseline fixes. If still failing, escalate to the user.")
        return sb.toString().trim()
    }

    private fun buildHitlSection(profile: ProfileDefinition): String {
        return """
            |## HITL Checkpoint
            |
            |${profile.hitlCheckpoint}
            |
            |**Protocol**: Pause execution, present deliverables clearly, highlight risks, and wait for explicit approval before proceeding.
        """.trimMargin()
    }

    private fun buildToolsSection(): String {
        return try {
            val tools = mcpProxyService.listTools()
            if (tools.isEmpty()) return ""

            val sb = StringBuilder()
            sb.appendLine("## Available MCP Tools")
            sb.appendLine()
            for (tool in tools) {
                sb.appendLine("- **${tool.name}**: ${tool.description}")
            }

            // Add baseline tool usage guidance
            val hasBaselineTool = tools.any { it.name == "run_baseline" }
            if (hasBaselineTool) {
                sb.appendLine()
                sb.appendLine("### Baseline Quality Gates")
                sb.appendLine()
                sb.appendLine("After generating or modifying code, use the `run_baseline` tool to verify quality.")
                sb.appendLine("This runs automated quality gate scripts that check code style, security, test coverage,")
                sb.appendLine("API contracts, and architecture constraints.")
                sb.appendLine()
                sb.appendLine("**Workflow**:")
                sb.appendLine("1. Generate or modify code (Act phase)")
                sb.appendLine("2. Call `run_baseline` with relevant baselines (e.g. `[\"code-style-baseline\", \"security-baseline\"]`)")
                sb.appendLine("3. If any baseline fails, analyze the failure output and fix the issues")
                sb.appendLine("4. Re-run baselines to confirm the fix")
                sb.appendLine("5. Only present the final result to the user after all baselines pass")
                sb.appendLine()
                sb.appendLine("Use `list_baselines` to see all available baseline scripts.")
            }

            // Add knowledge search guidance
            val hasKnowledgeTool = tools.any { it.name == "search_knowledge" }
            if (hasKnowledgeTool) {
                sb.appendLine()
                sb.appendLine("### Knowledge Base")
                sb.appendLine()
                sb.appendLine("Use `search_knowledge` to find relevant documentation before making decisions.")
                sb.appendLine("The knowledge base contains ADRs (Architecture Decision Records), runbooks,")
                sb.appendLine("coding conventions, and API documentation.")
                sb.appendLine("Use `read_file` to read the full content of a specific knowledge document.")
            }

            sb.toString().trim()
        } catch (e: Exception) {
            logger.debug("Could not load MCP tools for prompt: {}", e.message)
            ""
        }
    }

    /**
     * Truncate by dropping low-priority skill content until under the limit.
     */
    private fun truncatePrompt(
        profile: ProfileDefinition,
        skills: List<SkillDefinition>,
        roleDefinition: String
    ): String {
        val sections = mutableListOf<String>()
        if (roleDefinition.isNotBlank()) sections.add(roleDefinition)
        sections.add(buildProfileSection(profile))

        var currentLength = sections.sumOf { it.length }

        // Add skills in order until we'd exceed the limit
        for (skill in skills) {
            val skillSection = "## Skill: ${skill.name}\n\n${skill.content}"
            if (currentLength + skillSection.length + 10 > MAX_PROMPT_CHARS) {
                sections.add("## Note: Some skills were truncated due to prompt size limits.")
                break
            }
            sections.add(skillSection)
            currentLength += skillSection.length + 10
        }

        return sections.joinToString("\n\n---\n\n")
    }
}
