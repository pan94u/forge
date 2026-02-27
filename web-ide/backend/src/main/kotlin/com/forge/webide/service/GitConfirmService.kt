package com.forge.webide.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Manages user confirmation requests for git write operations (commit/push/pull).
 *
 * Uses the same ConcurrentHashMap<sessionId, CompletableFuture<Boolean>> pattern
 * as HitlCheckpointManager. No DB persistence — purely in-memory with 120s timeout.
 *
 * Flow:
 * 1. WorkspaceToolHandler calls requestConfirmation() before commit/push/pull
 * 2. requestConfirmation() emits a "git_confirm" event to the frontend and blocks (max 120s)
 * 3. Frontend shows a confirmation card; user clicks approve/cancel
 * 4. WebSocket handler calls respond() with the user's decision
 * 5. requestConfirmation() unblocks and returns true/false
 */
@Service
class GitConfirmService {

    private val logger = LoggerFactory.getLogger(GitConfirmService::class.java)
    private val pending = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

    companion object {
        const val TIMEOUT_SECONDS = 120L
    }

    /**
     * Request user confirmation for a git write operation.
     * Blocks the calling thread until the user responds or TIMEOUT_SECONDS elapses.
     *
     * @param sessionId  Chat session ID (used as correlation key)
     * @param tool       Tool name, e.g. "workspace_git_commit"
     * @param preview    Human-readable preview of the git command to be executed
     * @param onEvent    Stream event emitter to push the confirmation card to the frontend
     * @return true if user approved, false if user cancelled or timeout
     */
    fun requestConfirmation(
        sessionId: String,
        tool: String,
        preview: String,
        onEvent: (Map<String, Any?>) -> Unit
    ): Boolean {
        val future = CompletableFuture<Boolean>()
        pending[sessionId] = future

        onEvent(mapOf(
            "type" to "git_confirm",
            "gitConfirmTool" to tool,
            "gitConfirmPreview" to preview
        ))

        return try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("Git confirm timed out after {}s for session {}, tool {}", TIMEOUT_SECONDS, sessionId, tool)
            false
        } finally {
            pending.remove(sessionId)
        }
    }

    /**
     * Resolve a pending confirmation request (called from WebSocket handler).
     *
     * @param sessionId Chat session ID
     * @param approved  true = user approved, false = user cancelled
     */
    fun respond(sessionId: String, approved: Boolean) {
        val future = pending[sessionId]
        if (future != null) {
            future.complete(approved)
            logger.info("Git confirm resolved: session={}, approved={}", sessionId, approved)
        } else {
            logger.warn("No pending git confirmation for session {}", sessionId)
        }
    }
}
