---
name: runbook-knowledge
version: "2.0"
scope: platform
category: knowledge
profile: knowledge
description: "Provides knowledge about operational runbooks, incident response, and troubleshooting procedures"
tags:
  - runbooks
  - operations
  - incident-response
  - troubleshooting
required_tools:
  - forge-knowledge.search_runbooks
  - forge-observability.get_metrics
  - forge-observability.get_recent_alerts
---

## Purpose

This skill equips the agent with operational knowledge from runbooks and incident response playbooks. When the current task involves deployment, incident response, troubleshooting, or operational changes, the agent should use this skill to follow established procedures and avoid known pitfalls.

## Instructions

When working on operational tasks:

1. **Search for relevant runbooks**: Before making operational changes, check if a runbook exists:
   - Use `forge-knowledge.search_runbooks` with the task description
   - Check for both the specific operation and the general category
   - Pay special attention to prerequisite steps and warning sections

2. **Follow runbook steps precisely**: When a runbook exists:
   - Execute steps in the documented order
   - Do not skip steps marked as "required" or "safety check"
   - Record the outcome of each step for audit purposes
   - If a step fails, follow the documented rollback procedure

3. **Check observability data**: Before and after operational changes:
   - Use `forge-observability.get_metrics` to capture baseline metrics
   - Use `forge-observability.get_recent_alerts` to check for active incidents
   - Compare post-change metrics to baseline to verify success

4. **Escalation awareness**: Know when to escalate:
   - If the runbook says "escalate to on-call", do not attempt further remediation
   - If the issue does not match any runbook, flag it for human review

5. **Post-incident knowledge capture**: After resolving an issue:
   - Note if the runbook was accurate and complete
   - Identify any steps that were missing or unclear
   - Suggest runbook updates as part of the task completion

## Quality Criteria

- Operational changes follow documented runbook procedures
- Pre-change and post-change metrics are captured for comparison
- Rollback procedures are identified before making changes
- Escalation thresholds are respected
- Runbook gaps are documented for future improvement

## Anti-patterns

- Making production changes without checking for existing runbooks
- Skipping "safety check" steps in runbooks to save time
- Ignoring active alerts before making operational changes
- Not capturing baseline metrics before changes
