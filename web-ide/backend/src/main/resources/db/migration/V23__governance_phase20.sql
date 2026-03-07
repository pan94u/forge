CREATE TABLE governance_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) NOT NULL,
    domain VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor VARCHAR(255),
    detail TEXT,
    ai_recommendation TEXT,
    human_decision VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_gov_audit_org ON governance_audit_logs(org_id);
CREATE INDEX idx_gov_audit_domain ON governance_audit_logs(org_id, domain);
CREATE INDEX idx_gov_audit_created ON governance_audit_logs(created_at);
