package com.forge.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app")
data class AppConfig(
    var jwt: JwtConfig = JwtConfig(),
    var redis: RedisConfig = RedisConfig(),
    var routes: List<RouteConfig> = emptyList()
)

data class JwtConfig(
    var secret: String = "",
    var issuer: String = "forge-platform"
)

data class RedisConfig(
    var host: String = "localhost",
    var port: Int = 6379
)

data class RouteConfig(
    var id: String = "",
    var uri: String = "",
    var predicates: List<String> = emptyList(),
    var filters: List<String> = emptyList()
)