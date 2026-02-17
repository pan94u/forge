package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "chat_messages")
class ChatMessageEntity(
    @Id
    val id: String,

    @Column(name = "session_id", nullable = false)
    val sessionId: String,

    @Column(name = "role", nullable = false, length = 20)
    val role: String,

    @Column(name = "content", nullable = false, length = 1_000_000)
    val content: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "messageId", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val toolCalls: MutableList<ToolCallEntity> = mutableListOf()
)
