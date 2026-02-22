---
name: deployment-ops
description: "Skill for deployment operations — pre-deployment checklists, deployment pattern selection, rollback procedures, post-deployment verification, and incident response."
stage: operations
type: delivery-skill
version: "3.0"
category: delivery
scope: platform
tags: [methodology, operations, deployment, rollback]
---

# Deployment Operations Skill

## Purpose

Guides safe deployment to production: pre-deployment validation, pattern selection, rollback procedures, post-deployment verification, and incident response.

---

## Pre-Deployment Checklist

### Code Readiness
- [ ] All code reviews approved and merged
- [ ] CI pipeline passed (build, test, lint, security scan)
- [ ] All baseline checks passed
- [ ] No unresolved P0/P1 bugs in release scope
- [ ] Release notes drafted

### Infrastructure Readiness
- [ ] Target environment healthy
- [ ] Resource capacity sufficient (CPU, memory, disk)
- [ ] Database migrations tested in staging
- [ ] Configuration/secrets prepared
- [ ] Rollback plan documented

### Operational Readiness
- [ ] Monitoring dashboards configured
- [ ] Alerts configured for error rate, latency, resources
- [ ] On-call engineer available and notified
- [ ] Deployment window confirmed (avoid peak traffic)

---

## Deployment Pattern Selection

| Pattern | When to Use | Rollback Speed |
|---------|------------|----------------|
| **Rolling Update** | Low-risk, backward-compatible changes | ~30s (undo rollout) |
| **Blue-Green** | Medium-risk, need instant rollback | Instant (switch LB) |
| **Canary** | Higher-risk, validate with small traffic % | Fast (set weight to 0) |
| **Feature Flag** | Behavioral changes, toggleable without redeploy | Instant (disable flag) |

For detailed K8s/Istio YAML examples, see `reference/deployment-patterns.md`.

---

## Rollback Decision Matrix

| Signal | Severity | Action |
|--------|----------|--------|
| Error rate > 5% (vs baseline) | Critical | Immediate rollback |
| Latency p99 > 2x baseline | High | Rollback within 5 minutes |
| Core feature broken | Critical | Immediate rollback |
| Non-critical feature broken | Medium | Assess: hotfix vs rollback |
| Data corruption detected | Critical | Immediate rollback + incident |
| No user impact, error in logs | Low | Investigate, no rollback |

### Rollback Steps

1. **Announce** rollback to team
2. **Execute**: undo rollout / switch LB / disable flag
3. **Verify**: health checks passing, error rate returning to baseline
4. **Database rollback** (if applicable): run rollback migrations, verify data integrity
5. **Post-rollback**: notify stakeholders, create incident ticket, schedule post-mortem

---

## Post-Deployment Verification

### Smoke Tests (within 2 minutes)

- Health check endpoint responds
- Primary happy-path API call succeeds
- Response format matches contract

### Metric Monitoring (15-30 minutes)

| Metric | Threshold |
|--------|-----------|
| Error rate (5xx) | < 0.1% increase vs baseline |
| Latency p50/p99 | < 10%/25% increase vs baseline |
| CPU/Memory | < 80% utilization |
| DB connections | < 80% of pool |

---

## Incident Response

### Severity Levels

| Level | Description | Response Time |
|-------|-------------|---------------|
| SEV1 | Complete outage | 5 minutes |
| SEV2 | Major feature broken | 15 minutes |
| SEV3 | Minor feature broken, workaround exists | 1 hour |
| SEV4 | Cosmetic issue | Next business day |

### Response Steps

1. **Detect** → 2. **Acknowledge** → 3. **Assess severity** → 4. **Communicate** → 5. **Mitigate** (rollback/scale/flag) → 6. **Resolve** root cause → 7. **Verify** fix → 8. **Post-mortem**

For post-mortem template, see `reference/post-mortem-template.md`.
