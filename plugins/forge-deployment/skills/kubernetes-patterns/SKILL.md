---
name: kubernetes-patterns
version: "2.0"
scope: platform
category: delivery
profile: deployment
description: "Kubernetes deployment patterns, resource configuration, and operational best practices"
tags:
  - kubernetes
  - deployment
  - infrastructure
  - containers
required_tools:
  - forge-knowledge.search_runbooks
---

## Purpose

This skill provides the agent with knowledge of Kubernetes deployment patterns, resource configuration best practices, and operational conventions. When the agent needs to create, modify, or review Kubernetes manifests, Helm charts, or deployment configurations, it should apply these patterns to ensure production-ready, reliable deployments.

## Instructions

When working on Kubernetes deployment configurations:

1. **Resource Definitions**:
   - Always set resource requests AND limits for CPU and memory
   - Use the project's standard resource tiers: small (256Mi/250m), medium (512Mi/500m), large (1Gi/1000m)
   - Include liveness and readiness probes for all application containers
   - Set appropriate startup probes for slow-starting applications

2. **Deployment Strategy**:
   - Use `RollingUpdate` strategy for stateless services
   - Configure `maxSurge: 25%` and `maxUnavailable: 0` for zero-downtime deployments
   - Set `minReadySeconds: 10` to avoid premature rollout progression
   - Use `Recreate` strategy only for stateful services that cannot run multiple versions

3. **Pod Configuration**:
   - Run containers as non-root user (securityContext.runAsNonRoot: true)
   - Drop all capabilities and add only what is needed
   - Set `readOnlyRootFilesystem: true` where possible
   - Use pod disruption budgets for production workloads (minAvailable: 1 or maxUnavailable: 1)

4. **Service and Networking**:
   - Use ClusterIP services for internal communication
   - Use Ingress with TLS for external endpoints
   - Configure health check endpoints on the service
   - Set appropriate connection and read timeouts on ingress

5. **Configuration Management**:
   - Use ConfigMaps for non-sensitive configuration
   - Use Secrets for sensitive data (never plain text in manifests)
   - Reference secrets from the external secrets operator where available
   - Use environment-specific overlays (Kustomize) or values files (Helm)

6. **Observability**:
   - Include Prometheus scrape annotations for metrics endpoints
   - Configure structured logging with appropriate log levels
   - Set up pod labels for Grafana dashboard selectors

## Quality Criteria

- All containers have resource requests and limits defined
- Liveness and readiness probes are configured with appropriate thresholds
- Security context restricts container privileges
- Pod disruption budgets protect production availability
- No secrets appear in plain text in manifests
- Deployment strategy ensures zero-downtime updates

## Anti-patterns

- Running containers as root without justification
- Missing resource limits (risk of noisy neighbor problems)
- Using `latest` image tags in production
- Hardcoding configuration values instead of using ConfigMaps
- Missing pod disruption budgets for critical services
- Using NodePort services in production instead of Ingress
