CREATE TABLE organizations (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE org_members (
    org_id VARCHAR(36) REFERENCES organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (org_id, user_id)
);

ALTER TABLE workspaces ADD COLUMN org_id VARCHAR(36) REFERENCES organizations(id);
CREATE INDEX idx_workspaces_org ON workspaces(org_id);

CREATE TABLE org_model_configs (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) REFERENCES organizations(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    api_key_encrypted TEXT,
    base_url VARCHAR(500),
    model_allowlist_json TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (org_id, provider)
);

CREATE TABLE org_db_connections (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    jdbc_url VARCHAR(500) NOT NULL,
    username VARCHAR(100),
    password_encrypted TEXT,
    access_level VARCHAR(20) DEFAULT 'FULL_READ',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE org_env_configs (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) REFERENCES organizations(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    is_sensitive BOOLEAN DEFAULT FALSE,
    description VARCHAR(300),
    UNIQUE (org_id, category, config_key)
);
