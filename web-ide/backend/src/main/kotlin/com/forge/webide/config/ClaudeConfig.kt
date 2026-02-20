package com.forge.webide.config

import com.forge.adapter.model.ClaudeAdapter
import com.forge.adapter.model.LocalModelAdapter
import com.forge.adapter.model.ModelAdapter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Spring configuration for the model adapter.
 *
 * Selects the adapter based on configuration:
 * - If forge.model.provider=openai (or LOCAL_MODEL_URL is set), uses LocalModelAdapter
 * - Otherwise defaults to ClaudeAdapter with Anthropic API
 */
@Configuration
class ClaudeConfig {

    @Value("\${forge.claude.api-key:}")
    private var apiKey: String = ""

    @Value("\${forge.claude.api-url:https://api.anthropic.com}")
    private var apiUrl: String = "https://api.anthropic.com"

    @Value("\${forge.model.provider:anthropic}")
    private var modelProvider: String = "anthropic"

    @Value("\${forge.model.url:}")
    private var modelUrl: String = ""

    @Value("\${forge.model.name:}")
    private var modelName: String = ""

    @Value("\${forge.model.api-key:}")
    private var modelApiKey: String = ""

    @Bean
    fun claudeAdapter(): ModelAdapter {
        val localUrl = modelUrl.ifBlank { System.getenv("LOCAL_MODEL_URL") ?: "" }
        val useOpenAI = modelProvider == "openai" || localUrl.isNotBlank()

        return if (useOpenAI) {
            LocalModelAdapter(
                baseUrl = localUrl,
                defaultModel = modelName.ifBlank { System.getenv("LOCAL_MODEL_NAME") ?: "" },
                apiKey = modelApiKey.ifBlank { System.getenv("LOCAL_MODEL_API_KEY") }
            )
        } else {
            val key = apiKey.ifBlank { System.getenv("ANTHROPIC_API_KEY") ?: "" }
            ClaudeAdapter(apiKey = key, baseUrl = apiUrl)
        }
    }

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}
