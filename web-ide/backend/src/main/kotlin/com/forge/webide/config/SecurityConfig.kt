package com.forge.webide.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security configuration with OAuth2 resource server support.
 *
 * In development mode, security is relaxed to allow unauthenticated access.
 * In production, JWT-based authentication is enforced via the OAuth2
 * resource server configuration.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Value("\${forge.security.enabled:false}")
    private var securityEnabled: Boolean = false

    @Value("\${forge.cors.allowed-origins:http://localhost:3000}")
    private var allowedOrigins: String = "http://localhost:3000"

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        if (securityEnabled) {
            http
                .authorizeHttpRequests { auth ->
                    auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/ws/**").permitAll() // WebSocket upgrade
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                }
                .oauth2ResourceServer { oauth2 ->
                    oauth2.jwt { }
                }
        } else {
            http
                .authorizeHttpRequests { auth ->
                    auth.anyRequest().permitAll()
                }
        }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = allowedOrigins.split(",").map { it.trim() }
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        config.maxAge = 3600

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
