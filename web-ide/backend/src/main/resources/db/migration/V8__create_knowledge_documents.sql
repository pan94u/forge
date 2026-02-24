-- =============================================================================
-- V8: Create knowledge_documents table for persistent knowledge with scope
-- Supports Global / Workspace / Personal scope layering
-- =============================================================================

CREATE TABLE knowledge_documents (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    snippet VARCHAR(1000) NOT NULL DEFAULT '',
    author VARCHAR(255) NOT NULL DEFAULT '',
    tags VARCHAR(2000) NOT NULL DEFAULT '[]',
    scope VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',
    scope_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_docs_scope ON knowledge_documents(scope);
CREATE INDEX idx_knowledge_docs_scope_id ON knowledge_documents(scope, scope_id);
CREATE INDEX idx_knowledge_docs_type ON knowledge_documents(type);
