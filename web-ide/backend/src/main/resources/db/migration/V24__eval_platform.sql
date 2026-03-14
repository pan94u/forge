-- V24: Forge Agent Eval Platform — 智能体评测平台
-- Phase 21 Sprint 21.1: EvalTask Library + EvalRun + EvalResult

CREATE TABLE eval_tasks (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    input TEXT NOT NULL,
    success_criteria TEXT NOT NULL,
    grader_config TEXT,
    task_type VARCHAR(50),
    skill_tags TEXT,
    difficulty VARCHAR(20) DEFAULT 'MEDIUM',
    source VARCHAR(50) DEFAULT 'MANUAL',
    org_id VARCHAR(36),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eval_tasks_org ON eval_tasks (org_id);
CREATE INDEX idx_eval_tasks_type ON eval_tasks (task_type);
CREATE INDEX idx_eval_tasks_active ON eval_tasks (is_active);

CREATE TABLE eval_runs (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255),
    task_ids TEXT NOT NULL,
    model_provider VARCHAR(50),
    model_name VARCHAR(100),
    skill_profile VARCHAR(100),
    pass_k INT DEFAULT 1,
    mode VARCHAR(20) DEFAULT 'PASS_AT_K',
    agent_adapter VARCHAR(50) DEFAULT 'FORGE_INTERNAL',
    agent_endpoint VARCHAR(500),
    status VARCHAR(20) DEFAULT 'PENDING',
    org_id VARCHAR(36),
    triggered_by VARCHAR(50) DEFAULT 'MANUAL',
    total_tasks INT DEFAULT 0,
    completed_tasks INT DEFAULT 0,
    pass_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eval_runs_org ON eval_runs (org_id);
CREATE INDEX idx_eval_runs_status ON eval_runs (status);

CREATE TABLE eval_results (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    attempt_number INT DEFAULT 1,
    status VARCHAR(20),
    total_score DECIMAL(5,2),
    code_grade_passed BOOLEAN,
    code_grade_detail TEXT,
    model_grade_score DECIMAL(5,2),
    model_grade_detail TEXT,
    transcript TEXT,
    workspace_id VARCHAR(36),
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eval_results_run ON eval_results (run_id);
CREATE INDEX idx_eval_results_task ON eval_results (task_id);
