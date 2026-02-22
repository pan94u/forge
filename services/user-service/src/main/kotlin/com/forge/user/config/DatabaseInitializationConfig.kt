package com.forge.user.config

import com.forge.user.entity.UserStatus
import com.forge.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

/**
 * 数据库初始化配置
 * 在应用启动时执行初始化任务：
 * 1. 检查是否存在管理员用户
 * 2. 不存在则创建默认管理员用户
 */
@Configuration
class DatabaseInitializationConfig {

    private val logger = LoggerFactory.getLogger(DatabaseInitializationConfig::class.java)

    companion object {
        // 默认管理员配置
        const val DEFAULT_ADMIN_USERNAME = "admin"
        const val DEFAULT_ADMIN_EMAIL = "admin@forge.local"
        const val DEFAULT_ADMIN_PASSWORD = "ForgeAdmin123!"
    }

    @Bean
    @Profile("!test") // 测试环境跳过
    fun databaseInitializer(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder
    ): CommandLineRunner {
        return CommandLineRunner {
            logger.info("开始数据库初始化检查...")

            // 检查是否存在管理员用户
            val adminExists = userRepository.existsByUsername(DEFAULT_ADMIN_USERNAME)

            if (adminExists) {
                logger.info("管理员用户已存在，跳过创建")
            } else {
                logger.warn("未检测到管理员用户，正在创建默认管理员...")
                createDefaultAdmin(userRepository, passwordEncoder)
            }

            logger.info("数据库初始化检查完成")
        }
    }

    private fun createDefaultAdmin(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder
    ) {
        try {
            val adminUser = com.forge.user.entity.UserEntity(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                username = DEFAULT_ADMIN_USERNAME,
                email = DEFAULT_ADMIN_EMAIL,
                phone = null,
                passwordHash = passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD),
                status = UserStatus.ACTIVE,
                emailVerified = true,
                phoneVerified = false,
                avatar = null,
                bio = "系统管理员账户"
            )

            userRepository.save(adminUser)

            logger.warn("""
                |
                |========================================
                |  ⚠️  默认管理员用户已创建  ⚠️
                |========================================
                |  用户名: $DEFAULT_ADMIN_USERNAME
                |  密码: $DEFAULT_ADMIN_PASSWORD
                |
                |  ⚠️  请立即登录并修改密码！  ⚠️
                |========================================
                |
            """.trimMargin())

        } catch (e: Exception) {
            logger.error("创建管理员用户失败: ${e.message}", e)
            throw e
        }
    }
}

/**
 * 开发环境快速初始化配置
 * 使用固定密码便于开发测试
 */
@Configuration
@Profile("dev")
class DevDatabaseInitializationConfig {

    private val logger = LoggerFactory.getLogger(DevDatabaseInitializationConfig::class.java)

    companion object {
        const val DEV_ADMIN_USERNAME = "admin"
        const val DEV_ADMIN_PASSWORD = "admin123"
    }

    @Bean
    fun devDatabaseInitializer(
        userRepository: UserRepository,
        passwordEncoder: PasswordEncoder
    ): CommandLineRunner {
        return CommandLineRunner {
            val adminExists = userRepository.existsByUsername(DEV_ADMIN_USERNAME)

            if (!adminExists) {
                val adminUser = com.forge.user.entity.UserEntity(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    username = DEV_ADMIN_USERNAME,
                    email = "admin@forge.local",
                    phone = null,
                    passwordHash = passwordEncoder.encode(DEV_ADMIN_PASSWORD),
                    status = UserStatus.ACTIVE,
                    emailVerified = true,
                    phoneVerified = false,
                    avatar = null,
                    bio = "开发环境管理员"
                )
                userRepository.save(adminUser)

                logger.warn("""
                    |
                    |========================================
                    |  ⚠️  开发环境管理员已创建  ⚠️
                    |========================================
                    |  用户名: $DEV_ADMIN_USERNAME
                    |  密码: $DEV_ADMIN_PASSWORD
                    |========================================
                    |
                """.trimMargin())
            }
        }
    }
}