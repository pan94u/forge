-- =============================================================================
-- V11: Create interaction_evaluations table
-- Records 4D evaluation metrics for each AI interaction (Phase 7 Learning Loop)
-- Previously relied on hibernate.ddl-auto=update; now properly migrated
-- =============================================================================

CREATE TABLE IF NOT EXISTS interaction_evaluations (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) DEFAULT '',
    profile VARCHAR(255) NOT NULL,
    mode VARCHAR(50) NOT NULL DEFAULT 'default',
    capability_category VARCHAR(50) DEFAULT '',
    scenario_id VARCHAR(50) DEFAULT '',
    routing_confidence DOUBLE PRECISION DEFAULT 0.0,
    intent_confirmed BOOLEAN NOT NULL DEFAULT FALSE,

    -- Auto-computed 4D scores (0.0 - 1.0)
    intent_score DOUBLE PRECISION DEFAULT 0.0,
    completion_score DOUBLE PRECISION DEFAULT 0.0,
    quality_score DOUBLE PRECISION DEFAULT 0.0,
    experience_score DOUBLE PRECISION DEFAULT 0.0,

    -- Manual 4D scores (0 - 5, set by user)
    manual_intent_score INT,
    manual_completion_score INT,
    manual_quality_score INT,
    manual_experience_score INT,

    -- Metrics
    tool_call_count INT DEFAULT 0,
    tool_success_count INT DEFAULT 0,
    turn_count INT DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,

    user_feedback TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ie_session_id ON interaction_evaluations(session_id);
CREATE INDEX IF NOT EXISTS idx_ie_profile ON interaction_evaluations(profile);
CREATE INDEX IF NOT EXISTS idx_ie_created_at ON interaction_evaluations(created_at);
