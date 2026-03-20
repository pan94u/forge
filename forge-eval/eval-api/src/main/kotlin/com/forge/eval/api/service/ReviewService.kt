package com.forge.eval.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.eval.api.entity.*
import com.forge.eval.api.repository.*
import com.forge.eval.protocol.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ReviewService(
    private val reviewRepo: EvalReviewRepository,
    private val gradeRepo: EvalGradeRepository,
    private val taskRepo: EvalTaskRepository,
    private val trialRepo: EvalTrialRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ReviewService::class.java)

    fun getReviewQueue(page: Int, size: Int): PageResponse<ReviewQueueItem> {
        val pageable = PageRequest.of(page, size)
        val result = reviewRepo.findByStatus(ReviewStatusEnum.PENDING, pageable)

        val taskIds = result.content.map { it.taskId }.distinct()
        val taskNames = if (taskIds.isNotEmpty()) {
            taskRepo.findAllById(taskIds).associate { it.id to it.name }
        } else {
            emptyMap()
        }

        return PageResponse(
            content = result.content.map { review ->
                ReviewQueueItem(
                    gradeId = review.gradeId,
                    trialId = review.trialId,
                    taskId = review.taskId,
                    taskName = taskNames[review.taskId] ?: "unknown",
                    graderType = GraderType.valueOf(
                        gradeRepo.findById(review.gradeId)
                            .map { it.graderType.name }
                            .orElse("CODE_BASED")
                    ),
                    autoScore = review.autoScore,
                    confidence = review.autoConfidence,
                    reviewReasons = objectMapper.readValue(review.reviewReasons),
                    status = ReviewStatus.valueOf(review.status.name),
                    createdAt = review.createdAt
                )
            },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional
    fun submitReview(gradeId: UUID, request: SubmitReviewRequest): ReviewResponse {
        val review = reviewRepo.findByGradeId(gradeId)
            ?: throw NotFoundException("Review not found for grade: $gradeId")

        val now = Instant.now()
        review.humanScore = request.score
        review.humanPassed = request.passed
        review.explanation = request.explanation
        review.reviewer = request.reviewer
        review.status = ReviewStatusEnum.COMPLETED
        review.completedAt = now
        reviewRepo.save(review)

        val calibrationDelta = request.score - review.autoScore

        logger.info("Review submitted for grade {}: human={:.2f} auto={:.2f} delta={:.2f}",
            gradeId, request.score, review.autoScore, calibrationDelta)

        return ReviewResponse(
            gradeId = gradeId,
            humanScore = request.score,
            humanPassed = request.passed,
            reviewer = request.reviewer,
            explanation = request.explanation,
            calibrationDelta = calibrationDelta,
            completedAt = now
        )
    }

    fun getCalibrationMetrics(): CalibrationMetrics {
        val completedReviews = reviewRepo.findByStatusIn(
            listOf(ReviewStatusEnum.COMPLETED)
        )

        if (completedReviews.isEmpty()) {
            return CalibrationMetrics(
                totalReviews = 0,
                averageAutoScore = 0.0,
                averageHumanScore = 0.0,
                scoreDelta = 0.0,
                agreementRate = 0.0,
                cohensKappa = 0.0,
                byGraderType = emptyMap()
            )
        }

        val avgAuto = completedReviews.map { it.autoScore }.average()
        val avgHuman = completedReviews.mapNotNull { it.humanScore }.average()
        val agreementCount = completedReviews.count { review ->
            val humanPassed = review.humanPassed ?: false
            val autoPassed = review.autoScore >= 0.5
            humanPassed == autoPassed
        }
        val agreementRate = agreementCount.toDouble() / completedReviews.size

        // Compute Cohen's Kappa
        val cohensKappa = computeCohensKappa(completedReviews)

        // Group by grader type
        val gradeIds = completedReviews.map { it.gradeId }
        val grades = if (gradeIds.isNotEmpty()) {
            gradeRepo.findAllById(gradeIds).associate { it.id to it.graderType }
        } else {
            emptyMap()
        }

        val byGraderType = completedReviews.groupBy { grades[it.gradeId] ?: GraderTypeEnum.CODE_BASED }
            .map { (graderType, reviews) ->
                val gtAuto = reviews.map { it.autoScore }.average()
                val gtHuman = reviews.mapNotNull { it.humanScore }.average()
                val gtAgree = reviews.count { r ->
                    (r.humanPassed ?: false) == (r.autoScore >= 0.5)
                }.toDouble() / reviews.size
                GraderType.valueOf(graderType.name) to GraderCalibration(
                    graderType = GraderType.valueOf(graderType.name),
                    reviewCount = reviews.size,
                    averageAutoScore = gtAuto,
                    averageHumanScore = gtHuman,
                    agreementRate = gtAgree
                )
            }.toMap()

        return CalibrationMetrics(
            totalReviews = completedReviews.size,
            averageAutoScore = avgAuto,
            averageHumanScore = avgHuman,
            scoreDelta = avgHuman - avgAuto,
            agreementRate = agreementRate,
            cohensKappa = cohensKappa,
            byGraderType = byGraderType
        )
    }

    /**
     * Create a review entry for a grade that needs human review.
     */
    @Transactional
    fun createReview(
        gradeId: UUID,
        trialId: UUID,
        taskId: UUID,
        autoScore: Double,
        autoConfidence: Double,
        reasons: List<String>
    ) {
        val entity = EvalReviewEntity(
            gradeId = gradeId,
            trialId = trialId,
            taskId = taskId,
            autoScore = autoScore,
            autoConfidence = autoConfidence,
            reviewReasons = objectMapper.writeValueAsString(reasons)
        )
        reviewRepo.save(entity)
        logger.info("Created review for grade {}: reasons={}", gradeId, reasons)
    }

    private fun computeCohensKappa(reviews: List<EvalReviewEntity>): Double {
        if (reviews.isEmpty()) return 0.0

        val n = reviews.size.toDouble()
        var a = 0 // both pass
        var b = 0 // auto pass, human fail
        var c = 0 // auto fail, human pass
        var d = 0 // both fail

        for (review in reviews) {
            val autoPassed = review.autoScore >= 0.5
            val humanPassed = review.humanPassed ?: false
            when {
                autoPassed && humanPassed -> a++
                autoPassed && !humanPassed -> b++
                !autoPassed && humanPassed -> c++
                else -> d++
            }
        }

        val po = (a + d) / n // observed agreement
        val pe = ((a + b) / n) * ((a + c) / n) + ((c + d) / n) * ((b + d) / n) // expected agreement

        return if (pe == 1.0) 1.0 else (po - pe) / (1 - pe)
    }
}
