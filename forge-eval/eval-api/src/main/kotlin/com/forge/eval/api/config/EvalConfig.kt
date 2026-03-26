package com.forge.eval.api.config

import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.ModelAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.eval.engine.EvalEngine
import com.forge.eval.engine.ReportGenerator
import com.forge.eval.engine.grader.CodeBasedGrader
import com.forge.eval.engine.grader.ModelBasedGrader
import com.forge.eval.engine.lifecycle.LifecycleManager
import com.forge.eval.engine.review.ReviewTriggerRules
import com.forge.eval.engine.notification.WebhookNotifier
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EvalConfig {

    private val logger = LoggerFactory.getLogger(EvalConfig::class.java)

    @Bean
    fun codeBasedGrader(modelAdapterProvider: ObjectProvider<ModelAdapter>): CodeBasedGrader {
        val adapter = modelAdapterProvider.ifAvailable
        if (adapter == null) {
            logger.warn("CodeBasedGrader: ModelAdapter not available, semantic_similarity will fallback to contains")
            return CodeBasedGrader()
        }
        logger.info("CodeBasedGrader: semantic_similarity enabled via ModelAdapter")
        val semanticEvaluator: (String, String) -> CodeBasedGrader.SemanticResult = { expected, actual ->
            try {
                val prompt = """Judge semantic similarity. Return ONLY valid JSON, no other text.

Expected meaning: ${expected.take(1000)}

Actual output: ${actual.take(2000)}

Return JSON: {"similar": true or false, "score": 0.0 to 1.0, "rationale": "one sentence reason"}
Rules: score >= 0.7 means similar. Compare core meaning, not exact wording."""
                val model = adapter.supportedModels().firstOrNull()?.id
                logger.debug("Semantic similarity using model: {}", model)
                val opts = CompletionOptions(
                    model = model,
                    temperature = 0.0,
                    maxTokens = 1024
                )
                // 调用 LLM，空响应时重试一次
                var result = runBlocking { adapter.complete(prompt, opts) }
                if (result.content.isBlank()) {
                    logger.warn("Semantic similarity: empty response, retrying...")
                    result = runBlocking { adapter.complete(prompt, opts) }
                }
                logger.info("Semantic similarity raw: {}", result.content.take(300))
                val parsed = parseSemanticResponse(result.content)
                logger.info("Semantic similarity parsed: score={}, similar={}", parsed.score, parsed.similar)
                parsed
            } catch (e: Exception) {
                logger.error("Semantic similarity evaluation failed: {}", e.message)
                CodeBasedGrader.SemanticResult(similar = false, score = 0.0, rationale = "Evaluation failed: ${e.message}")
            }
        }
        return CodeBasedGrader(semanticEvaluator)
    }

    private val objectMapper = ObjectMapper()

    private fun parseSemanticResponse(response: String): CodeBasedGrader.SemanticResult {
        return try {
            val json = extractJson(response)
            val root = objectMapper.readTree(json)
            val score = (root.get("score")?.asDouble() ?: 0.0).coerceIn(0.0, 1.0)
            val similar = root.get("similar")?.asBoolean() ?: (score >= 0.7)
            val rationale = root.get("rationale")?.asText() ?: ""
            CodeBasedGrader.SemanticResult(similar = similar, score = score, rationale = rationale)
        } catch (e: Exception) {
            logger.warn("Failed to parse semantic response: {} | raw: {}", e.message, response.take(200))
            val scoreRegex = Regex(""""score"\s*:\s*([0-9]*\.?[0-9]+)""")
            val score = scoreRegex.find(response)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            CodeBasedGrader.SemanticResult(similar = score >= 0.7, score = score, rationale = "parsed via fallback")
        }
    }

    private fun extractJson(text: String): String {
        val trimmed = text.trim()
        try { objectMapper.readTree(trimmed); return trimmed } catch (_: Exception) {}
        val codeBlock = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```").find(trimmed)
        if (codeBlock != null) return codeBlock.groupValues[1].trim()
        val first = trimmed.indexOf('{'); val last = trimmed.lastIndexOf('}')
        if (first >= 0 && last > first) return trimmed.substring(first, last + 1)
        return trimmed
    }

    @Bean
    fun evalEngine(
        codeBasedGrader: CodeBasedGrader,
        modelAdapterProvider: ObjectProvider<ModelAdapter>
    ): EvalEngine {
        val adapter = modelAdapterProvider.ifAvailable
        logger.info("EvalEngine init: ModelAdapter ${if (adapter != null) "available (${adapter::class.simpleName})" else "NOT available — MODEL_BASED grading disabled"}")
        return EvalEngine(codeBasedGrader, adapter)
    }

    @Bean
    fun reportGenerator(): ReportGenerator = ReportGenerator()

    @Bean
    fun lifecycleManager(): LifecycleManager = LifecycleManager()

    @Bean
    fun reviewTriggerRules(): ReviewTriggerRules = ReviewTriggerRules()

    @Bean
    fun webhookNotifier(
        @Value("\${forge.eval.webhook-urls:}") webhookUrls: String
    ): WebhookNotifier {
        val urls = webhookUrls.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return WebhookNotifier(webhookUrls = urls)
    }
}
