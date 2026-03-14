package com.forge.webide.repository

import com.forge.webide.entity.EvalTaskEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EvalTaskRepository : JpaRepository<EvalTaskEntity, String> {

    fun findByIsActiveTrue(): List<EvalTaskEntity>

    fun findByOrgIdIsNullAndIsActiveTrue(): List<EvalTaskEntity>

    fun findByOrgIdAndIsActiveTrue(orgId: String): List<EvalTaskEntity>

    @Query("SELECT t FROM EvalTaskEntity t WHERE (t.orgId IS NULL OR t.orgId = :orgId) AND t.isActive = true")
    fun findAvailableForOrg(orgId: String): List<EvalTaskEntity>

    @Query("SELECT t FROM EvalTaskEntity t WHERE (t.orgId IS NULL OR t.orgId = :orgId) AND t.isActive = true AND t.taskType = :taskType")
    fun findAvailableForOrgByType(orgId: String, taskType: String): List<EvalTaskEntity>

    @Query("SELECT t FROM EvalTaskEntity t WHERE (t.orgId IS NULL OR t.orgId = :orgId) AND t.isActive = true AND t.difficulty = :difficulty")
    fun findAvailableForOrgByDifficulty(orgId: String, difficulty: String): List<EvalTaskEntity>

    fun countByOrgIdIsNullAndIsActiveTrue(): Long

    fun countByOrgIdAndIsActiveTrue(orgId: String): Long
}
