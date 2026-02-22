package com.forge.webide.config

import com.forge.webide.websocket.TerminalWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor

/**
 * WebSocket configuration for terminal sessions.
 *
 * Chat streaming is handled via HTTP SSE (POST /api/chat/sessions/{id}/stream).
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val terminalWebSocketHandler: TerminalWebSocketHandler,
    @Value("\${forge.websocket.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String
) : WebSocketConfigurer {

    @Bean
    fun createWebSocketContainer(): ServletServerContainerFactoryBean {
        val container = ServletServerContainerFactoryBean()
        container.setMaxTextMessageBufferSize(512 * 1024)
        container.setMaxBinaryMessageBufferSize(512 * 1024)
        return container
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val origins = allowedOrigins.split(",").map { it.trim() }.toTypedArray()

        registry
            .addHandler(terminalWebSocketHandler, "/ws/terminal/**")
            .addInterceptors(HttpSessionHandshakeInterceptor())
            .setAllowedOrigins(*origins)
    }
}
