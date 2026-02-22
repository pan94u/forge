package com.forge.user.repository

import com.forge.user.entity.UserRoleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRoleRepository : JpaRepository<UserRoleEntity, UUID> {

    fun findByUserIdAndOrgIdIsNull(userId: UUID): List<UserRoleEntity>

    fun findByUserIdAndOrgId(userId: UUID, orgId: UUID?): List<UserRoleEntity>

    @Query("SELECT ur.roleName FROM UserRoleEntity ur WHERE ur.userId = :userId AND (ur.orgId IS NULL OR ur.orgId = :orgId)")
    fun findRoleNamesByUserId(@Param("userId") userId: UUID, @Param("orgId") orgId: UUID?): List<String>

    fun existsByUserIdAndRoleNameAndOrgId(userId: UUID, roleName: String, orgId: UUID?): Boolean

    @Query("SELECT ur FROM UserRoleEntity ur WHERE ur.userId = :userId AND ur.roleName = :roleName AND ur.expiresAt < CURRENT_TIMESTAMP")
    fun findExpiredRoles(@Param("userId") userId: UUID, @Param("roleName") roleName: String): List<UserRoleEntity>
}