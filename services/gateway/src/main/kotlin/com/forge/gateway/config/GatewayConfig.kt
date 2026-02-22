package com.forge.gateway.config

import com.forge.gateway.filter.JwtAuthenticationFilter
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Gateway 路由配置
// /api/auth/** 放行，其他需要 JWT 认证
@Configuration
class GatewayConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {
    companion object {
        private const val USER_SERVICE_URL = "http://localhost:8086"
        private const val WEB_IDE_BACKEND_URL = "http://localhost:8080"
    }

    @Bean
    fun routeLocator(builder: RouteLocatorBuilder): RouteLocator {
        return builder.routes()
            // ========================================
            // User Service 路由 (8086)
            // ========================================

            // 认证路由 - 放行
            .route("auth") { r ->
                r.path("/api/auth/**").uri(USER_SERVICE_URL)
            }
            // 用户相关路由 - 需要 JWT
            .route("users") { r ->
                r.path("/api/users/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(USER_SERVICE_URL)
            }
            // 组织路由 - 需要 JWT
            .route("orgs") { r ->
                r.path("/api/orgs/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(USER_SERVICE_URL)
            }
            // 角色路由 - 需要 JWT
            .route("roles") { r ->
                r.path("/api/roles/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(USER_SERVICE_URL)
            }

            // ========================================
            // Web IDE Backend 路由 (8080)
            // ========================================

            // AI Chat 聊天路由 - 需要 JWT
            .route("chat") { r ->
                r.path("/api/chat/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }
            // Knowledge 知识库路由 - 需要 JWT
            .route("knowledge") { r ->
                r.path("/api/knowledge/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }
            // MCP 工具调用路由 - 需要 JWT
            .route("mcp") { r ->
                r.path("/api/mcp/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }
            // Context 上下文路由 - 需要 JWT
            .route("context") { r ->
                r.path("/api/context/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }
            // Models 模型路由 - 需要 JWT
            .route("models") { r ->
                r.path("/api/models/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }
            // User Model Config 用户模型配置路由 - 需要 JWT
            .route("user-model-configs") { r ->
                r.path("/api/user/model-configs/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }
            // Workflows 工作流路由 - 需要 JWT
            .route("workflows") { r ->
                r.path("/api/workflows/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }
            // Workspaces 工作空间路由 - 需要 JWT
            .route("workspaces") { r ->
                r.path("/api/workspaces/**")
                    .filters { f -> f.filter(jwtAuthenticationFilter.apply(JwtAuthenticationFilter.Config())) }
                    .uri(WEB_IDE_BACKEND_URL)
            }

            // ========================================
            // 健康检查 - 放行
            // ========================================
            .route("health") { r ->
                r.path("/actuator/health/**", "/actuator/info")
                    .uri(USER_SERVICE_URL)
            }
            .build()
    }
}
