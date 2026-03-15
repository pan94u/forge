package com.forge.eval.protocol

import java.time.Instant
import java.util.UUID

/** Definition of an evaluation suite — a collection of related eval tasks */
data class EvalSuite(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String = "",
    val platform: Platform,
    val agentType: AgentType,
    val lifecycle: Lifecycle = Lifecycle.CAPABILITY,
    val tags: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
