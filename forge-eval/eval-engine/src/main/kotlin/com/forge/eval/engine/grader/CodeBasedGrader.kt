package com.forge.eval.engine.grader

import com.forge.eval.protocol.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Deterministic grader that evaluates outputs using code-based assertions.
 *
 * Supports both legacy assertion types (contains, not_contains, matches_pattern, json_schema)
 * and new transcript-aware assertions (tool_used, tool_call_count, tool_call_order, etc.).
 */
class CodeBasedGrader(
    private val semanticEvaluator: ((expected: String, actual: String) -> SemanticResult)? = null
) {

    data class SemanticResult(val similar: Boolean, val score: Double, val rationale: String)

    private val logger = LoggerFactory.getLogger(CodeBasedGrader::class.java)
    private val gson = Gson()

    /**
     * Grade a trial output against a set of assertion configs.
     *
     * @param trialId The trial being graded
     * @param output The agent's text output
     * @param assertions The assertion configurations to evaluate
     * @param transcript Optional transcript for tool-call assertions
     * @return An EvalGrade with assertion-level results
     */
    fun grade(
        trialId: UUID,
        output: String,
        assertions: List<AssertionConfig>,
        transcript: EvalTranscript? = null
    ): EvalGrade {
        val results = assertions.map { assertion ->
            evaluateAssertion(assertion, output, transcript)
        }

        val passedCount = results.count { it.passed }
        val score = if (results.isNotEmpty()) passedCount.toDouble() / results.size else 1.0
        val allPassed = results.all { it.passed }

        return EvalGrade(
            trialId = trialId,
            graderType = GraderType.CODE_BASED,
            score = score,
            passed = allPassed,
            assertionResults = results,
            explanation = buildExplanation(results),
            confidence = 1.0
        )
    }

    internal fun evaluateAssertion(
        config: AssertionConfig,
        output: String,
        transcript: EvalTranscript? = null
    ): AssertionResult {
        return try {
            when (config.type) {
                "contains" -> evaluateContains(config, output)
                "not_contains" -> evaluateNotContains(config, output)
                "matches_pattern" -> evaluateMatchesPattern(config, output)
                "json_schema" -> evaluateJsonSchema(config, output)
                "json_path" -> evaluateJsonPath(config, output)
                "tool_used" -> evaluateToolUsed(config, transcript)
                "tool_not_used" -> evaluateToolNotUsed(config, transcript)
                "tool_call_count" -> evaluateToolCallCount(config, transcript)
                "tool_call_order" -> evaluateToolCallOrder(config, transcript)
                "turn_count_max" -> evaluateTurnCountMax(config, transcript)
                "semantic_similarity" -> evaluateSemanticSimilarity(config, output)
                // Legacy types from existing eval-sets
                "profile_routed" -> evaluateContains(config.copy(type = "contains"), output)
                "structure" -> evaluateStructure(config, output)
                else -> AssertionResult(
                    description = config.description,
                    passed = false,
                    expected = config.expected,
                    actual = "Unknown assertion type: ${config.type}",
                    assertionType = config.type
                )
            }
        } catch (e: Exception) {
            logger.warn("Assertion evaluation failed: {} - {}", config.type, e.message)
            AssertionResult(
                description = config.description,
                passed = false,
                expected = config.expected,
                actual = "Evaluation error: ${e.message}",
                assertionType = config.type
            )
        }
    }

    private fun evaluateContains(config: AssertionConfig, output: String): AssertionResult {
        val haystack = if (!config.caseSensitive) output.lowercase() else output
        val needle = if (!config.caseSensitive) config.expected.lowercase() else config.expected
        return AssertionResult(
            description = config.description,
            passed = haystack.contains(needle),
            expected = config.expected,
            actual = truncate(output),
            assertionType = "contains"
        )
    }

    private fun evaluateNotContains(config: AssertionConfig, output: String): AssertionResult {
        val haystack = if (!config.caseSensitive) output.lowercase() else output
        val needle = if (!config.caseSensitive) config.expected.lowercase() else config.expected
        return AssertionResult(
            description = config.description,
            passed = !haystack.contains(needle),
            expected = "NOT: ${config.expected}",
            actual = truncate(output),
            assertionType = "not_contains"
        )
    }

    private fun evaluateMatchesPattern(config: AssertionConfig, output: String): AssertionResult {
        val regex = Regex(config.expected)
        return AssertionResult(
            description = config.description,
            passed = regex.containsMatchIn(output),
            expected = config.expected,
            actual = truncate(output),
            assertionType = "matches_pattern"
        )
    }

    private fun evaluateJsonSchema(config: AssertionConfig, output: String): AssertionResult {
        return try {
            JsonParser.parseString(output)
            AssertionResult(
                description = config.description,
                passed = true,
                expected = config.expected,
                actual = truncate(output),
                assertionType = "json_schema"
            )
        } catch (e: Exception) {
            // Try to find JSON in markdown code blocks
            val jsonPattern = Regex("""```(?:json)?\s*\n([\s\S]*?)\n```""")
            val match = jsonPattern.find(output)
            if (match != null) {
                try {
                    JsonParser.parseString(match.groupValues[1])
                    AssertionResult(
                        description = config.description,
                        passed = true,
                        expected = config.expected,
                        actual = truncate(match.groupValues[1]),
                        assertionType = "json_schema"
                    )
                } catch (e2: Exception) {
                    AssertionResult(
                        description = config.description,
                        passed = false,
                        expected = config.expected,
                        actual = "Invalid JSON: ${e2.message}",
                        assertionType = "json_schema"
                    )
                }
            } else {
                AssertionResult(
                    description = config.description,
                    passed = false,
                    expected = config.expected,
                    actual = "Output is not valid JSON: ${e.message}",
                    assertionType = "json_schema"
                )
            }
        }
    }

    /**
     * JSONPath assertion: extract a value from JSON output and compare.
     * Expected format: "$.path.to.field=expectedValue" or "$.path.to.field" (existence check)
     */
    private fun evaluateJsonPath(config: AssertionConfig, output: String): AssertionResult {
        val parts = config.expected.split("=", limit = 2)
        val path = parts[0].trim()
        val expectedValue = parts.getOrNull(1)?.trim()

        val json = tryParseJson(output)
            ?: return AssertionResult(
                description = config.description,
                passed = false,
                expected = config.expected,
                actual = "Output is not valid JSON",
                assertionType = "json_path"
            )

        val actualValue = resolveJsonPath(json, path)
        val passed = if (expectedValue != null) {
            actualValue?.toString() == expectedValue
        } else {
            actualValue != null
        }

        return AssertionResult(
            description = config.description,
            passed = passed,
            expected = config.expected,
            actual = actualValue?.toString() ?: "(not found)",
            assertionType = "json_path"
        )
    }

    private fun evaluateToolUsed(config: AssertionConfig, transcript: EvalTranscript?): AssertionResult {
        val toolName = config.expected
        val used = transcript?.toolCallSummary?.any { it.toolName == toolName } == true
        return AssertionResult(
            description = config.description.ifEmpty { "Tool '$toolName' was used" },
            passed = used,
            expected = toolName,
            actual = if (used) "used" else "not used (tools: ${transcript?.toolCallSummary?.map { it.toolName }?.distinct()?.joinToString() ?: "none"})",
            assertionType = "tool_used"
        )
    }

    private fun evaluateToolNotUsed(config: AssertionConfig, transcript: EvalTranscript?): AssertionResult {
        val toolName = config.expected
        val used = transcript?.toolCallSummary?.any { it.toolName == toolName } == true
        return AssertionResult(
            description = config.description.ifEmpty { "Tool '$toolName' was NOT used" },
            passed = !used,
            expected = "NOT: $toolName",
            actual = if (used) "was used" else "not used",
            assertionType = "tool_not_used"
        )
    }

    /**
     * Assert tool call count. Expected format: "toolName:N" (exact), "toolName:>=N", "toolName:<=N", "toolName:N-M" (range)
     */
    private fun evaluateToolCallCount(config: AssertionConfig, transcript: EvalTranscript?): AssertionResult {
        val parts = config.expected.split(":", limit = 2)
        val toolName = parts[0].trim()
        val countSpec = parts.getOrElse(1) { ">=1" }.trim()
        val actualCount = transcript?.toolCallSummary?.count { it.toolName == toolName } ?: 0

        val passed = matchCountSpec(actualCount, countSpec)

        return AssertionResult(
            description = config.description.ifEmpty { "Tool '$toolName' call count matches $countSpec" },
            passed = passed,
            expected = config.expected,
            actual = "$actualCount calls",
            assertionType = "tool_call_count"
        )
    }

    /**
     * Assert tool call order. Expected: comma-separated tool names in expected order.
     * Verifies that tools appear in the specified order (other tools may appear between them).
     */
    private fun evaluateToolCallOrder(config: AssertionConfig, transcript: EvalTranscript?): AssertionResult {
        val expectedOrder = config.expected.split(",").map { it.trim() }
        val actualTools = transcript?.toolCallSummary?.map { it.toolName } ?: emptyList()

        var searchFrom = 0
        var allFound = true
        for (tool in expectedOrder) {
            val idx = actualTools.indexOf(tool, searchFrom)
            if (idx < 0) {
                allFound = false
                break
            }
            searchFrom = idx + 1
        }

        return AssertionResult(
            description = config.description.ifEmpty { "Tool call order: ${expectedOrder.joinToString(" → ")}" },
            passed = allFound,
            expected = expectedOrder.joinToString(" → "),
            actual = actualTools.joinToString(" → "),
            assertionType = "tool_call_order"
        )
    }

    /**
     * Assert max number of turns in the transcript.
     */
    private fun evaluateTurnCountMax(config: AssertionConfig, transcript: EvalTranscript?): AssertionResult {
        val maxTurns = config.expected.toIntOrNull() ?: return AssertionResult(
            description = config.description,
            passed = false,
            expected = config.expected,
            actual = "Invalid max turns value",
            assertionType = "turn_count_max"
        )
        val actualTurns = transcript?.turns?.size ?: 0
        return AssertionResult(
            description = config.description.ifEmpty { "Completed within $maxTurns turns" },
            passed = actualTurns <= maxTurns,
            expected = "<= $maxTurns turns",
            actual = "$actualTurns turns",
            assertionType = "turn_count_max"
        )
    }

    /**
     * Structure assertion — check markdown structure (headers, etc.)
     */
    private fun evaluateStructure(config: AssertionConfig, output: String): AssertionResult {
        val structureType = config.extras["structure_type"] as? String
            ?: config.expected
        return when (structureType) {
            "markdown_headers" -> {
                val hasHeaders = output.lines().any { it.startsWith("#") }
                AssertionResult(
                    description = config.description.ifEmpty { "Output contains markdown headers" },
                    passed = hasHeaders,
                    expected = "markdown headers",
                    actual = if (hasHeaders) "headers found" else "no headers",
                    assertionType = "structure"
                )
            }
            else -> AssertionResult(
                description = config.description,
                passed = true,
                expected = structureType,
                actual = "(structure check: $structureType)",
                assertionType = "structure"
            )
        }
    }

    private fun evaluateSemanticSimilarity(config: AssertionConfig, output: String): AssertionResult {
        if (semanticEvaluator == null) {
            // Fallback: contains check when no semantic evaluator is provided
            val found = output.lowercase().contains(config.expected.lowercase())
            return AssertionResult(
                description = config.description,
                passed = found,
                expected = config.expected,
                actual = if (found) "Contains match (semantic evaluator unavailable)" else "No match (semantic evaluator unavailable)",
                assertionType = config.type
            )
        }

        return try {
            val result = semanticEvaluator.invoke(config.expected, output)
            AssertionResult(
                description = config.description,
                passed = result.similar,
                expected = config.expected,
                actual = "Similarity: %.2f — %s".format(result.score, result.rationale),
                assertionType = config.type
            )
        } catch (e: Exception) {
            logger.error("Semantic similarity evaluation failed: {}", e.message, e)
            AssertionResult(
                description = config.description,
                passed = false,
                expected = config.expected,
                actual = "Semantic eval failed: ${e.message}",
                assertionType = config.type
            )
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun tryParseJson(text: String): com.google.gson.JsonElement? {
        return try {
            JsonParser.parseString(text)
        } catch (e: Exception) {
            val jsonPattern = Regex("""```(?:json)?\s*\n([\s\S]*?)\n```""")
            val match = jsonPattern.find(text)
            match?.let {
                try { JsonParser.parseString(it.groupValues[1]) } catch (_: Exception) { null }
            }
        }
    }

    /**
     * Simple JSONPath resolver supporting $.field.nested.array[0] style paths.
     */
    internal fun resolveJsonPath(element: com.google.gson.JsonElement, path: String): Any? {
        val segments = path.removePrefix("$").removePrefix(".").split(".")
        var current: com.google.gson.JsonElement? = element

        for (segment in segments) {
            if (current == null) return null
            val arrayMatch = Regex("""(\w+)\[(\d+)]""").matchEntire(segment)
            if (arrayMatch != null) {
                val field = arrayMatch.groupValues[1]
                val index = arrayMatch.groupValues[2].toInt()
                current = current.asJsonObject?.get(field)?.asJsonArray?.get(index)
            } else if (segment.isNotEmpty()) {
                current = current.asJsonObject?.get(segment)
            }
        }

        return when {
            current == null -> null
            current.isJsonPrimitive -> {
                val prim = current.asJsonPrimitive
                when {
                    prim.isBoolean -> prim.asBoolean
                    prim.isNumber -> prim.asNumber
                    else -> prim.asString
                }
            }
            else -> current.toString()
        }
    }

    internal fun matchCountSpec(actual: Int, spec: String): Boolean {
        return when {
            spec.startsWith(">=") -> actual >= spec.removePrefix(">=").trim().toInt()
            spec.startsWith("<=") -> actual <= spec.removePrefix("<=").trim().toInt()
            spec.startsWith(">") -> actual > spec.removePrefix(">").trim().toInt()
            spec.startsWith("<") -> actual < spec.removePrefix("<").trim().toInt()
            spec.contains("-") -> {
                val (min, max) = spec.split("-").map { it.trim().toInt() }
                actual in min..max
            }
            else -> actual == spec.toIntOrNull()
        }
    }

    private fun buildExplanation(results: List<AssertionResult>): String {
        val passed = results.count { it.passed }
        val failed = results.filter { !it.passed }
        return if (failed.isEmpty()) {
            "All $passed assertions passed"
        } else {
            "Passed $passed/${results.size}. Failed: ${failed.joinToString("; ") { it.description }}"
        }
    }

    private fun truncate(text: String, maxLen: Int = 200): String {
        return if (text.length > maxLen) text.take(maxLen) + "..." else text
    }

    private fun List<String>.indexOf(element: String, fromIndex: Int): Int {
        for (i in fromIndex until size) {
            if (this[i] == element) return i
        }
        return -1
    }
}
