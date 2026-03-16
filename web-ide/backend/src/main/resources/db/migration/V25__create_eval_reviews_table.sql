-- Forge Eval Phase 4: Human review queue table
CREATE TABLE eval_reviews (
    id              UUID PRIMARY KEY,
    grade_id        UUID NOT NULL,
    trial_id        UUID NOT NULL,
    task_id         UUID NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    review_reasons  TEXT DEFAULT '[]',
    auto_score      DOUBLE PRECISION DEFAULT 0.0,
    auto_confidence DOUBLE PRECISION DEFAULT 1.0,
    human_score     DOUBLE PRECISION,
    human_passed    BOOLEAN,
    explanation     TEXT DEFAULT '',
    reviewer        VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_eval_reviews_grade_id ON eval_reviews(grade_id);
CREATE INDEX idx_eval_reviews_status ON eval_reviews(status);
CREATE INDEX idx_eval_reviews_task_id ON eval_reviews(task_id);
