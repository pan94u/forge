package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * A user review/rating for a marketplace skill listing.
 * One review per user per listing (unique constraint on listing_id + reviewer_id).
 */
@Entity
@Table(
    name = "skill_reviews",
    uniqueConstraints = [UniqueConstraint(columnNames = ["listing_id", "reviewer_id"])]
)
class SkillReviewEntity(
    @Id
    @Column(name = "id", length = 36)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 36)
    val listingId: String,

    @Column(name = "skill_name", nullable = false)
    val skillName: String,

    @Column(name = "reviewer_id", nullable = false)
    val reviewerId: String,

    @Column(name = "reviewer_name", nullable = false)
    var reviewerName: String = "",

    @Column(name = "rating", nullable = false)
    var rating: Int,

    @Column(name = "comment", columnDefinition = "TEXT")
    var comment: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
