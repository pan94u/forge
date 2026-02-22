package com.forge.user.controller

import com.forge.user.dto.ApiResponse
import com.forge.user.entity.OrgMemberEntity
import com.forge.user.entity.OrgRole
import com.forge.user.entity.OrganizationEntity
import com.forge.user.service.OrganizationService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/orgs")
class OrganizationController(
    private val organizationService: OrganizationService
) {

    /**
     * 创建组织
     * POST /api/orgs
     */
    data class CreateOrgRequest(
        @field:NotBlank(message = "组织名称不能为空")
        val name: String,
        val slug: String? = null,
        val description: String? = null
    )

    @PostMapping
    fun createOrganization(
        @Valid @RequestBody request: CreateOrgRequest,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<ApiResponse<OrgResponse>> {
        return try {
            val org = organizationService.createOrganization(
                name = request.name,
                ownerId = UUID.fromString(userId),
                slug = request.slug,
                description = request.description
            )
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(OrgResponse.from(org), "组织创建成功"))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "创建失败"))
        }
    }

    /**
     * 获取组织详情
     * GET /api/orgs/{id}
     */
    @GetMapping("/{id}")
    fun getOrganization(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<ApiResponse<OrgResponse>> {
        return try {
            val org = organizationService.getOrganization(UUID.fromString(id))
            ResponseEntity.ok(ApiResponse.success(OrgResponse.from(org)))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("组织不存在"))
        }
    }

    /**
     * 获取用户所属组织列表
     * GET /api/orgs
     */
    @GetMapping
    fun getMyOrganizations(
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<ApiResponse<List<OrgResponse>>> {
        return try {
            val orgs = organizationService.getOrganizationsByUserId(UUID.fromString(userId))
            ResponseEntity.ok(ApiResponse.success(orgs.map { OrgResponse.from(it) }))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("获取组织列表失败"))
        }
    }

    /**
     * 添加组织成员
     * POST /api/orgs/{id}/members
     */
    data class AddMemberRequest(
        val userId: String,
        val role: OrgRole = OrgRole.MEMBER
    )

    @PostMapping("/{id}/members")
    fun addMember(
        @PathVariable id: String,
        @Valid @RequestBody request: AddMemberRequest,
        @RequestHeader("X-User-Id") currentUserId: String
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            organizationService.addMember(
                orgId = UUID.fromString(id),
                userId = UUID.fromString(request.userId),
                role = request.role,
                invitedBy = UUID.fromString(currentUserId)
            )
            ResponseEntity.ok(ApiResponse.success(message = "成员添加成功"))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "添加成员失败"))
        }
    }

    /**
     * 移除组织成员
     * DELETE /api/orgs/{id}/members/{userId}
     */
    @DeleteMapping("/{id}/members/{userId}")
    fun removeMember(
        @PathVariable id: String,
        @PathVariable userId: String,
        @RequestHeader("X-User-Id") currentUserId: String
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            organizationService.removeMember(
                orgId = UUID.fromString(id),
                userId = UUID.fromString(userId)
            )
            ResponseEntity.ok(ApiResponse.success(message = "成员移除成功"))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "移除成员失败"))
        }
    }

    /**
     * 更新成员角色
     * PUT /api/orgs/{id}/members/{userId}/role
     */
    data class UpdateRoleRequest(
        val role: OrgRole
    )

    @PutMapping("/{id}/members/{userId}/role")
    fun updateMemberRole(
        @PathVariable id: String,
        @PathVariable userId: String,
        @Valid @RequestBody request: UpdateRoleRequest,
        @RequestHeader("X-User-Id") currentUserId: String
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            organizationService.updateMemberRole(
                orgId = UUID.fromString(id),
                userId = UUID.fromString(userId),
                newRole = request.role
            )
            ResponseEntity.ok(ApiResponse.success(message = "角色更新成功"))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "更新角色失败"))
        }
    }

    /**
     * 获取组织成员列表
     * GET /api/orgs/{id}/members
     */
    @GetMapping("/{id}/members")
    fun getMembers(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<ApiResponse<List<MemberResponse>>> {
        return try {
            val members = organizationService.getMembers(UUID.fromString(id))
            ResponseEntity.ok(ApiResponse.success(members.map { MemberResponse.from(it) }))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("获取成员列表失败"))
        }
    }
}

data class OrgResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val avatar: String?,
    val description: String?,
    val plan: String,
    val createdAt: java.time.Instant
) {
    companion object {
        fun from(org: OrganizationEntity): OrgResponse = OrgResponse(
            id = org.id,
            name = org.name,
            slug = org.slug,
            avatar = org.avatar,
            description = org.description,
            plan = org.plan.name,
            createdAt = org.createdAt
        )
    }
}

data class MemberResponse(
    val userId: UUID,
    val orgId: UUID,
    val role: String,
    val joinedAt: java.time.Instant,
    val invitedBy: UUID?
) {
    companion object {
        fun from(member: OrgMemberEntity): MemberResponse = MemberResponse(
            userId = member.userId,
            orgId = member.orgId,
            role = member.role.name,
            joinedAt = member.joinedAt,
            invitedBy = member.invitedBy
        )
    }
}