package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.CompletionResult
import com.forge.adapter.model.ModelAdapter
import com.forge.adapter.model.StopReason
import com.forge.adapter.model.TokenUsage
import io.mockk.coEvery
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ModelGraderTest {

    private lateinit var modelAdapter: ModelAdapter
    private lateinit var objectMapper: ObjectMapper
    private lateinit var grader: ModelGrader

    private fun completionResult(content: String) = CompletionResult(
        content = content,
        model = "claude-sonnet-4-20250514",
        usage = TokenUsage(100, 50),
        stopReason = StopReason.END_TURN,
        latencyMs = 500
    )

    @BeforeEach
    fun setUp() {
        modelAdapter = mockk()
        objectMapper = ObjectMapper()
        grader = ModelGrader(modelAdapter, objectMapper)
    }

    @Test
    fun `grade returns parsed score from LLM response`() {
        coEvery { modelAdapter.complete(any(), any<CompletionOptions>()) } returns
            completionResult("""{"score": 0.85, "dimensions": {}, "rationale": "Good output"}""")

        val result = grader.grade("rubric", "criteria", "input", "output")
        assertThat(result.score).isEqualByComparingTo(BigDecimal("0.85"))
        assertThat(result.rationale).isEqualTo("Good output")
    }

    @Test
    fun `grade extracts JSON from code block`() {
        val response = "Here is my evaluation:\n```json\n{\"score\": 0.72, \"dimensions\": {}, \"rationale\": \"Decent\"}\n```"
        coEvery { modelAdapter.complete(any(), any<CompletionOptions>()) } returns completionResult(response)

        val result = grader.grade("rubric", "criteria", "input", "output")
        assertThat(result.score).isEqualByComparingTo(BigDecimal("0.72"))
    }

    @Test
    fun `grade uses regex fallback when JSON is invalid`() {
        val response = "I think the score is {\"score\": 0.65 but JSON is broken"
        coEvery { modelAdapter.complete(any(), any<CompletionOptions>()) } returns completionResult(response)

        val result = grader.grade("rubric", "criteria", "input", "output")
        assertThat(result.score).isEqualByComparingTo(BigDecimal("0.65"))
        assertThat(result.rationale).contains("regex fallback")
    }

    @Test
    fun `grade returns zero on adapter exception`() {
        coEvery { modelAdapter.complete(any(), any<CompletionOptions>()) } throws RuntimeException("API error")

        val result = grader.grade("rubric", "criteria", "input", "output")
        assertThat(result.score).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.rationale).contains("Grading failed")
    }

    @Test
    fun `score is clamped to 0-1 range`() {
        val response = """{"score": 1.5, "dimensions": {}, "rationale": "Over max"}"""
        coEvery { modelAdapter.complete(any(), any<CompletionOptions>()) } returns completionResult(response)

        val result = grader.grade("rubric", "criteria", "input", "output")
        assertThat(result.score).isLessThanOrEqualTo(BigDecimal.ONE)
    }

    @Test
    fun `negative score is clamped to zero`() {
        val response = """{"score": -0.5, "dimensions": {}, "rationale": "Under min"}"""
        coEvery { modelAdapter.complete(any(), any<CompletionOptions>()) } returns completionResult(response)

        val result = grader.grade("rubric", "criteria", "input", "output")
        assertThat(result.score).isGreaterThanOrEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `parseGradeResponse handles missing rationale`() {
        val result = grader.parseGradeResponse("""{"score": 0.5, "dimensions": {}}""")
        assertThat(result.score).isEqualByComparingTo(BigDecimal("0.50"))
        assertThat(result.rationale).isEmpty()
    }
}
