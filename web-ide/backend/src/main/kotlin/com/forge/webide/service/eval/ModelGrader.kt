package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.ModelAdapter
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * LLM-based Model Grader：调用 LLM 按 rubric 对 Agent 输出进行质量评分。
 */
class ModelGrader(
    private val modelAdapter: ModelAdapter,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(ModelGrader::class.java)

    companion object {
        private const val MODEL = "claude-sonnet-4-20250514"
        private const val MAX_INPUT_LENGTH = 2000
        private const val MAX_OUTPUT_LENGTH = 4000
    }

    fun grade(rubric: String, successCriteria: String, taskInput: String, actualOutput: String): ModelGradeResult {
        val truncatedInput = taskInput.take(MAX_INPUT_LENGTH)
        val truncatedOutput = actualOutput.take(MAX_OUTPUT_LENGTH)

        val systemPrompt = """You are an expert evaluator for AI agent outputs.
Score the output based on the rubric and success criteria below.
Respond ONLY with a JSON object: {"score": 0.0-1.0, "dimensions": {}, "rationale": "..."}
- score: overall quality score from 0.0 (worst) to 1.0 (perfect)
- dimensions: breakdown by rubric dimensions (key→score)
- rationale: 1-2 sentence explanation"""

        val userPrompt = """## Rubric
$rubric

## Success Criteria
$successCriteria

## Task Input
$truncatedInput

## Agent Output
$truncatedOutput"""

        return try {
            val result = runBlocking {
                modelAdapter.complete(
                    prompt = userPrompt,
                    options = CompletionOptions(
                        model = MODEL,
                        temperature = 0.1,
                        maxTokens = 1024,
                        systemPrompt = systemPrompt
                    )
                )
            }
            parseGradeResponse(result.content)
        } catch (e: Exception) {
            log.error("ModelGrader failed: {}", e.message, e)
            ModelGradeResult(
                score = BigDecimal.ZERO,
                rationale = "Grading failed: ${e.message}",
                detail = "{\"error\": \"${e.message}\"}"
            )
        }
    }

    internal fun parseGradeResponse(response: String): ModelGradeResult {
        // 先尝试提取 code block 中的 JSON
        val jsonContent = extractJson(response)

        return try {
            val tree = objectMapper.readTree(jsonContent)
            val rawScore = tree.get("score")?.asDouble() ?: 0.0
            val score = clampScore(rawScore)
            val rationale = tree.get("rationale")?.asText() ?: ""
            ModelGradeResult(
                score = score,
                rationale = rationale,
                detail = jsonContent
            )
        } catch (e: Exception) {
            log.warn("JSON parse failed, attempting regex fallback: {}", e.message)
            regexFallback(response)
        }
    }

    private fun regexFallback(response: String): ModelGradeResult {
        val scoreRegex = Regex(""""score"\s*:\s*([0-9]*\.?[0-9]+)""")
        val match = scoreRegex.find(response)
        val rawScore = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val score = clampScore(rawScore)

        return ModelGradeResult(
            score = score,
            rationale = "Parsed via regex fallback",
            detail = response.take(500)
        )
    }

    private fun clampScore(raw: Double): BigDecimal {
        val clamped = raw.coerceIn(0.0, 1.0)
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP)
    }

    private fun extractJson(output: String): String {
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n```")
        val match = codeBlockRegex.find(output)
        return match?.groupValues?.get(1)?.trim() ?: output.trim()
    }
}
