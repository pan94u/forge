package com.forge.webide.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

/**
 * Tests for PlanToolHandler.
 *
 * Covers all 4 plan tools:
 *  - plan_create  (blocking, emits plan_ready, returns approved/cancelled/modified text)
 *  - plan_update_task  (fire-and-forget, emits plan_task_update)
 *  - plan_ask_user  (blocking, emits plan_ask, returns answers JSON)
 *  - plan_complete  (fire-and-forget, emits plan_summary)
 */
class PlanToolHandlerTest {

    private val planConfirmService = mockk<PlanConfirmService>()
    private val objectMapper = ObjectMapper()
    private lateinit var handler: PlanToolHandler

    // Captured events emitted by the handler
    private val capturedEvents = mutableListOf<Map<String, Any?>>()
    private val onEvent: (Map<String, Any?>) -> Unit = { capturedEvents.add(it) }

    @BeforeEach
    fun setup() {
        handler = PlanToolHandler(planConfirmService, objectMapper)
        capturedEvents.clear()
    }

    // ---- plan_create ----

    @Nested
    inner class PlanCreate {

        private val sampleTasks = listOf(
            mapOf(
                "id" to "task-001",
                "title" to "创建 UserService",
                "files" to listOf("src/UserService.kt"),
                "successCriteria" to "workspace_compile 返回零错误",
                "estimatedLines" to 120
            )
        )

        @Test
        fun `plan_create emits plan_ready event with planId and tasks`() {
            every { planConfirmService.waitForPlanApproval(any(), any()) } returns
                PlanDecision(action = "APPROVED")

            handler.handle("plan_create", mapOf("tasks" to sampleTasks), "sess-1", onEvent)

            val planReadyEvents = capturedEvents.filter { it["type"] == "plan_ready" }
            assertThat(planReadyEvents).hasSize(1)
            assertThat(planReadyEvents[0]["planId"]).isNotNull()
            assertThat(planReadyEvents[0]["tasks"]).isEqualTo(sampleTasks)
        }

        @Test
        fun `plan_create returns approval message when approved`() {
            every { planConfirmService.waitForPlanApproval(any(), any()) } returns
                PlanDecision(action = "APPROVED")

            val result = handler.handle("plan_create", mapOf("tasks" to sampleTasks), "sess-1", onEvent)

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).contains("approved")
            assertThat(result.content[0].text).contains("1 tasks")
        }

        @Test
        fun `plan_create returns error response when cancelled`() {
            every { planConfirmService.waitForPlanApproval(any(), any()) } returns
                PlanDecision(action = "CANCELLED")

            val result = handler.handle("plan_create", mapOf("tasks" to sampleTasks), "sess-1", onEvent)

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("cancelled")
        }

        @Test
        fun `plan_create returns timeout message when cancelled due to timeout`() {
            every { planConfirmService.waitForPlanApproval(any(), any()) } returns
                PlanDecision(action = "CANCELLED", reason = "timeout")

            val result = handler.handle("plan_create", mapOf("tasks" to sampleTasks), "sess-1", onEvent)

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).containsIgnoringCase("timeout")
        }

        @Test
        fun `plan_create returns modified message with task count when modified`() {
            val modifiedTasks = listOf(
                mapOf("id" to "task-A", "title" to "New Task"),
                mapOf("id" to "task-B", "title" to "Another Task")
            )
            every { planConfirmService.waitForPlanApproval(any(), any()) } returns
                PlanDecision(action = "MODIFIED", modifiedTasks = modifiedTasks)

            val result = handler.handle("plan_create", mapOf("tasks" to sampleTasks), "sess-1", onEvent)

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).contains("modified")
            assertThat(result.content[0].text).contains("2 tasks")
        }

        @Test
        fun `plan_create returns error when tasks param is missing`() {
            val result = handler.handle("plan_create", emptyMap(), "sess-1", onEvent)

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("tasks")
            assertThat(capturedEvents).isEmpty()
        }

        @Test
        fun `plan_create works with null onEvent`() {
            every { planConfirmService.waitForPlanApproval(any(), any()) } returns
                PlanDecision(action = "APPROVED")

            val result = handler.handle("plan_create", mapOf("tasks" to sampleTasks), "sess-1", null)

            assertThat(result.isError).isFalse()
        }

        @Test
        fun `plan_create generates unique planId per invocation`() {
            val planIds = mutableListOf<String>()
            every { planConfirmService.waitForPlanApproval(any(), capture(slot())) } answers {
                PlanDecision(action = "APPROVED")
            }
            every { planConfirmService.waitForPlanApproval(any(), any()) } returns
                PlanDecision(action = "APPROVED")

            repeat(3) {
                capturedEvents.clear()
                handler.handle("plan_create", mapOf("tasks" to sampleTasks), "sess-1", onEvent)
                val planId = capturedEvents.first { it["type"] == "plan_ready" }["planId"] as String
                planIds.add(planId)
            }

            assertThat(planIds.distinct()).hasSize(3)
        }
    }

    // ---- plan_update_task ----

    @Nested
    inner class PlanUpdateTask {

        @Test
        fun `plan_update_task emits plan_task_update event with correct fields`() {
            val result = handler.handle(
                "plan_update_task",
                mapOf("taskId" to "task-001", "status" to "in_progress"),
                "sess-1", onEvent
            )

            assertThat(result.isError).isFalse()
            assertThat(capturedEvents).hasSize(1)
            val event = capturedEvents[0]
            assertThat(event["type"]).isEqualTo("plan_task_update")
            assertThat(event["taskId"]).isEqualTo("task-001")
            assertThat(event["status"]).isEqualTo("in_progress")
        }

        @Test
        fun `plan_update_task includes optional detail field`() {
            handler.handle(
                "plan_update_task",
                mapOf("taskId" to "task-002", "status" to "failed", "detail" to "编译错误: 找不到符号"),
                "sess-1", onEvent
            )

            val event = capturedEvents[0]
            assertThat(event["detail"]).isEqualTo("编译错误: 找不到符号")
        }

        @Test
        fun `plan_update_task includes empty detail when not provided`() {
            handler.handle(
                "plan_update_task",
                mapOf("taskId" to "task-003", "status" to "done"),
                "sess-1", onEvent
            )

            val event = capturedEvents[0]
            assertThat(event["detail"]).isEqualTo("")
        }

        @Test
        fun `plan_update_task returns error when taskId is missing`() {
            val result = handler.handle(
                "plan_update_task",
                mapOf("status" to "done"),
                "sess-1", onEvent
            )

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("taskId")
            assertThat(capturedEvents).isEmpty()
        }

        @Test
        fun `plan_update_task returns error when status is missing`() {
            val result = handler.handle(
                "plan_update_task",
                mapOf("taskId" to "task-001"),
                "sess-1", onEvent
            )

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("status")
        }

        @Test
        fun `plan_update_task works with null onEvent`() {
            val result = handler.handle(
                "plan_update_task",
                mapOf("taskId" to "task-001", "status" to "done"),
                "sess-1", null
            )

            assertThat(result.isError).isFalse()
        }

        @Test
        fun `plan_update_task return text confirms update`() {
            val result = handler.handle(
                "plan_update_task",
                mapOf("taskId" to "task-007", "status" to "blocked"),
                "sess-1", onEvent
            )

            assertThat(result.content[0].text).contains("task-007")
            assertThat(result.content[0].text).contains("blocked")
        }
    }

    // ---- plan_ask_user ----

    @Nested
    inner class PlanAskUser {

        private val sampleQuestions = listOf(
            mapOf("type" to "choice", "question" to "修改范围？", "options" to listOf("前端", "后端", "全栈")),
            mapOf("type" to "text", "question" to "有其他约束吗？")
        )

        @Test
        fun `plan_ask_user emits plan_ask event with askId and questions`() {
            every { planConfirmService.waitForUserAnswers(any(), any()) } returns
                mapOf("q0" to "后端", "q1" to "无")

            handler.handle("plan_ask_user", mapOf("questions" to sampleQuestions), "sess-1", onEvent)

            val askEvents = capturedEvents.filter { it["type"] == "plan_ask" }
            assertThat(askEvents).hasSize(1)
            assertThat(askEvents[0]["askId"]).isNotNull()
            assertThat(askEvents[0]["questions"]).isEqualTo(sampleQuestions)
        }

        @Test
        fun `plan_ask_user returns answers as JSON string`() {
            every { planConfirmService.waitForUserAnswers(any(), any()) } returns
                mapOf("q0" to "前端", "q1" to "需要支持移动端")

            val result = handler.handle("plan_ask_user", mapOf("questions" to sampleQuestions), "sess-1", onEvent)

            assertThat(result.isError).isFalse()
            val json = result.content[0].text!!
            val parsed = objectMapper.readValue(json, Map::class.java)
            assertThat(parsed["q0"]).isEqualTo("前端")
            assertThat(parsed["q1"]).isEqualTo("需要支持移动端")
        }

        @Test
        fun `plan_ask_user returns empty JSON when timeout returns empty map`() {
            every { planConfirmService.waitForUserAnswers(any(), any()) } returns emptyMap()

            val result = handler.handle("plan_ask_user", mapOf("questions" to sampleQuestions), "sess-1", onEvent)

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).isEqualTo("{}")
        }

        @Test
        fun `plan_ask_user returns error when questions param is missing`() {
            val result = handler.handle("plan_ask_user", emptyMap(), "sess-1", onEvent)

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("questions")
            assertThat(capturedEvents).isEmpty()
        }

        @Test
        fun `plan_ask_user generates unique askId per invocation`() {
            val askIds = mutableListOf<String>()
            every { planConfirmService.waitForUserAnswers(any(), any()) } returns emptyMap()

            repeat(3) {
                capturedEvents.clear()
                handler.handle("plan_ask_user", mapOf("questions" to sampleQuestions), "sess-1", onEvent)
                val askId = capturedEvents.first { it["type"] == "plan_ask" }["askId"] as String
                askIds.add(askId)
            }

            assertThat(askIds.distinct()).hasSize(3)
        }
    }

    // ---- plan_complete ----

    @Nested
    inner class PlanComplete {

        @Test
        fun `plan_complete emits plan_summary event with content and suggestions`() {
            val summary = "## 做了什么\n- ✅ 创建 UserService\n## 遇到什么\n- 无\n## 建议什么\n- P1: 补充测试"
            val suggestions = listOf("P1: 补充测试", "P2: 提取公共接口")

            handler.handle(
                "plan_complete",
                mapOf("summary" to summary, "suggestions" to suggestions),
                "sess-1", onEvent
            )

            val summaryEvents = capturedEvents.filter { it["type"] == "plan_summary" }
            assertThat(summaryEvents).hasSize(1)
            assertThat(summaryEvents[0]["content"]).isEqualTo(summary)
            assertThat(summaryEvents[0]["suggestions"]).isEqualTo(suggestions)
        }

        @Test
        fun `plan_complete works without suggestions field`() {
            val result = handler.handle(
                "plan_complete",
                mapOf("summary" to "任务完成"),
                "sess-1", onEvent
            )

            assertThat(result.isError).isFalse()
            val event = capturedEvents.first { it["type"] == "plan_summary" }
            @Suppress("UNCHECKED_CAST")
            assertThat(event["suggestions"] as List<*>).isEmpty()
        }

        @Test
        fun `plan_complete returns error when summary is missing`() {
            val result = handler.handle("plan_complete", emptyMap(), "sess-1", onEvent)

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("summary")
            assertThat(capturedEvents).isEmpty()
        }

        @Test
        fun `plan_complete return text confirms delivery`() {
            val result = handler.handle(
                "plan_complete",
                mapOf("summary" to "Done"),
                "sess-1", onEvent
            )

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).contains("Summary delivered")
        }

        @Test
        fun `plan_complete works with null onEvent`() {
            val result = handler.handle(
                "plan_complete",
                mapOf("summary" to "Done"),
                "sess-1", null
            )

            assertThat(result.isError).isFalse()
        }
    }

    // ---- unknown tool ----

    @Test
    fun `unknown plan tool returns error`() {
        val result = handler.handle("plan_unknown", emptyMap(), "sess-1", onEvent)

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("Unknown plan tool")
    }
}
