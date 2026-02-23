-- =============================================================================
-- V5: Create Memory Management tables + missing Phase 4 tables
-- 3-layer memory architecture for cross-session continuity
-- Also backfills skill_preferences and skill_usage (Phase 4 entities
-- that were missing from migrations)
-- =============================================================================

-- Skill preferences (Phase 4 backfill)
CREATE TABLE skill_preferences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, skill_name)
);

-- Skill usage tracking (Phase 4 backfill)
CREATE TABLE skill_usage (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    script_type VARCHAR(50),
    profile VARCHAR(255) NOT NULL DEFAULT '',
    success BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_usage_skill ON skill_usage(skill_name);
CREATE INDEX idx_skill_usage_session ON skill_usage(session_id);

-- Layer 3: Session Summaries — structured summary per session
CREATE TABLE session_summaries (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    workspace_id VARCHAR(255) NOT NULL,
    profile VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    completed_work TEXT DEFAULT '[]',
    artifacts TEXT DEFAULT '[]',
    decisions TEXT DEFAULT '[]',
    unresolved TEXT DEFAULT '[]',
    next_steps TEXT DEFAULT '[]',
    turn_count INT DEFAULT 0,
    tool_call_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_session_summaries_workspace ON session_summaries(workspace_id);
CREATE INDEX idx_session_summaries_session ON session_summaries(session_id);
CREATE INDEX idx_session_summaries_ws_created ON session_summaries(workspace_id, created_at);

-- Layer 1: Workspace Memory — persistent workspace-level knowledge
CREATE TABLE workspace_memories (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL UNIQUE,
    content TEXT NOT NULL DEFAULT '',
    version INT DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workspace_memories_ws ON workspace_memories(workspace_id);

-- Layer 2: Stage Memory — profile-scoped cross-session aggregation
CREATE TABLE stage_memories (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL,
    profile VARCHAR(255) NOT NULL,
    completed_work TEXT DEFAULT '[]',
    key_decisions TEXT DEFAULT '[]',
    unresolved_issues TEXT DEFAULT '[]',
    next_steps TEXT DEFAULT '[]',
    session_count INT DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workspace_id, profile)
);

CREATE INDEX idx_stage_memories_ws ON stage_memories(workspace_id);
CREATE INDEX idx_stage_memories_ws_profile ON stage_memories(workspace_id, profile);
