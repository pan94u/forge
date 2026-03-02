package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * A published skill listing in the Marketplace.
 * Each listing maps to exactly one skill (by skillName).
 */
@Entity
@Table(name = "marketplace_listings")
class MarketplaceListingEntity(
    @Id
    @Column(name = "id", length = 36)
    val id: String,

    @Column(name = "skill_name", nullable = false, unique = true)
    val skillName: String,

    @Column(name = "author_id", nullable = false)
    val authorId: String,

    @Column(name = "author_name", nullable = false)
    var authorName: String = "",

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "tags", columnDefinition = "TEXT")
    var tags: String? = null,

    @Column(name = "status", nullable = false, length = 50)
    var status: String = "ACTIVE",

    @Column(name = "featured", nullable = false)
    var featured: Boolean = false,

    @Column(name = "publish_reason", length = 500)
    var publishReason: String? = null,

    @Column(name = "suspend_reason", length = 500)
    var suspendReason: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun getTagList(): List<String> =
        tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun setTagList(tagList: List<String>) {
        tags = tagList.joinToString(",")
    }
}
