package com.forge.webide.service.governance

import com.forge.webide.entity.AuditLogEntity
import com.forge.webide.repository.AuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class SecurityPosture(
    val orgId: String,
    val days: Int,
    val totalEvents: Long,
    val anomalyCount: Int,
    val riskLevel: String,
    val actionBreakdown: List<ActionStat>,
    val recentAnomalies: List<AnomalyEvent>
)

data class ActionStat(val action: String, val count: Long)
data class AnomalyEvent(val timestamp: String, val actorId: String, val action: String, val detail: String?)

@Service
class SecurityGovernanceService(
    private val auditLogRepo: AuditLogRepository
) {
    fun getSecurityPosture(orgId: String, days: Int = 30): SecurityPosture {
        val pageable = PageRequest.of(0, 500)
        val logs = auditLogRepo.findByOrgIdOrderByCreatedAtDesc(orgId, pageable).content
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val recentLogs = logs.filter { it.createdAt.isAfter(since) }

        val actionBreakdown = recentLogs
            .groupBy { it.action }
            .map { (action, entries) -> ActionStat(action, entries.size.toLong()) }
            .sortedByDescending { it.count }
            .take(10)

        val anomalies = detectAnomalies(recentLogs)
        val riskLevel = when {
            anomalies.size > 10 -> "high"
            anomalies.size > 3 -> "medium"
            else -> "low"
        }

        return SecurityPosture(
            orgId = orgId,
            days = days,
            totalEvents = recentLogs.size.toLong(),
            anomalyCount = anomalies.size,
            riskLevel = riskLevel,
            actionBreakdown = actionBreakdown,
            recentAnomalies = anomalies.take(10)
        )
    }

    private fun detectAnomalies(logs: List<AuditLogEntity>): List<AnomalyEvent> {
        // 检测非工作时间操作（UTC 22:00-06:00）
        return logs.filter { log ->
            val hour = log.createdAt.atZone(ZoneOffset.UTC).hour
            hour < 6 || hour >= 22
        }.map { log ->
            AnomalyEvent(
                timestamp = log.createdAt.toString(),
                actorId = log.actorId,
                action = log.action,
                detail = log.detail
            )
        }
    }
}
