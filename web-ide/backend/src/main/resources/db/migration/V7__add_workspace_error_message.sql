-- =============================================================================
-- V7: Add error_message column to workspaces for async clone error reporting
-- =============================================================================

ALTER TABLE workspaces ADD COLUMN error_message VARCHAR(1000);
