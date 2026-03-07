package com.forge.webide.repository

import com.forge.webide.entity.GovernanceAuditLogEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GovernanceAuditLogRepository : JpaRepository<GovernanceAuditLogEntity, String> {

    fun findByOrgIdOrderByCreatedAtDesc(orgId: String, pageable: Pageable): Page<GovernanceAuditLogEntity>

    fun findByOrgIdAndDomainOrderByCreatedAtDesc(orgId: String, domain: String): List<GovernanceAuditLogEntity>

    fun countByOrgId(orgId: String): Long
}
