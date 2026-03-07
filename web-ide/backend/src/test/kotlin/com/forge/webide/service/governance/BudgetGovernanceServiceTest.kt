package com.forge.webide.service.governance

import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.repository.ExecutionRecordRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class BudgetGovernanceServiceTest {

    private lateinit var sessionRepo: ChatSessionRepository
    private lateinit var execRepo: ExecutionRecordRepository
    private lateinit var service: BudgetGovernanceService

    @BeforeEach
    fun setUp() {
        sessionRepo = mockk(relaxed = true)
        execRepo = mockk(relaxed = true)
        every { sessionRepo.countByOrgSince(any(), any()) } returns 10L
        every { execRepo.countByOrgSince(any(), any()) } returns 25L
        every { sessionRepo.findTimestampsByOrg(any(), any()) } returns listOf(Instant.now())
        every { execRepo.findTimestampsByOrg(any(), any()) } returns listOf(Instant.now())
        service = BudgetGovernanceService(sessionRepo, execRepo)
    }

    @Test
    fun `getOrgCostSummary returns correct totals`() {
        val result = service.getOrgCostSummary("org-1", 30)
        assertThat(result.orgId).isEqualTo("org-1")
        assertThat(result.totalSessions).isEqualTo(10L)
        assertThat(result.totalExecutions).isEqualTo(25L)
        assertThat(result.roiScore).isGreaterThan(0.0)
    }

    @Test
    fun `getOrgCostSummary with zero sessions returns zero roi`() {
        every { sessionRepo.countByOrgSince(any(), any()) } returns 0L
        every { execRepo.countByOrgSince(any(), any()) } returns 0L
        every { sessionRepo.findTimestampsByOrg(any(), any()) } returns emptyList()
        every { execRepo.findTimestampsByOrg(any(), any()) } returns emptyList()
        val result = service.getOrgCostSummary("org-1", 30)
        assertThat(result.roiScore).isEqualTo(0.0)
        assertThat(result.dailyActivity).isEmpty()
    }
}
