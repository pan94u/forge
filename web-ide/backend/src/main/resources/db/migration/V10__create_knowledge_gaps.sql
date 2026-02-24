-- =============================================================================
-- V10: Create knowledge_gaps table for persistent knowledge gap tracking
-- Replaces in-memory ConcurrentHashMap in KnowledgeGapDetectorService
-- =============================================================================

CREATE TABLE knowledge_gaps (
    id VARCHAR(36) PRIMARY KEY,
    query TEXT NOT NULL,
    context TEXT,
    topic VARCHAR(500),
    hit_count INT NOT NULL DEFAULT 1,
    workspace_id VARCHAR(255),
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP,
    auto_stub_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kg_resolved ON knowledge_gaps(resolved);
CREATE INDEX idx_kg_topic ON knowledge_gaps(topic);
CREATE INDEX idx_kg_workspace ON knowledge_gaps(workspace_id);
CREATE INDEX idx_kg_created_at ON knowledge_gaps(created_at);
