package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "user_model_configs")
class UserModelConfigEntity(
    @Id
    val id: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,

    @Column(name = "api_key_encrypted", length = 1024)
    var apiKeyEncrypted: String = "",

    @Column(name = "base_url", length = 512)
    var baseUrl: String = "",

    @Column(name = "region", length = 50)
    var region: String = "",

    @Column(name = "enabled")
    var enabled: Boolean = true,

    @Column(name = "custom_models", columnDefinition = "TEXT")
    var customModels: String = "[]",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
