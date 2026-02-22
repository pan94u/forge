package com.forge.webide.service.skill

import com.forge.webide.model.McpTool
import com.forge.webide.service.McpProxyService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SystemPromptAssemblerTest {

    private lateinit var skillLoader: SkillLoader
    private lateinit var mcpProxyService: McpProxyService
    private lateinit var assembler: SystemPromptAssembler

    private val testProfile = ProfileDefinition(
        name = "development-profile",
        description = "Development stage profile",
        skills = listOf("code-generation"),
        baselines = listOf("code-style-baseline", "security-baseline"),
        hitlCheckpoint = "Code review — submit PR for review.",
        oodaGuidance = """
            |# Development Profile OODA Guidance
            |
            |## Observe
            |Read the design documents carefully.
        """.trimMargin(),
        sourcePath = "test"
    )

    private val testSkills = listOf(
        SkillDefinition(
            name = "kotlin-conventions",
            description = "Kotlin coding conventions",
            tags = listOf("kotlin"),
            content = "# Kotlin Conventions\n\nUse data classes for DTOs.",
            sourcePath = "test/kotlin-conventions/SKILL.md",
            category = SkillCategory.FOUNDATION
        ),
        SkillDefinition(
            name = "code-generation",
            description = "Code generation skill",
            tags = listOf("code"),
            content = "# Code Generation\n\nGenerate code from design.",
            sourcePath = "test/code-generation/SKILL.md",
            category = SkillCategory.DELIVERY,
            scripts = listOf(
                SkillScript("scripts/compile_check.py", "Check compilation", "python")
            )
        )
    )

    @BeforeEach
    fun setUp() {
        skillLoader = mockk()
        mcpProxyService = mockk()

        every { skillLoader.superAgentInstructions } returns """
            |# Forge SuperAgent — System Instructions
            |
            |## Role Definition
            |
            |You are **Forge SuperAgent**, a unified intelligent agent.
            |
            |## Skill Profile Routing Logic
            |
            |When a task arrives, determine the correct Skill Profile.
            |
            |## OODA Loop Instructions
            |
            |Every task follows the OODA cycle.
            |
            |## Foundation Skills Loading
            |
            |Foundation Skills are loaded based on context.
        """.trimMargin()

        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search documentation", emptyMap()),
            McpTool("query_database", "Query the database", emptyMap())
        )

        assembler = SystemPromptAssembler(skillLoader, mcpProxyService)
    }

    @Nested
    inner class Assemble {

        @Test
        fun `should include role definition from CLAUDE_md`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("Forge SuperAgent")
            assertThat(prompt).contains("unified intelligent agent")
        }

        @Test
        fun `should exclude routing logic section`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).doesNotContain("Skill Profile Routing Logic")
            assertThat(prompt).doesNotContain("determine the correct Skill Profile")
        }

        @Test
        fun `should include OODA loop instructions`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("OODA Loop Instructions")
        }

        @Test
        fun `should exclude platform-handled sections`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).doesNotContain("Foundation Skills Loading")
            assertThat(prompt).doesNotContain("loaded based on context")
        }

        @Test
        fun `should include active profile section`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("Active Profile: development-profile")
            assertThat(prompt).contains("Development stage profile")
            assertThat(prompt).contains("Development Profile OODA Guidance")
        }

        @Test
        fun `should include skill metadata but NOT full content`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            // Level 1 metadata: name + description should appear
            assertThat(prompt).contains("kotlin-conventions")
            assertThat(prompt).contains("Kotlin coding conventions")
            assertThat(prompt).contains("code-generation")
            assertThat(prompt).contains("Code generation skill")

            // Full skill content should NOT appear in system prompt
            assertThat(prompt).doesNotContain("Use data classes for DTOs.")
            assertThat(prompt).doesNotContain("Generate code from design.")

            // Available Skills section header should exist
            assertThat(prompt).contains("Available Skills")
        }

        @Test
        fun `should include skill scripts in metadata`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("scripts/compile_check.py")
        }

        @Test
        fun `should include skill category in metadata`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("[foundation]")
            assertThat(prompt).contains("[delivery]")
        }

        @Test
        fun `should include progressive loading protocol`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("read_skill")
            assertThat(prompt).contains("run_skill_script")
            assertThat(prompt).contains("渐进式使用协议")
        }

        @Test
        fun `should include baseline enforcement rules`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("Baseline Enforcement")
            assertThat(prompt).contains("code-style-baseline")
            assertThat(prompt).contains("security-baseline")
            assertThat(prompt).contains("Maximum 3 OODA loops")
        }

        @Test
        fun `should include HITL checkpoint`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("HITL Checkpoint")
            assertThat(prompt).contains("Code review")
            assertThat(prompt).contains("wait for explicit approval")
        }

        @Test
        fun `should include available MCP tools`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).contains("Available MCP Tools")
            assertThat(prompt).contains("search_knowledge")
            assertThat(prompt).contains("query_database")
        }

        @Test
        fun `should omit baselines section when profile has no baselines`() {
            val noBaselineProfile = testProfile.copy(baselines = emptyList())

            val prompt = assembler.assemble(noBaselineProfile, testSkills)

            assertThat(prompt).doesNotContain("Baseline Enforcement")
        }

        @Test
        fun `should omit HITL section when checkpoint is blank`() {
            val noHitlProfile = testProfile.copy(hitlCheckpoint = "")

            val prompt = assembler.assemble(noHitlProfile, testSkills)

            assertThat(prompt).doesNotContain("HITL Checkpoint")
        }

        @Test
        fun `should omit tools section when no tools available`() {
            every { mcpProxyService.listTools() } returns emptyList()

            val prompt = assembler.assemble(testProfile, testSkills)

            assertThat(prompt).doesNotContain("Available MCP Tools")
        }

        @Test
        fun `should handle MCP tools error gracefully`() {
            every { mcpProxyService.listTools() } throws RuntimeException("MCP down")

            val prompt = assembler.assemble(testProfile, testSkills)

            // Should still assemble without tools section
            assertThat(prompt).contains("Active Profile")
            assertThat(prompt).doesNotContain("Available MCP Tools")
        }
    }

    @Nested
    inner class ExtractRoleDefinition {

        @Test
        fun `should extract role definition and OODA sections`() {
            val claudeMd = """
                |# Forge SuperAgent
                |
                |## Role Definition
                |You are the SuperAgent.
                |
                |## Skill Profile Routing Logic
                |Route based on tags.
                |
                |## OODA Loop Instructions
                |Follow OODA cycle.
                |
                |## Baseline Enforcement
                |Run baselines.
            """.trimMargin()

            val extracted = assembler.extractRoleDefinition(claudeMd)

            assertThat(extracted).contains("Role Definition")
            assertThat(extracted).contains("You are the SuperAgent.")
            assertThat(extracted).doesNotContain("Skill Profile Routing Logic")
            assertThat(extracted).doesNotContain("Route based on tags")
            assertThat(extracted).contains("OODA Loop Instructions")
            assertThat(extracted).contains("Baseline Enforcement")
        }

        @Test
        fun `should return empty string for blank input`() {
            assertThat(assembler.extractRoleDefinition("")).isEmpty()
            assertThat(assembler.extractRoleDefinition("  ")).isEmpty()
        }
    }

    @Nested
    inner class FallbackPrompt {

        @Test
        fun `should return a valid static prompt`() {
            val fallback = assembler.fallbackPrompt()

            assertThat(fallback).contains("Forge AI")
            assertThat(fallback).contains("intelligent development assistant")
        }
    }

    @Nested
    inner class PromptSizeManagement {

        @Test
        fun `should handle empty skills list`() {
            val prompt = assembler.assemble(testProfile, emptyList())

            assertThat(prompt).contains("Active Profile")
            // The "## Available Skills" section header should not appear when no skills loaded
            assertThat(prompt).doesNotContain("## Available Skills")
        }

        @Test
        fun `prompt with metadata-only skills should be small`() {
            val prompt = assembler.assemble(testProfile, testSkills)

            // With metadata-only skills, prompt should be much smaller than content-based
            // Previously ~55K for 7 skills, now should be < 5K for the skills section
            val skillsSectionStart = prompt.indexOf("## Available Skills")
            val skillsSectionEnd = prompt.indexOf("---", skillsSectionStart + 1).let {
                if (it > 0) it else prompt.length
            }
            val skillsSection = prompt.substring(skillsSectionStart, skillsSectionEnd)

            // Skills section should be < 1K for 2 test skills
            assertThat(skillsSection.length).isLessThan(1000)
        }
    }
}
