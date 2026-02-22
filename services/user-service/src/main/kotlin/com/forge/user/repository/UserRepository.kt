package com.forge.user.repository

import com.forge.user.entity.UserEntity
import com.forge.user.entity.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {

    fun findByUsername(username: String): Optional<UserEntity>

    fun findByEmail(email: String): Optional<UserEntity>

    fun findByPhone(phone: String): Optional<UserEntity>

    fun existsByUsername(username: String): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByPhone(phone: String): Boolean

    @Query("SELECT u FROM UserEntity u WHERE u.status = :status")
    fun findByStatus(@Param("status") status: UserStatus): List<UserEntity>

    @Query("SELECT u FROM UserEntity u WHERE u.username ILIKE %:keyword% OR u.email ILIKE %:keyword%")
    fun searchByKeyword(@Param("keyword") keyword: String): List<UserEntity>

    @Query("SELECT u FROM UserEntity u WHERE u.id IN :ids")
    fun findAllByIds(@Param("ids") ids: Collection<UUID>): List<UserEntity>
}