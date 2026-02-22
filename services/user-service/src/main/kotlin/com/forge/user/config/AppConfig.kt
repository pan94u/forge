package com.forge.user.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app")
data class AppConfig(
    var host: String = "0.0.0.0",
    var port: Int = 8086,
    var jwt: JwtConfig = JwtConfig(),
    var encryption: EncryptionConfig = EncryptionConfig(),
    var security: SecurityConfig = SecurityConfig(),
    var rateLimit: RateLimitConfig = RateLimitConfig(),
    var github: GithubConfig = GithubConfig()
)

data class JwtConfig(
    var secret: String = "",
    var refreshSecret: String = "",
    var expirationMs: Long = 900000,
    var refreshExpirationMs: Long = 604800000
)

data class EncryptionConfig(
    var key: String = ""
)

data class SecurityConfig(
    var bcryptRounds: Int = 12,
    var maxLoginAttempts: Int = 5,
    var lockoutDurationMinutes: Int = 30
)

data class RateLimitConfig(
    var enabled: Boolean = true,
    var anonymousRpm: Int = 60,
    var authenticatedRpm: Int = 1000,
    var apiRpm: Int = 5000
)

data class GithubConfig(
    var clientId: String = "",
    var clientSecret: String = "",
    var redirectUri: String = ""
)