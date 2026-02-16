# /deploy-checklist

## Purpose

Generate a pre-deployment checklist customized to the specific service and environment being deployed. Ensures all safety checks, approvals, and verification steps are completed before and after deployment.

## Usage

```
/deploy-checklist <service-name> --env <environment>
```

**Arguments:**
- `service-name` (required): Name of the service being deployed
- `--env`: Target environment: dev, staging, production (default: staging)

## Steps

1. Identify the service and target environment
2. Retrieve the service profile from knowledge base for deployment-specific notes
3. Generate the appropriate checklist based on environment:

### Pre-Deployment Checklist

**For all environments:**
- [ ] All tests pass on the deployment branch
- [ ] Code review approved and merged
- [ ] Container image built and pushed to registry
- [ ] Image vulnerability scan passed (no critical/high findings)
- [ ] Configuration changes reviewed (ConfigMaps, Secrets, env vars)
- [ ] Database migrations tested (if applicable)

**Additional for staging:**
- [ ] Integration tests pass against staging dependencies
- [ ] Performance baseline captured
- [ ] Rollback plan documented

**Additional for production:**
- [ ] Staging deployment verified and stable for 24+ hours
- [ ] Change management ticket created and approved
- [ ] On-call team notified of upcoming deployment
- [ ] Rollback runbook reviewed and ready
- [ ] Monitoring dashboards open and baseline metrics noted
- [ ] Communication sent to stakeholders

### Post-Deployment Checklist

- [ ] Health checks passing on new pods
- [ ] No increase in error rates (compare to baseline)
- [ ] Latency within acceptable range (compare to baseline)
- [ ] Smoke tests passing
- [ ] No new alerts triggered
- [ ] Deployment recorded in change log

4. Present the checklist to the user with checkboxes
5. Offer to help verify any specific items using MCP tools

## Error Handling

- If the service is not found in the knowledge base, generate a generic checklist
- Warn if deploying to production without a staging verification record
