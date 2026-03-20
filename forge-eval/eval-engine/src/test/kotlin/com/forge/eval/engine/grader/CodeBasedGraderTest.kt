package com.forge.eval.engine.grader

import com.forge.eval.protocol.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class CodeBasedGraderTest {

    private val grader = CodeBasedGrader()
    private val trialId = UUID.randomUUID()

    // ── Contains ────────────────────────────────────────────────────

    @Nested
    inner class ContainsAssertions {

        @Test
        fun `contains - passes when text is present`() {
            val result = grader.grade(trialId, "Hello World", listOf(
                AssertionConfig(type = "contains", expected = "World", description = "has World")
            ))
            assertThat(result.passed).isTrue()
            assertThat(result.score).isEqualTo(1.0)
        }

        @Test
        fun `contains - fails when text is absent`() {
            val result = grader.grade(trialId, "Hello World", listOf(
                AssertionConfig(type = "contains", expected = "Foo", description = "has Foo")
            ))
            assertThat(result.passed).isFalse()
            assertThat(result.score).isEqualTo(0.0)
        }

        @Test
        fun `contains - case insensitive`() {
            val result = grader.grade(trialId, "Hello World", listOf(
                AssertionConfig(type = "contains", expected = "hello", description = "ci", caseSensitive = false)
            ))
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `contains - case sensitive by default`() {
            val result = grader.grade(trialId, "Hello World", listOf(
                AssertionConfig(type = "contains", expected = "hello", description = "cs")
            ))
            assertThat(result.passed).isFalse()
        }
    }

    // ── Not Contains ────────────────────────────────────────────────

    @Nested
    inner class NotContainsAssertions {

        @Test
        fun `not_contains - passes when text is absent`() {
            val result = grader.grade(trialId, "Hello World", listOf(
                AssertionConfig(type = "not_contains", expected = "Foo", description = "no Foo")
            ))
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `not_contains - fails when text is present`() {
            val result = grader.grade(trialId, "Hello World", listOf(
                AssertionConfig(type = "not_contains", expected = "World", description = "no World")
            ))
            assertThat(result.passed).isFalse()
        }
    }

    // ── Matches Pattern ─────────────────────────────────────────────

    @Nested
    inner class MatchesPatternAssertions {

        @Test
        fun `matches_pattern - regex match`() {
            val result = grader.grade(trialId, "Error code: 404", listOf(
                AssertionConfig(type = "matches_pattern", expected = "\\d{3}", description = "has 3-digit code")
            ))
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `matches_pattern - no match`() {
            val result = grader.grade(trialId, "No numbers here", listOf(
                AssertionConfig(type = "matches_pattern", expected = "\\d{3}", description = "has 3-digit code")
            ))
            assertThat(result.passed).isFalse()
        }
    }

    // ── JSON Schema ─────────────────────────────────────────────────

    @Nested
    inner class JsonSchemaAssertions {

        @Test
        fun `json_schema - valid JSON passes`() {
            val result = grader.grade(trialId, """{"name":"test","value":42}""", listOf(
                AssertionConfig(type = "json_schema", expected = "object", description = "is json")
            ))
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `json_schema - invalid JSON fails`() {
            val result = grader.grade(trialId, "not json at all", listOf(
                AssertionConfig(type = "json_schema", expected = "object", description = "is json")
            ))
            assertThat(result.passed).isFalse()
        }

        @Test
        fun `json_schema - JSON in markdown code block`() {
            val output = """
                Here is the result:
                ```json
                {"status": "ok"}
                ```
            """.trimIndent()
            val result = grader.grade(trialId, output, listOf(
                AssertionConfig(type = "json_schema", expected = "object", description = "is json")
            ))
            assertThat(result.passed).isTrue()
        }
    }

    // ── JSON Path ───────────────────────────────────────────────────

    @Nested
    inner class JsonPathAssertions {

        @Test
        fun `json_path - extracts and matches value`() {
            val json = """{"user":{"name":"Alice","age":30}}"""
            val result = grader.grade(trialId, json, listOf(
                AssertionConfig(type = "json_path", expected = "$.user.name=Alice", description = "user name")
            ))
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `json_path - existence check without value`() {
            val json = """{"user":{"name":"Alice"}}"""
            val result = grader.grade(trialId, json, listOf(
                AssertionConfig(type = "json_path", expected = "$.user.name", description = "has user name")
            ))
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `json_path - missing field fails`() {
            val json = """{"user":{"name":"Alice"}}"""
            val result = grader.grade(trialId, json, listOf(
                AssertionConfig(type = "json_path", expected = "$.user.email", description = "has email")
            ))
            assertThat(result.passed).isFalse()
        }

        @Test
        fun `json_path - wrong value fails`() {
            val json = """{"user":{"name":"Alice"}}"""
            val result = grader.grade(trialId, json, listOf(
                AssertionConfig(type = "json_path", expected = "$.user.name=Bob", description = "user is Bob")
            ))
            assertThat(result.passed).isFalse()
        }
    }

    // ── Tool Used ───────────────────────────────────────────────────

    @Nested
    inner class ToolUsedAssertions {

        private val transcript = EvalTranscript(
            toolCallSummary = listOf(
                ToolCallInfo(toolName = "workspace_write_file"),
                ToolCallInfo(toolName = "search_knowledge"),
                ToolCallInfo(toolName = "workspace_write_file")
            )
        )

        @Test
        fun `tool_used - passes when tool was called`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_used", expected = "workspace_write_file", description = "wrote file")
            ), transcript)
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `tool_used - fails when tool was not called`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_used", expected = "workspace_compile", description = "compiled")
            ), transcript)
            assertThat(result.passed).isFalse()
        }

        @Test
        fun `tool_not_used - passes when tool was not called`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_not_used", expected = "workspace_compile", description = "no compile")
            ), transcript)
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `tool_not_used - fails when tool was called`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_not_used", expected = "search_knowledge", description = "no search")
            ), transcript)
            assertThat(result.passed).isFalse()
        }
    }

    // ── Tool Call Count ─────────────────────────────────────────────

    @Nested
    inner class ToolCallCountAssertions {

        private val transcript = EvalTranscript(
            toolCallSummary = listOf(
                ToolCallInfo(toolName = "write"),
                ToolCallInfo(toolName = "write"),
                ToolCallInfo(toolName = "write"),
                ToolCallInfo(toolName = "read")
            )
        )

        @Test
        fun `exact count`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_call_count", expected = "write:3", description = "3 writes")
            ), transcript)
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `gte count`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_call_count", expected = "write:>=2", description = ">=2 writes")
            ), transcript)
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `range count`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_call_count", expected = "write:1-5", description = "1-5 writes")
            ), transcript)
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `count mismatch fails`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_call_count", expected = "write:5", description = "5 writes")
            ), transcript)
            assertThat(result.passed).isFalse()
        }
    }

    // ── Tool Call Order ─────────────────────────────────────────────

    @Nested
    inner class ToolCallOrderAssertions {

        private val transcript = EvalTranscript(
            toolCallSummary = listOf(
                ToolCallInfo(toolName = "read"),
                ToolCallInfo(toolName = "analyze"),
                ToolCallInfo(toolName = "write"),
                ToolCallInfo(toolName = "test")
            )
        )

        @Test
        fun `correct order passes`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_call_order", expected = "read,write,test", description = "order ok")
            ), transcript)
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `wrong order fails`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_call_order", expected = "write,read", description = "write before read")
            ), transcript)
            assertThat(result.passed).isFalse()
        }

        @Test
        fun `missing tool in order fails`() {
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "tool_call_order", expected = "read,deploy", description = "has deploy")
            ), transcript)
            assertThat(result.passed).isFalse()
        }
    }

    // ── Turn Count Max ──────────────────────────────────────────────

    @Nested
    inner class TurnCountMaxAssertions {

        @Test
        fun `within limit passes`() {
            val transcript = EvalTranscript(
                turns = listOf(
                    TranscriptTurn(role = "user", content = "hello"),
                    TranscriptTurn(role = "assistant", content = "hi"),
                    TranscriptTurn(role = "user", content = "do X"),
                    TranscriptTurn(role = "assistant", content = "done")
                )
            )
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "turn_count_max", expected = "10", description = "within 10")
            ), transcript)
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `exceeds limit fails`() {
            val transcript = EvalTranscript(
                turns = (1..20).map { TranscriptTurn(role = "user", content = "msg $it") }
            )
            val result = grader.grade(trialId, "", listOf(
                AssertionConfig(type = "turn_count_max", expected = "5", description = "within 5")
            ), transcript)
            assertThat(result.passed).isFalse()
        }
    }

    // ── Structure ───────────────────────────────────────────────────

    @Nested
    inner class StructureAssertions {

        @Test
        fun `markdown_headers - passes with headers`() {
            val output = "# Title\n\nContent\n\n## Section\n\nMore"
            val result = grader.grade(trialId, output, listOf(
                AssertionConfig(type = "structure", expected = "markdown_headers", description = "has headers")
            ))
            assertThat(result.passed).isTrue()
        }

        @Test
        fun `markdown_headers - fails without headers`() {
            val output = "Just plain text without any headers"
            val result = grader.grade(trialId, output, listOf(
                AssertionConfig(type = "structure", expected = "markdown_headers", description = "has headers")
            ))
            assertThat(result.passed).isFalse()
        }
    }

    // ── Composite Scoring ───────────────────────────────────────────

    @Nested
    inner class CompositeScoring {

        @Test
        fun `partial pass calculates correct score`() {
            val result = grader.grade(trialId, "Hello World foo", listOf(
                AssertionConfig(type = "contains", expected = "Hello", description = "has Hello"),
                AssertionConfig(type = "contains", expected = "World", description = "has World"),
                AssertionConfig(type = "contains", expected = "Missing", description = "has Missing")
            ))
            assertThat(result.passed).isFalse()
            assertThat(result.score).isCloseTo(0.6667, org.assertj.core.data.Offset.offset(0.001))
            assertThat(result.assertionResults).hasSize(3)
            assertThat(result.assertionResults.count { it.passed }).isEqualTo(2)
        }

        @Test
        fun `empty assertions means pass`() {
            val result = grader.grade(trialId, "anything", emptyList())
            assertThat(result.passed).isTrue()
            assertThat(result.score).isEqualTo(1.0)
        }
    }

    // ── JSONPath resolver ───────────────────────────────────────────

    @Nested
    inner class JsonPathResolver {

        @Test
        fun `resolves nested field`() {
            val json = com.google.gson.JsonParser.parseString("""{"a":{"b":{"c":"deep"}}}""")
            assertThat(grader.resolveJsonPath(json, "$.a.b.c")).isEqualTo("deep")
        }

        @Test
        fun `resolves array index`() {
            val json = com.google.gson.JsonParser.parseString("""{"items":[{"name":"first"},{"name":"second"}]}""")
            assertThat(grader.resolveJsonPath(json, "$.items[1].name")).isEqualTo("second")
        }

        @Test
        fun `returns null for missing path`() {
            val json = com.google.gson.JsonParser.parseString("""{"a":"b"}""")
            assertThat(grader.resolveJsonPath(json, "$.x.y.z")).isNull()
        }
    }

    // ── Count spec matcher ──────────────────────────────────────────

    @Nested
    inner class CountSpecMatcher {

        @Test
        fun `exact match`() {
            assertThat(grader.matchCountSpec(3, "3")).isTrue()
            assertThat(grader.matchCountSpec(3, "4")).isFalse()
        }

        @Test
        fun `gte`() {
            assertThat(grader.matchCountSpec(3, ">=3")).isTrue()
            assertThat(grader.matchCountSpec(2, ">=3")).isFalse()
        }

        @Test
        fun `lte`() {
            assertThat(grader.matchCountSpec(3, "<=3")).isTrue()
            assertThat(grader.matchCountSpec(4, "<=3")).isFalse()
        }

        @Test
        fun `range`() {
            assertThat(grader.matchCountSpec(3, "1-5")).isTrue()
            assertThat(grader.matchCountSpec(0, "1-5")).isFalse()
            assertThat(grader.matchCountSpec(6, "1-5")).isFalse()
        }
    }
}
