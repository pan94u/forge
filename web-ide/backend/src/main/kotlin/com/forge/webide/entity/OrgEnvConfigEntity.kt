package com.forge.webide.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "org_env_configs")
class OrgEnvConfigEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "org_id", nullable = false, length = 36)
    val orgId: String,

    @Column(nullable = false, length = 50)
    val category: String,

    @Column(name = "config_key", nullable = false, length = 100)
    var configKey: String,

    @Column(name = "config_value", columnDefinition = "TEXT")
    var configValue: String? = null,

    @Column(name = "is_sensitive", nullable = false)
    var isSensitive: Boolean = false,

    @Column(length = 300)
    var description: String? = null
)
