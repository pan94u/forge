package com.forge.webide.entity

import com.forge.webide.model.DocumentType
import com.forge.webide.model.KnowledgeScope
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "knowledge_documents")
class KnowledgeDocumentEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 500)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val type: DocumentType,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(nullable = false, length = 1000)
    var snippet: String = "",

    @Column(nullable = false)
    val author: String = "",

    @Column(nullable = false, length = 2000)
    var tags: String = "[]",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val scope: KnowledgeScope = KnowledgeScope.GLOBAL,

    @Column(name = "scope_id")
    val scopeId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
