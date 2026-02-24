package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Persistent knowledge gap record (Phase 8.3).
 * Replaces the in-memory ConcurrentHashMap in KnowledgeGapDetectorService.
 */
@Entity
@Table(name = "knowledge_gaps")
class KnowledgeGapEntity(
    @Id
    val id: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val query: String,

    @Column(columnDefinition = "TEXT")
    val context: String? = null,

    @Column
    val topic: String? = null,

    @Column(name = "hit_count", nullable = false)
    var hitCount: Int = 1,

    @Column(name = "workspace_id")
    val workspaceId: String? = null,

    @Column(nullable = false)
    var resolved: Boolean = false,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "auto_stub_created", nullable = false)
    var autoStubCreated: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
