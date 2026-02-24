package com.forge.webide.service.skill

import com.forge.webide.service.McpProxyService
import com.forge.webide.service.memory.MemoryContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Assembles a dynamic system prompt from a Profile, its loaded Skills,
 * and the SuperAgent instructions.
 *
 * Phase 4: Only injects Level 1 metadata (name + description) for each skill.
 * Full skill content is served on-demand via read_skill MCP tool.
 *
 * Structure:
 * [1] SuperAgent role definition (core paragraphs from CLAUDE.md)
 * [2] Current Profile OODA guidance
 * [3] Available Skills metadata (name + description only)
 * [4] Baseline execution rules
 * [5] HITL checkpoint rules
 * [6] Available MCP tools list + progressive loading protocol
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
        return assemble(profile, skills, MemoryContext())
    }

    /**
     * Assemble a complete system prompt with memory context injection.
     * Memory layers are inserted between the SuperAgent role and the active profile.
     */
    fun assemble(
        profile: ProfileDefinition,
        skills: List<SkillDefinition>,
        memoryContext: MemoryContext
    ): String {
        val sections = mutableListOf<String>()

        // [1] SuperAgent role definition
        val superAgentIntro = extractRoleDefinition(skillLoader.superAgentInstructions)
        if (superAgentIntro.isNotBlank()) {
            sections.add(superAgentIntro)
        }

        // [2] Workspace Memory (Layer 1) — always injected
        if (memoryContext.workspaceMemory.isNotBlank()) {
            sections.add("## 工作区记忆\n\n${memoryContext.workspaceMemory}")
        }

        // [3] Stage Memory (Layer 2) — Profile-scoped
        if (memoryContext.stageMemory.isNotBlank()) {
            sections.add("## 当前阶段上下文\n\n${memoryContext.stageMemory}")
        }

        // [4] Recent Session Summaries (Layer 3)
        if (memoryContext.recentSessions.isNotEmpty()) {
            val sessionsSection = buildString {
                appendLine("## 近期会话摘要")
                appendLine()
                for (s in memoryContext.recentSessions) {
                    appendLine(s)
                    appendLine()
                }
            }
            sections.add(sessionsSection.trim())
        }

        // [5] Active profile context
        sections.add(buildProfileSection(profile))

        // [6] Available Skills — Level 1 metadata only
        val skillsSection = buildSkillsSections(skills)
        if (skillsSection.isNotBlank()) {
            sections.add(skillsSection)
        }

        // [7] Baseline rules
        if (profile.baselines.isNotEmpty()) {
            sections.add(buildBaselineSection(profile))
        }

        // [8] HITL checkpoint
        if (profile.hitlCheckpoint.isNotBlank()) {
            sections.add(buildHitlSection(profile))
        }

        // [9] Available MCP tools + progressive loading protocol
        val toolsSection = buildToolsSection(profile.mode)
        if (toolsSection.isNotBlank()) {
            sections.add(toolsSection)
        }

        val assembled = sections.joinToString("\n\n---\n\n")

        // Truncate if exceeding limit (unlikely with metadata-only skills)
        if (assembled.length > MAX_PROMPT_CHARS) {
            logger.warn(
                "Assembled prompt exceeds limit ({} chars > {}), truncating",
                assembled.length, MAX_PROMPT_CHARS
            )
            return assembled.take(MAX_PROMPT_CHARS)
        }

        logger.debug(
            "Assembled system prompt: {} chars, profile={}, skills={}, memory={}c+{}c+{}sessions",
            assembled.length, profile.name, skills.map { it.name },
            memoryContext.workspaceMemory.length, memoryContext.stageMemory.length,
            memoryContext.recentSessions.size
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

    /**
     * Build skills section with Level 1 metadata only.
     * Full content is served on-demand via read_skill MCP tool.
     */
    private fun buildSkillsSections(skills: List<SkillDefinition>): String {
        if (skills.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("## Available Skills")
        sb.appendLine()
        sb.appendLine("以下 Skills 已为当前 Profile 加载。使用 `read_skill` 工具读取详细指南，使用 `run_skill_script` 执行脚本。")
        sb.appendLine()

        for (skill in skills) {
            sb.appendLine("- **${skill.name}** [${skill.scope.name.lowercase()}/${skill.category.name.lowercase()}]: ${skill.description}")
            if (skill.tags.isNotEmpty()) {
                sb.appendLine("  Tags: ${skill.tags.joinToString(", ")}")
            }
            if (skill.subFiles.isNotEmpty()) {
                sb.appendLine("  Sub-files: ${skill.subFiles.joinToString(", ") { it.path }}")
            }
            if (skill.scripts.isNotEmpty()) {
                sb.appendLine("  Scripts: ${skill.scripts.joinToString(", ") { "${it.path} (${it.scriptType.name.lowercase()})" }}")
            }
        }

        return sb.toString().trim()
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

    private fun buildToolsSection(activeProfileMode: String = "default"): String {
        return try {
            val tools = mcpProxyService.listTools()
            if (tools.isEmpty()) return ""

            val sb = StringBuilder()
            sb.appendLine("## Available MCP Tools")
            sb.appendLine()
            for (tool in tools) {
                sb.appendLine("- **${tool.name}**: ${tool.description}")
            }

            // Progressive loading protocol
            sb.appendLine()
            sb.appendLine("### Skill 渐进式使用协议")
            sb.appendLine()
            sb.appendLine("1. **发现**: 从 Available Skills 列表了解有哪些 Skill 可用")
            sb.appendLine("2. **选择**: 根据任务需求选择相关 Skill")
            sb.appendLine("3. **读取**: 使用 `read_skill(skill_name)` 读取 SKILL.md 核心指南")
            sb.appendLine("4. **深入**: 如需详细示例或参考，读取子文件 `read_skill(skill_name, \"examples/xxx.md\")`")
            sb.appendLine("5. **执行**: 如 Skill 提供脚本，使用 `run_skill_script` 执行确定性操作")
            sb.appendLine("6. **原则**: 不要猜测 Skill 内容，始终先 read 再使用")

            // Add behavior guidance based on profile mode
            val hasWorkspaceTools = tools.any { it.name == "workspace_write_file" }
            if (hasWorkspaceTools && activeProfileMode != "read-only") {
                sb.appendLine()
                sb.appendLine("### Delivery Behavior")
                sb.appendLine()
                sb.appendLine("You are a delivery assistant, not a chatbot. When users request code:")
                sb.appendLine("1. **Always use `workspace_write_file`** to write code into workspace files")
                sb.appendLine("2. Do NOT only show code in chat — actually create the files")
                sb.appendLine("3. Use `workspace_list_files` first to understand the current project structure")
                sb.appendLine("4. After writing files, briefly summarize which files you created or modified")
                sb.appendLine("5. To modify existing files, first use `workspace_read_file` to read them, then use `workspace_write_file` to write the complete updated content")
                sb.appendLine("6. Choose appropriate file paths based on the project structure and conventions")
            }

            if (activeProfileMode == "read-only") {
                sb.appendLine()
                sb.appendLine("### Analysis Behavior (Read-Only Mode)")
                sb.appendLine()
                sb.appendLine("You are in **read-only analysis mode**. Your role is to observe, analyze, and report — NOT to modify code.")
                sb.appendLine("1. Use `workspace_list_files` and `workspace_read_file` to understand the project")
                sb.appendLine("2. Use `search_knowledge` and `get_session_history` to gather historical context")
                sb.appendLine("3. **Do NOT use `workspace_write_file`** unless the user explicitly requests saving a report/document")
                sb.appendLine("4. Output structured analysis reports in chat (Markdown format)")
                sb.appendLine("5. Focus on: project status, code quality, progress assessment, risk identification")
                sb.appendLine("6. When generating documents (architecture diagrams, reports), present in chat first, then ask if the user wants to save")
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

            // Add memory management guidance
            val hasMemoryTools = tools.any { it.name == "update_workspace_memory" }
            if (hasMemoryTools) {
                sb.appendLine()
                sb.appendLine("### 记忆管理协议")
                sb.appendLine()
                sb.appendLine("1. 在每次会话接近结束时，主动使用 `update_workspace_memory` 更新关键发现")
                sb.appendLine("2. 内容应为：项目事实、技术决策、约束条件、当前进度")
                sb.appendLine("3. 限制在 4000 字符以内，保持精简")
                sb.appendLine("4. 不要存储临时性内容（调试日志、中间状态）")
                sb.appendLine("5. 使用 `get_session_history` 回顾之前的工作成果")
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
}
