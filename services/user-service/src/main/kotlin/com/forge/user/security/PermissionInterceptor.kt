package com.forge.user.security

import com.forge.user.service.PermissionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

@Component
class PermissionInterceptor(
    private val permissionService: PermissionService
) : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(PermissionInterceptor::class.java)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) {
            return true
        }

        // 检查是否有 @RequirePermission 注解
        val annotation = handler.getMethodAnnotation(RequirePermission::class.java)
            ?: return true

        // 从请求头获取用户信息
        val userIdStr = request.getHeader("X-User-Id")
            ?: return true  // 没有用户信息，跳过检查

        val userId = try {
            UUID.fromString(userIdStr)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid user ID: $userIdStr")
            return true
        }

        // 获取组织 ID (如果指定)
        val orgIdStr = request.getHeader("X-User-Org-Id")
        val orgId = if (annotation.orgIdParam.isNotEmpty()) {
            request.getParameter(annotation.orgIdParam)?.let {
                try { UUID.fromString(it) } catch (e: Exception) { null }
            }
        } else if (orgIdStr.isNullOrBlank()) {
            null
        } else {
            try {
                UUID.fromString(orgIdStr)
            } catch (e: Exception) {
                null
            }
        }

        // 检查权限
        val hasPermission = permissionService.hasPermission(
            userId = userId,
            resource = annotation.resource,
            action = annotation.action,
            orgId = orgId
        )

        if (!hasPermission) {
            logger.warn("Permission denied: user=$userId, resource=${annotation.resource}, action=${annotation.action}")
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"success":false,"error":"Permission denied: ${annotation.resource}:${annotation.action}"}""")
            return false
        }

        return true
    }
}