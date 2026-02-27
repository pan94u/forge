package com.forge.webide.repository

import com.forge.webide.entity.OrgDbConnectionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrgDbConnectionRepository : JpaRepository<OrgDbConnectionEntity, String> {
    fun findByOrgId(orgId: String): List<OrgDbConnectionEntity>
}
