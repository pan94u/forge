package com.forge.webide.repository

import com.forge.webide.entity.OrgModelConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrgModelConfigRepository : JpaRepository<OrgModelConfigEntity, String> {
    fun findByOrgId(orgId: String): List<OrgModelConfigEntity>
    fun findByOrgIdAndProvider(orgId: String, provider: String): OrgModelConfigEntity?
}
