package com.forge.webide.service.marketplace

import com.forge.webide.entity.MarketplaceListingEntity
import com.forge.webide.entity.SkillReviewEntity
import com.forge.webide.repository.MarketplaceListingRepository
import com.forge.webide.repository.SkillReviewRepository
import com.forge.webide.repository.SkillUsageRepository
import com.forge.webide.service.skill.SkillLoader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.pow

/**
 * Skill Marketplace service — publish, discover, review, and quality-manage skills.
 *
 * Core design:
 * - "Publish" registers a custom skill into the marketplace registry
 * - "Use" executes via existing SkillManager/SkillLoader — no install step
 * - Stats are aggregated from SkillUsage + reviews
 * - Quality management auto-suspends low-quality skills
 */
@Service
class MarketplaceService(
    private val listingRepository: MarketplaceListingRepository,
    private val reviewRepository: SkillReviewRepository,
    private val skillUsageRepository: SkillUsageRepository,
    private val skillLoader: SkillLoader
) {
    private val logger = LoggerFactory.getLogger(MarketplaceService::class.java)

    // Default quality thresholds (mutable at runtime via API)
    private var qualityThresholds = QualityThresholds()

    // ========== Status ==========

    fun getStatus(): MarketplaceStatusView {
        val totalListings = listingRepository.count()
        val activeListings = listingRepository.countByStatus("ACTIVE")
        val suspendedListings = listingRepository.countByStatus("SUSPENDED")
        val totalReviews = reviewRepository.count()

        return MarketplaceStatusView(
            totalListings = totalListings,
            activeListings = activeListings,
            suspendedListings = suspendedListings,
            totalReviews = totalReviews,
            qualityThresholds = qualityThresholds
        )
    }

    // ========== Browse & Search ==========

    fun searchSkills(
        query: String? = null,
        category: String? = null,
        tags: List<String>? = null,
        minRating: Double? = null,
        sortBy: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): MarketplaceSearchResult {
        var listings = if (!query.isNullOrBlank()) {
            listingRepository.searchByQuery(query.trim())
        } else {
            listingRepository.findByStatus("ACTIVE")
        }

        // Filter by tags
        if (!tags.isNullOrEmpty()) {
            listings = listings.filter { listing ->
                val listingTags = listing.getTagList().map { it.lowercase() }
                tags.any { tag -> listingTags.contains(tag.lowercase()) }
            }
        }

        // Filter by minimum rating
        if (minRating != null && minRating > 0) {
            listings = listings.filter { listing ->
                val avg = reviewRepository.averageRatingByListingId(listing.id) ?: 0.0
                avg >= minRating
            }
        }

        // Sort
        val sorted = when (sortBy) {
            "rating" -> listings.sortedByDescending {
                reviewRepository.averageRatingByListingId(it.id) ?: 0.0
            }
            "newest" -> listings.sortedByDescending { it.createdAt }
            "name" -> listings.sortedBy { it.skillName }
            "ranking" -> listings.sortedByDescending { calculateRankingScore(it) }
            else -> listings.sortedByDescending { calculateRankingScore(it) }
        }

        val total = sorted.size
        val paged = sorted.drop(offset).take(limit)

        return MarketplaceSearchResult(
            items = paged.map { toListingView(it) },
            total = total,
            limit = limit,
            offset = offset
        )
    }

    fun getFeaturedSkills(): List<MarketplaceListingView> {
        return listingRepository.findByFeaturedTrueAndStatus("ACTIVE")
            .map { toListingView(it) }
    }

    fun getTopSkills(limit: Int = 10): List<MarketplaceListingView> {
        return listingRepository.findByStatus("ACTIVE")
            .sortedByDescending { calculateRankingScore(it) }
            .take(limit)
            .map { toListingView(it) }
    }

    fun getSkillDetail(listingId: String): MarketplaceSkillDetailView? {
        val listing = listingRepository.findById(listingId).orElse(null) ?: return null
        val reviews = reviewRepository.findByListingId(listingId)
        val stats = computeStats(listing)

        return MarketplaceSkillDetailView(
            listing = toListingView(listing),
            stats = stats,
            reviews = reviews.map { toReviewView(it) }
        )
    }

    // ========== Publish / Update / Unpublish ==========

    @Transactional
    fun publish(request: PublishRequest): MarketplaceListingView? {
        // Verify the skill exists
        val skill = skillLoader.loadSkill(request.skillId)
        if (skill == null) {
            logger.warn("Cannot publish: skill '{}' not found", request.skillId)
            return null
        }

        // Check if already published
        val existing = listingRepository.findBySkillName(request.skillId)
        if (existing != null) {
            logger.warn("Skill '{}' already published as listing '{}'", request.skillId, existing.id)
            return null
        }

        val listing = MarketplaceListingEntity(
            id = UUID.randomUUID().toString(),
            skillName = request.skillId,
            authorId = request.author.id,
            authorName = request.author.name,
            description = skill.description,
            featured = false
        )
        listing.setTagList(request.tags)

        listingRepository.save(listing)
        logger.info("Skill '{}' published to marketplace as listing '{}'", request.skillId, listing.id)

        return toListingView(listing)
    }

    @Transactional
    fun updateListing(listingId: String, request: UpdateListingRequest): Boolean {
        val listing = listingRepository.findById(listingId).orElse(null) ?: return false

        request.tags?.let { listing.setTagList(it) }
        request.featured?.let { listing.featured = it }
        request.description?.let { listing.description = it }
        listing.updatedAt = Instant.now()

        listingRepository.save(listing)
        logger.info("Marketplace listing '{}' updated", listingId)
        return true
    }

    @Transactional
    fun unpublish(listingId: String): Boolean {
        val listing = listingRepository.findById(listingId).orElse(null) ?: return false
        listingRepository.delete(listing)
        logger.info("Marketplace listing '{}' removed (skill={})", listingId, listing.skillName)
        return true
    }

    // ========== Reviews ==========

    fun getReviews(listingId: String): List<SkillReviewView> {
        return reviewRepository.findByListingId(listingId).map { toReviewView(it) }
    }

    @Transactional
    fun submitReview(listingId: String, request: SubmitReviewRequest): SkillReviewView? {
        val listing = listingRepository.findById(listingId).orElse(null) ?: return null

        // One review per user per listing
        val existing = reviewRepository.findByListingIdAndReviewerId(listingId, request.reviewerId)
        if (existing != null) {
            logger.warn("User '{}' already reviewed listing '{}'", request.reviewerId, listingId)
            return null
        }

        val review = SkillReviewEntity(
            id = UUID.randomUUID().toString(),
            listingId = listingId,
            skillName = listing.skillName,
            reviewerId = request.reviewerId,
            reviewerName = request.reviewerName,
            rating = request.rating.coerceIn(1, 5),
            comment = request.comment
        )

        reviewRepository.save(review)
        logger.info("Review submitted for listing '{}' by '{}'", listingId, request.reviewerId)
        return toReviewView(review)
    }

    @Transactional
    fun updateReview(reviewId: String, request: UpdateReviewRequest): Boolean {
        val review = reviewRepository.findById(reviewId).orElse(null) ?: return false

        request.rating?.let { review.rating = it.coerceIn(1, 5) }
        request.comment?.let { review.comment = it }
        review.updatedAt = Instant.now()

        reviewRepository.save(review)
        return true
    }

    @Transactional
    fun deleteReview(reviewId: String): Boolean {
        if (!reviewRepository.existsById(reviewId)) return false
        reviewRepository.deleteById(reviewId)
        return true
    }

    // ========== Stats ==========

    fun getStats(listingId: String): MarketplaceSkillStats? {
        val listing = listingRepository.findById(listingId).orElse(null) ?: return null
        return computeStats(listing)
    }

    fun refreshAllStats(): Int {
        val listings = listingRepository.findByStatus("ACTIVE")
        // Stats are computed on-the-fly from usage + reviews, so "refresh" is a no-op
        // but we return the count to confirm the operation
        return listings.size
    }

    // ========== Quality Management ==========

    fun runQualityCheck(): List<QualityCheckResult> {
        val listings = listingRepository.findByStatus("ACTIVE")
        val results = mutableListOf<QualityCheckResult>()

        for (listing in listings) {
            val result = checkQuality(listing)
            results.add(result)

            if (result.action == "suspend") {
                listing.status = "SUSPENDED"
                listing.suspendReason = result.issues.joinToString("; ")
                listing.updatedAt = Instant.now()
                listingRepository.save(listing)
                logger.warn("Listing '{}' (skill={}) auto-suspended: {}",
                    listing.id, listing.skillName, listing.suspendReason)
            }
        }

        return results
    }

    fun getThresholds(): QualityThresholds = qualityThresholds

    fun updateThresholds(newThresholds: QualityThresholds): QualityThresholds {
        qualityThresholds = newThresholds
        logger.info("Quality thresholds updated: {}", newThresholds)
        return qualityThresholds
    }

    // ========== Internal: Ranking ==========

    /**
     * Ranking score formula:
     * score = (usage30d × 0.3) + (avgRating/5 × 0.3) + (successRate × 0.2) + (recencyFactor × 0.2)
     * recencyFactor = 2^(-daysSinceUpdate / 90)   // 半衰期 90 天
     */
    private fun calculateRankingScore(listing: MarketplaceListingEntity): Double {
        val since30d = Instant.now().minus(30, ChronoUnit.DAYS)

        // usage30d: normalized to 0-1 range (cap at 100 uses)
        val usage30d = skillUsageRepository.countBySkillNameSince(since30d)
            .find { (it[0] as String) == listing.skillName }
            ?.let { (it[1] as Long).toDouble().coerceAtMost(100.0) / 100.0 }
            ?: 0.0

        // avgRating: 0-1 range
        val avgRating = (reviewRepository.averageRatingByListingId(listing.id) ?: 0.0) / 5.0

        // successRate: 0-1 range
        val totalUsage = skillUsageRepository.countBySkillName(listing.skillName)
        val successCount = skillUsageRepository.countSuccessBySkillName(listing.skillName)
        val successRate = if (totalUsage > 0) successCount.toDouble() / totalUsage else 1.0

        // recencyFactor: half-life 90 days
        val daysSinceUpdate = ChronoUnit.DAYS.between(listing.updatedAt, Instant.now()).toDouble()
        val recencyFactor = 2.0.pow(-daysSinceUpdate / 90.0)

        return (usage30d * 0.3) + (avgRating * 0.3) + (successRate * 0.2) + (recencyFactor * 0.2)
    }

    // ========== Internal: Quality Check ==========

    private fun checkQuality(listing: MarketplaceListingEntity): QualityCheckResult {
        val issues = mutableListOf<String>()
        var action = "none"

        // Check rating threshold (need at least minReviewsForSuspend reviews)
        val reviewCount = reviewRepository.countByListingId(listing.id)
        val avgRating = reviewRepository.averageRatingByListingId(listing.id)

        if (reviewCount >= qualityThresholds.minReviewsForSuspend && avgRating != null) {
            if (avgRating < qualityThresholds.minRatingThreshold) {
                issues.add("平均评分 %.1f 低于阈值 %.1f（基于 %d 条评价）".format(
                    avgRating, qualityThresholds.minRatingThreshold, reviewCount))
                action = "suspend"
            }
        }

        // Check failure rate threshold (need at least minExecutionsForSuspend executions)
        val totalUsage = skillUsageRepository.countBySkillName(listing.skillName)
        val successCount = skillUsageRepository.countSuccessBySkillName(listing.skillName)
        val failureRate = if (totalUsage > 0) 1.0 - (successCount.toDouble() / totalUsage) else 0.0

        if (totalUsage >= qualityThresholds.minExecutionsForSuspend) {
            if (failureRate > qualityThresholds.maxFailureRate) {
                issues.add("失败率 %.0f%% 超过阈值 %.0f%%（基于 %d 次执行）".format(
                    failureRate * 100, qualityThresholds.maxFailureRate * 100, totalUsage))
                action = "suspend"
            }
        }

        // Check staleness (warn only)
        val daysSinceUpdate = ChronoUnit.DAYS.between(listing.updatedAt, Instant.now())
        if (daysSinceUpdate > qualityThresholds.staleDays) {
            issues.add("超过 %d 天未更新".format(daysSinceUpdate))
            if (action == "none") action = "warn"
        }

        return QualityCheckResult(
            listingId = listing.id,
            skillName = listing.skillName,
            issues = issues,
            action = action
        )
    }

    // ========== Internal: View Mapping ==========

    private fun computeStats(listing: MarketplaceListingEntity): MarketplaceSkillStats {
        val since30d = Instant.now().minus(30, ChronoUnit.DAYS)

        val totalUsage = skillUsageRepository.countBySkillName(listing.skillName)
        val successCount = skillUsageRepository.countSuccessBySkillName(listing.skillName)
        val successRate = if (totalUsage > 0) successCount.toDouble() / totalUsage else 0.0

        val usage30d = skillUsageRepository.countBySkillNameSince(since30d)
            .find { (it[0] as String) == listing.skillName }
            ?.let { it[1] as Long }
            ?: 0L

        val avgRating = reviewRepository.averageRatingByListingId(listing.id) ?: 0.0
        val reviewCount = reviewRepository.countByListingId(listing.id)
        val rankingScore = calculateRankingScore(listing)

        return MarketplaceSkillStats(
            skillName = listing.skillName,
            totalExecutions = totalUsage,
            successCount = successCount,
            successRate = successRate,
            usage30d = usage30d,
            avgRating = avgRating,
            reviewCount = reviewCount,
            rankingScore = rankingScore
        )
    }

    private fun toListingView(listing: MarketplaceListingEntity): MarketplaceListingView {
        val avgRating = reviewRepository.averageRatingByListingId(listing.id) ?: 0.0
        val reviewCount = reviewRepository.countByListingId(listing.id)

        return MarketplaceListingView(
            id = listing.id,
            skillName = listing.skillName,
            authorId = listing.authorId,
            authorName = listing.authorName,
            description = listing.description ?: "",
            tags = listing.getTagList(),
            status = listing.status,
            featured = listing.featured,
            avgRating = avgRating,
            reviewCount = reviewCount,
            createdAt = listing.createdAt.toString(),
            updatedAt = listing.updatedAt.toString()
        )
    }

    private fun toReviewView(review: SkillReviewEntity): SkillReviewView {
        return SkillReviewView(
            id = review.id,
            listingId = review.listingId,
            skillName = review.skillName,
            reviewerId = review.reviewerId,
            reviewerName = review.reviewerName,
            rating = review.rating,
            comment = review.comment,
            createdAt = review.createdAt.toString(),
            updatedAt = review.updatedAt.toString()
        )
    }
}
