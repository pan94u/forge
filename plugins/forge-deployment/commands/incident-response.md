# /incident-response

## Purpose

Provide structured incident response guidance when a production issue is detected. Walks through triage, diagnosis, mitigation, and post-incident steps following the organization's incident response playbook.

## Usage

```
/incident-response <description> [--severity <level>] [--service <name>]
```

**Arguments:**
- `description` (required): Brief description of the incident
- `--severity`: Incident severity: P1 (critical), P2 (major), P3 (minor), P4 (low)
- `--service`: Affected service name (helps narrow diagnosis)

## Steps

### Phase 1: Triage (first 5 minutes)

1. **Assess severity** based on the description:
   - P1: Complete service outage or data loss affecting all users
   - P2: Significant degradation affecting many users
   - P3: Partial issue affecting some users or non-critical functionality
   - P4: Minor issue with workaround available

2. **Check current system state**:
   - Use `forge-observability.get_recent_alerts` to see active alerts
   - Use `forge-observability.get_metrics` for affected service metrics
   - Note error rates, latency, and availability numbers

3. **Search for matching runbooks**:
   - Use `forge-knowledge.search_runbooks` with the incident description
   - If a runbook matches, follow it from Phase 2 onward

### Phase 2: Diagnosis (5-15 minutes)

4. **Identify the blast radius**:
   - Which services are affected?
   - Which user segments are impacted?
   - Is the issue getting worse or stable?

5. **Check recent changes**:
   - Were there recent deployments to the affected service?
   - Were there configuration changes?
   - Were there infrastructure changes?

6. **Gather evidence**:
   - Error logs from the affected service
   - Metrics showing the onset of the issue
   - Correlation with external events (traffic spike, dependency outage)

### Phase 3: Mitigation (15-30 minutes)

7. **Apply mitigation**:
   - If caused by a recent deployment: initiate rollback
   - If caused by traffic spike: scale up affected services
   - If caused by dependency: activate circuit breakers
   - If root cause unknown: apply the most likely mitigation first

8. **Verify mitigation**:
   - Check if error rates are decreasing
   - Confirm service health checks are passing
   - Verify user-facing functionality is restored

### Phase 4: Communication

9. **Update stakeholders**:
   - Post incident status to the designated channel
   - Include: what happened, current status, ETA for resolution
   - Update status page if applicable

### Phase 5: Post-Incident

10. **Document the incident**:
    - Timeline of events
    - Root cause (or best current hypothesis)
    - Actions taken
    - Follow-up items for preventing recurrence

## Error Handling

- If severity is P1 or P2 and the issue is not resolving, recommend escalating to the on-call engineering lead
- If no matching runbook exists, follow the generic incident response steps above
- Never make changes to production without documenting what is being changed and why
