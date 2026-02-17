package com.forge.webide.entity

import jakarta.persistence.*

@Entity
@Table(name = "tool_calls")
class ToolCallEntity(
    @Id
    val id: String,

    @Column(name = "message_id", nullable = false)
    val messageId: String,

    @Column(name = "tool_name", nullable = false)
    val toolName: String,

    @Column(name = "input", length = 1_000_000)
    val input: String? = null,

    @Column(name = "output", length = 1_000_000)
    val output: String? = null,

    @Column(name = "status", length = 20)
    val status: String = "complete",

    @Column(name = "duration_ms")
    val durationMs: Long? = null
)
