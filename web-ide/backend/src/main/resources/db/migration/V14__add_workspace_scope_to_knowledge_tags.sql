-- V14: Add workspace scope to knowledge_tags
-- Compound ID format: {workspaceId}_{tagKey} needs ~52 characters

ALTER TABLE knowledge_tags ALTER COLUMN id TYPE VARCHAR(100);

ALTER TABLE knowledge_tags ADD COLUMN workspace_id VARCHAR(36);
ALTER TABLE knowledge_tags ADD COLUMN tag_key VARCHAR(50);

-- Backfill existing global templates
UPDATE knowledge_tags SET tag_key = id WHERE workspace_id IS NULL;

CREATE INDEX idx_knowledge_tags_workspace ON knowledge_tags(workspace_id);
CREATE INDEX idx_knowledge_tags_workspace_sort ON knowledge_tags(workspace_id, sort_order);
