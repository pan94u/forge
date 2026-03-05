package com.forge.webide.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Manages user confirmation requests for Planning Mode operations.
 *
 * Provides two blocking flows:
 *  1. plan_create → waitForPlanApproval (blocks until user clicks "开始执行" or "取消")
 *  2. plan_ask_user → waitForUserAnswers (blocks until user submits answers)
 *
 * Uses ConcurrentHashMap<correlationKey, CompletableFuture<T>> pattern,
 * same as GitConfirmService. No DB persistence — purely in-memory with 300s timeout.
 *
 * Correlation key = "$sessionId:$id" to avoid collisions between sessions.
 */
@Service
class PlanConfirmService {

    private val logger = LoggerFactory.getLogger(PlanConfirmService::class.java)

    private val pendingApprovals = ConcurrentHashMap<String, CompletableFuture<PlanDecision>>()
    private val pendingAnswers = ConcurrentHashMap<String, CompletableFuture<Map<String, String>>>()

    companion object {
        const val TIMEOUT_SECONDS = 300L
    }

    // ---- plan_create blocking flow ----

    /**
     * Block until the user approves, cancels, or modifies the plan.
     * Called by PlanToolHandler.handle("plan_create", ...).
     *
     * @param sessionId  Chat session ID
     * @param planId     Unique ID for this plan instance
     * @return PlanDecision with action APPROVED / CANCELLED / MODIFIED
     */
    fun waitForPlanApproval(sessionId: String, planId: String): PlanDecision {
        val key = "$sessionId:$planId"
        val future = CompletableFuture<PlanDecision>()
        pendingApprovals[key] = future

        return try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("Plan approval timed out after {}s for session={}, planId={}", TIMEOUT_SECONDS, sessionId, planId)
            PlanDecision(action = "CANCELLED", reason = "timeout")
        } finally {
            pendingApprovals.remove(key)
        }
    }

    /**
     * Resolve a pending plan approval (called from REST endpoint).
     *
     * @param sessionId     Chat session ID
     * @param planId        Plan ID
     * @param decision      User's decision
     */
    fun resolvePlanApproval(sessionId: String, planId: String, decision: PlanDecision) {
        val key = "$sessionId:$planId"
        val future = pendingApprovals[key]
        if (future != null) {
            future.complete(decision)
            logger.info("Plan approval resolved: session={}, planId={}, action={}", sessionId, planId, decision.action)
        } else {
            logger.warn("No pending plan approval for session={}, planId={}", sessionId, planId)
        }
    }

    // ---- plan_ask_user blocking flow ----

    /**
     * Block until the user submits answers to all questions.
     * Called by PlanToolHandler.handle("plan_ask_user", ...).
     *
     * @param sessionId  Chat session ID
     * @param askId      Unique ID for this ask instance
     * @return Map of question key → answer string
     */
    fun waitForUserAnswers(sessionId: String, askId: String): Map<String, String> {
        val key = "$sessionId:$askId"
        val future = CompletableFuture<Map<String, String>>()
        pendingAnswers[key] = future

        return try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("Plan ask timed out after {}s for session={}, askId={}", TIMEOUT_SECONDS, sessionId, askId)
            emptyMap()
        } finally {
            pendingAnswers.remove(key)
        }
    }

    /**
     * Resolve a pending user-ask (called from REST endpoint).
     *
     * @param sessionId  Chat session ID
     * @param askId      Ask ID
     * @param answers    Map of question key → answer value
     */
    fun resolveUserAnswers(sessionId: String, askId: String, answers: Map<String, String>) {
        val key = "$sessionId:$askId"
        val future = pendingAnswers[key]
        if (future != null) {
            future.complete(answers)
            logger.info("Plan ask resolved: session={}, askId={}, answerCount={}", sessionId, askId, answers.size)
        } else {
            logger.warn("No pending plan ask for session={}, askId={}", sessionId, askId)
        }
    }
}

/**
 * Represents the user's decision for a plan approval request.
 *
 * @param action        "APPROVED", "CANCELLED", or "MODIFIED"
 * @param reason        Optional reason (e.g. "timeout")
 * @param modifiedTasks Modified task list when action == "MODIFIED"
 */
data class PlanDecision(
    val action: String,
    val reason: String? = null,
    @Suppress("UNCHECKED_CAST")
    val modifiedTasks: List<Map<String, Any>>? = null
)
