package com.forge.webide.repository

import com.forge.webide.entity.ChatSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatSessionRepository : JpaRepository<ChatSessionEntity, String> {

    fun findByWorkspaceId(workspaceId: String): List<ChatSessionEntity>

    fun findByUserId(userId: String): List<ChatSessionEntity>

    fun findByWorkspaceIdAndUserId(workspaceId: String, userId: String): List<ChatSessionEntity>
}
