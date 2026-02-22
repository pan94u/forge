package com.forge.user.controller

import com.forge.user.dto.*
import com.forge.user.exception.AuthenticationException
import com.forge.user.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val ipAddress = getClientIp(httpRequest)

        return try {
            val response = authService.register(request)
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "注册成功"))
        } catch (e: Exception) {
            logger.warn("Registration failed: ${e.message}")
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "注册失败"))
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val ipAddress = getClientIp(httpRequest)

        return try {
            val response = authService.login(request, ipAddress)
            ResponseEntity.ok(ApiResponse.success(response, "登录成功"))
        } catch (e: AuthenticationException) {
            logger.warn("Login failed: ${e.message}")
            ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.message ?: "认证失败"))
        } catch (e: Exception) {
            logger.error("Login error: ${e.message}", e)
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("登录失败，请稍后重试"))
        }
    }

    /**
     * 刷新 Token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest,
        @RequestHeader("Authorization") authHeader: String?
    ): ResponseEntity<ApiResponse<TokenRefreshResponse>> {
        val accessToken = authHeader?.substringAfter("Bearer ", "")

        return try {
            val response = authService.refreshToken(request)
            ResponseEntity.ok(ApiResponse.success(response))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Token 刷新失败"))
        }
    }

    /**
     * 用户登出
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    fun logout(
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody body: Map<String, String>?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val accessToken = authHeader?.substringAfter("Bearer ", "") ?: ""
        val refreshToken = body?.get("refreshToken") ?: ""

        return try {
            authService.logout(refreshToken, accessToken)
            ResponseEntity.ok(ApiResponse.success(message = "登出成功"))
        } catch (e: Exception) {
            ResponseEntity.ok(ApiResponse.success(message = "登出成功"))
        }
    }

    /**
     * 获取当前用户信息
     * GET /api/auth/me
     */
    @GetMapping("/me")
    fun me(@RequestHeader("X-User-Id") userId: String): ResponseEntity<ApiResponse<UserResponse>> {
        return try {
            // 从请求头获取用户信息，实际实现中应该调用 UserService
            ResponseEntity.ok(ApiResponse.success(null))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("获取用户信息失败"))
        }
    }

    /**
     * 访客登录
     * POST /api/auth/guest
     * 如果用户不存在则自动创建，返回 guest 角色的 token
     */
    @PostMapping("/guest")
    fun guestLogin(
        @Valid @RequestBody request: GuestLoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val ipAddress = getClientIp(httpRequest)

        return try {
            val response = authService.guestLogin(request, ipAddress)
            ResponseEntity.ok(ApiResponse.success(response, "访客登录成功"))
        } catch (e: Exception) {
            logger.error("Guest login failed: ${e.message}", e)
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("访客登录失败，请稍后重试"))
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