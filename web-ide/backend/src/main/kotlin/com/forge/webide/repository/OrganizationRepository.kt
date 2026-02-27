package com.forge.webide.repository

import com.forge.webide.entity.OrganizationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrganizationRepository : JpaRepository<OrganizationEntity, String> {
    fun findByStatus(status: String): List<OrganizationEntity>
    fun findBySlug(slug: String): OrganizationEntity?
}
