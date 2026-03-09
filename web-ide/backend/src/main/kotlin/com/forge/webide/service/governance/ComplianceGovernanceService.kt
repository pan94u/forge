package com.forge.webide.service.governance

import com.forge.webide.repository.AuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

data class ComplianceSummary(
    val orgId: String,
    val days: Int,
    val totalAuditEvents: Long,
    val hitlEvents: Long,
    val hitlApprovalRate: Double,
    val anomalyCount: Long,
    val riskLevel: String,
    val recentEvents: List<ComplianceEvent>
)

data class ComplianceEvent(
    val timestamp: String,
    val action: String,
    val actor: String,
    val domain: String
)

@Service
class ComplianceGovernanceService(
    private val auditLogRepo: AuditLogRepository
) {
    fun getComplianceSummary(orgId: String, days: Int = 30): ComplianceSummary {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val pageable = PageRequest.of(0, 1000)
        val logs = auditLogRepo.findByOrgIdOrderByCreatedAtDesc(orgId, pageable).content
        val recentLogs = logs.filter { it.createdAt.isAfter(since) }

        val totalAuditEvents = recentLogs.size.toLong()

        val hitlKeywords = listOf("hitl", "approve", "reject")
        val hitlLogs = recentLogs.filter { log ->
            hitlKeywords.any { kw -> log.action.contains(kw, ignoreCase = true) }
        }
        val hitlEvents = hitlLogs.size.toLong()

        val approvedHitl = hitlLogs.count { log ->
            log.action.contains("approve", ignoreCase = true)
        }
        val hitlApprovalRate = if (hitlEvents > 0) {
            approvedHitl.toDouble() / hitlEvents.toDouble() * 100.0
        } else 100.0

        // 异常检测：非工作时间（UTC 22:00-06:00）操作
        val anomalyCount = recentLogs.count { log ->
            val hour = log.createdAt.atOffset(java.time.ZoneOffset.UTC).hour
            hour < 6 || hour >= 22
        }.toLong()

        val riskLevel = when {
            anomalyCount >= 5 -> "high"
            anomalyCount > 0 -> "medium"
            else -> "low"
        }

        val recentEvents = recentLogs.take(10).map { log ->
            ComplianceEvent(
                timestamp = log.createdAt.toString(),
                action = log.action,
                actor = log.actorId,
                domain = log.targetType ?: "general"
            )
        }

        return ComplianceSummary(
            orgId = orgId,
            days = days,
            totalAuditEvents = totalAuditEvents,
            hitlEvents = hitlEvents,
            hitlApprovalRate = hitlApprovalRate,
            anomalyCount = anomalyCount,
            riskLevel = riskLevel,
            recentEvents = recentEvents
        )
    }
}
