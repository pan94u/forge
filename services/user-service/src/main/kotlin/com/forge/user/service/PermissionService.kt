package com.forge.user.service

import com.forge.user.entity.PermissionEntity
import com.forge.user.exception.PermissionDeniedException
import com.forge.user.repository.PermissionRepository
import com.forge.user.repository.RolePermissionRepository
import com.forge.user.repository.UserRoleRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val userRoleRepository: UserRoleRepository
) {
    /**
     * 检查用户是否有指定权限
     */
    fun hasPermission(userId: UUID, resource: String, action: String, orgId: UUID? = null): Boolean {
        // 获取用户角色
        val roles = getUserRoles(userId, orgId)

        // 检查每个角色是否有权限
        return roles.any { roleName ->
            hasRolePermission(roleName, resource, action)
        }
    }

    /**
     * 检查用户是否有任意一个权限
     */
    fun hasAnyPermission(userId: UUID, permissions: List<Pair<String, String>>, orgId: UUID? = null): Boolean {
        return permissions.any { (resource, action) ->
            hasPermission(userId, resource, action, orgId)
        }
    }

    /**
     * 检查用户是否有所有权限
     */
    fun hasAllPermissions(userId: UUID, permissions: List<Pair<String, String>>, orgId: UUID? = null): Boolean {
        return permissions.all { (resource, action) ->
            hasPermission(userId, resource, action, orgId)
        }
    }

    /**
     * 检查用户是否有指定角色
     */
    fun hasRole(userId: UUID, roleName: String, orgId: UUID? = null): Boolean {
        return userRoleRepository.existsByUserIdAndRoleNameAndOrgId(userId, roleName, orgId)
    }

    /**
     * 检查用户是否有任意一个角色
     */
    fun hasAnyRole(userId: UUID, roleNames: List<String>, orgId: UUID? = null): Boolean {
        return roleNames.any { roleName ->
            hasRole(userId, roleName, orgId)
        }
    }

    /**
     * 确保用户有权限，否则抛出异常
     */
    fun requirePermission(userId: UUID, resource: String, action: String, orgId: UUID? = null) {
        if (!hasPermission(userId, resource, action, orgId)) {
            throw PermissionDeniedException("缺少权限: $resource:$action")
        }
    }

    /**
     * 确保用户有角色，否则抛出异常
     */
    fun requireRole(userId: UUID, roleName: String, orgId: UUID? = null) {
        if (!hasRole(userId, roleName, orgId)) {
            throw PermissionDeniedException("缺少角色: $roleName")
        }
    }

    /**
     * 检查角色是否有指定权限
     */
    private fun hasRolePermission(roleName: String, resource: String, action: String): Boolean {
        if (roleName == "admin") return true  // admin 拥有所有权限

        val permission = permissionRepository.findByResourceAndAction(resource, action)
            ?: return false

        return rolePermissionRepository.existsByRoleNameAndPermissionId(roleName, permission.id)
    }

    /**
     * 获取用户角色列表
     */
    private fun getUserRoles(userId: UUID, orgId: UUID?): List<String> {
        return userRoleRepository.findRoleNamesByUserId(userId, orgId)
    }

    /**
     * 获取所有权限
     */
    fun getAllPermissions(): List<PermissionEntity> {
        return permissionRepository.findAll()
    }

    /**
     * 按资源获取权限
     */
    fun getPermissionsByResource(resource: String): List<PermissionEntity> {
        return permissionRepository.findByResource(resource)
    }
}