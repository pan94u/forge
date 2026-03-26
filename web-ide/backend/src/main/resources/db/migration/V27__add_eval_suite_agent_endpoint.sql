-- Add agent_endpoint and agent_config to eval_suites for external agent integration
ALTER TABLE eval_suites ADD COLUMN IF NOT EXISTS agent_endpoint VARCHAR(500);
ALTER TABLE eval_suites ADD COLUMN IF NOT EXISTS agent_config TEXT;
