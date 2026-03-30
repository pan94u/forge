package com.forge.webide.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
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

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/forge}")
    private var jwtIssuerUri: String = "http://localhost:8180/realms/forge"

    @Value("\${forge.security.jwk-set-uri:}")
    private var jwkSetUri: String = ""

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
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/health").permitAll()
                        .requestMatchers("/ws/**").permitAll() // WebSocket upgrade
                        .requestMatchers("/api/auth/**").permitAll() // Auth endpoints
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/invitations/**").permitAll() // Public invitation preview
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                }
                .addFilterBefore(GatewayUserFilter(), UsernamePasswordAuthenticationFilter::class.java)
                .headers { headers ->
                    headers.frameOptions { it.sameOrigin() } // For H2 console
                }
        } else {
            http
                .authorizeHttpRequests { auth ->
                    auth.anyRequest().permitAll()
                }
        }

        return http.build()
    }

    /**
     * Custom JwtDecoder using JWKS endpoint directly, bypassing OIDC discovery.
     *
     * In Docker, the container cannot reach localhost:8180 (Keycloak on host machine).
     * FORGE_SECURITY_JWK_SET_URI (e.g. host.docker.internal:8180/.../certs) is used
     * for JWKS fetching. The JWT issuer claim is still validated against jwtIssuerUri
     * (localhost:8180) which matches what Keycloak puts in the token (KC_HOSTNAME_URL).
     */
    @Bean
    fun jwtDecoder(): JwtDecoder {
        val jwkUri = if (jwkSetUri.isNotBlank()) jwkSetUri
                     else "$jwtIssuerUri/protocol/openid-connect/certs"
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwkUri).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(),
                JwtIssuerValidator(jwtIssuerUri)
            )
        )
        return decoder
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOriginPatterns = listOf("*")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        config.maxAge = 3600

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
