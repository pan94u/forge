package com.forge.webide.service.eval

/**
 * 评测执行引擎的 Agent 抽象层。
 * 隔离 Forge 内部 Agent 与外部 API 两种执行方式。
 */
interface AgentAdapter {
    fun execute(input: String, config: AgentExecutionConfig): AgentResponse
}

data class AgentExecutionConfig(
    val modelProvider: String? = null,
    val modelName: String? = null,
    val skillProfile: String? = null,
    val workspaceId: String? = null,
    val timeoutMs: Long = 300_000  // 5 min
)

data class AgentResponse(
    val output: String,
    val transcript: List<TranscriptTurn> = emptyList(),
    val durationMs: Long = 0,
    val success: Boolean = true,
    val errorMessage: String? = null
)

data class TranscriptTurn(
    val role: String,     // "user" | "assistant" | "tool"
    val content: String,
    val toolName: String? = null
)
