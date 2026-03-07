package com.forge.webide.service.governance

import com.forge.webide.repository.KnowledgeTagRepository
import com.forge.webide.repository.WorkspaceRepository
import org.springframework.stereotype.Service

data class ArchitectureSummary(
    val orgId: String,
    val totalWorkspaces: Int,
    val workspacesWithKnowledge: Int,
    val knowledgeCoverageRate: Double,
    val healthScore: Double,
    val recentWorkspaces: List<WorkspaceItem>
)

data class WorkspaceItem(val id: String, val name: String, val createdAt: String)

@Service
class ArchitectureGovernanceService(
    private val workspaceRepo: WorkspaceRepository,
    private val knowledgeTagRepo: KnowledgeTagRepository
) {
    fun getArchitectureSummary(orgId: String): ArchitectureSummary {
        val workspaces = workspaceRepo.findByOrgId(orgId)
        val totalWorkspaces = workspaces.size

        val workspacesWithKnowledge = workspaces.count { ws ->
            knowledgeTagRepo.countByWorkspaceId(ws.id) > 0
        }

        val knowledgeCoverageRate = if (totalWorkspaces > 0) {
            workspacesWithKnowledge.toDouble() / totalWorkspaces.toDouble() * 100.0
        } else 0.0

        // healthScore 基于知识覆盖率，满分100
        val healthScore = knowledgeCoverageRate.coerceIn(0.0, 100.0)

        val recentWorkspaces = workspaces
            .sortedByDescending { it.createdAt }
            .take(5)
            .map { WorkspaceItem(it.id, it.name, it.createdAt.toString()) }

        return ArchitectureSummary(
            orgId = orgId,
            totalWorkspaces = totalWorkspaces,
            workspacesWithKnowledge = workspacesWithKnowledge,
            knowledgeCoverageRate = knowledgeCoverageRate,
            healthScore = healthScore,
            recentWorkspaces = recentWorkspaces
        )
    }
}
