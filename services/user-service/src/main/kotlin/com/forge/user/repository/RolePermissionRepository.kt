package com.forge.user.repository

import com.forge.user.entity.RolePermissionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RolePermissionRepository : JpaRepository<RolePermissionEntity, UUID> {

    fun existsByRoleNameAndPermissionId(roleName: String, permissionId: UUID): Boolean

    fun findByRoleName(roleName: String): List<RolePermissionEntity>

    @Query("SELECT rp.permissionId FROM RolePermissionEntity rp WHERE rp.roleName = :roleName")
    fun findPermissionIdsByRoleName(@Param("roleName") roleName: String): List<UUID>
}