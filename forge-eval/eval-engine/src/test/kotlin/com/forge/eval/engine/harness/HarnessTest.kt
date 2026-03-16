package com.forge.eval.engine.harness

import com.forge.eval.engine.TrialOutput
import com.forge.eval.protocol.Difficulty
import com.forge.eval.protocol.EvalTask
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class HarnessTest {

    private val testTask = EvalTask(
        id = UUID.randomUUID(),
        suiteId = UUID.randomUUID(),
        name = "Test Task",
        prompt = "Write hello world"
    )

    @Nested
    inner class PassthroughHarnessTests {

        @Test
        fun `should return static output`() = runBlocking {
            val harness = PassthroughHarness(output = "hello world")
            val result = harness.execute(testTask)

            assertThat(result.output).isEqualTo("hello world")
            assertThat(harness.type).isEqualTo(HarnessType.PASSTHROUGH)
        }

        @Test
        fun `should use output provider when available`() = runBlocking {
            val harness = PassthroughHarness(
                outputProvider = { task -> TrialOutput(output = "Response for: ${task.name}") }
            )
            val result = harness.execute(testTask)

            assertThat(result.output).isEqualTo("Response for: Test Task")
        }

        @Test
        fun `should prefer output provider over static output`() = runBlocking {
            val harness = PassthroughHarness(
                output = "static",
                outputProvider = { TrialOutput(output = "dynamic") }
            )
            val result = harness.execute(testTask)

            assertThat(result.output).isEqualTo("dynamic")
        }
    }

    @Nested
    inner class MockLlmHarnessTests {

        @Test
        fun `should return response by task id`() = runBlocking {
            val harness = MockLlmHarness(
                responses = mapOf(testTask.id.toString() to "mocked by id")
            )
            val result = harness.execute(testTask)

            assertThat(result.output).isEqualTo("mocked by id")
            assertThat(harness.type).isEqualTo(HarnessType.MOCK_LLM)
        }

        @Test
        fun `should return response by task name`() = runBlocking {
            val harness = MockLlmHarness(
                responses = mapOf("Test Task" to "mocked by name")
            )
            val result = harness.execute(testTask)

            assertThat(result.output).isEqualTo("mocked by name")
        }

        @Test
        fun `should return default response when no match`() = runBlocking {
            val harness = MockLlmHarness(defaultResponse = "fallback")
            val result = harness.execute(testTask)

            assertThat(result.output).isEqualTo("fallback")
        }

        @Test
        fun `should prefer id match over name match`() = runBlocking {
            val harness = MockLlmHarness(
                responses = mapOf(
                    testTask.id.toString() to "by-id",
                    testTask.name to "by-name"
                )
            )
            val result = harness.execute(testTask)

            assertThat(result.output).isEqualTo("by-id")
        }
    }

    @Nested
    inner class DockerHarnessTests {

        @Test
        fun `should return stub output (not yet implemented)`() = runBlocking {
            val harness = DockerHarness()
            val result = harness.execute(testTask)

            assertThat(result.error).isNotNull()
            assertThat(harness.type).isEqualTo(HarnessType.DOCKER)
        }
    }
}
