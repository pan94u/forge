package com.forge.user.service

import com.forge.user.config.AppConfig
import com.forge.user.dto.*
import com.forge.user.entity.UserEntity
import com.forge.user.entity.UserStatus
import com.forge.user.exception.AuthenticationException
import com.forge.user.exception.UserNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userService: UserService,
    private val jwtService: JwtService,
    private val redisTemplate: StringRedisTemplate,
    private val appConfig: AppConfig,
    private val passwordEncoder: PasswordEncoder
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * 用户注册
     */
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val user = userService.register(request)

        // 生成 Token
        val accessToken = jwtService.generateAccessToken(
            user.id.toString(),
            user.username,
            listOf("user")
        )
        val refreshToken = jwtService.generateRefreshToken(user.id.toString())

        // 更新最后登录
        userService.updateLastLogin(user.id, null)

        logger.info("User registered: ${user.username}")

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = appConfig.jwt.expirationMs / 1000,
            user = userService.toResponse(user)
        )
    }

    /**
     * 用户登录
     */
    @Transactional
    fun login(request: LoginRequest, ipAddress: String?): AuthResponse {
        var user: UserEntity? = null

        // 根据用户名查找
        try {
            user = userService.getUserByUsername(request.username)
        } catch (e: UserNotFoundException) {
            // 用户名不存在，尝试邮箱
            try {
                user = userService.getUserByEmail(request.username)
            } catch (e2: UserNotFoundException) {
                recordLoginLog(null, "password", ipAddress, false, "User not found")
                throw AuthenticationException("用户名或密码不正确")
            }
        }

        // 确保 user 不为空
        val loggedInUser = user ?: throw AuthenticationException("用户不存在")

        // 检查账户状态
        if (loggedInUser.status != UserStatus.ACTIVE) {
            recordLoginLog(loggedInUser.id.toString(), "password", ipAddress, false, "Account ${loggedInUser.status}")
            throw AuthenticationException("账户已被${loggedInUser.status.name.lowercase()}")
        }

        // 检查密码
        if (!passwordEncoder.matches(request.password, loggedInUser.passwordHash)) {
            // 记录登录失败
            recordLoginLog(loggedInUser.id.toString(), "password", ipAddress, false, "Invalid password")
            throw AuthenticationException("用户名或密码不正确")
        }

        // 检查登录失败次数
        val failKey = "login:fail:${loggedInUser.id}"
        val failCount = redisTemplate.opsForValue().get(failKey)?.toIntOrNull() ?: 0

        if (failCount >= appConfig.security.maxLoginAttempts) {
            recordLoginLog(loggedInUser.id.toString(), "password", ipAddress, false, "Too many attempts")
            throw AuthenticationException("登录失败次数过多，请${appConfig.security.lockoutDurationMinutes}分钟后重试")
        }

        // 生成 Token
        val accessToken = jwtService.generateAccessToken(
            loggedInUser.id.toString(),
            loggedInUser.username,
            listOf("user")
        )
        val refreshToken = jwtService.generateRefreshToken(loggedInUser.id.toString())

        // 清除失败计数
        redisTemplate.delete(failKey)

        // 更新最后登录
        userService.updateLastLogin(loggedInUser.id, ipAddress)

        // 记录登录成功
        recordLoginLog(loggedInUser.id.toString(), "password", ipAddress, true)

        logger.info("User logged in: ${loggedInUser.username} from $ipAddress")

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = appConfig.jwt.expirationMs / 1000,
            user = userService.toResponse(loggedInUser)
        )
    }

    /**
     * 刷新 Token
     */
    fun refreshToken(request: RefreshTokenRequest): TokenRefreshResponse {
        val result = jwtService.refreshAccessToken(request.refreshToken)
            ?: throw AuthenticationException("无效的 Refresh Token")

        return TokenRefreshResponse(
            accessToken = result.first,
            expiresIn = appConfig.jwt.expirationMs / 1000
        )
    }

    /**
     * 用户登出
     */
    @Transactional
    fun logout(refreshToken: String, accessToken: String) {
        // 使 Refresh Token 失效
        jwtService.revokeRefreshToken(refreshToken)

        // 将 Access Token 加入黑名单 (剩余有效期)
        val claims = jwtService.validateAccessToken(accessToken)
        val expiresIn = claims?.expiration?.time?.let {
            (it - System.currentTimeMillis()) / 1000
        } ?: 900

        jwtService.addToBlacklist(accessToken, expiresIn)

        logger.info("User logged out")
    }

    /**
     * 记录登录日志
     */
    private fun recordLoginLog(
        userId: String?,
        provider: String,
        ipAddress: String?,
        success: Boolean,
        failureReason: String? = null
    ) {
        // 实际实现中应该调用 AuditService
        logger.info("Login log: userId=$userId, provider=$provider, ip=$ipAddress, success=$success")
    }

    /**
     * 访客登录
     * 如果用户不存在则自动创建，返回 guest 角色的 token
     */
    @Transactional
    fun guestLogin(request: GuestLoginRequest, ipAddress: String?): AuthResponse {
        // 创建或获取访客用户
        val user = userService.createGuest(request.email, request.phone, request.displayName)

        // 生成访客 Token (角色为 guest)
        val accessToken = jwtService.generateAccessToken(
            user.id.toString(),
            user.username,
            listOf("guest")  // 访客角色
        )
        val refreshToken = jwtService.generateRefreshToken(user.id.toString())

        // 更新最后登录
        userService.updateLastLogin(user.id, ipAddress)

        logger.info("Guest login: ${user.email ?: user.phone} -> userId=${user.id}")

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = appConfig.jwt.expirationMs / 1000,
            user = userService.toResponse(user)
        )
    }

    /**
     * 为已存在的用户生成 Token（不验证密码，用于 OAuth 登录）
     */
    fun generateTokenForUser(user: UserEntity, roles: List<String> = listOf("user")): AuthResponse {
        val accessToken = jwtService.generateAccessToken(
            user.id.toString(),
            user.username,
            roles
        )
        val refreshToken = jwtService.generateRefreshToken(user.id.toString())

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = appConfig.jwt.expirationMs / 1000,
            user = userService.toResponse(user)
        )
    }
}