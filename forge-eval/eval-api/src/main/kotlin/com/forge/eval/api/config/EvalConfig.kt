package com.forge.eval.api.config

import com.forge.adapter.model.ModelAdapter
import com.forge.eval.engine.EvalEngine
import com.forge.eval.engine.ReportGenerator
import com.forge.eval.engine.grader.CodeBasedGrader
import com.forge.eval.engine.grader.ModelBasedGrader
import com.forge.eval.engine.lifecycle.LifecycleManager
import com.forge.eval.engine.review.ReviewTriggerRules
import com.forge.eval.engine.notification.WebhookNotifier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EvalConfig {

    private val logger = LoggerFactory.getLogger(EvalConfig::class.java)

    @Bean
    fun codeBasedGrader(): CodeBasedGrader = CodeBasedGrader()

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
