package com.forge.webide.entity

import com.forge.webide.model.WorkspaceStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "workspaces")
class WorkspaceEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val description: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WorkspaceStatus = WorkspaceStatus.ACTIVE,

    @Column(nullable = false)
    val owner: String = "",

    val repository: String? = null,

    val branch: String? = null,

    @Column(name = "local_path")
    var localPath: String? = null,

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "org_id", length = 36)
    var orgId: String? = null
)
