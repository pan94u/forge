-- Relax eval_grades.trial_id FK constraint to allow external transcript grades
-- External transcripts don't have a parent trial/run record
ALTER TABLE eval_grades DROP CONSTRAINT IF EXISTS CONSTRAINT_CDC;
ALTER TABLE eval_trials DROP CONSTRAINT IF EXISTS CONSTRAINT_CD;
