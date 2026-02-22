package com.forge.user.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "username", nullable = false, unique = true, length = 64)
    var username: String,

    @Column(name = "email", unique = true)
    var email: String? = null,

    @Column(name = "phone", unique = true, length = 20)
    var phone: String? = null,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "avatar", length = 512)
    var avatar: String? = null,

    @Column(name = "bio", columnDefinition = "TEXT")
    var bio: String? = null,

    @Column(name = "settings")
    var settings: String = "{}",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "last_login_ip")
    var lastLoginIp: String? = null,

    @Column(name = "email_verified")
    var emailVerified: Boolean = false,

    @Column(name = "phone_verified")
    var phoneVerified: Boolean = false
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now()
    }
}

enum class UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    DELETED
}