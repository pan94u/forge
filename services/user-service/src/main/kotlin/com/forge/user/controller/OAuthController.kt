package com.forge.user.controller

import com.forge.user.dto.ApiResponse
import com.forge.user.service.AuthService
import com.forge.user.service.GithubOAuthService
import com.forge.user.service.UserService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth/oauth")
class OAuthController(
    private val githubOAuthService: GithubOAuthService,
    private val authService: AuthService,
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(OAuthController::class.java)

    /**
     * GitHub OAuth 登录 - 第一步：重定向到 GitHub
     * GET /api/auth/oauth/github/authorize?state=xxx&redirectUri=xxx
     */
    data class AuthorizeRequest(
        val state: String,
        val redirectUri: String
    )

    @GetMapping("/github/authorize")
    fun githubAuthorize(
        @RequestParam state: String,
        @RequestParam(required = false) redirectUri: String?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<String>> {
        if (!githubOAuthService.isConfigured()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("GitHub OAuth 未配置"))
        }

        val redirect = redirectUri ?: "${getBaseUrl(request)}/auth/callback/github"
        val authUrl = githubOAuthService.getAuthorizationUrl(state, redirect)

        return ResponseEntity.ok(ApiResponse.success(authUrl, "GitHub 授权 URL"))
    }

    /**
     * GitHub OAuth 回调
     * GET /api/auth/oauth/github/callback?code=xxx&state=xxx
     */
    data class GithubCallbackRequest(
        val code: String,
        val state: String,
        val redirectUri: String?
    )

    @GetMapping("/github/callback")
    fun githubCallback(
        @RequestParam code: String,
        @RequestParam state: String,
        @RequestParam(required = false) redirectUri: String?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val result = githubOAuthService.handleOAuthCallback(code)

            // 直接为用户生成 Token（不验证密码）
            val authResponse = authService.generateTokenForUser(result.user)

            // 更新最后登录
            userService.updateLastLogin(result.user.id, getClientIp(request))

            // 返回认证信息和重定向 URL
            val redirect = redirectUri ?: getBaseUrl(request)
            val response = mapOf(
                "auth" to authResponse,
                "redirectUri" to "$redirect?token=${authResponse.accessToken}"
            )

            ResponseEntity.ok(ApiResponse.success(response))
        } catch (e: Exception) {
            logger.error("GitHub OAuth error: ${e.message}", e)
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("GitHub 登录失败: ${e.message}"))
        }
    }

    /**
     * 检查 OAuth 提供商是否可用
     * GET /api/auth/oauth/providers
     */
    @GetMapping("/providers")
    fun getAvailableProviders(): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        return ResponseEntity.ok(
            ApiResponse.success(
                mapOf(
                    "github" to githubOAuthService.isConfigured()
                )
            )
        )
    }

    private fun getBaseUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val host = request.serverName
        val port = request.serverPort
        return if ((scheme == "http" && port == 80) || (scheme == "https" && port == 443)) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }
    }

    private fun getClientIp(request: HttpServletRequest): String? {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (xForwardedFor.isNullOrBlank()) {
            request.remoteAddr
        } else {
            xForwardedFor.split(",")[0].trim()
        }
    }
}