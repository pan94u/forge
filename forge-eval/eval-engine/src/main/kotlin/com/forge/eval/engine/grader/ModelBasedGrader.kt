package com.forge.eval.engine.grader

import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.ModelAdapter
import com.forge.eval.protocol.*
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Model-based grader: 使用 LLM (judge model) 按 rubric 标准评估 agent 输出。
 *
 * 设计要点：
 * - 构建 system prompt 指示 judge model 按 rubric 逐项评分
 * - 解析 LLM 返回的结构化 JSON 响应
 * - 计算加权总分
 * - Anti-self-eval：judge model 应与被评估模型不同
 * - 解析失败时优雅降级（fallback 0.5 分 + 低 confidence）
 */
class ModelBasedGrader(
    private val modelAdapter: ModelAdapter
) : Grader {

    private val logger = LoggerFactory.getLogger(ModelBasedGrader::class.java)

    companion object {
        const val DEFAULT_JUDGE_MODEL = "claude-sonnet-4-6"
        private const val FALLBACK_SCORE = 0.5
        private const val FALLBACK_CONFIDENCE = 0.2
    }

    override suspend fun grade(
        trialId: UUID,
        output: String,
        config: GraderConfig,
        transcript: EvalTranscript?
    ): EvalGrade {
        val rubric = config.rubric
        if (rubric.isEmpty()) {
            logger.warn("ModelBasedGrader called with empty rubric for trial {}, returning default score", trialId)
            return EvalGrade(
                trialId = trialId,
                graderType = GraderType.MODEL_BASED,
                score = 1.0,
                passed = true,
                rubricScores = emptyMap(),
                explanation = "No rubric criteria defined — auto-pass",
                confidence = 1.0
            )
        }

        val judgeModel = config.model ?: DEFAULT_JUDGE_MODEL
        val prompt = buildJudgePrompt(output, rubric, transcript)

        return try {
            val result = modelAdapter.complete(
                prompt = prompt,
                options = CompletionOptions(
                    model = judgeModel,
                    maxTokens = 2048,
                    temperature = 0.0,
                    systemPrompt = buildSystemPrompt()
                )
            )

            parseJudgeResponse(trialId, result.content, rubric)
        } catch (e: Exception) {
            logger.error("Model-based grading failed for trial {}: {}", trialId, e.message, e)
            buildFallbackGrade(trialId, rubric, "Model call failed: ${e.message}")
        }
    }

    /**
     * 选择 judge model，确保不与被评估模型相同（anti-self-eval）。
     * 如果 config.model 已指定则使用，否则默认使用 claude-sonnet-4-6。
     */
    fun selectJudgeModel(evaluatedModel: String?, configModel: String?): String {
        val judge = configModel ?: DEFAULT_JUDGE_MODEL
        if (evaluatedModel != null && judge == evaluatedModel) {
            logger.warn(
                "Anti-self-eval: judge model '{}' is the same as evaluated model. " +
                    "Consider using a different judge model for unbiased evaluation.",
                judge
            )
        }
        return judge
    }

    internal fun buildSystemPrompt(): String {
        return """You are an expert evaluation judge. Your task is to evaluate an AI agent's output against a rubric.

You MUST respond with ONLY a valid JSON object in the following exact format — no markdown, no code blocks, no extra text:
{"scores":{"criterion_name":0.75},"explanation":"Brief explanation","confidence":0.8}

Rules:
- "scores" must contain one entry per rubric criterion, keyed by criterion name
- Each score must be a number between 0.0 and 1.0
- "explanation" should be a concise summary of the evaluation
- "confidence" must be a number between 0.0 and 1.0 indicating your confidence in the evaluation
- Be objective, fair, and consistent in your scoring"""
    }

    internal fun buildJudgePrompt(
        output: String,
        rubric: List<RubricCriterion>,
        transcript: EvalTranscript?
    ): String {
        val rubricSection = rubric.joinToString("\n") { criterion ->
            val scaleStr = criterion.scale.joinToString(", ")
            "- ${criterion.criterion} (weight=${criterion.weight}): ${criterion.description} [scale: $scaleStr]"
        }

        val transcriptSection = if (transcript != null && transcript.turns.isNotEmpty()) {
            val turnSummary = transcript.turns.joinToString("\n") { turn ->
                "  [${turn.role}]: ${turn.content.take(200)}"
            }
            "\n## Interaction Transcript (summary)\n$turnSummary"
        } else {
            ""
        }

        return """## Agent Output
$output

## Rubric Criteria
$rubricSection
$transcriptSection

Evaluate the agent output against each rubric criterion. Respond with ONLY a JSON object."""
    }

    internal fun parseJudgeResponse(
        trialId: UUID,
        responseContent: String,
        rubric: List<RubricCriterion>
    ): EvalGrade {
        return try {
            val json = extractJson(responseContent)
                ?: throw IllegalArgumentException("No valid JSON found in response")

            val root = JsonParser.parseString(json).asJsonObject
            val scoresObj = root.getAsJsonObject("scores")
                ?: throw IllegalArgumentException("Missing 'scores' field")
            val explanation = root.get("explanation")?.asString ?: ""
            val confidence = root.get("confidence")?.asDouble ?: 0.5

            // 提取每个 criterion 的分数
            val rubricScores = mutableMapOf<String, Double>()
            for (criterion in rubric) {
                val score = scoresObj.get(criterion.criterion)?.asDouble
                if (score != null) {
                    rubricScores[criterion.criterion] = score.coerceIn(0.0, 1.0)
                } else {
                    logger.warn("Judge response missing score for criterion '{}'", criterion.criterion)
                    rubricScores[criterion.criterion] = FALLBACK_SCORE
                }
            }

            // 计算加权总分
            val weightedScore = calculateWeightedScore(rubricScores, rubric)
            val passed = weightedScore >= 0.5

            EvalGrade(
                trialId = trialId,
                graderType = GraderType.MODEL_BASED,
                score = weightedScore,
                passed = passed,
                rubricScores = rubricScores,
                explanation = explanation,
                confidence = confidence.coerceIn(0.0, 1.0)
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse judge response for trial {}: {}", trialId, e.message)
            buildFallbackGrade(trialId, rubric, "Parse error: ${e.message}")
        }
    }

    /**
     * 从响应文本中提取 JSON。
     * 支持纯 JSON、markdown 代码块包裹的 JSON。
     */
    internal fun extractJson(text: String): String? {
        val trimmed = text.trim()

        // 尝试直接解析
        try {
            JsonParser.parseString(trimmed)
            return trimmed
        } catch (_: Exception) {
            // 继续尝试其他格式
        }

        // 尝试从 markdown 代码块中提取
        val codeBlockPattern = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""")
        val match = codeBlockPattern.find(trimmed)
        if (match != null) {
            val extracted = match.groupValues[1].trim()
            try {
                JsonParser.parseString(extracted)
                return extracted
            } catch (_: Exception) {
                // 继续
            }
        }

        // 尝试找到 JSON 对象边界 { ... }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val candidate = trimmed.substring(firstBrace, lastBrace + 1)
            try {
                JsonParser.parseString(candidate)
                return candidate
            } catch (_: Exception) {
                // 放弃
            }
        }

        return null
    }

    internal fun calculateWeightedScore(
        scores: Map<String, Double>,
        rubric: List<RubricCriterion>
    ): Double {
        val totalWeight = rubric.sumOf { it.weight }
        if (totalWeight == 0.0) return scores.values.average()

        var weightedSum = 0.0
        for (criterion in rubric) {
            val score = scores[criterion.criterion] ?: FALLBACK_SCORE
            weightedSum += score * criterion.weight
        }
        return weightedSum / totalWeight
    }

    private fun buildFallbackGrade(
        trialId: UUID,
        rubric: List<RubricCriterion>,
        reason: String
    ): EvalGrade {
        val fallbackScores = rubric.associate { it.criterion to FALLBACK_SCORE }
        return EvalGrade(
            trialId = trialId,
            graderType = GraderType.MODEL_BASED,
            score = FALLBACK_SCORE,
            passed = false,
            rubricScores = fallbackScores,
            explanation = "Fallback grade — $reason",
            confidence = FALLBACK_CONFIDENCE
        )
    }
}
