package com.forge.user.repository

import com.forge.user.entity.PermissionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, UUID> {

    fun findByResourceAndAction(resource: String, action: String): PermissionEntity?

    fun existsByResourceAndAction(resource: String, action: String): Boolean

    @Query("SELECT p FROM PermissionEntity p WHERE p.resource = :resource")
    fun findByResource(@Param("resource") resource: String): List<PermissionEntity>
}