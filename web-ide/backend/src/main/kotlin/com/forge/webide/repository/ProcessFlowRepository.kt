package com.forge.webide.repository

import com.forge.webide.entity.ProcessFlowEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProcessFlowRepository : JpaRepository<ProcessFlowEntity, String> {
    fun findByOrgIdOrderByExtractedAtDesc(orgId: String): List<ProcessFlowEntity>
    fun findByWorkspaceIdOrderByExtractedAtDesc(workspaceId: String): List<ProcessFlowEntity>
    fun findByOrgIdAndWorkspaceIdOrderByExtractedAtDesc(orgId: String, workspaceId: String): List<ProcessFlowEntity>
}
