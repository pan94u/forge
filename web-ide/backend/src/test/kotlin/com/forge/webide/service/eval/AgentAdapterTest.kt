package com.forge.webide.service.eval

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentAdapterTest {

    @Test
    fun `AgentExecutionConfig has sensible defaults`() {
        val config = AgentExecutionConfig()
        assertThat(config.modelProvider).isNull()
        assertThat(config.modelName).isNull()
        assertThat(config.skillProfile).isNull()
        assertThat(config.workspaceId).isNull()
        assertThat(config.timeoutMs).isEqualTo(300_000)
    }

    @Test
    fun `AgentExecutionConfig accepts custom values`() {
        val config = AgentExecutionConfig(
            modelProvider = "anthropic",
            modelName = "claude-sonnet-4-20250514",
            skillProfile = "development-profile",
            workspaceId = "ws-001",
            timeoutMs = 60_000
        )
        assertThat(config.modelProvider).isEqualTo("anthropic")
        assertThat(config.timeoutMs).isEqualTo(60_000)
    }

    @Test
    fun `AgentResponse defaults to success`() {
        val response = AgentResponse(output = "hello")
        assertThat(response.success).isTrue()
        assertThat(response.errorMessage).isNull()
        assertThat(response.transcript).isEmpty()
        assertThat(response.durationMs).isEqualTo(0)
    }

    @Test
    fun `TranscriptTurn data class works correctly`() {
        val turn = TranscriptTurn(role = "assistant", content = "result", toolName = "search")
        assertThat(turn.role).isEqualTo("assistant")
        assertThat(turn.toolName).isEqualTo("search")

        val userTurn = TranscriptTurn(role = "user", content = "question")
        assertThat(userTurn.toolName).isNull()
    }
}
