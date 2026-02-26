package com.forge.webide.repository

import com.forge.webide.entity.KnowledgeExtractionLogEntity
import org.springframework.data.jpa.repository.JpaRepository

interface KnowledgeExtractionLogRepository : JpaRepository<KnowledgeExtractionLogEntity, String> {
    fun findByJobIdOrderByCreatedAtAsc(jobId: String): List<KnowledgeExtractionLogEntity>
    fun findByTagIdOrderByCreatedAtDesc(tagId: String): List<KnowledgeExtractionLogEntity>
    fun findTop30ByOrderByCreatedAtDesc(): List<KnowledgeExtractionLogEntity>
    fun findByWorkspaceIdOrderByCreatedAtDesc(workspaceId: String): List<KnowledgeExtractionLogEntity>
}
