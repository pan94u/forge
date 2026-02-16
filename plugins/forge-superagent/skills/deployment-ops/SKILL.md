---
name: deployment-ops
description: "Skill for deployment operations — pre-deployment checklists, deployment patterns, rollback procedures, post-deployment verification, and incident response."
stage: operations
type: delivery-skill
---

# Deployment Operations Skill

## Purpose

This skill guides the SuperAgent in safely deploying applications to production environments. It covers the full deployment lifecycle from pre-deployment validation through post-deployment verification, including rollback procedures and incident response.

---

## Pre-Deployment Checklist

Every deployment must pass this checklist before execution:

### Code Readiness
- [ ] All code reviews approved and merged to the release branch
- [ ] All CI pipeline stages passed (build, test, lint, security scan)
- [ ] All baseline checks passed (code-style, security, test-coverage)
- [ ] No unresolved P0/P1 bugs in the release scope
- [ ] Release notes drafted and reviewed

### Infrastructure Readiness
- [ ] Target environment is healthy (no ongoing incidents)
- [ ] Resource capacity is sufficient (CPU, memory, disk, connections)
- [ ] Database migration scripts tested in staging environment
- [ ] Configuration changes prepared (environment variables, feature flags)
- [ ] Secrets and credentials are up to date in the secrets manager

### Operational Readiness
- [ ] Monitoring dashboards are configured for the deployment
- [ ] Alerts are configured for error rate, latency, and resource thresholds
- [ ] On-call engineer is available and notified
- [ ] Rollback plan is documented and reviewed
- [ ] Communication channels are set up (deployment channel, stakeholder notification)

### Dependency Readiness
- [ ] Dependent services are compatible with the new version
- [ ] API contract changes are backward-compatible (or consumers are updated)
- [ ] Database schema changes are backward-compatible with the current version
- [ ] External service dependencies are available and within SLA

### Approval
- [ ] Release manager has approved the deployment
- [ ] Change management ticket is created (if required by process)
- [ ] Deployment window is confirmed (avoid peak traffic periods)

---

## Deployment Patterns

### Rolling Update

**When to use**: Low-risk changes, backward-compatible, no database migrations that break the old version.

```yaml
# Kubernetes Rolling Update
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  template:
    spec:
      containers:
        - name: order-service
          image: registry.example.com/order-service:v2.1.0
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
```

**Process**:
1. Update image tag in deployment manifest
2. Apply deployment: `kubectl apply -f deployment.yaml`
3. Monitor rollout: `kubectl rollout status deployment/order-service`
4. Verify health: check readiness and liveness probes
5. Monitor metrics for 15 minutes

**Rollback**: `kubectl rollout undo deployment/order-service`

### Blue-Green Deployment

**When to use**: Medium-risk changes, need instant rollback, can afford double resources temporarily.

**Process**:
1. **Blue environment** (current): serving all production traffic
2. **Deploy to Green environment**: new version
3. **Verify Green**:
   - Run smoke tests against Green endpoint
   - Check health endpoints
   - Verify configuration
4. **Switch traffic**: Update load balancer/ingress to point to Green
5. **Monitor**: Watch metrics for 15-30 minutes
6. **Decommission Blue**: After confidence period, scale down Blue

**Rollback**: Switch load balancer/ingress back to Blue (instant, seconds).

```yaml
# Ingress switch (Blue → Green)
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: order-service-ingress
spec:
  rules:
    - host: order-service.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: order-service-green  # Changed from order-service-blue
                port:
                  number: 80
```

### Canary Deployment

**When to use**: Higher-risk changes, want to validate with a small percentage of traffic first.

**Process**:
1. Deploy canary alongside current version (1 pod of new, N pods of old)
2. Route 5% of traffic to canary
3. Monitor canary metrics for 10 minutes:
   - Error rate comparison: canary vs. stable
   - Latency comparison: canary vs. stable
   - Business metric comparison
4. If metrics are healthy: increase to 25%, then 50%, then 100%
5. If metrics degrade: route 0% to canary, investigate

**Rollback**: Scale canary to 0, all traffic goes to stable version.

```yaml
# Canary with Istio VirtualService
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: order-service
spec:
  hosts:
    - order-service
  http:
    - route:
        - destination:
            host: order-service
            subset: stable
          weight: 95
        - destination:
            host: order-service
            subset: canary
          weight: 5
```

### Feature Flag Deployment

**When to use**: Behavioral changes that can be toggled without redeployment.

**Process**:
1. Deploy code with feature behind a flag (disabled by default)
2. Verify deployment is stable (no impact since flag is off)
3. Enable flag for internal users / staging
4. Verify feature behavior with internal testing
5. Enable flag for a percentage of production users
6. Gradually increase percentage while monitoring
7. Once at 100% and stable, remove the flag from code (cleanup)

**Rollback**: Disable the feature flag (instant, no redeployment).

---

## Rollback Procedures

### Rollback Decision Matrix

| Signal                          | Severity | Action                          |
|---------------------------------|----------|---------------------------------|
| Error rate > 5% (vs baseline)  | Critical | Immediate rollback              |
| Latency p99 > 2x baseline      | High     | Rollback within 5 minutes       |
| Core feature broken             | Critical | Immediate rollback              |
| Non-critical feature broken     | Medium   | Assess: hotfix vs rollback      |
| Data corruption detected        | Critical | Immediate rollback + incident   |
| No user impact, error in logs   | Low      | Investigate, no rollback needed  |

### Rollback Steps

1. **Announce**: Notify team that rollback is starting
2. **Execute rollback**:
   - **Kubernetes**: `kubectl rollout undo deployment/<service>`
   - **Blue-green**: Switch ingress back to previous environment
   - **Canary**: Set canary weight to 0%
   - **Feature flag**: Disable the flag
3. **Verify rollback**:
   - Health checks passing
   - Error rate returning to baseline
   - Key functionality working
4. **Database rollback** (if applicable):
   - Run rollback migration scripts
   - Verify data integrity
   - **WARNING**: Database rollbacks may cause data loss — assess carefully
5. **Post-rollback**:
   - Notify stakeholders
   - Create incident ticket
   - Schedule post-mortem

### Database Migration Rollback

Database migrations require special care:

- **Additive migrations** (add column, add table): Usually do not need rollback
- **Destructive migrations** (drop column, rename): Always require a rollback script
- **Data migrations**: Must be tested with production-like data volumes
- **Rule**: Every migration file must have a corresponding rollback file

---

## Post-Deployment Verification

### Smoke Tests

Run immediately after deployment (within 2 minutes):

```bash
#!/bin/bash
# Smoke test script
BASE_URL="https://order-service.example.com"

# Health check
curl -sf "$BASE_URL/actuator/health" || exit 1

# Create order (happy path)
RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SMOKE_TEST_TOKEN" \
  -d '{"customerId": "smoke-test-customer", "items": [{"productId": "smoke-test-product", "quantity": 1}]}')

echo "$RESPONSE" | jq -e '.id' || exit 1

# Get order (verify persistence)
ORDER_ID=$(echo "$RESPONSE" | jq -r '.id')
curl -sf "$BASE_URL/api/v1/orders/$ORDER_ID" \
  -H "Authorization: Bearer $SMOKE_TEST_TOKEN" || exit 1

echo "Smoke tests passed"
```

### Metric Verification

Monitor these metrics for 15-30 minutes post-deployment:

| Metric              | Check                                | Threshold              |
|---------------------|--------------------------------------|------------------------|
| Error rate (5xx)    | Compare to pre-deploy baseline       | < 0.1% increase        |
| Latency p50         | Compare to pre-deploy baseline       | < 10% increase         |
| Latency p99         | Compare to pre-deploy baseline       | < 25% increase         |
| CPU utilization     | Check for unexpected increase        | < 80%                  |
| Memory utilization  | Check for leaks (steady increase)    | < 80%                  |
| Request throughput  | Compare to expected levels           | Within normal range    |
| Database connections| Check pool utilization               | < 80% of pool          |

### Health Check Endpoints

Every service must expose:

```
GET /actuator/health          → Overall health
GET /actuator/health/readiness → Ready to receive traffic
GET /actuator/health/liveness  → Process is alive
GET /actuator/info             → Version, build info, git commit
```

---

## Incident Response Playbook

### Severity Levels

| Level | Description                           | Response Time | Communication        |
|-------|---------------------------------------|--------------|----------------------|
| SEV1  | Complete service outage               | 5 minutes    | All hands, exec notify|
| SEV2  | Major feature broken, data at risk    | 15 minutes   | On-call + team lead  |
| SEV3  | Minor feature broken, workaround exists| 1 hour      | On-call              |
| SEV4  | Cosmetic issue, no user impact        | Next business day | Ticket          |

### Incident Response Steps

1. **Detect**: Alert fires or user report received
2. **Acknowledge**: On-call engineer acknowledges within response time
3. **Assess**: Determine severity and scope of impact
4. **Communicate**: Notify stakeholders per severity level
5. **Mitigate**: Take immediate action to reduce impact (rollback, scale, feature flag)
6. **Resolve**: Fix the root cause
7. **Verify**: Confirm the fix resolves the issue
8. **Post-mortem**: Document timeline, root cause, and action items

### Post-Mortem Template

```markdown
# Incident Post-Mortem: [Title]

## Summary
- **Date**: [date]
- **Duration**: [start time] - [end time] ([duration])
- **Severity**: SEV[1-4]
- **Impact**: [who/what was affected]
- **Root Cause**: [one sentence]

## Timeline
| Time  | Event                                    | Actor  |
|-------|------------------------------------------|--------|
| 14:00 | Deploy v2.1.0 to production              | CI/CD  |
| 14:05 | Error rate alert fires                   | PagerDuty |
| 14:07 | On-call acknowledges                     | [name] |
| 14:10 | Root cause identified: DB migration error| [name] |
| 14:12 | Rollback initiated                       | [name] |
| 14:15 | Rollback complete, service recovering    | [name] |
| 14:20 | All metrics back to normal               | Auto   |

## Root Cause
[Detailed description of what went wrong and why]

## What Went Well
- [thing that worked]

## What Went Poorly
- [thing that did not work]

## Action Items
| # | Action | Owner | Due Date | Status |
|---|--------|-------|----------|--------|
| 1 | [action] | [owner] | [date] | Open |

## Lessons Learned
[Key takeaways for future prevention]
```
