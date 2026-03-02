package com.forge.webide.repository

import com.forge.webide.entity.SkillReviewEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SkillReviewRepository : JpaRepository<SkillReviewEntity, String> {

    fun findByListingId(listingId: String): List<SkillReviewEntity>

    fun findByListingIdAndReviewerId(listingId: String, reviewerId: String): SkillReviewEntity?

    fun findBySkillName(skillName: String): List<SkillReviewEntity>

    fun findByReviewerId(reviewerId: String): List<SkillReviewEntity>

    fun countByListingId(listingId: String): Long

    @Query("SELECT AVG(r.rating) FROM SkillReviewEntity r WHERE r.listingId = :listingId")
    fun averageRatingByListingId(listingId: String): Double?

    @Query("SELECT AVG(r.rating) FROM SkillReviewEntity r WHERE r.skillName = :skillName")
    fun averageRatingBySkillName(skillName: String): Double?
}
