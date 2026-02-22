package com.forge.user.service

import com.forge.user.config.AppConfig
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    private val appConfig: AppConfig,
    private val redisTemplate: StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    private val accessTokenKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(appConfig.jwt.secret.toByteArray())
    }

    private val refreshTokenKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(appConfig.jwt.refreshSecret.toByteArray())
    }

    /**
     * 生成 Access Token
     */
    fun generateAccessToken(userId: String, username: String, roles: List<String>): String {
        val now = Instant.now().toEpochMilli()
        val expiresAt = now + appConfig.jwt.expirationMs

        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("roles", roles)
            .claim("type", "access")
            .issuer("forge-platform")
            .issuedAt(Date(now))
            .expiration(Date(expiresAt))
            .signWith(accessTokenKey)
            .compact()
    }

    /**
     * 生成 Refresh Token
     */
    fun generateRefreshToken(userId: String): String {
        val now = Instant.now().toEpochMilli()
        val expiresAt = now + appConfig.jwt.refreshExpirationMs
        val tokenId = java.util.UUID.randomUUID().toString()

        // 存储到 Redis
        redisTemplate.opsForValue().set(
            "refresh:$tokenId",
            userId,
            Duration.ofMillis(appConfig.jwt.refreshExpirationMs)
        )

        return Jwts.builder()
            .subject(userId)
            .claim("type", "refresh")
            .claim("jti", tokenId)
            .issuedAt(Date(now))
            .expiration(Date(expiresAt))
            .signWith(refreshTokenKey)
            .compact()
    }

    /**
     * 验证 Access Token
     */
    fun validateAccessToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(accessTokenKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            logger.warn("Access token expired: ${e.message}")
            null
        } catch (e: Exception) {
            logger.warn("Invalid access token: ${e.message}")
            null
        }
    }

    /**
     * 验证 Refresh Token
     */
    fun validateRefreshToken(token: String): Claims? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(refreshTokenKey)
                .build()
                .parseSignedClaims(token)
                .payload

            // 检查 Token 是否在黑名单
            val jti = claims["jti"] as? String ?: return null
            if (!redisTemplate.hasKey("refresh:$jti")) {
                return null
            }

            claims
        } catch (e: Exception) {
            logger.warn("Invalid refresh token: ${e.message}")
            null
        }
    }

    /**
     * 刷新 Access Token
     */
    fun refreshAccessToken(refreshToken: String): Pair<String, String>? {
        val claims = validateRefreshToken(refreshToken) ?: return null

        val userId = claims.subject
        val jti = claims["jti"] as? String ?: return null

        val newAccessToken = generateAccessToken(userId, "username", emptyList())
        val newRefreshToken = generateRefreshToken(userId)

        // 使旧 Refresh Token 失效
        redisTemplate.delete("refresh:$jti")

        return Pair(newAccessToken, newRefreshToken)
    }

    /**
     * 使 Refresh Token 失效
     */
    fun revokeRefreshToken(token: String) {
        try {
            val claims = Jwts.parser()
                .verifyWith(refreshTokenKey)
                .build()
                .parseSignedClaims(token)
                .payload

            val jti = claims["jti"] as? String
            jti?.let { redisTemplate.delete("refresh:$it") }
        } catch (e: Exception) {
            logger.warn("Failed to revoke refresh token: ${e.message}")
        }
    }

    /**
     * 使所有 Refresh Token 失效 (用户登出所有设备)
     */
    fun revokeAllUserTokens(userId: String) {
        val pattern = "refresh:*"
        val keys = redisTemplate.keys(pattern)
        keys?.forEach { key ->
            if (redisTemplate.opsForValue().get(key) == userId) {
                redisTemplate.delete(key)
            }
        }
    }

    /**
     * 将用户 ID 添加到 Token 黑名单
     */
    fun addToBlacklist(token: String, ttlSeconds: Long) {
        redisTemplate.opsForValue().set(
            "token:blacklist:$token",
            "revoked",
            Duration.ofSeconds(ttlSeconds)
        )
    }
}