package com.forge.webide.service.governance

import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.repository.ExecutionRecordRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class BudgetSummary(
    val orgId: String,
    val days: Int,
    val totalSessions: Long,
    val totalExecutions: Long,
    val roiScore: Double,
    val dailyActivity: List<DailyActivity>
)

data class DailyActivity(val date: String, val sessions: Long, val executions: Long)

@Service
class BudgetGovernanceService(
    private val sessionRepo: ChatSessionRepository,
    private val execRepo: ExecutionRecordRepository
) {
    fun getOrgCostSummary(orgId: String, days: Int = 30): BudgetSummary {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)

        val totalSessions = sessionRepo.countByOrgSince(orgId, since)
        val totalExecutions = execRepo.countByOrgSince(orgId, since)

        val roiScore = if (totalSessions > 0) {
            (totalExecutions.toDouble() / totalSessions.toDouble()).coerceAtMost(100.0)
        } else 0.0

        val sessionTimestamps = sessionRepo.findTimestampsByOrg(orgId, since)
        val execTimestamps = execRepo.findTimestampsByOrg(orgId, since)

        val sessionsByDay = sessionTimestamps
            .groupBy { LocalDate.ofInstant(it, ZoneOffset.UTC).toString() }
            .mapValues { it.value.size.toLong() }
        val execsByDay = execTimestamps
            .groupBy { LocalDate.ofInstant(it, ZoneOffset.UTC).toString() }
            .mapValues { it.value.size.toLong() }

        val allDates = (sessionsByDay.keys + execsByDay.keys).toSortedSet()
        val dailyActivity = allDates.map { date ->
            DailyActivity(date, sessionsByDay[date] ?: 0L, execsByDay[date] ?: 0L)
        }

        return BudgetSummary(orgId, days, totalSessions, totalExecutions, roiScore, dailyActivity)
    }
}
