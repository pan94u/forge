CREATE TABLE governance_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36) NOT NULL,
    domain VARCHAR(50) NOT NULL,
    snapshot_data TEXT NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gov_snapshots_org_domain ON governance_snapshots (org_id, domain);
CREATE INDEX idx_gov_snapshots_period ON governance_snapshots (period_start, period_end);

CREATE TABLE process_flows (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(36),
    workspace_id VARCHAR(36),
    flow_name VARCHAR(255) NOT NULL,
    flow_type VARCHAR(50),
    raw_mermaid TEXT NOT NULL,
    parsed_nodes TEXT,
    parsed_edges TEXT,
    node_count INT,
    edge_count INT,
    extracted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source_tag_id VARCHAR(36)
);

CREATE INDEX idx_process_flows_org ON process_flows (org_id);
CREATE INDEX idx_process_flows_workspace ON process_flows (workspace_id);
