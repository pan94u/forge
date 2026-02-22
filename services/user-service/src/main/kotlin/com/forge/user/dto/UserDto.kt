package com.forge.user.dto

import com.forge.user.entity.UserStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// ==================== 请求 DTO ====================

data class RegisterRequest(
    @field:NotBlank(message = "用户名不能为空")
    @field:Size(min = 3, max = 64, message = "用户名长度必须在3-64之间")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]{3,64}\$", message = "用户名格式不正确，只能包含字母、数字、下划线和连字符")
    val username: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 8, max = 128, message = "密码长度必须在8-128之间")
    val password: String,

    @field:Email(message = "邮箱格式不正确")
    val email: String? = null,

    @field:Pattern(regexp = "^\\+?[1-9]\\d{1,14}\$", message = "手机号格式不正确")
    val phone: String? = null
)

data class LoginRequest(
    @field:NotBlank(message = "用户名不能为空")
    val username: String,

    @field:NotBlank(message = "密码不能为空")
    val password: String
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token 不能为空")
    val refreshToken: String
)

data class UpdateUserRequest(
    @field:Size(max = 64, message = "用户名长度不能超过64")
    val username: String? = null,

    @field:Email(message = "邮箱格式不正确")
    val email: String? = null,

    @field:Size(max = 512, message = "头像URL长度不能超过512")
    val avatar: String? = null,

    @field:Size(max = 500, message = "个人简介不能超过500字符")
    val bio: String? = null
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "原密码不能为空")
    val oldPassword: String,

    @field:NotBlank(message = "新密码不能为空")
    @field:Size(min = 8, max = 128, message = "新密码长度必须在8-128之间")
    val newPassword: String
)

data class UpdateUserStatusRequest(
    val status: UserStatus
)

// 访客登录请求
data class GuestLoginRequest(
    @field:Email(message = "邮箱格式不正确")
    val email: String? = null,

    @field:Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "手机号格式不正确")
    val phone: String? = null,

    // 可选：访客显示名称
    @field:Size(max = 64, message = "名称长度不能超过64")
    val displayName: String? = null
) {
    init {
        require(email != null || phone != null) { "邮箱或手机号至少提供一个" }
    }
}

// ==================== 响应 DTO ====================

data class UserResponse(
    val id: UUID,
    val username: String,
    val email: String?,
    val phone: String?,
    val status: UserStatus,
    val avatar: String?,
    val bio: String?,
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant?
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserResponse
)

data class TokenRefreshResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long
)

data class LogoutResponse(
    val success: Boolean = true,
    val message: String = "Successfully logged out"
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val error: String?
) {
    companion object {
        fun <T> success(data: T? = null, message: String? = null): ApiResponse<T> =
            ApiResponse(success = true, data = data, message = message, error = null)

        fun <T> error(message: String, error: String? = null): ApiResponse<T> =
            ApiResponse(success = false, data = null, message = message, error = error)
    }
}