package com.forge.eval.engine.grader

import com.forge.adapter.model.CompletionResult
import com.forge.adapter.model.ModelAdapter
import com.forge.adapter.model.StopReason
import com.forge.eval.protocol.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class CompositeGraderTest {

    private val codeGrader = CodeBasedGrader()
    private val mockAdapter = mockk<ModelAdapter>()
    private val modelGrader = ModelBasedGrader(mockAdapter)
    private val trialId = UUID.randomUUID()

    private fun completionResult(content: String) = CompletionResult(
        content = content,
        model = "claude-sonnet-4-6",
        usage = com.forge.adapter.model.TokenUsage(inputTokens = 100, outputTokens = 50),
        stopReason = StopReason.END_TURN,
        latencyMs = 500
    )

    // ── CODE_BASED Routing ──────────────────────────────────────────

    @Nested
    inner class CodeBasedRouting {

        @Test
        fun `routes CODE_BASED to code grader`() = runTest {
            val composite = CompositeGrader(codeGrader)
            val configs = listOf(
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = listOf(
                        AssertionConfig(type = "contains", expected = "hello", description = "has hello")
                    )
                )
            )

            val grades = composite.gradeAll(trialId, "hello world", configs)

            assertThat(grades).hasSize(1)
            assertThat(grades[0].graderType).isEqualTo(GraderType.CODE_BASED)
            assertThat(grades[0].passed).isTrue()
        }

        @Test
        fun `multiple CODE_BASED configs produce multiple grades`() = runTest {
            val composite = CompositeGrader(codeGrader)
            val configs = listOf(
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = listOf(
                        AssertionConfig(type = "contains", expected = "hello", description = "has hello")
                    )
                ),
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = listOf(
                        AssertionConfig(type = "contains", expected = "world", description = "has world")
                    )
                )
            )

            val grades = composite.gradeAll(trialId, "hello world", configs)

            assertThat(grades).hasSize(2)
            assertThat(grades).allMatch { it.graderType == GraderType.CODE_BASED }
            assertThat(grades).allMatch { it.passed }
        }
    }

    // ── MODEL_BASED Routing ─────────────────────────────────────────

    @Nested
    inner class ModelBasedRouting {

        @Test
        fun `routes MODEL_BASED to model grader`() = runTest {
            val composite = CompositeGrader(codeGrader, modelGrader)

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """{"scores":{"quality":0.8},"explanation":"Good","confidence":0.9}"""
            )

            val configs = listOf(
                GraderConfig(
                    type = GraderType.MODEL_BASED,
                    rubric = listOf(
                        RubricCriterion(criterion = "quality", weight = 1.0, description = "Quality check")
                    )
                )
            )

            val grades = composite.gradeAll(trialId, "output", configs)

            assertThat(grades).hasSize(1)
            assertThat(grades[0].graderType).isEqualTo(GraderType.MODEL_BASED)
            assertThat(grades[0].rubricScores["quality"]).isEqualTo(0.8)
        }

        @Test
        fun `MODEL_BASED skipped when modelGrader is null`() = runTest {
            val composite = CompositeGrader(codeGrader, modelGrader = null)
            val configs = listOf(
                GraderConfig(
                    type = GraderType.MODEL_BASED,
                    rubric = listOf(
                        RubricCriterion(criterion = "quality", weight = 1.0, description = "Quality")
                    )
                )
            )

            val grades = composite.gradeAll(trialId, "output", configs)

            assertThat(grades).isEmpty()
        }
    }

    // ── Mixed Configs ───────────────────────────────────────────────

    @Nested
    inner class MixedConfigs {

        @Test
        fun `handles mixed CODE_BASED and MODEL_BASED configs`() = runTest {
            val composite = CompositeGrader(codeGrader, modelGrader)

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """{"scores":{"depth":0.7},"explanation":"Decent","confidence":0.8}"""
            )

            val configs = listOf(
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = listOf(
                        AssertionConfig(type = "contains", expected = "result", description = "has result")
                    )
                ),
                GraderConfig(
                    type = GraderType.MODEL_BASED,
                    rubric = listOf(
                        RubricCriterion(criterion = "depth", weight = 1.0, description = "Analysis depth")
                    )
                )
            )

            val grades = composite.gradeAll(trialId, "here is the result with analysis", configs)

            assertThat(grades).hasSize(2)
            assertThat(grades[0].graderType).isEqualTo(GraderType.CODE_BASED)
            assertThat(grades[0].passed).isTrue()
            assertThat(grades[1].graderType).isEqualTo(GraderType.MODEL_BASED)
            assertThat(grades[1].rubricScores["depth"]).isEqualTo(0.7)
        }

        @Test
        fun `mixed configs with MODEL_BASED skipped when no model grader`() = runTest {
            val composite = CompositeGrader(codeGrader, modelGrader = null)
            val configs = listOf(
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = listOf(
                        AssertionConfig(type = "contains", expected = "ok", description = "has ok")
                    )
                ),
                GraderConfig(
                    type = GraderType.MODEL_BASED,
                    rubric = listOf(
                        RubricCriterion(criterion = "quality", weight = 1.0, description = "Quality")
                    )
                )
            )

            val grades = composite.gradeAll(trialId, "ok done", configs)

            assertThat(grades).hasSize(1)
            assertThat(grades[0].graderType).isEqualTo(GraderType.CODE_BASED)
        }
    }

    // ── HUMAN Grader ────────────────────────────────────────────────

    @Nested
    inner class HumanGrader {

        @Test
        fun `HUMAN configs are skipped`() = runTest {
            val composite = CompositeGrader(codeGrader, modelGrader)
            val configs = listOf(
                GraderConfig(type = GraderType.HUMAN),
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = listOf(
                        AssertionConfig(type = "contains", expected = "yes", description = "has yes")
                    )
                )
            )

            val grades = composite.gradeAll(trialId, "yes", configs)

            assertThat(grades).hasSize(1)
            assertThat(grades[0].graderType).isEqualTo(GraderType.CODE_BASED)
        }
    }

    // ── Empty Configs ───────────────────────────────────────────────

    @Nested
    inner class EmptyConfigs {

        @Test
        fun `empty config list returns empty grades`() = runTest {
            val composite = CompositeGrader(codeGrader, modelGrader)

            val grades = composite.gradeAll(trialId, "output", emptyList())

            assertThat(grades).isEmpty()
        }
    }
}
