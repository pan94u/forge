-- =============================================================================
-- V9: Create skill quality records and learned patterns tables
-- Supports 3-layer quality model + self-learning for Skill executions
-- =============================================================================

CREATE TABLE skill_quality_records (
    id VARCHAR(36) PRIMARY KEY,
    skill_name VARCHAR(255) NOT NULL,
    script_path VARCHAR(500),
    workspace_id VARCHAR(255),
    session_id VARCHAR(255),

    -- Execution results
    exit_code INT NOT NULL DEFAULT 0,
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    output_length INT NOT NULL DEFAULT 0,
    output_snippet TEXT,

    -- Quality check results
    overall_status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    layer1_passed BOOLEAN NOT NULL DEFAULT TRUE,
    layer1_details TEXT,
    layer2_passed BOOLEAN,
    layer2_details TEXT,

    -- Auto-fix
    auto_fix_applied BOOLEAN NOT NULL DEFAULT FALSE,
    auto_fix_type VARCHAR(100),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sqr_skill_name ON skill_quality_records(skill_name);
CREATE INDEX idx_sqr_status ON skill_quality_records(overall_status);
CREATE INDEX idx_sqr_created_at ON skill_quality_records(created_at);

CREATE TABLE skill_quality_learned_patterns (
    id VARCHAR(36) PRIMARY KEY,
    skill_name VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(100) NOT NULL,
    pattern_description TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    sample_size INT NOT NULL DEFAULT 0,
    suggestion TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP
);

CREATE INDEX idx_sqlp_skill_name ON skill_quality_learned_patterns(skill_name);
CREATE INDEX idx_sqlp_status ON skill_quality_learned_patterns(status);
