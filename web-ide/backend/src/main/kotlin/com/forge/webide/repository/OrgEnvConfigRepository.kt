package com.forge.webide.repository

import com.forge.webide.entity.OrgEnvConfigEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrgEnvConfigRepository : JpaRepository<OrgEnvConfigEntity, String> {
    fun findByOrgId(orgId: String): List<OrgEnvConfigEntity>
    fun findByOrgIdAndCategory(orgId: String, category: String): List<OrgEnvConfigEntity>
    fun deleteByOrgIdAndCategoryAndConfigKey(orgId: String, category: String, configKey: String)
}
