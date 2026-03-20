-- Forge Eval: Unified evaluation engine tables
-- Supports multi-platform (Forge/Synapse/Application) evaluation with
-- code-based, model-based, and human grading

-- ── Eval Suites ──────────────────────────────────────────────────────
CREATE TABLE eval_suites (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT DEFAULT '',
    platform    VARCHAR(50) NOT NULL DEFAULT 'FORGE',
    agent_type  VARCHAR(50) NOT NULL DEFAULT 'CODING',
    lifecycle   VARCHAR(50) NOT NULL DEFAULT 'CAPABILITY',
    tags        TEXT DEFAULT '[]',
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_eval_suites_platform ON eval_suites(platform);
CREATE INDEX idx_eval_suites_lifecycle ON eval_suites(lifecycle);

-- ── Eval Tasks ───────────────────────────────────────────────────────
CREATE TABLE eval_tasks (
    id                UUID PRIMARY KEY,
    suite_id          UUID NOT NULL REFERENCES eval_suites(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    description       TEXT DEFAULT '',
    prompt            TEXT NOT NULL,
    context           TEXT DEFAULT '{}',
    reference_answer  TEXT,
    grader_configs    TEXT DEFAULT '[]',
    difficulty        VARCHAR(50) DEFAULT 'MEDIUM',
    tags              TEXT DEFAULT '[]',
    baseline_pass_rate DOUBLE PRECISION DEFAULT 0.8,
    saturation_count  INTEGER DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_eval_tasks_suite_id ON eval_tasks(suite_id);

-- ── Eval Runs ────────────────────────────────────────────────────────
CREATE TABLE eval_runs (
    id              UUID PRIMARY KEY,
    suite_id        UUID NOT NULL REFERENCES eval_suites(id) ON DELETE CASCADE,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    trials_per_task INTEGER DEFAULT 1,
    model           VARCHAR(255),
    summary         TEXT,
    started_at      TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_eval_runs_suite_id ON eval_runs(suite_id);
CREATE INDEX idx_eval_runs_status ON eval_runs(status);

-- ── Eval Trials ──────────────────────────────────────────────────────
CREATE TABLE eval_trials (
    id            UUID PRIMARY KEY,
    run_id        UUID NOT NULL REFERENCES eval_runs(id) ON DELETE CASCADE,
    task_id       UUID NOT NULL REFERENCES eval_tasks(id) ON DELETE CASCADE,
    trial_number  INTEGER NOT NULL,
    outcome       VARCHAR(50) DEFAULT 'ERROR',
    score         DOUBLE PRECISION DEFAULT 0.0,
    duration_ms   BIGINT DEFAULT 0,
    token_usage   TEXT,
    output        TEXT DEFAULT '',
    error_message TEXT,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_eval_trials_run_id ON eval_trials(run_id);
CREATE INDEX idx_eval_trials_task_id ON eval_trials(task_id);

-- ── Eval Transcripts ─────────────────────────────────────────────────
CREATE TABLE eval_transcripts (
    id                UUID PRIMARY KEY,
    trial_id          UUID REFERENCES eval_trials(id) ON DELETE SET NULL,
    source            VARCHAR(50) DEFAULT 'FORGE',
    turns             TEXT DEFAULT '[]',
    tool_call_summary TEXT DEFAULT '[]',
    metadata          TEXT DEFAULT '{}',
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_eval_transcripts_trial_id ON eval_transcripts(trial_id);

-- ── Eval Grades ──────────────────────────────────────────────────────
CREATE TABLE eval_grades (
    id                UUID PRIMARY KEY,
    trial_id          UUID NOT NULL REFERENCES eval_trials(id) ON DELETE CASCADE,
    grader_type       VARCHAR(50) NOT NULL,
    score             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    passed            BOOLEAN NOT NULL DEFAULT FALSE,
    assertion_results TEXT DEFAULT '[]',
    rubric_scores     TEXT DEFAULT '{}',
    explanation       TEXT DEFAULT '',
    confidence        DOUBLE PRECISION DEFAULT 1.0,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_eval_grades_trial_id ON eval_grades(trial_id);
CREATE INDEX idx_eval_grades_grader_type ON eval_grades(grader_type);
