package com.forge.webide.service

import com.forge.webide.entity.OrgMemberEntity
import com.forge.webide.entity.OrgMemberId
import com.forge.webide.entity.OrganizationEntity
import com.forge.webide.entity.WorkspaceEntity
import com.forge.webide.model.*
import com.forge.webide.repository.OrgMemberRepository
import com.forge.webide.repository.OrganizationRepository
import com.forge.webide.repository.WorkspaceRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional

class OrganizationServiceTest {

    private lateinit var orgRepository: OrganizationRepository
    private lateinit var memberRepository: OrgMemberRepository
    private lateinit var workspaceRepository: WorkspaceRepository
    private lateinit var service: OrganizationService

    private fun makeOrg(
        id: String = "org-1",
        name: String = "Test Org",
        slug: String = "test-org",
        status: String = "ACTIVE"
    ) = OrganizationEntity(id = id, name = name, slug = slug, status = status)

    private fun makeMember(
        orgId: String = "org-1",
        userId: String = "user-1",
        role: String = "MEMBER"
    ) = OrgMemberEntity(orgId = orgId, userId = userId, role = role)

    private fun makeWorkspace(id: String = "ws-1", orgId: String? = null) = WorkspaceEntity(
        id = id,
        name = "Test WS",
        description = "",
        owner = "user-1",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    ).also { it.orgId = orgId }

    @BeforeEach
    fun setUp() {
        orgRepository = mockk(relaxed = true)
        memberRepository = mockk(relaxed = true)
        workspaceRepository = mockk(relaxed = true)
        service = OrganizationService(orgRepository, memberRepository, workspaceRepository)
    }

    // --- listOrgs ---

    @Test
    fun `listOrgs returns all organizations`() {
        every { orgRepository.findAll() } returns listOf(makeOrg("o1", "Org 1"), makeOrg("o2", "Org 2", "org-2"))
        val result = service.listOrgs()
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("o1")
        assertThat(result[1].id).isEqualTo("o2")
    }

    // --- getOrg ---

    @Test
    fun `getOrg returns organization when found`() {
        every { orgRepository.findById("org-1") } returns Optional.of(makeOrg())
        val result = service.getOrg("org-1")
        assertThat(result).isNotNull
        assertThat(result!!.slug).isEqualTo("test-org")
    }

    @Test
    fun `getOrg returns null when not found`() {
        every { orgRepository.findById("missing") } returns Optional.empty()
        assertThat(service.getOrg("missing")).isNull()
    }

    // --- createOrg ---

    @Test
    fun `createOrg saves and returns new organization`() {
        every { orgRepository.findBySlug("new-org") } returns null
        every { orgRepository.save(any()) } answers { firstArg() }

        val req = CreateOrgRequest(name = "New Org", slug = "new-org", description = "Desc")
        val result = service.createOrg(req)

        assertThat(result.name).isEqualTo("New Org")
        assertThat(result.slug).isEqualTo("new-org")
        assertThat(result.description).isEqualTo("Desc")
        verify { orgRepository.save(any()) }
    }

    @Test
    fun `createOrg throws when slug already taken`() {
        every { orgRepository.findBySlug("dupe-slug") } returns makeOrg(slug = "dupe-slug")

        assertThrows<IllegalArgumentException> {
            service.createOrg(CreateOrgRequest(name = "Another", slug = "dupe-slug"))
        }
        verify(exactly = 0) { orgRepository.save(any()) }
    }

    // --- updateOrg ---

    @Test
    fun `updateOrg updates name and description`() {
        val entity = makeOrg()
        every { orgRepository.findById("org-1") } returns Optional.of(entity)
        every { orgRepository.save(any()) } answers { firstArg() }

        val result = service.updateOrg("org-1", UpdateOrgRequest(name = "Updated", description = "New Desc"))
        assertThat(result!!.name).isEqualTo("Updated")
        assertThat(result.description).isEqualTo("New Desc")
    }

    @Test
    fun `updateOrg returns null when org not found`() {
        every { orgRepository.findById("bad-id") } returns Optional.empty()
        assertThat(service.updateOrg("bad-id", UpdateOrgRequest(name = "X"))).isNull()
    }

    // --- deleteOrg ---

    @Test
    fun `deleteOrg returns true when org deleted`() {
        every { orgRepository.existsById("org-1") } returns true
        assertThat(service.deleteOrg("org-1")).isTrue()
        verify { orgRepository.deleteById("org-1") }
    }

    @Test
    fun `deleteOrg returns false when org not found`() {
        every { orgRepository.existsById("missing") } returns false
        assertThat(service.deleteOrg("missing")).isFalse()
        verify(exactly = 0) { orgRepository.deleteById(any()) }
    }

    // --- Member management ---

    @Test
    fun `listMembers returns members for org`() {
        every { memberRepository.findByOrgId("org-1") } returns listOf(
            makeMember(userId = "u1"),
            makeMember(userId = "u2", role = "ADMIN")
        )
        val result = service.listMembers("org-1")
        assertThat(result).hasSize(2)
        assertThat(result[1].role).isEqualTo("ADMIN")
    }

    @Test
    fun `addMember saves and returns member`() {
        every { memberRepository.save(any()) } answers { firstArg() }
        val result = service.addMember("org-1", AddMemberRequest(userId = "u99", role = "ADMIN"))
        assertThat(result.userId).isEqualTo("u99")
        assertThat(result.role).isEqualTo("ADMIN")
        verify { memberRepository.save(any()) }
    }

    @Test
    fun `removeMember returns true when member exists`() {
        every { memberRepository.existsById(OrgMemberId("org-1", "u1")) } returns true
        assertThat(service.removeMember("org-1", "u1")).isTrue()
        verify { memberRepository.deleteById(OrgMemberId("org-1", "u1")) }
    }

    @Test
    fun `removeMember returns false when member not found`() {
        every { memberRepository.existsById(OrgMemberId("org-1", "missing")) } returns false
        assertThat(service.removeMember("org-1", "missing")).isFalse()
    }

    // --- Workspace binding ---

    @Test
    fun `bindWorkspace sets orgId on workspace`() {
        val ws = makeWorkspace("ws-1", null)
        every { workspaceRepository.findById("ws-1") } returns Optional.of(ws)
        every { workspaceRepository.save(any()) } answers { firstArg() }

        val result = service.bindWorkspace("org-1", "ws-1")
        assertThat(result).isTrue()
        assertThat(ws.orgId).isEqualTo("org-1")
    }

    @Test
    fun `bindWorkspace returns false when workspace not found`() {
        every { workspaceRepository.findById("missing") } returns Optional.empty()
        assertThat(service.bindWorkspace("org-1", "missing")).isFalse()
    }

    @Test
    fun `unbindWorkspace clears orgId`() {
        val ws = makeWorkspace("ws-1", "org-1")
        every { workspaceRepository.findById("ws-1") } returns Optional.of(ws)
        every { workspaceRepository.save(any()) } answers { firstArg() }

        val result = service.unbindWorkspace("ws-1")
        assertThat(result).isTrue()
        assertThat(ws.orgId).isNull()
    }
}
