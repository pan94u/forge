package com.forge.webide.repository

import com.forge.webide.entity.MarketplaceListingEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MarketplaceListingRepository : JpaRepository<MarketplaceListingEntity, String> {

    fun findBySkillName(skillName: String): MarketplaceListingEntity?

    fun findByStatus(status: String): List<MarketplaceListingEntity>

    fun findByFeaturedTrueAndStatus(status: String): List<MarketplaceListingEntity>

    fun findByAuthorId(authorId: String): List<MarketplaceListingEntity>

    @Query("""
        SELECT l FROM MarketplaceListingEntity l
        WHERE l.status = :status
        AND (
            LOWER(l.skillName) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(l.description) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(l.tags) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(l.authorName) LIKE LOWER(CONCAT('%', :query, '%'))
        )
    """)
    fun searchByQuery(query: String, status: String = "ACTIVE"): List<MarketplaceListingEntity>

    fun countByStatus(status: String): Long
}
