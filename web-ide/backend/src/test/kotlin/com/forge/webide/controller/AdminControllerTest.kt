package com.forge.webide.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.webide.model.*
import com.forge.webide.service.OrgConfigService
import com.forge.webide.service.OrganizationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@WebMvcTest(AdminController::class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var orgService: OrganizationService

    @Autowired
    private lateinit var configService: OrgConfigService

    @TestConfiguration
    class Config {
        @Bean
        fun orgService(): OrganizationService = mockk(relaxed = true)

        @Bean
        fun configService(): OrgConfigService = mockk(relaxed = true)
    }

    private fun sampleOrg(id: String = "org-1") = Organization(
        id = id, name = "Test Org", slug = "test-org", description = null,
        status = "ACTIVE", createdAt = Instant.now()
    )

    private fun sampleMember() = OrgMember(
        orgId = "org-1", userId = "user-1", role = "MEMBER", joinedAt = Instant.now()
    )

    // =========================================================================
    // Org CRUD
    // =========================================================================

    @Test
    fun `GET orgs returns 200 with list`() {
        every { orgService.listOrgs() } returns listOf(sampleOrg("o1"), sampleOrg("o2"))

        mockMvc.perform(get("/api/admin/orgs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("o1"))
    }

    @Test
    fun `POST orgs returns 201 when created`() {
        val req = CreateOrgRequest(name = "New Org", slug = "new-org")
        every { orgService.createOrg(any()) } returns sampleOrg()

        mockMvc.perform(
            post("/api/admin/orgs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("org-1"))
    }

    @Test
    fun `POST orgs returns 400 when slug taken`() {
        every { orgService.createOrg(any()) } throws IllegalArgumentException("Slug already taken")

        mockMvc.perform(
            post("/api/admin/orgs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateOrgRequest(name = "X", slug = "dupe")))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET orgs-id returns 200 when found`() {
        every { orgService.getOrg("org-1") } returns sampleOrg()

        mockMvc.perform(get("/api/admin/orgs/org-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slug").value("test-org"))
    }

    @Test
    fun `GET orgs-id returns 404 when not found`() {
        every { orgService.getOrg("missing") } returns null

        mockMvc.perform(get("/api/admin/orgs/missing"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT orgs-id returns 200 when updated`() {
        every { orgService.updateOrg(eq("org-1"), any()) } returns sampleOrg()

        mockMvc.perform(
            put("/api/admin/orgs/org-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateOrgRequest(name = "Updated")))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `DELETE orgs-id returns 204 when deleted`() {
        every { orgService.deleteOrg("org-1") } returns true

        mockMvc.perform(delete("/api/admin/orgs/org-1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE orgs-id returns 404 when not found`() {
        every { orgService.deleteOrg("missing") } returns false

        mockMvc.perform(delete("/api/admin/orgs/missing"))
            .andExpect(status().isNotFound)
    }

    // =========================================================================
    // Members
    // =========================================================================

    @Test
    fun `GET members returns 200 with list`() {
        every { orgService.listMembers("org-1") } returns listOf(sampleMember())

        mockMvc.perform(get("/api/admin/orgs/org-1/members"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].userId").value("user-1"))
    }

    @Test
    fun `POST members returns 201`() {
        every { orgService.addMember(eq("org-1"), any()) } returns sampleMember()

        mockMvc.perform(
            post("/api/admin/orgs/org-1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(AddMemberRequest(userId = "user-1")))
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `DELETE member returns 204`() {
        every { orgService.removeMember("org-1", "user-1") } returns true

        mockMvc.perform(delete("/api/admin/orgs/org-1/members/user-1"))
            .andExpect(status().isNoContent)
    }

    // =========================================================================
    // Workspace binding
    // =========================================================================

    @Test
    fun `POST workspace bind returns 200`() {
        every { orgService.bindWorkspace("org-1", "ws-1") } returns true

        mockMvc.perform(post("/api/admin/orgs/org-1/workspaces/ws-1/bind"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `DELETE workspace bind returns 204`() {
        every { orgService.unbindWorkspace("ws-1") } returns true

        mockMvc.perform(delete("/api/admin/orgs/org-1/workspaces/ws-1/bind"))
            .andExpect(status().isNoContent)
    }

    // =========================================================================
    // Model Configs
    // =========================================================================

    @Test
    fun `GET model-configs returns 200`() {
        every { configService.listModelConfigs("org-1") } returns listOf(
            OrgModelConfig("mc-1", "org-1", "anthropic", true, "****1234", null, null, Instant.now())
        )

        mockMvc.perform(get("/api/admin/orgs/org-1/model-configs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].provider").value("anthropic"))
            .andExpect(jsonPath("$[0].apiKeyMasked").value("****1234"))
    }

    @Test
    fun `PUT model-config returns 200`() {
        every { configService.upsertModelConfig(eq("org-1"), eq("minimax"), any()) } returns
            OrgModelConfig("mc-2", "org-1", "minimax", true, "****5678", null, null, Instant.now())

        mockMvc.perform(
            put("/api/admin/orgs/org-1/model-configs/minimax")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpsertModelConfigRequest(enabled = true, apiKey = "sk-12345678")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.provider").value("minimax"))
    }

    // =========================================================================
    // DB Connections
    // =========================================================================

    @Test
    fun `POST db-connections returns 201`() {
        every { configService.createDbConnection(eq("org-1"), any()) } returns
            OrgDbConnection("db-1", "org-1", "Prod DB", "jdbc:postgresql://localhost/db", "admin", "FULL_READ", Instant.now())

        mockMvc.perform(
            post("/api/admin/orgs/org-1/db-connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    CreateDbConnectionRequest(name = "Prod DB", jdbcUrl = "jdbc:postgresql://localhost/db", username = "admin")
                ))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Prod DB"))
    }

    @Test
    fun `POST db-connections test returns success flag`() {
        every { configService.testDbConnection("org-1", "db-1") } returns mapOf("success" to true, "message" to "Connection successful")

        mockMvc.perform(post("/api/admin/orgs/org-1/db-connections/db-1/test"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    // =========================================================================
    // Env Configs
    // =========================================================================

    @Test
    fun `GET env-configs returns 200 with list`() {
        every { configService.listEnvConfigs("org-1", null) } returns listOf(
            OrgEnvConfig("ec-1", "org-1", "build", "JDK_VERSION", "21", false, null)
        )

        mockMvc.perform(get("/api/admin/orgs/org-1/env-configs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].configKey").value("JDK_VERSION"))
    }

    @Test
    fun `PUT env-config returns 200`() {
        every { configService.upsertEnvConfig(eq("org-1"), eq("build"), any()) } returns
            OrgEnvConfig("ec-1", "org-1", "build", "JDK_VERSION", "21", false, null)

        mockMvc.perform(
            put("/api/admin/orgs/org-1/env-configs/build/JDK_VERSION")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    UpsertEnvConfigRequest(configKey = "JDK_VERSION", configValue = "21")
                ))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `DELETE env-config returns 204`() {
        every { configService.deleteEnvConfig("org-1", "build", "JDK_VERSION") } returns true

        mockMvc.perform(delete("/api/admin/orgs/org-1/env-configs/build/JDK_VERSION"))
            .andExpect(status().isNoContent)
    }
}
