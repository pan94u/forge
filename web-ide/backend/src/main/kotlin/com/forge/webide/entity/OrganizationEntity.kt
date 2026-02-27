package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "organizations")
class OrganizationEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(unique = true, nullable = false, length = 50)
    var slug: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
