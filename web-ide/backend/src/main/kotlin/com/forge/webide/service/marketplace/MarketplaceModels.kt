package com.forge.webide.service.marketplace

// ========== Request DTOs ==========

data class PublishRequest(
    val skillId: String,
    val author: AuthorInfo,
    val tags: List<String> = emptyList()
)

data class AuthorInfo(
    val id: String,
    val name: String
)

data class UpdateListingRequest(
    val tags: List<String>? = null,
    val featured: Boolean? = null,
    val description: String? = null
)

data class SubmitReviewRequest(
    val reviewerId: String,
    val reviewerName: String,
    val rating: Int,
    val comment: String? = null
)

data class UpdateReviewRequest(
    val rating: Int? = null,
    val comment: String? = null
)

// ========== View Models ==========

data class MarketplaceStatusView(
    val totalListings: Long,
    val activeListings: Long,
    val suspendedListings: Long,
    val totalReviews: Long,
    val qualityThresholds: QualityThresholds
)

data class MarketplaceListingView(
    val id: String,
    val skillName: String,
    val authorId: String,
    val authorName: String,
    val description: String,
    val tags: List<String>,
    val status: String,
    val featured: Boolean,
    val avgRating: Double,
    val reviewCount: Long,
    val createdAt: String,
    val updatedAt: String
)

data class MarketplaceSearchResult(
    val items: List<MarketplaceListingView>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class MarketplaceSkillDetailView(
    val listing: MarketplaceListingView,
    val stats: MarketplaceSkillStats,
    val reviews: List<SkillReviewView>
)

data class MarketplaceSkillStats(
    val skillName: String,
    val totalExecutions: Long,
    val successCount: Long,
    val successRate: Double,
    val usage30d: Long,
    val avgRating: Double,
    val reviewCount: Long,
    val rankingScore: Double
)

data class SkillReviewView(
    val id: String,
    val listingId: String,
    val skillName: String,
    val reviewerId: String,
    val reviewerName: String,
    val rating: Int,
    val comment: String?,
    val createdAt: String,
    val updatedAt: String
)

data class QualityCheckResult(
    val listingId: String,
    val skillName: String,
    val issues: List<String>,
    val action: String   // "none", "warn", "suspend"
)

/**
 * Quality thresholds for auto-suspend logic.
 * Default values per plan:
 * - Rating < 2.0 with >= 3 reviews → suspend
 * - Failure rate > 50% with >= 10 executions → suspend
 * - > 180 days no update → stale (warn only)
 */
data class QualityThresholds(
    val minRatingThreshold: Double = 2.0,
    val minReviewsForSuspend: Long = 3,
    val maxFailureRate: Double = 0.5,
    val minExecutionsForSuspend: Long = 10,
    val staleDays: Long = 180
)
