package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "org_model_configs")
class OrgModelConfigEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "org_id", nullable = false, length = 36)
    val orgId: String,

    @Column(nullable = false, length = 50)
    val provider: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    var apiKeyEncrypted: String? = null,

    @Column(name = "base_url", length = 500)
    var baseUrl: String? = null,

    @Column(name = "model_allowlist_json", columnDefinition = "TEXT")
    var modelAllowlistJson: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
