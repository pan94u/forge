package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "org_db_connections")
class OrgDbConnectionEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "org_id", nullable = false, length = 36)
    val orgId: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "jdbc_url", nullable = false, length = 500)
    var jdbcUrl: String,

    @Column(length = 100)
    var username: String? = null,

    @Column(name = "password_encrypted", columnDefinition = "TEXT")
    var passwordEncrypted: String? = null,

    @Column(name = "access_level", nullable = false, length = 20)
    var accessLevel: String = "FULL_READ",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
