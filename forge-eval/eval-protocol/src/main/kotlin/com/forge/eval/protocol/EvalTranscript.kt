package com.forge.eval.protocol

import java.time.Instant
import java.util.UUID

/** A transcript capturing the full interaction trace of a trial */
data class EvalTranscript(
    val id: UUID = UUID.randomUUID(),
    val trialId: UUID? = null,
    val source: TranscriptSource = TranscriptSource.FORGE,
    val turns: List<TranscriptTurn> = emptyList(),
    val toolCallSummary: List<ToolCallInfo> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now()
)

/** A single turn in a transcript */
data class TranscriptTurn(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val timestamp: Instant? = null
)

/** Info about a single tool call within a turn */
data class ToolCallInfo(
    val toolName: String,
    val arguments: Map<String, Any> = emptyMap(),
    val result: String? = null,
    val durationMs: Long? = null
)
