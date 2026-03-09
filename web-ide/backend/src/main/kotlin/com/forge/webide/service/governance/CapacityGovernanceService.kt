package com.forge.webide.service.governance

import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.repository.WorkspaceRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

data class CapacitySummary(
    val orgId: String,
    val currentWorkspaces: Int,
    val workspacesLast30Days: Int,
    val workspaceGrowthRate: Double,
    val currentSessions: Long,
    val sessionsGrowthRate: Double,
    val forecastWorkspaces30Days: Int,
    val capacityStatus: String
)

@Service
class CapacityGovernanceService(
    private val workspaceRepo: WorkspaceRepository,
    private val sessionRepo: ChatSessionRepository
) {
    fun getCapacitySummary(orgId: String): CapacitySummary {
        val since30Days = Instant.now().minus(30, ChronoUnit.DAYS)
        val since60Days = Instant.now().minus(60, ChronoUnit.DAYS)

        val allWorkspaces = workspaceRepo.findByOrgId(orgId)
        val currentWorkspaces = allWorkspaces.size

        val workspacesLast30Days = allWorkspaces.count { it.createdAt.isAfter(since30Days) }
        val workspacesPrev30Days = allWorkspaces.count {
            it.createdAt.isAfter(since60Days) && !it.createdAt.isAfter(since30Days)
        }

        val workspaceGrowthRate = if (workspacesPrev30Days > 0) {
            workspacesLast30Days.toDouble() / workspacesPrev30Days.toDouble() * 100.0
        } else {
            val base = maxOf(currentWorkspaces - workspacesLast30Days, 1)
            workspacesLast30Days.toDouble() / base.toDouble() * 100.0
        }

        val currentSessions = sessionRepo.countByOrgSince(orgId, since30Days)
        val prevSessions = run {
            // 计算前一个30天区间的 session 数（60天前到30天前）
            val allTimestamps = sessionRepo.findTimestampsByOrg(orgId, since60Days)
            allTimestamps.count { it.isBefore(since30Days) }.toLong()
        }

        val sessionsGrowthRate = if (prevSessions > 0) {
            currentSessions.toDouble() / prevSessions.toDouble() * 100.0
        } else if (currentSessions > 0) 100.0 else 0.0

        val forecastWorkspaces30Days = currentWorkspaces + workspacesLast30Days

        val capacityStatus = when {
            workspaceGrowthRate >= 50.0 -> "rapid_growth"
            workspaceGrowthRate >= 10.0 -> "growing"
            else -> "normal"
        }

        return CapacitySummary(
            orgId = orgId,
            currentWorkspaces = currentWorkspaces,
            workspacesLast30Days = workspacesLast30Days,
            workspaceGrowthRate = workspaceGrowthRate,
            currentSessions = currentSessions,
            sessionsGrowthRate = sessionsGrowthRate,
            forecastWorkspaces30Days = forecastWorkspaces30Days,
            capacityStatus = capacityStatus
        )
    }
}
