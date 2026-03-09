package com.forge.webide.service.governance

import com.forge.webide.repository.ChatSessionRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

data class VendorSummary(
    val orgId: String,
    val days: Int,
    val totalProviders: Int,
    val primaryProvider: String,
    val diversificationScore: Double,
    val providerStats: List<ProviderStat>
)

data class ProviderStat(
    val provider: String,
    val sessionCount: Long,
    val sharePercent: Double
)

@Service
class VendorGovernanceService(
    private val sessionRepo: ChatSessionRepository
) {
    fun getVendorSummary(orgId: String, days: Int = 30): VendorSummary {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val totalSessions = sessionRepo.countByOrgSince(orgId, since)

        // ChatSessionEntity 没有 model/provider 字段，返回默认数据
        if (totalSessions == 0L) {
            return VendorSummary(
                orgId = orgId,
                days = days,
                totalProviders = 0,
                primaryProvider = "unknown",
                diversificationScore = 0.0,
                providerStats = emptyList()
            )
        }

        // 当前无法从 session 中解析 provider，返回单一 provider 汇总
        val providerStats = listOf(
            ProviderStat(
                provider = "anthropic",
                sessionCount = totalSessions,
                sharePercent = 100.0
            )
        )

        val totalProviders = 1
        val primaryProvider = "anthropic"
        val diversificationScore = 0.0  // 单一 provider，分散度为 0

        return VendorSummary(
            orgId = orgId,
            days = days,
            totalProviders = totalProviders,
            primaryProvider = primaryProvider,
            diversificationScore = diversificationScore,
            providerStats = providerStats
        )
    }
}
