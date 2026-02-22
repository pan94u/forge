package com.forge.gateway.filter

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import javax.crypto.SecretKey

/**
 * Gateway JWT 认证过滤器
 * 1. 验证 JWT Token (支持 Authorization: Bearer 和 access-token 两种格式)
 * 2. 验证成功后，将用户信息添加到请求头
 * 3. 下游服务通过 X-User-Id 和 user-account 获取用户信息
 */
@Component
class JwtAuthenticationFilter : AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config>(Config::class.java) {

    companion object {
        private const val DEFAULT_JWT_SECRET = "your_jwt_secret_key_at_least_32_chars_here"
        private const val HEADER_USER_ID = "X-User-Id"
        private const val HEADER_USER_ACCOUNT = "user-account"
        private const val HEADER_USER_ROLES = "X-User-Roles"
    }

    private val jwtSecret: String = System.getenv("JWT_SECRET") ?: DEFAULT_JWT_SECRET

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    private val jwtKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request

            // 获取 Token (支持两种格式)
            val token = extractToken(request)
            if (token == null) {
                // 无 Token，返回 401
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                return@GatewayFilter exchange.response.setComplete()
            }

            try {
                // 验证 JWT
                val claims = validateAndParseToken(token)
                if (claims == null) {
                    // Token 无效，返回 401
                    exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                    return@GatewayFilter exchange.response.setComplete()
                }

                // 提取用户信息
                val userId = claims.subject
                val username = claims.get("username", String::class.java) ?: ""
                @Suppress("UNCHECKED_CAST")
                val roles = claims.get("roles", List::class.java) as? List<String> ?: emptyList()

                // 构建用户账户信息 (JSON 格式)
                val userAccount = buildJsonUserAccount(userId, username, roles)

                // 将用户信息添加到请求头
                val modifiedRequest = request.mutate()
                    .header(HEADER_USER_ID, userId)
                    .header(HEADER_USER_ACCOUNT, userAccount)
                    .header(HEADER_USER_ROLES, roles.joinToString(","))
                    .build()

                chain.filter(exchange.mutate().request(modifiedRequest).build())
            } catch (e: Exception) {
                logger.warn("JWT validation failed: ${e.message}")
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                exchange.response.setComplete()
            }
        }
    }

    /**
     * 从请求头提取 JWT Token
     * 支持格式:
     * - Authorization: Bearer <token>
     * - access-token: <token>
     */
    private fun extractToken(request: org.springframework.http.server.reactive.ServerHttpRequest): String? {
        // 优先从 Authorization 头获取
        val authHeader = request.headers.getFirst("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        // 其次从 access-token 头获取
        val accessToken = request.headers.getFirst("access-token")
        if (!accessToken.isNullOrBlank()) {
            return accessToken
        }

        return null
    }

    /**
     * 验证并解析 JWT Token
     */
    private fun validateAndParseToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(jwtKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            logger.debug("Invalid JWT token: ${e.message}")
            null
        }
    }

    /**
     * 构建用户账户 JSON
     */
    private fun buildJsonUserAccount(userId: String, username: String, roles: List<String>): String {
        return """{"userId":"$userId","username":"$username","roles":${roles.map { "\"$it\"" }}}"""
    }

    data class Config(
        val enabled: Boolean = true
    )
}