package com.forge.user.controller

import com.forge.user.dto.*
import com.forge.user.service.UserService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(UserController::class.java)

    /**
     * 获取当前用户信息
     * GET /api/users/me
     */
    @GetMapping("/me")
    fun getCurrentUser(@RequestHeader("X-User-Id") userId: String): ResponseEntity<ApiResponse<UserResponse>> {
        return try {
            val user = userService.getUserById(UUID.fromString(userId))
            ResponseEntity.ok(ApiResponse.success(userService.toResponse(user)))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("用户不存在"))
        }
    }

    /**
     * 更新当前用户信息
     * PUT /api/users/me
     */
    @PutMapping("/me")
    fun updateCurrentUser(
        @RequestHeader("X-User-Id") userId: String,
        @Valid @RequestBody request: UpdateUserRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        return try {
            val user = userService.updateUser(UUID.fromString(userId), request)
            ResponseEntity.ok(ApiResponse.success(userService.toResponse(user), "更新成功"))
        } catch (e: Exception) {
            logger.warn("Update user failed: ${e.message}")
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "更新失败"))
        }
    }

    /**
     * 修改密码
     * POST /api/users/me/password
     */
    @PostMapping("/me/password")
    fun changePassword(
        @RequestHeader("X-User-Id") userId: String,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        return try {
            userService.changePassword(UUID.fromString(userId), request)
            ResponseEntity.ok(ApiResponse.success(message = "密码修改成功"))
        } catch (e: Exception) {
            logger.warn("Change password failed: ${e.message}")
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.message ?: "密码修改失败"))
        }
    }

    /**
     * 获取用户详情
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: String): ResponseEntity<ApiResponse<UserResponse>> {
        return try {
            val user = userService.getUserById(UUID.fromString(id))
            ResponseEntity.ok(ApiResponse.success(userService.toResponse(user)))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("用户不存在"))
        }
    }

    /**
     * 搜索用户
     * GET /api/users/search?keyword=xxx
     */
    @GetMapping("/search")
    fun searchUsers(@RequestParam keyword: String): ResponseEntity<ApiResponse<List<UserResponse>>> {
        return try {
            val users = userService.searchByKeyword(keyword)
            ResponseEntity.ok(ApiResponse.success(userService.toResponseList(users)))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("搜索失败"))
        }
    }
}