package com.forge.webide.config

import com.forge.adapter.model.ClaudeAdapter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Spring configuration for the Claude model adapter.
 *
 * Creates a [ClaudeAdapter] bean configured with the API key and base URL
 * from application properties, making it available for injection across the backend.
 */
@Configuration
class ClaudeConfig {

    @Value("\${forge.claude.api-key:}")
    private var apiKey: String = ""

    @Value("\${forge.claude.api-url:https://api.anthropic.com}")
    private var apiUrl: String = "https://api.anthropic.com"

    @Bean
    fun claudeAdapter(): ClaudeAdapter {
        val key = apiKey.ifBlank { System.getenv("ANTHROPIC_API_KEY") ?: "" }
        return ClaudeAdapter(apiKey = key, baseUrl = apiUrl)
    }

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}
