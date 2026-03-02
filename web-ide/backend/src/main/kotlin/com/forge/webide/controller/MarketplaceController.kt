package com.forge.webide.controller

import com.forge.webide.service.marketplace.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Skill Marketplace REST API — 17 endpoints for publishing, browsing, reviewing,
 * and quality-managing marketplace skills.
 */
@RestController
@RequestMapping("/api/marketplace")
class MarketplaceController(
    private val marketplaceService: MarketplaceService
) {

    // ==================== Status ====================

    /** GET /api/marketplace/status — system status overview */
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<MarketplaceStatusView> {
        return ResponseEntity.ok(marketplaceService.getStatus())
    }

    // ==================== Browse & Search ====================

    /** GET /api/marketplace/skills — search/browse (q, category, tags, minRating, sortBy, limit, offset) */
    @GetMapping("/skills")
    fun searchSkills(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) tags: String?,
        @RequestParam(required = false) minRating: Double?,
        @RequestParam(required = false, defaultValue = "ranking") sortBy: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int
    ): ResponseEntity<MarketplaceSearchResult> {
        val tagList = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        val result = marketplaceService.searchSkills(
            query = q,
            category = category,
            tags = tagList,
            minRating = minRating,
            sortBy = sortBy,
            limit = limit,
            offset = offset
        )
        return ResponseEntity.ok(result)
    }

    /** GET /api/marketplace/skills/featured — featured skills */
    @GetMapping("/skills/featured")
    fun getFeaturedSkills(): ResponseEntity<List<MarketplaceListingView>> {
        return ResponseEntity.ok(marketplaceService.getFeaturedSkills())
    }

    /** GET /api/marketplace/skills/top — top ranked skills */
    @GetMapping("/skills/top")
    fun getTopSkills(
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<List<MarketplaceListingView>> {
        return ResponseEntity.ok(marketplaceService.getTopSkills(limit))
    }

    /** GET /api/marketplace/skills/{id} — detail (listing + stats + reviews) */
    @GetMapping("/skills/{id}")
    fun getSkillDetail(
        @PathVariable id: String
    ): ResponseEntity<MarketplaceSkillDetailView> {
        val detail = marketplaceService.getSkillDetail(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }

    // ==================== Publish / Update / Unpublish ====================

    /** POST /api/marketplace/publish — publish a skill to the marketplace */
    @PostMapping("/publish")
    fun publish(
        @RequestBody request: PublishRequest
    ): ResponseEntity<MarketplaceListingView> {
        val listing = marketplaceService.publish(request)
            ?: return ResponseEntity.badRequest().body(null)
        return ResponseEntity.ok(listing)
    }

    /** PUT /api/marketplace/skills/{id} — update listing (tags, featured) */
    @PutMapping("/skills/{id}")
    fun updateListing(
        @PathVariable id: String,
        @RequestBody request: UpdateListingRequest
    ): ResponseEntity<Void> {
        val success = marketplaceService.updateListing(id, request)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.notFound().build()
    }

    /** DELETE /api/marketplace/skills/{id} — unpublish / remove listing */
    @DeleteMapping("/skills/{id}")
    fun unpublish(
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val success = marketplaceService.unpublish(id)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.notFound().build()
    }

    // ==================== Reviews ====================

    /** GET /api/marketplace/skills/{id}/reviews — list reviews */
    @GetMapping("/skills/{id}/reviews")
    fun getReviews(
        @PathVariable id: String
    ): ResponseEntity<List<SkillReviewView>> {
        return ResponseEntity.ok(marketplaceService.getReviews(id))
    }

    /** POST /api/marketplace/skills/{id}/reviews — submit a review */
    @PostMapping("/skills/{id}/reviews")
    fun submitReview(
        @PathVariable id: String,
        @RequestBody request: SubmitReviewRequest
    ): ResponseEntity<SkillReviewView> {
        val review = marketplaceService.submitReview(id, request)
            ?: return ResponseEntity.badRequest().body(null)
        return ResponseEntity.ok(review)
    }

    /** PUT /api/marketplace/reviews/{reviewId} — update a review */
    @PutMapping("/reviews/{reviewId}")
    fun updateReview(
        @PathVariable reviewId: String,
        @RequestBody request: UpdateReviewRequest
    ): ResponseEntity<Void> {
        val success = marketplaceService.updateReview(reviewId, request)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.notFound().build()
    }

    /** DELETE /api/marketplace/reviews/{reviewId} — delete a review */
    @DeleteMapping("/reviews/{reviewId}")
    fun deleteReview(
        @PathVariable reviewId: String
    ): ResponseEntity<Void> {
        val success = marketplaceService.deleteReview(reviewId)
        return if (success) ResponseEntity.ok().build() else ResponseEntity.notFound().build()
    }

    // ==================== Stats ====================

    /** GET /api/marketplace/skills/{id}/stats — stats for a listing */
    @GetMapping("/skills/{id}/stats")
    fun getStats(
        @PathVariable id: String
    ): ResponseEntity<MarketplaceSkillStats> {
        val stats = marketplaceService.getStats(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(stats)
    }

    /** POST /api/marketplace/stats/refresh — refresh all stats */
    @PostMapping("/stats/refresh")
    fun refreshStats(): ResponseEntity<Map<String, Int>> {
        val count = marketplaceService.refreshAllStats()
        return ResponseEntity.ok(mapOf("refreshed" to count))
    }

    // ==================== Quality Management ====================

    /** POST /api/marketplace/quality/check — run quality check on all active listings */
    @PostMapping("/quality/check")
    fun runQualityCheck(): ResponseEntity<List<QualityCheckResult>> {
        return ResponseEntity.ok(marketplaceService.runQualityCheck())
    }

    /** GET /api/marketplace/quality/thresholds — get current quality thresholds */
    @GetMapping("/quality/thresholds")
    fun getThresholds(): ResponseEntity<QualityThresholds> {
        return ResponseEntity.ok(marketplaceService.getThresholds())
    }

    /** PUT /api/marketplace/quality/thresholds — update quality thresholds */
    @PutMapping("/quality/thresholds")
    fun updateThresholds(
        @RequestBody thresholds: QualityThresholds
    ): ResponseEntity<QualityThresholds> {
        return ResponseEntity.ok(marketplaceService.updateThresholds(thresholds))
    }
}
