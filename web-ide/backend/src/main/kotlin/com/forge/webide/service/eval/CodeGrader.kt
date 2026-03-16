package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * 纯函数 Code Grader：对 Agent 输出执行代码断言检查。
 * 从 agent-eval/EvalRunner.evaluateAssertion() 移植断言逻辑。
 */
object CodeGrader {

    private val log = LoggerFactory.getLogger(CodeGrader::class.java)

    fun grade(assertions: List<AssertionConfig>, actualOutput: String, objectMapper: ObjectMapper): CodeGradeResult {
        if (assertions.isEmpty()) {
            return CodeGradeResult(passed = true, assertions = emptyList(), passRate = 1.0, detail = "{}")
        }

        val results = assertions.map { evaluate(it, actualOutput, objectMapper) }
        val passCount = results.count { it.passed }
        val passRate = passCount.toDouble() / results.size

        val detailMap = mapOf(
            "total" to results.size,
            "passed" to passCount,
            "failed" to (results.size - passCount),
            "results" to results.map { mapOf(
                "description" to it.description,
                "passed" to it.passed,
                "expected" to it.expected,
                "actual" to it.actual
            )}
        )
        val detail = objectMapper.writeValueAsString(detailMap)

        return CodeGradeResult(
            passed = passCount == results.size,
            assertions = results,
            passRate = passRate,
            detail = detail
        )
    }

    private fun evaluate(assertion: AssertionConfig, output: String, objectMapper: ObjectMapper): AssertionResult {
        return when (assertion.type.lowercase()) {
            "contains" -> {
                val found = output.contains(assertion.expected, ignoreCase = true)
                AssertionResult(
                    passed = found,
                    expected = assertion.expected,
                    actual = if (found) "Found" else "Not found in output",
                    description = assertion.description
                )
            }
            "not_contains" -> {
                val absent = !output.contains(assertion.expected, ignoreCase = true)
                AssertionResult(
                    passed = absent,
                    expected = "NOT: ${assertion.expected}",
                    actual = if (absent) "Absent (correct)" else "Found (unexpected)",
                    description = assertion.description
                )
            }
            "matches_pattern", "regex" -> {
                try {
                    val regex = Regex(assertion.expected)
                    val found = regex.containsMatchIn(output)
                    AssertionResult(
                        passed = found,
                        expected = assertion.expected,
                        actual = if (found) "Pattern matched" else "No match",
                        description = assertion.description
                    )
                } catch (e: Exception) {
                    log.warn("Invalid regex pattern: {}", assertion.expected, e)
                    AssertionResult(
                        passed = false,
                        expected = assertion.expected,
                        actual = "Invalid regex: ${e.message}",
                        description = assertion.description
                    )
                }
            }
            "json_schema" -> {
                val jsonContent = extractJson(output)
                try {
                    objectMapper.readTree(jsonContent)
                    AssertionResult(
                        passed = true,
                        expected = "Valid JSON",
                        actual = "Parsed successfully",
                        description = assertion.description
                    )
                } catch (e: Exception) {
                    AssertionResult(
                        passed = false,
                        expected = "Valid JSON",
                        actual = "Parse error: ${e.message}",
                        description = assertion.description
                    )
                }
            }
            else -> {
                log.warn("Unknown assertion type: {}", assertion.type)
                AssertionResult(
                    passed = false,
                    expected = assertion.expected,
                    actual = "Unknown assertion type: ${assertion.type}",
                    description = assertion.description
                )
            }
        }
    }

    /**
     * 提取 markdown code block 中的 JSON，或直接返回原文。
     */
    private fun extractJson(output: String): String {
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n```")
        val match = codeBlockRegex.find(output)
        return match?.groupValues?.get(1)?.trim() ?: output.trim()
    }
}
