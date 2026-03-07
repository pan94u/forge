package com.forge.webide.repository

import com.forge.webide.entity.GovernanceSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GovernanceSnapshotRepository : JpaRepository<GovernanceSnapshotEntity, String> {
    fun findByOrgIdAndDomainOrderByCreatedAtDesc(orgId: String, domain: String): List<GovernanceSnapshotEntity>
    fun findByOrgIdOrderByCreatedAtDesc(orgId: String): List<GovernanceSnapshotEntity>
}
