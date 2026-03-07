package com.forge.webide.controller

import com.forge.webide.repository.GovernanceSnapshotRepository
import com.forge.webide.service.RbacHelper
import com.forge.webide.service.governance.*
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(GovernanceController::class)
@AutoConfigureMockMvc(addFilters = false)
class GovernanceControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var budgetService: BudgetGovernanceService
    @Autowired private lateinit var teamService: TeamGovernanceService
    @Autowired private lateinit var securityService: SecurityGovernanceService
    @Autowired private lateinit var knowledgeService: KnowledgeGovernanceService
    @Autowired private lateinit var processMiningService: ProcessMiningService
    @Autowired private lateinit var snapshotRepo: GovernanceSnapshotRepository

    @TestConfiguration
    class Config {
        @Bean fun budgetService() = mockk<BudgetGovernanceService>(relaxed = true)
        @Bean fun teamService() = mockk<TeamGovernanceService>(relaxed = true)
        @Bean fun securityService() = mockk<SecurityGovernanceService>(relaxed = true)
        @Bean fun knowledgeService() = mockk<KnowledgeGovernanceService>(relaxed = true)
        @Bean fun processMiningService() = mockk<ProcessMiningService>(relaxed = true)
        @Bean fun snapshotRepo() = mockk<GovernanceSnapshotRepository>(relaxed = true)
        @Bean fun rbacHelper() = mockk<RbacHelper> {
            every { requireOrgAdmin(any(), any()) } just runs
            every { isOrgAdmin(any(), any()) } returns true
            every { isSystemAdmin(any()) } returns true
        }
    }

    @Test
    fun `GET budget returns 200`() {
        every { budgetService.getOrgCostSummary(any(), any()) } returns BudgetSummary(
            "org-1", 30, 100L, 250L, 2.5, emptyList()
        )
        mockMvc.perform(get("/api/governance/org-1/budget"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orgId").value("org-1"))
            .andExpect(jsonPath("$.totalSessions").value(100))
    }

    @Test
    fun `GET security returns 200`() {
        every { securityService.getSecurityPosture(any(), any()) } returns SecurityPosture(
            "org-1", 30, 50L, 2, "low", emptyList(), emptyList()
        )
        mockMvc.perform(get("/api/governance/org-1/security"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.riskLevel").value("low"))
    }

    @Test
    fun `GET process flows returns 200`() {
        mockMvc.perform(get("/api/governance/org-1/process"))
            .andExpect(status().isOk)
    }

    @Test
    fun `POST snapshot returns 200`() {
        every { budgetService.getOrgCostSummary(any(), any()) } returns BudgetSummary("org-1", 30, 0, 0, 0.0, emptyList())
        every { teamService.getTeamActivity(any(), any()) } returns TeamActivity("org-1", 30, 0, emptyList())
        every { securityService.getSecurityPosture(any(), any()) } returns SecurityPosture("org-1", 30, 0, 0, "low", emptyList(), emptyList())
        every { knowledgeService.getKnowledgeHealth(any()) } returns KnowledgeHealth("org-1", 0, 0, 0, 0, 0.0, 0.0, emptyList())
        every { processMiningService.getProcessFlows(any(), any()) } returns emptyList()
        every { snapshotRepo.save(any<com.forge.webide.entity.GovernanceSnapshotEntity>()) } returnsArgument 0
        mockMvc.perform(post("/api/governance/org-1/snapshot"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("created"))
    }
}
