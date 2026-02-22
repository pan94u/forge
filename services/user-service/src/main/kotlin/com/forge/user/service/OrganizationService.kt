package com.forge.user.service

import com.forge.user.entity.*
import com.forge.user.exception.ResourceNotFoundException
import com.forge.user.exception.UserException
import com.forge.user.repository.OrgMemberRepository
import com.forge.user.repository.OrganizationRepository
import com.forge.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val orgMemberRepository: OrgMemberRepository,
    private val userRepository: UserRepository
) {
    /**
     * 创建组织
     */
    @Transactional
    fun createOrganization(name: String, ownerId: UUID, slug: String? = null, description: String? = null): OrganizationEntity {
        // 检查用户是否存在
        userRepository.findById(ownerId)
            .orElseThrow { UserException("用户不存在") }

        // 生成 slug
        val orgSlug = slug ?: generateSlug(name)

        // 检查 slug 是否存在
        if (organizationRepository.existsBySlug(orgSlug)) {
            throw UserException("组织标识符已存在")
        }

        val organization = OrganizationEntity(
            name = name,
            slug = orgSlug,
            ownerId = ownerId,
            description = description
        )

        val savedOrg = organizationRepository.save(organization)

        // 添加所有者为成员
        val member = OrgMemberEntity(
            orgId = savedOrg.id,
            userId = ownerId,
            role = OrgRole.OWNER,
            joinedAt = Instant.now()
        )
        orgMemberRepository.save(member)

        return savedOrg
    }

    /**
     * 获取组织详情
     */
    fun getOrganization(orgId: UUID): OrganizationEntity {
        return organizationRepository.findById(orgId)
            .orElseThrow { ResourceNotFoundException("组织不存在") }
    }

    /**
     * 获取用户所属组织列表
     */
    fun getOrganizationsByUserId(userId: UUID): List<OrganizationEntity> {
        return organizationRepository.findByMemberUserId(userId)
    }

    /**
     * 添加组织成员
     */
    @Transactional
    fun addMember(orgId: UUID, userId: UUID, role: OrgRole, invitedBy: UUID): OrgMemberEntity {
        // 检查组织是否存在
        getOrganization(orgId)

        // 检查被邀请用户是否存在
        userRepository.findById(userId)
            .orElseThrow { UserException("用户不存在") }

        // 检查是否已是成员
        if (orgMemberRepository.existsByOrgIdAndUserId(orgId, userId)) {
            throw UserException("用户已是组织成员")
        }

        val member = OrgMemberEntity(
            orgId = orgId,
            userId = userId,
            role = role,
            joinedAt = Instant.now(),
            invitedBy = invitedBy
        )

        return orgMemberRepository.save(member)
    }

    /**
     * 移除组织成员
     */
    @Transactional
    fun removeMember(orgId: UUID, userId: UUID) {
        val member = orgMemberRepository.findByOrgIdAndUserId(orgId, userId)
            ?: throw UserException("用户不是组织成员")

        // 不能移除所有者
        if (member.role == OrgRole.OWNER) {
            throw UserException("不能移除组织所有者")
        }

        orgMemberRepository.delete(member)
    }

    /**
     * 更新成员角色
     */
    @Transactional
    fun updateMemberRole(orgId: UUID, userId: UUID, newRole: OrgRole) {
        val member = orgMemberRepository.findByOrgIdAndUserId(orgId, userId)
            ?: throw UserException("用户不是组织成员")

        // 不能修改所有者角色
        if (member.role == OrgRole.OWNER && newRole != OrgRole.OWNER) {
            throw UserException("不能修改所有者的角色")
        }

        member.role = newRole
        orgMemberRepository.save(member)
    }

    /**
     * 获取组织成员列表
     */
    fun getMembers(orgId: UUID): List<OrgMemberEntity> {
        return orgMemberRepository.findByOrgId(orgId)
    }

    /**
     * 检查用户是否是组织成员
     */
    fun isMember(orgId: UUID, userId: UUID): Boolean {
        return orgMemberRepository.existsByOrgIdAndUserId(orgId, userId)
    }

    /**
     * 检查用户是否有组织角色
     */
    fun hasRole(orgId: UUID, userId: UUID, role: OrgRole): Boolean {
        val member = orgMemberRepository.findByOrgIdAndUserId(orgId, userId) ?: return false
        return member.role == role
    }

    /**
     * 生成 slug
     */
    private fun generateSlug(name: String): String {
        val baseSlug = name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

        var slug = baseSlug
        var counter = 1

        while (organizationRepository.existsBySlug(slug)) {
            slug = "$baseSlug-$counter"
            counter++
        }

        return slug
    }
}