package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodeGraderTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `contains assertion passes when output includes expected text`() {
        val assertions = listOf(AssertionConfig("contains", "hello", "should contain hello"))
        val result = CodeGrader.grade(assertions, "say hello world", objectMapper)
        assertThat(result.passed).isTrue()
        assertThat(result.assertions[0].passed).isTrue()
    }

    @Test
    fun `contains assertion fails when output missing expected text`() {
        val assertions = listOf(AssertionConfig("contains", "goodbye", "should contain goodbye"))
        val result = CodeGrader.grade(assertions, "hello world", objectMapper)
        assertThat(result.passed).isFalse()
        assertThat(result.assertions[0].passed).isFalse()
    }

    @Test
    fun `contains is case insensitive`() {
        val assertions = listOf(AssertionConfig("contains", "HELLO", "case test"))
        val result = CodeGrader.grade(assertions, "say hello world", objectMapper)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `not_contains passes when text absent`() {
        val assertions = listOf(AssertionConfig("not_contains", "error", "no error"))
        val result = CodeGrader.grade(assertions, "all good", objectMapper)
        assertThat(result.passed).isTrue()
        assertThat(result.assertions[0].passed).isTrue()
    }

    @Test
    fun `not_contains fails when text present`() {
        val assertions = listOf(AssertionConfig("not_contains", "error", "no error"))
        val result = CodeGrader.grade(assertions, "got an error here", objectMapper)
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `regex assertion passes on match`() {
        val assertions = listOf(AssertionConfig("regex", "\\d{3}-\\d{4}", "phone pattern"))
        val result = CodeGrader.grade(assertions, "call 123-4567", objectMapper)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `regex assertion fails on no match`() {
        val assertions = listOf(AssertionConfig("matches_pattern", "^\\d+$", "only digits"))
        val result = CodeGrader.grade(assertions, "abc123", objectMapper)
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `invalid regex returns failed assertion`() {
        val assertions = listOf(AssertionConfig("regex", "[invalid(", "bad regex"))
        val result = CodeGrader.grade(assertions, "anything", objectMapper)
        assertThat(result.passed).isFalse()
        assertThat(result.assertions[0].actual).contains("Invalid regex")
    }

    @Test
    fun `json_schema passes for valid JSON`() {
        val assertions = listOf(AssertionConfig("json_schema", "", "valid json"))
        val result = CodeGrader.grade(assertions, """{"key": "value"}""", objectMapper)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `json_schema extracts JSON from markdown code block`() {
        val output = "Here is the result:\n```json\n{\"status\": \"ok\"}\n```\nDone."
        val assertions = listOf(AssertionConfig("json_schema", "", "json in code block"))
        val result = CodeGrader.grade(assertions, output, objectMapper)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `json_schema fails for invalid JSON`() {
        val assertions = listOf(AssertionConfig("json_schema", "", "valid json"))
        val result = CodeGrader.grade(assertions, "not json at all", objectMapper)
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `empty assertions returns passed with rate 1`() {
        val result = CodeGrader.grade(emptyList(), "any output", objectMapper)
        assertThat(result.passed).isTrue()
        assertThat(result.passRate).isEqualTo(1.0)
        assertThat(result.assertions).isEmpty()
    }

    @Test
    fun `passRate calculation with mixed results`() {
        val assertions = listOf(
            AssertionConfig("contains", "found", "first"),
            AssertionConfig("contains", "missing", "second"),
            AssertionConfig("contains", "also found", "third")
        )
        val result = CodeGrader.grade(assertions, "found this and also found that", objectMapper)
        assertThat(result.passRate).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(0.01))
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `detail is valid JSON`() {
        val assertions = listOf(AssertionConfig("contains", "test", "check"))
        val result = CodeGrader.grade(assertions, "test output", objectMapper)
        // detail should be parseable JSON
        val tree = objectMapper.readTree(result.detail)
        assertThat(tree.has("total")).isTrue()
        assertThat(tree.has("passed")).isTrue()
        assertThat(tree.get("total").asInt()).isEqualTo(1)
    }
}
