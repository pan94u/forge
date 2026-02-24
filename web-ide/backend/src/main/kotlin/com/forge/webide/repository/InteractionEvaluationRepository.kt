package com.forge.webide.repository

import com.forge.webide.entity.InteractionEvaluationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface InteractionEvaluationRepository : JpaRepository<InteractionEvaluationEntity, String> {

    fun findBySessionId(sessionId: String): List<InteractionEvaluationEntity>

    fun findByWorkspaceId(workspaceId: String): List<InteractionEvaluationEntity>

    fun findByCreatedAtAfterOrderByCreatedAtDesc(since: Instant): List<InteractionEvaluationEntity>

    fun findAllByOrderByCreatedAtDesc(): List<InteractionEvaluationEntity>

    @Query("""
        SELECT e.profile, AVG(e.intentScore), AVG(e.completionScore), AVG(e.qualityScore), AVG(e.experienceScore), COUNT(e)
        FROM InteractionEvaluationEntity e
        WHERE e.createdAt > :since
        GROUP BY e.profile
    """)
    fun avgScoresByProfileSince(since: Instant): List<Array<Any>>

    @Query("""
        SELECT e.capabilityCategory, AVG(e.intentScore), AVG(e.completionScore), AVG(e.qualityScore), AVG(e.experienceScore), COUNT(e)
        FROM InteractionEvaluationEntity e
        WHERE e.createdAt > :since AND e.capabilityCategory <> ''
        GROUP BY e.capabilityCategory
    """)
    fun avgScoresByCategorySince(since: Instant): List<Array<Any>>

    @Query("SELECT COUNT(e) FROM InteractionEvaluationEntity e WHERE e.createdAt > :since")
    fun countSince(since: Instant): Long
}
