---
name: ops-profile
description: "Skill Profile for the Operations delivery stage. Focused on deployment execution, infrastructure management, monitoring, and incident response."
skills:
  - deployment-ops
  - kubernetes-patterns
  - ci-cd-patterns
baselines: []
hitl-checkpoint: "Release approval — present deployment plan with risk assessment for release manager approval before executing deployment."
---

# Operations Profile — OODA Guidance

## Overview

The Operations Profile activates when the SuperAgent enters the Operations delivery stage. The primary objective is to safely deploy changes to production with proper risk management, monitoring, and rollback capability.

This stage has **no automated baselines** because deployment operations are procedural and environment-dependent. Quality is enforced through the HITL checkpoint (release approval) and post-deployment verification.

---

## Observe

Gather the deployment context:

1. **Read deployment configuration**:
   - Kubernetes manifests (Deployments, Services, ConfigMaps, Secrets)
   - Helm charts and values files
   - Docker images and tags
   - Environment-specific configurations (dev, staging, production)
   - CI/CD pipeline definitions

2. **Assess current state**:
   - What version is currently deployed in each environment?
   - Are there any ongoing incidents or degradations?
   - What is the current resource utilization?
   - Are there any pending migrations or manual steps?

3. **Review the changes to deploy**:
   - What code changes are included? (git log between versions)
   - Are there database migrations?
   - Are there configuration changes?
   - Are there infrastructure changes (new services, scaled resources)?
   - Are there breaking API changes?

4. **Query context servers**:
   - `forge-context-server.getServiceGraph()` — understand service dependencies
   - `forge-metrics-server.getBaselineHistory("development")` — verify all dev baselines passed
   - `forge-knowledge-server.getLessonsLearned("deployment")` — past deployment issues

5. **Check prerequisites**:
   - All tests passing in CI?
   - All baselines passing in Development profile?
   - Code review approved and merged?
   - Database migrations tested in staging?
   - Feature flags configured (if applicable)?

### Observe Checklist
- [ ] Deployment configuration reviewed
- [ ] Current environment state assessed
- [ ] Change set understood
- [ ] Prerequisites verified
- [ ] Dependencies mapped

---

## Orient

Assess risks and form deployment strategy:

1. **Risk assessment**:
   - **Change risk**: How significant are the changes? (new service vs. config change)
   - **Dependency risk**: How many services are affected?
   - **Data risk**: Are there database migrations? Are they reversible?
   - **Traffic risk**: What is the expected traffic during deployment?
   - **Rollback risk**: Can we safely roll back? What would be lost?

2. **Deployment strategy selection**:
   - **Rolling update**: Low-risk changes, backward-compatible, no data migration
   - **Blue-green**: Medium-risk changes, need instant rollback capability
   - **Canary**: Higher-risk changes, want to validate with subset of traffic first
   - **Feature flags**: Behavioral changes that can be toggled without deployment
   - **Maintenance window**: Breaking changes that require downtime

3. **Dependency ordering**:
   - Which services must be deployed first? (e.g., database migrations before app)
   - Which services can be deployed in parallel?
   - Are there cross-service dependencies that require coordinated deployment?

4. **Monitoring plan**:
   - What metrics to watch during deployment?
   - What error rate threshold triggers rollback?
   - What latency threshold triggers investigation?
   - How long to wait after deployment before declaring success?

5. **Rollback plan**:
   - Exact steps to roll back each component
   - Data rollback strategy (if migrations involved)
   - Communication plan for rollback scenario

### Orient Output
A risk-assessed deployment strategy with monitoring and rollback plans.

---

## Decide

Finalize the deployment plan:

1. **Deployment plan document**:
   - Deployment strategy (rolling/blue-green/canary)
   - Step-by-step execution order
   - Expected duration
   - Monitoring checkpoints
   - Rollback triggers and procedures

2. **Pre-deployment checklist**:
   - [ ] All tests passing in CI
   - [ ] All code reviews approved
   - [ ] Database migrations tested in staging
   - [ ] Feature flags configured
   - [ ] Monitoring dashboards prepared
   - [ ] On-call team notified
   - [ ] Rollback plan documented

3. **Communication plan**:
   - Who needs to be notified before deployment?
   - Who needs to be on-call during deployment?
   - What channels to use for status updates?

4. **Success criteria**:
   - Error rate < X% for 15 minutes post-deploy
   - Latency p99 < Xms for 15 minutes post-deploy
   - No increase in error logs
   - Smoke tests passing
   - Key business metrics stable

### Decide Output
A complete deployment plan ready for HITL approval.

---

## Act

Execute the deployment (after HITL approval):

### Pre-Deployment
1. **Verify pre-deployment checklist** — every item must be checked
2. **Notify stakeholders** — deployment is starting
3. **Set up monitoring** — open dashboards, set up alerts
4. **Take snapshot** — record current state for rollback reference

### Deployment Execution
1. **Execute database migrations** (if any):
   - Run migrations in staging first (final verification)
   - Run migrations in production
   - Verify data integrity after migration

2. **Deploy infrastructure changes** (if any):
   - Apply Kubernetes resource changes
   - Scale resources as needed
   - Verify infrastructure health

3. **Deploy application changes**:
   - Follow the selected deployment strategy
   - For canary: start with 5% traffic, monitor, increase to 25%, 50%, 100%
   - For blue-green: deploy to inactive environment, verify, switch traffic
   - For rolling: let Kubernetes handle pod-by-pod replacement

4. **Monitor during deployment**:
   - Watch error rates in real-time
   - Watch latency metrics
   - Check application logs for errors
   - If any metric breaches threshold: **STOP and assess**

### Post-Deployment Verification
1. **Smoke tests**: Run critical path tests against production
2. **Health checks**: Verify all service health endpoints return OK
3. **Metric validation**: Confirm metrics are within success criteria
4. **Log review**: Check for unexpected errors or warnings
5. **Business metric check**: Verify key business metrics are stable

### Rollback (if needed)
1. **Trigger rollback** immediately if:
   - Error rate exceeds threshold
   - Critical functionality is broken
   - Data integrity issues detected
2. **Execute rollback steps** from the rollback plan
3. **Verify rollback** — confirm system is back to previous state
4. **Post-mortem**: Document what went wrong for learning loop

### Post-Deployment
1. **Announce deployment complete**
2. **Update version tracking**
3. **Close deployment ticket**
4. **Log execution data** for learning loop

### Act Output
Successfully deployed application with verification, or rolled-back with incident report.

---

## HITL Checkpoint: Release Approval

**When**: After the deployment plan is complete, before executing the deployment.

**Present to the user**:
1. Deployment plan summary
2. Risk assessment (Low/Medium/High)
3. Change set summary (what is being deployed)
4. Deployment strategy and rationale
5. Rollback plan
6. Pre-deployment checklist status
7. Recommended deployment window

**Wait for**: Release manager approval or revision requests.

**On rejection**: Revise the deployment plan, address concerns, return to **Orient** phase.

---

## Incident Response (Reactive Mode)

When activated in response to a production incident:

### Observe
- Read alert details and error logs
- Check metrics dashboards for anomalies
- Identify the scope of impact (which services, which users)
- Check recent deployments that might be the cause

### Orient
- Correlate symptoms with potential root causes
- Check if rollback would resolve the issue
- Assess business impact and urgency
- Identify subject matter experts to involve

### Decide
- Determine immediate mitigation strategy
- Decide between rollback, hotfix, or configuration change
- Set communication cadence

### Act
- Execute mitigation
- Verify recovery
- Communicate status updates
- Create post-incident report
- Feed learnings back to knowledge base
