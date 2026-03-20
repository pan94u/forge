package com.forge.eval.engine.review

import com.forge.eval.protocol.EvalGrade
import com.forge.eval.protocol.GraderType
import org.slf4j.LoggerFactory
import kotlin.random.Random

/**
 * Determines when a grade should be flagged for human review.
 *
 * Trigger conditions:
 * 1. Model-based grader confidence < confidenceThreshold
 * 2. Code-based and Model-based score divergence > divergenceThreshold
 * 3. Random sampling at samplingRate
 * 4. First N runs of a new task (bootstrapCount)
 */
class ReviewTriggerRules(
    private val confidenceThreshold: Double = 0.7,
    private val divergenceThreshold: Double = 0.3,
    private val samplingRate: Double = 0.10,
    private val bootstrapCount: Int = 3,
    private val random: Random = Random.Default
) {
    private val logger = LoggerFactory.getLogger(ReviewTriggerRules::class.java)

    data class ReviewDecision(
        val shouldReview: Boolean,
        val reasons: List<ReviewReason>
    )

    data class ReviewReason(
        val rule: ReviewRule,
        val description: String
    )

    enum class ReviewRule {
        LOW_CONFIDENCE,
        GRADER_DIVERGENCE,
        RANDOM_SAMPLE,
        BOOTSTRAP_TASK
    }

    /**
     * Check whether a set of grades should be flagged for human review.
     *
     * @param grades All grades for a trial
     * @param taskRunCount Number of times this task has been evaluated (for bootstrap)
     * @return Decision with reasons
     */
    fun shouldReview(
        grades: List<EvalGrade>,
        taskRunCount: Int = Int.MAX_VALUE
    ): ReviewDecision {
        val reasons = mutableListOf<ReviewReason>()

        // Rule 1: Low confidence on model-based grade
        val modelGrades = grades.filter { it.graderType == GraderType.MODEL_BASED }
        for (grade in modelGrades) {
            if (grade.confidence < confidenceThreshold) {
                reasons.add(ReviewReason(
                    rule = ReviewRule.LOW_CONFIDENCE,
                    description = "Model-based 置信度 ${"%.2f".format(grade.confidence)} < $confidenceThreshold"
                ))
            }
        }

        // Rule 2: Code-based vs Model-based divergence
        val codeGrades = grades.filter { it.graderType == GraderType.CODE_BASED }
        if (codeGrades.isNotEmpty() && modelGrades.isNotEmpty()) {
            val codeAvg = codeGrades.map { it.score }.average()
            val modelAvg = modelGrades.map { it.score }.average()
            val divergence = kotlin.math.abs(codeAvg - modelAvg)
            if (divergence > divergenceThreshold) {
                reasons.add(ReviewReason(
                    rule = ReviewRule.GRADER_DIVERGENCE,
                    description = "Code(${
                        "%.2f".format(codeAvg)
                    }) vs Model(${"%.2f".format(modelAvg)}) 分差 ${"%.2f".format(divergence)} > $divergenceThreshold"
                ))
            }
        }

        // Rule 3: Random sampling
        if (random.nextDouble() < samplingRate) {
            reasons.add(ReviewReason(
                rule = ReviewRule.RANDOM_SAMPLE,
                description = "随机抽样（${(samplingRate * 100).toInt()}%）"
            ))
        }

        // Rule 4: Bootstrap for new tasks
        if (taskRunCount <= bootstrapCount) {
            reasons.add(ReviewReason(
                rule = ReviewRule.BOOTSTRAP_TASK,
                description = "新任务前 $bootstrapCount 次运行（当前第 $taskRunCount 次）"
            ))
        }

        return ReviewDecision(
            shouldReview = reasons.isNotEmpty(),
            reasons = reasons
        )
    }
}
