package com.forge.webide.repository

import com.forge.webide.entity.ChatSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ChatSessionRepository : JpaRepository<ChatSessionEntity, String> {

    fun findByWorkspaceId(workspaceId: String): List<ChatSessionEntity>

    fun findByUserId(userId: String): List<ChatSessionEntity>

    fun findByWorkspaceIdAndUserId(workspaceId: String, userId: String): List<ChatSessionEntity>

    @Query("""
        SELECT COUNT(s) FROM ChatSessionEntity s, WorkspaceEntity w
        WHERE s.workspaceId = w.id AND w.orgId = :orgId AND s.createdAt >= :since
    """)
    fun countByOrgSince(@Param("orgId") orgId: String, @Param("since") since: Instant): Long

    @Query("""
        SELECT s.createdAt FROM ChatSessionEntity s, WorkspaceEntity w
        WHERE s.workspaceId = w.id AND w.orgId = :orgId AND s.createdAt >= :since
    """)
    fun findTimestampsByOrg(@Param("orgId") orgId: String, @Param("since") since: Instant): List<Instant>
}
