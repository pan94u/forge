package com.forge.user.repository

import com.forge.user.entity.OrganizationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface OrganizationRepository : JpaRepository<OrganizationEntity, UUID> {

    fun findBySlug(slug: String): Optional<OrganizationEntity>

    fun existsBySlug(slug: String): Boolean

    fun findByOwnerId(ownerId: UUID): List<OrganizationEntity>

    @Query("SELECT o FROM OrganizationEntity o JOIN OrgMemberEntity m ON o.id = m.orgId WHERE m.userId = :userId")
    fun findByMemberUserId(@Param("userId") userId: UUID): List<OrganizationEntity>
}