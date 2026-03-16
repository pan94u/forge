package com.forge.eval.engine.grader

import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.CompletionResult
import com.forge.adapter.model.ModelAdapter
import com.forge.adapter.model.StopReason
import com.forge.eval.protocol.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ModelBasedGraderTest {

    private val mockAdapter = mockk<ModelAdapter>()
    private val grader = ModelBasedGrader(mockAdapter)
    private val trialId = UUID.randomUUID()

    private fun completionResult(content: String) = CompletionResult(
        content = content,
        model = "claude-sonnet-4-6",
        usage = com.forge.adapter.model.TokenUsage(inputTokens = 100, outputTokens = 50),
        stopReason = StopReason.END_TURN,
        latencyMs = 500
    )

    private fun rubric(vararg criteria: Pair<String, Double>): List<RubricCriterion> =
        criteria.map { (name, weight) ->
            RubricCriterion(
                criterion = name,
                weight = weight,
                description = "Evaluate $name",
                scale = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
            )
        }

    // ── Basic Rubric Grading ────────────────────────────────────────

    @Nested
    inner class BasicRubricGrading {

        @Test
        fun `grades with valid JSON response`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                model = "claude-sonnet-4-6",
                rubric = rubric("correctness" to 1.0, "completeness" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """{"scores":{"correctness":0.9,"completeness":0.7},"explanation":"Good work","confidence":0.85}"""
            )

            val grade = grader.grade(trialId, "agent output here", config)

            assertThat(grade.graderType).isEqualTo(GraderType.MODEL_BASED)
            assertThat(grade.rubricScores["correctness"]).isEqualTo(0.9)
            assertThat(grade.rubricScores["completeness"]).isEqualTo(0.7)
            assertThat(grade.score).isCloseTo(0.8, Offset.offset(0.001))
            assertThat(grade.explanation).isEqualTo("Good work")
            assertThat(grade.confidence).isEqualTo(0.85)
            assertThat(grade.passed).isTrue()
        }

        @Test
        fun `grades with JSON wrapped in markdown code block`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("quality" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """```json
{"scores":{"quality":0.6},"explanation":"Average","confidence":0.7}
```"""
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.rubricScores["quality"]).isEqualTo(0.6)
            assertThat(grade.score).isCloseTo(0.6, Offset.offset(0.001))
        }

        @Test
        fun `grades with JSON embedded in surrounding text`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("accuracy" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """Here is my evaluation:
{"scores":{"accuracy":0.8},"explanation":"Mostly correct","confidence":0.9}
That's my assessment."""
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.rubricScores["accuracy"]).isEqualTo(0.8)
        }
    }

    // ── Malformed Response Handling ──────────────────────────────────

    @Nested
    inner class MalformedResponseHandling {

        @Test
        fun `falls back gracefully on completely invalid response`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("correctness" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                "I'm sorry, I can't evaluate this output properly."
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.score).isEqualTo(0.5)
            assertThat(grade.confidence).isEqualTo(0.2)
            assertThat(grade.passed).isFalse()
            assertThat(grade.explanation).contains("Fallback")
            assertThat(grade.rubricScores["correctness"]).isEqualTo(0.5)
        }

        @Test
        fun `falls back on JSON missing scores field`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("quality" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """{"explanation":"good","confidence":0.8}"""
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.score).isEqualTo(0.5)
            assertThat(grade.confidence).isEqualTo(0.2)
            assertThat(grade.explanation).contains("Fallback")
        }

        @Test
        fun `falls back when model adapter throws exception`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("correctness" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } throws RuntimeException("API timeout")

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.score).isEqualTo(0.5)
            assertThat(grade.confidence).isEqualTo(0.2)
            assertThat(grade.passed).isFalse()
            assertThat(grade.explanation).contains("Model call failed")
        }

        @Test
        fun `handles missing criterion in judge response with fallback score`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("correctness" to 1.0, "style" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """{"scores":{"correctness":0.9},"explanation":"Only scored one","confidence":0.7}"""
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.rubricScores["correctness"]).isEqualTo(0.9)
            assertThat(grade.rubricScores["style"]).isEqualTo(0.5) // fallback
            assertThat(grade.score).isCloseTo(0.7, Offset.offset(0.001)) // (0.9 + 0.5) / 2
        }
    }

    // ── Weighted Score Calculation ───────────────────────────────────

    @Nested
    inner class WeightedScoreCalculation {

        @Test
        fun `equal weights calculate simple average`() {
            val scores = mapOf("a" to 0.8, "b" to 0.6)
            val rubric = rubric("a" to 1.0, "b" to 1.0)

            val result = grader.calculateWeightedScore(scores, rubric)

            assertThat(result).isCloseTo(0.7, Offset.offset(0.001))
        }

        @Test
        fun `different weights calculate weighted average`() {
            val scores = mapOf("important" to 0.9, "minor" to 0.3)
            val rubric = rubric("important" to 3.0, "minor" to 1.0)

            val result = grader.calculateWeightedScore(scores, rubric)

            // (0.9 * 3 + 0.3 * 1) / (3 + 1) = 3.0 / 4.0 = 0.75
            assertThat(result).isCloseTo(0.75, Offset.offset(0.001))
        }

        @Test
        fun `single criterion returns its score`() {
            val scores = mapOf("only" to 0.65)
            val rubric = rubric("only" to 2.0)

            val result = grader.calculateWeightedScore(scores, rubric)

            assertThat(result).isCloseTo(0.65, Offset.offset(0.001))
        }
    }

    // ── Anti-Self-Eval ──────────────────────────────────────────────

    @Nested
    inner class AntiSelfEval {

        @Test
        fun `selectJudgeModel uses config model when provided`() {
            val result = grader.selectJudgeModel("claude-opus-4-20250514", "claude-sonnet-4-6")
            assertThat(result).isEqualTo("claude-sonnet-4-6")
        }

        @Test
        fun `selectJudgeModel falls back to default when config is null`() {
            val result = grader.selectJudgeModel("claude-opus-4-20250514", null)
            assertThat(result).isEqualTo(ModelBasedGrader.DEFAULT_JUDGE_MODEL)
        }

        @Test
        fun `selectJudgeModel warns but still returns when same as evaluated model`() {
            // 不会抛出异常，但会记录警告
            val result = grader.selectJudgeModel("claude-sonnet-4-6", "claude-sonnet-4-6")
            assertThat(result).isEqualTo("claude-sonnet-4-6")
        }
    }

    // ── Empty Rubric Handling ───────────────────────────────────────

    @Nested
    inner class EmptyRubricHandling {

        @Test
        fun `empty rubric returns auto-pass`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = emptyList()
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.score).isEqualTo(1.0)
            assertThat(grade.passed).isTrue()
            assertThat(grade.rubricScores).isEmpty()
            assertThat(grade.explanation).contains("No rubric criteria")
            assertThat(grade.confidence).isEqualTo(1.0)
        }
    }

    // ── Score Clamping ──────────────────────────────────────────────

    @Nested
    inner class ScoreClamping {

        @Test
        fun `scores above 1_0 are clamped to 1_0`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("x" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """{"scores":{"x":1.5},"explanation":"Over","confidence":0.9}"""
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.rubricScores["x"]).isEqualTo(1.0)
        }

        @Test
        fun `scores below 0_0 are clamped to 0_0`() = runTest {
            val config = GraderConfig(
                type = GraderType.MODEL_BASED,
                rubric = rubric("x" to 1.0)
            )

            coEvery { mockAdapter.complete(any(), any()) } returns completionResult(
                """{"scores":{"x":-0.3},"explanation":"Negative","confidence":0.9}"""
            )

            val grade = grader.grade(trialId, "output", config)

            assertThat(grade.rubricScores["x"]).isEqualTo(0.0)
        }
    }

    // ── JSON Extraction ─────────────────────────────────────────────

    @Nested
    inner class JsonExtraction {

        @Test
        fun `extracts pure JSON`() {
            val json = """{"key":"value"}"""
            assertThat(grader.extractJson(json)).isEqualTo(json)
        }

        @Test
        fun `extracts from markdown code block`() {
            val text = """```json
{"key":"value"}
```"""
            assertThat(grader.extractJson(text)).isEqualTo("""{"key":"value"}""")
        }

        @Test
        fun `extracts from surrounding text`() {
            val text = """Here is my answer: {"key":"value"} end."""
            assertThat(grader.extractJson(text)).isEqualTo("""{"key":"value"}""")
        }

        @Test
        fun `returns null for no JSON`() {
            assertThat(grader.extractJson("no json here")).isNull()
        }
    }

    // ── Prompt Building ─────────────────────────────────────────────

    @Nested
    inner class PromptBuilding {

        @Test
        fun `buildJudgePrompt includes output and rubric`() {
            val rubric = rubric("correctness" to 1.0, "style" to 0.5)
            val prompt = grader.buildJudgePrompt("Hello world", rubric, null)

            assertThat(prompt).contains("Hello world")
            assertThat(prompt).contains("correctness")
            assertThat(prompt).contains("style")
            assertThat(prompt).contains("weight=1.0")
            assertThat(prompt).contains("weight=0.5")
        }

        @Test
        fun `buildJudgePrompt includes transcript when available`() {
            val rubric = rubric("quality" to 1.0)
            val transcript = EvalTranscript(
                turns = listOf(
                    TranscriptTurn(role = "user", content = "Do the task"),
                    TranscriptTurn(role = "assistant", content = "Done")
                )
            )

            val prompt = grader.buildJudgePrompt("output", rubric, transcript)

            assertThat(prompt).contains("Interaction Transcript")
            assertThat(prompt).contains("[user]: Do the task")
            assertThat(prompt).contains("[assistant]: Done")
        }

        @Test
        fun `buildJudgePrompt omits transcript section when null`() {
            val rubric = rubric("quality" to 1.0)
            val prompt = grader.buildJudgePrompt("output", rubric, null)

            assertThat(prompt).doesNotContain("Interaction Transcript")
        }

        @Test
        fun `buildSystemPrompt instructs JSON-only output`() {
            val systemPrompt = grader.buildSystemPrompt()

            assertThat(systemPrompt).contains("JSON")
            assertThat(systemPrompt).contains("scores")
            assertThat(systemPrompt).contains("explanation")
            assertThat(systemPrompt).contains("confidence")
        }
    }
}
