package com.forge.webide.repository

import com.forge.webide.entity.KnowledgeGapEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface KnowledgeGapRepository : JpaRepository<KnowledgeGapEntity, String> {

    fun findByResolved(resolved: Boolean): List<KnowledgeGapEntity>

    fun findByTopicAndResolved(topic: String, resolved: Boolean): List<KnowledgeGapEntity>

    fun findByWorkspaceId(workspaceId: String): List<KnowledgeGapEntity>

    fun findByCreatedAtAfter(since: Instant): List<KnowledgeGapEntity>

    @Query("SELECT g FROM KnowledgeGapEntity g WHERE g.resolved = false AND g.hitCount >= :minHits ORDER BY g.hitCount DESC")
    fun findFrequentUnresolved(minHits: Int): List<KnowledgeGapEntity>

    @Query("SELECT g FROM KnowledgeGapEntity g WHERE g.topic = :topic AND g.resolved = false")
    fun findUnresolvedByTopic(topic: String): List<KnowledgeGapEntity>
}
