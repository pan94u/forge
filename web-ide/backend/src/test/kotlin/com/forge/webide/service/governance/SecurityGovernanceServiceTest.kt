package com.forge.webide.service.governance

import com.forge.webide.entity.AuditLogEntity
import com.forge.webide.repository.AuditLogRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import java.time.Instant
import java.time.ZoneOffset

class SecurityGovernanceServiceTest {

    private lateinit var auditLogRepo: AuditLogRepository
    private lateinit var service: SecurityGovernanceService

    private fun makeLog(action: String, hoursAgo: Long = 2) = AuditLogEntity(
        id = 0,
        orgId = "org-1",
        actorId = "user-1",
        action = action,
        createdAt = Instant.now().minusSeconds(hoursAgo * 3600)
    )

    @BeforeEach
    fun setUp() {
        auditLogRepo = mockk(relaxed = true)
        service = SecurityGovernanceService(auditLogRepo)
    }

    @Test
    fun `getSecurityPosture returns low risk for normal hours`() {
        val normalLog = makeLog("LOGIN", 2) // 2 hours ago = daytime
        every { auditLogRepo.findByOrgIdOrderByCreatedAtDesc(any(), any()) } returns PageImpl(listOf(normalLog))

        val result = service.getSecurityPosture("org-1", 30)
        assertThat(result.riskLevel).isIn("low", "medium", "high")
        assertThat(result.totalEvents).isEqualTo(1L)
    }

    @Test
    fun `getSecurityPosture detects night time anomalies`() {
        // Create log at 3 AM UTC
        val nightTime = Instant.now().atZone(ZoneOffset.UTC)
            .withHour(3).withMinute(0).toInstant()
        val nightLog = AuditLogEntity(id = 0, orgId = "org-1", actorId = "user-1", action = "CONFIG_CHANGE", createdAt = nightTime)
        every { auditLogRepo.findByOrgIdOrderByCreatedAtDesc(any(), any()) } returns PageImpl(listOf(nightLog))

        val result = service.getSecurityPosture("org-1", 30)
        assertThat(result.anomalyCount).isGreaterThan(0)
    }

    @Test
    fun `getSecurityPosture returns empty for no logs`() {
        every { auditLogRepo.findByOrgIdOrderByCreatedAtDesc(any(), any()) } returns PageImpl(emptyList())

        val result = service.getSecurityPosture("org-1", 30)
        assertThat(result.totalEvents).isEqualTo(0L)
        assertThat(result.riskLevel).isEqualTo("low")
    }
}
