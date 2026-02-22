package com.forge.webide.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
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
 *
 * When running behind Gateway, the Gateway validates JWT and injects
 * X-User-Id and user-account headers. This filter reads those headers
 * and sets up Spring Security authentication context.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Value("\${forge.security.enabled:false}")
    private var securityEnabled: Boolean = false

    @Value("\${forge.cors.allowed-origins:http://localhost:3000,http://localhost:4000}")
    private var allowedOrigins: String = "http://localhost:3000,http://localhost:4000"

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_USER_ACCOUNT = "user-account"
        const val HEADER_USER_ROLES = "X-User-Roles"
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        if (securityEnabled) {
            http
                // 添加 Gateway 头信息认证过滤器 (在 JWT 验证之前)
                .addFilterBefore(gatewayAuthFilter(), UsernamePasswordAuthenticationFilter::class.java)
                .authorizeHttpRequests { auth ->
                    auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/ws/**").permitAll() // WebSocket upgrade
                        .requestMatchers("/api/auth/**").permitAll() // Auth endpoints (OAuth2 handled by Keycloak)
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                }
                // 当 security.enabled=true 且需要 JWT 验证时使用 OAuth2 Resource Server
                .oauth2ResourceServer { oauth2 ->
                    oauth2.jwt { }
                }
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
     * Gateway 头信息认证过滤器
     * 当请求经过 Gateway 时,Gateway 已经验证 JWT 并注入 X-User-Id 和 user-account 头
     * 此过滤器读取这些头并设置 Spring Security 认证上下文
     */
    @Bean
    fun gatewayAuthFilter(): GatewayAuthenticationFilter {
        return GatewayAuthenticationFilter()
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

/**
 * Gateway 头信息认证过滤器
 * 读取 Gateway 注入的 X-User-Id, user-account, X-User-Roles 头并设置认证
 */
class GatewayAuthenticationFilter : jakarta.servlet.Filter {

    private val objectMapper = ObjectMapper()

    override fun doFilter(request: jakarta.servlet.ServletRequest, response: jakarta.servlet.ServletResponse, chain: jakarta.servlet.FilterChain) {
        val httpRequest = request as HttpServletRequest

        val xUserId = httpRequest.getHeader(SecurityConfig.HEADER_USER_ID)
        val userAccount = httpRequest.getHeader(SecurityConfig.HEADER_USER_ACCOUNT)
        val xUserRoles = httpRequest.getHeader(SecurityConfig.HEADER_USER_ROLES)

        // 如果有 Gateway 注入的用户信息,设置认证
        if (!userAccount.isNullOrBlank()) {
            try {
                val userNode = objectMapper.readTree(userAccount)
                val userId = userNode.get("userId")?.asText() ?: xUserId
                val rolesNode = userNode.get("roles")
                val roles = if (rolesNode != null && rolesNode.isArray) {
                    rolesNode.map { SimpleGrantedAuthority("ROLE_${it.asText().uppercase()}") }
                } else {
                    parseRoles(xUserRoles)
                }

                val authentication = UsernamePasswordAuthenticationToken(userId, null, roles)
                SecurityContextHolder.getContext().authentication = authentication
            } catch (e: Exception) {
                // 解析失败,静默忽略,让后续 JWT 验证处理
            }
        } else if (!xUserId.isNullOrBlank()) {
            // 只有 X-User-Id,使用默认角色
            val roles = parseRoles(xUserRoles)
            val authentication = UsernamePasswordAuthenticationToken(xUserId, null, roles)
            SecurityContextHolder.getContext().authentication = authentication
        }

        chain.doFilter(request, response)
    }

    private fun parseRoles(rolesHeader: String?): List<SimpleGrantedAuthority> {
        return rolesHeader?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { SimpleGrantedAuthority("ROLE_${it.trim().uppercase()}") }
            ?: listOf(SimpleGrantedAuthority("ROLE_USER"))
    }
}
