package com.forge.user.repository

import com.forge.user.entity.OrgMemberEntity
import com.forge.user.entity.OrgRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrgMemberRepository : JpaRepository<OrgMemberEntity, UUID> {

    fun findByOrgIdAndUserId(orgId: UUID, userId: UUID): OrgMemberEntity?

    fun existsByOrgIdAndUserId(orgId: UUID, userId: UUID): Boolean

    fun findByOrgId(orgId: UUID): List<OrgMemberEntity>

    fun findByUserId(userId: UUID): List<OrgMemberEntity>

    @Query("SELECT m FROM OrgMemberEntity m WHERE m.orgId = :orgId AND m.role = :role")
    fun findByOrgIdAndRole(@Param("orgId") orgId: UUID, @Param("role") role: OrgRole): List<OrgMemberEntity>

    @Modifying
    @Query("DELETE FROM OrgMemberEntity m WHERE m.orgId = :orgId AND m.userId = :userId")
    fun deleteByOrgIdAndUserId(@Param("orgId") orgId: UUID, @Param("userId") userId: UUID)

    @Query("SELECT COUNT(m) FROM OrgMemberEntity m WHERE m.orgId = :orgId")
    fun countByOrgId(@Param("orgId") orgId: UUID): Long
}