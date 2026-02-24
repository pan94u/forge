package com.forge.webide.repository

import com.forge.webide.entity.KnowledgeDocumentEntity
import com.forge.webide.model.KnowledgeScope
import org.springframework.data.jpa.repository.JpaRepository

interface KnowledgeDocumentRepository : JpaRepository<KnowledgeDocumentEntity, String> {
    fun findByScopeAndScopeId(scope: KnowledgeScope, scopeId: String?): List<KnowledgeDocumentEntity>
    fun findByScopeIn(scopes: List<KnowledgeScope>): List<KnowledgeDocumentEntity>
    fun findByScopeAndScopeIdIn(scope: KnowledgeScope, scopeIds: List<String>): List<KnowledgeDocumentEntity>
    fun findByTitleAndScope(title: String, scope: KnowledgeScope): KnowledgeDocumentEntity?
}
