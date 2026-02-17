package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "chat_sessions")
class ChatSessionEntity(
    @Id
    val id: String,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: String,

    @Column(name = "user_id")
    val userId: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "sessionId", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val messages: MutableList<ChatMessageEntity> = mutableListOf()
)
