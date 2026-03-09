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
    @Autowired private lateinit var architectureService: ArchitectureGovernanceService
    @Autowired private lateinit var processSummaryService: ProcessSummaryService
    @Autowired private lateinit var complianceService: ComplianceGovernanceService
    @Autowired private lateinit var capacityService: CapacityGovernanceService
    @Autowired private lateinit var vendorService: VendorGovernanceService
    @Autowired private lateinit var snapshotRepo: GovernanceSnapshotRepository

    @TestConfiguration
    class Config {
        @Bean fun budgetService() = mockk<BudgetGovernanceService>(relaxed = true)
        @Bean fun teamService() = mockk<TeamGovernanceService>(relaxed = true)
        @Bean fun securityService() = mockk<SecurityGovernanceService>(relaxed = true)
        @Bean fun knowledgeService() = mockk<KnowledgeGovernanceService>(relaxed = true)
        @Bean fun processMiningService() = mockk<ProcessMiningService>(relaxed = true)
        @Bean fun architectureService() = mockk<ArchitectureGovernanceService>(relaxed = true)
        @Bean fun processSummaryService() = mockk<ProcessSummaryService>(relaxed = true)
        @Bean fun complianceService() = mockk<ComplianceGovernanceService>(relaxed = true)
        @Bean fun capacityService() = mockk<CapacityGovernanceService>(relaxed = true)
        @Bean fun vendorService() = mockk<VendorGovernanceService>(relaxed = true)
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
        every { architectureService.getArchitectureSummary(any()) } returns ArchitectureSummary("org-1", 0, 0, 0.0, 0.0, emptyList())
        every { processSummaryService.getProcessSummary(any()) } returns ProcessSummary("org-1", 0, 0, 0, emptyMap(), 0.0, emptyList())
        every { complianceService.getComplianceSummary(any(), any()) } returns ComplianceSummary("org-1", 30, 0, 0, 100.0, 0, "low", emptyList())
        every { capacityService.getCapacitySummary(any()) } returns CapacitySummary("org-1", 0, 0, 0.0, 0, 0.0, 0, "normal")
        every { vendorService.getVendorSummary(any(), any()) } returns VendorSummary("org-1", 30, 0, "unknown", 0.0, emptyList())
        every { snapshotRepo.save(any<com.forge.webide.entity.GovernanceSnapshotEntity>()) } returnsArgument 0
        mockMvc.perform(post("/api/governance/org-1/snapshot"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("created"))
    }

    @Test
    fun `GET architecture returns 200`() {
        every { architectureService.getArchitectureSummary(any()) } returns ArchitectureSummary(
            "org-1", 5, 3, 60.0, 60.0, emptyList()
        )
        mockMvc.perform(get("/api/governance/org-1/architecture"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orgId").value("org-1"))
            .andExpect(jsonPath("$.totalWorkspaces").value(5))
    }

    @Test
    fun `GET process-summary returns 200`() {
        every { processSummaryService.getProcessSummary(any()) } returns ProcessSummary(
            "org-1", 10, 50, 30, mapOf("flowchart" to 7, "sequence" to 3), 5.0, emptyList()
        )
        mockMvc.perform(get("/api/governance/org-1/process-summary"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orgId").value("org-1"))
            .andExpect(jsonPath("$.totalFlows").value(10))
    }

    @Test
    fun `GET compliance returns 200`() {
        every { complianceService.getComplianceSummary(any(), any()) } returns ComplianceSummary(
            "org-1", 30, 100, 10, 90.0, 2, "medium", emptyList()
        )
        mockMvc.perform(get("/api/governance/org-1/compliance"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orgId").value("org-1"))
            .andExpect(jsonPath("$.riskLevel").value("medium"))
    }

    @Test
    fun `GET capacity returns 200`() {
        every { capacityService.getCapacitySummary(any()) } returns CapacitySummary(
            "org-1", 20, 5, 33.3, 150, 20.0, 25, "growing"
        )
        mockMvc.perform(get("/api/governance/org-1/capacity"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orgId").value("org-1"))
            .andExpect(jsonPath("$.capacityStatus").value("growing"))
    }

    @Test
    fun `GET vendor returns 200`() {
        every { vendorService.getVendorSummary(any(), any()) } returns VendorSummary(
            "org-1", 30, 1, "anthropic", 0.0, listOf(ProviderStat("anthropic", 100, 100.0))
        )
        mockMvc.perform(get("/api/governance/org-1/vendor"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orgId").value("org-1"))
            .andExpect(jsonPath("$.primaryProvider").value("anthropic"))
    }
}
