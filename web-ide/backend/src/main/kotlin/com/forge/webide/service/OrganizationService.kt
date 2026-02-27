package com.forge.webide.service

import com.forge.webide.entity.OrgMemberEntity
import com.forge.webide.entity.OrgMemberId
import com.forge.webide.entity.OrganizationEntity
import com.forge.webide.model.*
import com.forge.webide.repository.OrgMemberRepository
import com.forge.webide.repository.OrganizationRepository
import com.forge.webide.repository.WorkspaceRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OrganizationService(
    private val orgRepository: OrganizationRepository,
    private val memberRepository: OrgMemberRepository,
    private val workspaceRepository: WorkspaceRepository
) {

    private val logger = LoggerFactory.getLogger(OrganizationService::class.java)

    // =========================================================================
    // Org CRUD
    // =========================================================================

    fun listOrgs(): List<Organization> {
        return orgRepository.findAll().map { it.toModel() }
    }

    fun getOrg(id: String): Organization? {
        return orgRepository.findById(id).orElse(null)?.toModel()
    }

    @Transactional
    fun createOrg(req: CreateOrgRequest): Organization {
        if (orgRepository.findBySlug(req.slug) != null) {
            throw IllegalArgumentException("Slug '${req.slug}' already taken")
        }
        val entity = OrganizationEntity(
            name = req.name,
            slug = req.slug,
            description = req.description
        )
        orgRepository.save(entity)
        logger.info("Created organization: {} ({})", entity.name, entity.id)
        return entity.toModel()
    }

    @Transactional
    fun updateOrg(id: String, req: UpdateOrgRequest): Organization? {
        val entity = orgRepository.findById(id).orElse(null) ?: return null
        req.name?.let { entity.name = it }
        req.description?.let { entity.description = it }
        req.status?.let { entity.status = it }
        orgRepository.save(entity)
        logger.info("Updated organization: {}", id)
        return entity.toModel()
    }

    @Transactional
    fun deleteOrg(id: String): Boolean {
        if (!orgRepository.existsById(id)) return false
        orgRepository.deleteById(id)
        logger.info("Deleted organization: {}", id)
        return true
    }

    // =========================================================================
    // Member Management
    // =========================================================================

    fun listMembers(orgId: String): List<OrgMember> {
        return memberRepository.findByOrgId(orgId).map { it.toModel() }
    }

    @Transactional
    fun addMember(orgId: String, req: AddMemberRequest): OrgMember {
        val entity = OrgMemberEntity(
            orgId = orgId,
            userId = req.userId,
            role = req.role
        )
        memberRepository.save(entity)
        logger.info("Added member {} to org {}", req.userId, orgId)
        return entity.toModel()
    }

    @Transactional
    fun removeMember(orgId: String, userId: String): Boolean {
        val id = OrgMemberId(orgId = orgId, userId = userId)
        if (!memberRepository.existsById(id)) return false
        memberRepository.deleteById(id)
        logger.info("Removed member {} from org {}", userId, orgId)
        return true
    }

    // =========================================================================
    // Workspace Binding
    // =========================================================================

    fun listWorkspaces(orgId: String): List<Workspace> {
        return workspaceRepository.findByOrgId(orgId).map { ws ->
            Workspace(
                id = ws.id,
                name = ws.name,
                description = ws.description,
                status = ws.status,
                owner = ws.owner,
                repository = ws.repository,
                branch = ws.branch,
                errorMessage = ws.errorMessage,
                createdAt = ws.createdAt,
                updatedAt = ws.updatedAt
            )
        }
    }

    @Transactional
    fun bindWorkspace(orgId: String, workspaceId: String): Boolean {
        val ws = workspaceRepository.findById(workspaceId).orElse(null) ?: return false
        ws.orgId = orgId
        workspaceRepository.save(ws)
        logger.info("Bound workspace {} to org {}", workspaceId, orgId)
        return true
    }

    @Transactional
    fun unbindWorkspace(workspaceId: String): Boolean {
        val ws = workspaceRepository.findById(workspaceId).orElse(null) ?: return false
        ws.orgId = null
        workspaceRepository.save(ws)
        logger.info("Unbound workspace {} from org", workspaceId)
        return true
    }

    // =========================================================================
    // Mapping helpers
    // =========================================================================

    private fun OrganizationEntity.toModel() = Organization(
        id = id,
        name = name,
        slug = slug,
        description = description,
        status = status,
        createdAt = createdAt
    )

    private fun OrgMemberEntity.toModel() = OrgMember(
        orgId = orgId,
        userId = userId,
        role = role,
        joinedAt = joinedAt
    )
}
