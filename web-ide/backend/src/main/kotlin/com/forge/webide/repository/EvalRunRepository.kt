package com.forge.webide.repository

import com.forge.webide.entity.EvalRunEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface EvalRunRepository : JpaRepository<EvalRunEntity, String> {

    fun findByOrgIdOrderByCreatedAtDesc(orgId: String): List<EvalRunEntity>

    fun findAllByOrderByCreatedAtDesc(): List<EvalRunEntity>

    fun findByStatus(status: String): List<EvalRunEntity>

    fun findByOrgIdAndStatus(orgId: String, status: String): List<EvalRunEntity>

    fun countByOrgIdAndCreatedAtAfter(orgId: String, since: Instant): Long
}
