-- =============================================================================
-- V1: Create chat persistence tables
-- =============================================================================

CREATE TABLE chat_sessions (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL REFERENCES chat_sessions(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tool_calls (
    id VARCHAR(36) PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL REFERENCES chat_messages(id),
    tool_name VARCHAR(255) NOT NULL,
    input TEXT,
    output TEXT,
    status VARCHAR(20) DEFAULT 'complete',
    duration_ms BIGINT
);

CREATE INDEX idx_sessions_workspace ON chat_sessions(workspace_id);
CREATE INDEX idx_messages_session ON chat_messages(session_id);
CREATE INDEX idx_tool_calls_message ON tool_calls(message_id);
