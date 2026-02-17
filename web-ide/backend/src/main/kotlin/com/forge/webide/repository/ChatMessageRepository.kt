package com.forge.webide.repository

import com.forge.webide.entity.ChatMessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessageEntity, String> {

    fun findBySessionIdOrderByCreatedAt(sessionId: String): List<ChatMessageEntity>
}
