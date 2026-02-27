package com.forge.webide.repository

import com.forge.webide.entity.WorkspaceEntity
import com.forge.webide.model.WorkspaceStatus
import org.springframework.data.jpa.repository.JpaRepository

interface WorkspaceRepository : JpaRepository<WorkspaceEntity, String> {

    fun findByOwnerOrOwnerIn(owner: String, fallbacks: List<String>): List<WorkspaceEntity>

    fun findByStatusNot(status: WorkspaceStatus): List<WorkspaceEntity>

    fun findByOrgId(orgId: String): List<WorkspaceEntity>
}
