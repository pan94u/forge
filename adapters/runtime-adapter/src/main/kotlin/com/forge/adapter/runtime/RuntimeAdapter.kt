package com.forge.adapter.runtime

/**
 * Abstraction layer for the agent runtime environment.
 *
 * The RuntimeAdapter isolates Forge skills and baselines from the specifics
 * of which agent runtime is executing them. Today this is Claude Code, but
 * the adapter pattern allows future support for other runtimes (Cursor Agent,
 * GitHub Copilot Workspace, custom orchestrators, etc.) without changing
 * any skill or baseline code.
 *
 * Key responsibilities:
 * - Resolve and load skill assets (SKILL.md, CLAUDE.md, commands)
 * - Execute baseline scripts and report results
 * - Query runtime capabilities (tools available, context window, etc.)
 * - Provide environment context to skills
 */
interface RuntimeAdapter {

    /**
     * Identify the current runtime environment.
     */
    fun runtimeInfo(): RuntimeInfo

    /**
     * Load a skill by name, resolving it from the configured plugin directories.
     *
     * @param skillName Fully-qualified skill name (e.g., "forge-superagent/code-review")
     * @return The resolved skill definition, or null if not found
     */
    suspend fun loadSkill(skillName: String): SkillDefinition?

    /**
     * List all available skills across loaded plugins.
     */
    suspend fun listSkills(): List<SkillSummary>

    /**
     * Execute a baseline script and return the result.
     *
     * @param baselinePath Path to the baseline script
     * @param workingDir Working directory for execution
     * @param env Additional environment variables
     * @return Execution result with exit code, stdout, stderr
     */
    suspend fun executeBaseline(
        baselinePath: String,
        workingDir: String,
        env: Map<String, String> = emptyMap()
    ): BaselineResult

    /**
     * Query the available tools in the current runtime.
     */
    suspend fun availableTools(): List<ToolInfo>

    /**
     * Read the effective CLAUDE.md context for the current project,
     * merging organization, team, and project-level instructions.
     */
    suspend fun effectiveContext(): String

    /**
     * Report a structured event to the runtime's telemetry system.
     */
    suspend fun reportEvent(event: RuntimeEvent)
}

/**
 * Information about the runtime environment.
 */
data class RuntimeInfo(
    /** Runtime name (e.g., "claude-code", "cursor-agent") */
    val name: String,
    /** Runtime version */
    val version: String,
    /** Available context window in tokens */
    val contextWindow: Int,
    /** Whether the runtime supports MCP */
    val supportsMcp: Boolean,
    /** Whether the runtime supports multi-file editing */
    val supportsMultiFileEdit: Boolean,
    /** Additional runtime capabilities */
    val capabilities: Set<String>
)

/**
 * A fully resolved skill definition loaded from disk.
 */
data class SkillDefinition(
    /** Skill name */
    val name: String,
    /** Plugin that provides this skill */
    val plugin: String,
    /** Skill profile (planning, development, testing, etc.) */
    val profile: String,
    /** Skill version */
    val version: String,
    /** The full SKILL.md content */
    val content: String,
    /** Parsed frontmatter metadata */
    val metadata: Map<String, String>,
    /** Associated baseline scripts, if any */
    val baselines: List<String>,
    /** MCP tools this skill depends on */
    val requiredTools: List<String>
)

/**
 * Summary information about a skill (for listing).
 */
data class SkillSummary(
    val name: String,
    val plugin: String,
    val profile: String,
    val version: String,
    val description: String
)

/**
 * Result of executing a baseline script.
 */
data class BaselineResult(
    /** Process exit code (0 = success) */
    val exitCode: Int,
    /** Standard output content */
    val stdout: String,
    /** Standard error content */
    val stderr: String,
    /** Execution duration in milliseconds */
    val durationMs: Long,
    /** Whether the baseline passed (exit code 0 and no error patterns) */
    val passed: Boolean
)

/**
 * Information about a tool available in the runtime.
 */
data class ToolInfo(
    /** Tool name */
    val name: String,
    /** Tool description */
    val description: String,
    /** Source: "built-in", "mcp", "plugin" */
    val source: String,
    /** MCP server name if source is "mcp" */
    val mcpServer: String? = null
)

/**
 * A structured event for runtime telemetry.
 */
data class RuntimeEvent(
    /** Event type (e.g., "skill.activated", "baseline.executed", "error.occurred") */
    val type: String,
    /** Event payload */
    val data: Map<String, Any>,
    /** Timestamp in epoch milliseconds */
    val timestamp: Long = System.currentTimeMillis()
)
