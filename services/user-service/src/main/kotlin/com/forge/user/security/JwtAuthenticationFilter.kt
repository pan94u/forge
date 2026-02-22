package com.forge.user.security

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.lang.NonNull
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

/**
 * User Service 认证过滤器
 *
 * 设计原则:
 * - JWT 验证由 Gateway 负责
 * - User Service 只从请求头读取用户信息
 * - 支持从 X-User-Id 和 user-account 头获取用户信息
 *
 * 请求头来源:
 * - X-User-Id: 用户 ID
 * - user-account: 用户账户信息 (JSON)
 * - X-User-Roles: 用户角色列表 (逗号分隔)
 */
@Component
class JwtAuthenticationFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
        const val HEADER_USER_ACCOUNT = "user-account"
        const val HEADER_USER_ROLES = "X-User-Roles"
    }

    private val objectMapper = ObjectMapper()

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        @NonNull request: HttpServletRequest,
        @NonNull response: HttpServletResponse,
        @NonNull filterChain: FilterChain
    ) {
        val xUserId = request.getHeader(HEADER_USER_ID)
        val userAccount = request.getHeader(HEADER_USER_ACCOUNT)
        val xUserRoles = request.getHeader(HEADER_USER_ROLES)

        // 优先从 user-account JSON 解析
        if (!userAccount.isNullOrBlank()) {
            try {
                val userNode = objectMapper.readTree(userAccount)
                val userId = userNode.get("userId")?.asText() ?: xUserId
                val username = userNode.get("username")?.asText() ?: ""
                val rolesNode = userNode.get("roles")
                val roles = if (rolesNode != null && rolesNode.isArray) {
                    rolesNode.map { it.asText() }
                } else {
                    xUserRoles?.split(",")?.filter { it.isNotBlank() } ?: listOf("user")
                }

                setAuthentication(userId, username, roles, request)
            } catch (e: Exception) {
                // JSON 解析失败，尝试从单独的头读取
                if (!xUserId.isNullOrBlank()) {
                    val roles = xUserRoles?.split(",")?.filter { it.isNotBlank() } ?: listOf("user")
                    setAuthentication(xUserId, "", roles, request)
                }
            }
        }
        // 其次从 X-User-Id 头读取
        else if (!xUserId.isNullOrBlank()) {
            val roles = xUserRoles?.split(",")?.filter { it.isNotBlank() } ?: listOf("user")
            setAuthentication(xUserId, "", roles, request)
        }
        // 如果请求带有 Authorization 头，尝试 JWT 验证 (兼容直接访问)
        else {
            val authHeader = request.getHeader("Authorization")
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // JWT 验证由 SecurityConfig 中的 JwtService 处理
                // 这里不做处理，让后续的 FilterChain 处理
            }
        }

        filterChain.doFilter(request, response)
    }

    /**
     * 设置 Spring Security 认证信息
     */
    private fun setAuthentication(
        userId: String,
        username: String,
        roles: List<String>,
        request: HttpServletRequest
    ) {
        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authentication
    }
}