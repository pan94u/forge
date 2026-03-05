package com.forge.webide.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Handles Planning Mode MCP tools: plan_create, plan_update_task, plan_ask_user, plan_complete.
 *
 * Two tools are blocking (like GitConfirmService):
 *  - plan_create: emits plan_ready event, blocks until user approves/cancels/modifies
 *  - plan_ask_user: emits plan_ask event, blocks until user submits answers
 *
 * Two tools are fire-and-forget:
 *  - plan_update_task: emits plan_task_update event immediately
 *  - plan_complete: emits plan_summary event immediately
 */
@Service
class PlanToolHandler(
    private val planConfirmService: PlanConfirmService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(PlanToolHandler::class.java)

    fun handle(
        toolName: String,
        args: Map<String, Any?>,
        sessionId: String,
        onEvent: ((Map<String, Any?>) -> Unit)?
    ): McpToolCallResponse {
        return try {
            when (toolName) {
                "plan_create" -> handlePlanCreate(args, sessionId, onEvent)
                "plan_update_task" -> handlePlanUpdateTask(args, onEvent)
                "plan_ask_user" -> handlePlanAskUser(args, sessionId, onEvent)
                "plan_complete" -> handlePlanComplete(args, onEvent)
                else -> McpProxyService.errorResponse("Unknown plan tool: $toolName")
            }
        } catch (e: Exception) {
            logger.error("Plan tool execution failed: tool={}, error={}", toolName, e.message, e)
            McpProxyService.errorResponse("Plan tool error: ${e.message}")
        }
    }

    /**
     * plan_create — blocking.
     * Emits a plan_ready event with tasks, then waits for user to approve/cancel/modify.
     *
     * Args: { tasks: List<{id, title, files, successCriteria, estimatedLines, dependsOn?}> }
     * Returns: description of the user's decision
     */
    private fun handlePlanCreate(
        args: Map<String, Any?>,
        sessionId: String,
        onEvent: ((Map<String, Any?>) -> Unit)?
    ): McpToolCallResponse {
        @Suppress("UNCHECKED_CAST")
        val tasks = args["tasks"] as? List<Map<String, Any?>>
            ?: return McpProxyService.errorResponse("'tasks' parameter is required (list of task objects)")

        val planId = UUID.randomUUID().toString()

        // Emit plan_ready to show the plan card in the UI
        onEvent?.invoke(mapOf(
            "type" to "plan_ready",
            "planId" to planId,
            "tasks" to tasks
        ))

        logger.info("Plan created: session={}, planId={}, taskCount={}", sessionId, planId, tasks.size)

        // Block until the user decides
        val decision = planConfirmService.waitForPlanApproval(sessionId, planId)

        val resultText = when (decision.action) {
            "APPROVED" -> "Plan approved by user. Proceeding with ${tasks.size} tasks."
            "MODIFIED" -> {
                val modCount = decision.modifiedTasks?.size ?: 0
                "Plan modified by user. Proceeding with $modCount tasks."
            }
            "CANCELLED" -> "Plan cancelled by user${if (decision.reason == "timeout") " (timeout)" else ""}. No changes will be made."
            else -> "Unexpected plan decision: ${decision.action}"
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = resultText)),
            isError = decision.action == "CANCELLED"
        )
    }

    /**
     * plan_update_task — fire-and-forget.
     * Emits a plan_task_update event to update task status in the UI.
     *
     * Args: { taskId: String, status: "pending"|"in_progress"|"done"|"failed"|"blocked", detail?: String }
     */
    private fun handlePlanUpdateTask(
        args: Map<String, Any?>,
        onEvent: ((Map<String, Any?>) -> Unit)?
    ): McpToolCallResponse {
        val taskId = args["taskId"] as? String
            ?: return McpProxyService.errorResponse("'taskId' parameter is required")
        val status = args["status"] as? String
            ?: return McpProxyService.errorResponse("'status' parameter is required")
        val detail = args["detail"] as? String

        onEvent?.invoke(mapOf(
            "type" to "plan_task_update",
            "taskId" to taskId,
            "status" to status,
            "detail" to (detail ?: "")
        ))

        logger.debug("Task updated: taskId={}, status={}", taskId, status)
        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = "Task $taskId updated to $status")),
            isError = false
        )
    }

    /**
     * plan_ask_user — blocking.
     * Emits a plan_ask event with questions, then waits for user to submit answers.
     *
     * Args: {
     *   questions: List<{
     *     type: "choice"|"text",
     *     question: String,
     *     options?: List<String>  // required when type == "choice"
     *   }>
     * }
     * Returns: JSON object of answers keyed by "q0", "q1", etc.
     */
    private fun handlePlanAskUser(
        args: Map<String, Any?>,
        sessionId: String,
        onEvent: ((Map<String, Any?>) -> Unit)?
    ): McpToolCallResponse {
        @Suppress("UNCHECKED_CAST")
        val questions = args["questions"] as? List<Map<String, Any?>>
            ?: return McpProxyService.errorResponse("'questions' parameter is required (list of question objects)")

        val askId = UUID.randomUUID().toString()

        // Emit plan_ask to show the question card in the UI
        onEvent?.invoke(mapOf(
            "type" to "plan_ask",
            "askId" to askId,
            "questions" to questions
        ))

        logger.info("Plan ask emitted: session={}, askId={}, questionCount={}", sessionId, askId, questions.size)

        // Block until the user submits answers
        val answers = planConfirmService.waitForUserAnswers(sessionId, askId)

        val answersJson = if (answers.isEmpty()) {
            "{}"
        } else {
            objectMapper.writeValueAsString(answers)
        }

        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = answersJson)),
            isError = false
        )
    }

    /**
     * plan_complete — fire-and-forget.
     * Emits a plan_summary event to render the final summary card in the UI.
     *
     * Args: { summary: String, suggestions?: List<String> }
     */
    private fun handlePlanComplete(
        args: Map<String, Any?>,
        onEvent: ((Map<String, Any?>) -> Unit)?
    ): McpToolCallResponse {
        val summary = args["summary"] as? String
            ?: return McpProxyService.errorResponse("'summary' parameter is required")

        @Suppress("UNCHECKED_CAST")
        val suggestions = args["suggestions"] as? List<String> ?: emptyList()

        onEvent?.invoke(mapOf(
            "type" to "plan_summary",
            "content" to summary,
            "suggestions" to suggestions
        ))

        logger.info("Plan complete summary emitted")
        return McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = "Summary delivered to user.")),
            isError = false
        )
    }
}
