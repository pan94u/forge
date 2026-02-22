package com.forge.user.service

import com.forge.user.dto.*
import com.forge.user.entity.UserEntity
import com.forge.user.entity.UserStatus
import com.forge.user.exception.*
import com.forge.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun register(request: RegisterRequest): UserEntity {
        // 检查用户名是否存在
        if (userRepository.existsByUsername(request.username)) {
            throw UsernameAlreadyExistsException("用户名已存在")
        }

        // 检查邮箱是否存在
        request.email?.let {
            if (userRepository.existsByEmail(it)) {
                throw EmailAlreadyExistsException("邮箱已被注册")
            }
        }

        // 检查手机号是否存在
        request.phone?.let {
            if (userRepository.existsByPhone(it)) {
                throw PhoneAlreadyExistsException("手机号已被注册")
            }
        }

        // 密码加密
        val passwordHash = passwordEncoder.encode(request.password)

        // 创建用户
        val user = UserEntity(
            username = request.username,
            passwordHash = passwordHash,
            email = request.email,
            phone = request.phone,
            emailVerified = false,
            phoneVerified = false
        )

        return userRepository.save(user)
    }

    fun getUserById(id: UUID): UserEntity {
        return userRepository.findById(id)
            .orElseThrow { UserNotFoundException("用户不存在") }
    }

    fun getUserByUsername(username: String): UserEntity {
        return userRepository.findByUsername(username)
            .orElseThrow { UserNotFoundException("用户不存在") }
    }

    fun getUserByEmail(email: String): UserEntity {
        return userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("用户不存在") }
    }

    @Transactional
    fun updateUser(id: UUID, request: UpdateUserRequest): UserEntity {
        val user = getUserById(id)

        request.username?.let {
            if (it != user.username && userRepository.existsByUsername(it)) {
                throw UsernameAlreadyExistsException("用户名已存在")
            }
            user.username = it
        }

        request.email?.let {
            if (it != user.email && userRepository.existsByEmail(it)) {
                throw EmailAlreadyExistsException("邮箱已被注册")
            }
            user.email = it
            user.emailVerified = false
        }

        request.avatar?.let { user.avatar = it }
        request.bio?.let { user.bio = it }

        return userRepository.save(user)
    }

    @Transactional
    fun changePassword(id: UUID, request: ChangePasswordRequest) {
        val user = getUserById(id)

        // 验证原密码
        if (!passwordEncoder.matches(request.oldPassword, user.passwordHash)) {
            throw InvalidPasswordException("原密码不正确")
        }

        // 更新密码
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
    }

    @Transactional
    fun updateLastLogin(id: UUID, ipAddress: String?) {
        val user = getUserById(id)
        user.lastLoginAt = Instant.now()
        user.lastLoginIp = ipAddress
        userRepository.save(user)
    }

    @Transactional
    fun updateUserStatus(id: UUID, status: UserStatus) {
        val user = getUserById(id)
        user.status = status
        userRepository.save(user)
    }

    fun searchByKeyword(keyword: String): List<UserEntity> {
        return userRepository.searchByKeyword(keyword)
    }

    /**
     * 创建访客用户
     * 访客用户没有密码，使用随机的 guest_xxx 用户名
     */
    @Transactional
    fun createGuest(email: String?, phone: String?, displayName: String?): UserEntity {
        // 检查邮箱是否已存在
        email?.let {
            val existingByEmail = userRepository.findByEmail(it).orElse(null)
            if (existingByEmail != null) {
                return existingByEmail
            }
        }

        // 检查手机号是否已存在
        phone?.let {
            val existingByPhone = userRepository.findByPhone(it).orElse(null)
            if (existingByPhone != null) {
                return existingByPhone
            }
        }

        // 生成唯一的访客用户名
        val guestUsername = "guest_${System.currentTimeMillis()}_${(1000..9999).random()}"

        // 创建访客用户（不需要密码，因为只能通过邮箱/手机登录）
        val user = UserEntity(
            username = guestUsername,
            passwordHash = "", // 访客用户没有密码
            email = email,
            phone = phone,
            emailVerified = email != null,
            phoneVerified = phone != null
        )

        // 如果有显示名称，设置 bio 作为显示名称的标识
        displayName?.let { user.bio = "Guest: $it" }

        return userRepository.save(user)
    }

    fun toResponse(user: UserEntity): UserResponse {
        return UserResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            phone = user.phone,
            status = user.status,
            avatar = user.avatar,
            bio = user.bio,
            emailVerified = user.emailVerified,
            phoneVerified = user.phoneVerified,
            createdAt = user.createdAt,
            lastLoginAt = user.lastLoginAt
        )
    }

    fun toResponseList(users: List<UserEntity>): List<UserResponse> {
        return users.map { toResponse(it) }
    }
}