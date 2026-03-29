-- Add total_tasks column to eval_runs for async run progress tracking
ALTER TABLE eval_runs ADD COLUMN IF NOT EXISTS total_tasks INT DEFAULT 0;
UPDATE eval_runs SET total_tasks = 0 WHERE total_tasks IS NULL;
ALTER TABLE eval_runs ALTER COLUMN total_tasks SET NOT NULL;
