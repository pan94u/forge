package com.forge.user.service

import com.forge.user.config.AppConfig
import com.forge.user.entity.IdentityProvider
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * GitHub OAuth2 服务
 * 负责 GitHub OAuth 登录流程
 */
@Service
class GithubOAuthService(
    private val appConfig: AppConfig,
    private val identityService: IdentityService,
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(GithubOAuthService::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
        .build()

    /**
     * 生成 GitHub OAuth 授权 URL
     */
    fun getAuthorizationUrl(state: String, redirectUri: String): String {
        return UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
            .queryParam("client_id", appConfig.github.clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "read:user user:email")
            .queryParam("state", state)
            .toUriString()
    }

    /**
     * 交换 code 获取 access token
     */
    fun exchangeCodeForToken(code: String): GithubTokenResponse {
        val tokenResponse = WebClient.builder()
            .baseUrl("https://github.com/login/oauth/access_token")
            .build()
            .post()
            .uri {
                it.queryParam("client_id", appConfig.github.clientId)
                    .queryParam("client_secret", appConfig.github.clientSecret)
                    .queryParam("code", code)
                    .build()
            }
            .header(HttpHeaders.ACCEPT, "application/json")
            .retrieve()
            .bodyToMono<GithubTokenResponse>()
            .block(Duration.ofSeconds(10))
            ?: throw OAuthException("Failed to exchange code for token")

        if (tokenResponse.error != null) {
            throw OAuthException(tokenResponse.errorDescription ?: "GitHub OAuth error")
        }

        return tokenResponse
    }

    /**
     * 获取 GitHub 用户信息
     */
    fun getUserInfo(accessToken: String): GithubUserResponse {
        return webClient.get()
            .uri("/user")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .bodyToMono(GithubUserResponse::class.java)
            .block(Duration.ofSeconds(10))
            ?: throw OAuthException("Failed to get GitHub user info")
    }

    /**
     * 获取用户邮箱（如果 private）
     */
    fun getUserEmails(accessToken: String): List<GithubEmail> {
        return webClient.get()
            .uri("/user/emails")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .bodyToMono<List<GithubEmail>>()
            .block(Duration.ofSeconds(10))
            ?: emptyList()
    }

    /**
     * 处理 GitHub OAuth 登录/注册
     */
    fun handleOAuthCallback(code: String): LoginResult {
        // 1. 交换 token
        val tokenResponse = exchangeCodeForToken(code)

        // 2. 获取 GitHub 用户信息
        val githubUser = getUserInfo(tokenResponse.accessToken)

        // 3. 查找或创建用户
        var user = identityService.findUserByIdentity(IdentityProvider.GITHUB, githubUser.id.toString())

        if (user == null) {
            // 创建新用户
            val email = githubUser.email ?: getVerifiedEmail(tokenResponse.accessToken)
            val username = generateUsername(githubUser.login)

            val registerRequest = com.forge.user.dto.RegisterRequest(
                username = username,
                password = generateRandomPassword(), // 随机密码，第三方登录用户可重置
                email = email,
                phone = null
            )

            user = userService.register(registerRequest)

            // 绑定 GitHub 账号
            identityService.bindIdentity(user.id, IdentityProvider.GITHUB, githubUser.id.toString())
        } else {
            // 已存在用户，检查是否绑定 GitHub
            if (!identityService.isBound(user.id, IdentityProvider.GITHUB)) {
                identityService.bindIdentity(user.id, IdentityProvider.GITHUB, githubUser.id.toString())
            }
        }

        logger.info("GitHub login: user=${user.username}, githubId=${githubUser.id}")

        return LoginResult(
            user = user,
            provider = IdentityProvider.GITHUB
        )
    }

    /**
     * 获取已验证的邮箱
     */
    private fun getVerifiedEmail(accessToken: String): String? {
        val emails = getUserEmails(accessToken)
        return emails.find { it.verified && it.primary }?.email
    }

    /**
     * 生成用户名（避免重复）
     */
    private fun generateUsername(login: String): String {
        var username = login.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (username.length < 3) {
            username = "user_$username"
        }

        // 检查是否重复
        var counter = 1
        var finalUsername = username
        while (true) {
            try {
                userService.getUserByUsername(finalUsername)
                finalUsername = "${username}${counter}"
                counter++
            } catch (e: Exception) {
                break
            }
        }

        return finalUsername
    }

    /**
     * 生成随机密码
     */
    private fun generateRandomPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%"
        return (1..32).map { chars[java.security.SecureRandom().nextInt(chars.length)] }.joinToString("")
    }

    /**
     * 检查 GitHub OAuth 是否已配置
     */
    fun isConfigured(): Boolean {
        return appConfig.github.clientId.isNotBlank() && appConfig.github.clientSecret.isNotBlank()
    }
}

// GitHub OAuth 响应
data class GithubTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val scope: String?,
    val error: String?,
    val errorDescription: String?
)

data class GithubUserResponse(
    val id: Long,
    val login: String,
    val email: String?,
    val name: String?,
    val avatarUrl: String?
)

data class GithubEmail(
    val email: String,
    val primary: Boolean,
    val verified: Boolean
)

data class LoginResult(
    val user: com.forge.user.entity.UserEntity,
    val provider: IdentityProvider
)

class OAuthException(message: String) : RuntimeException(message)