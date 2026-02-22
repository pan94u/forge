---
name: ci-cd-patterns
version: "2.0"
scope: platform
category: delivery
profile: deployment
description: "CI/CD pipeline patterns, workflow configuration, and release automation best practices"
tags:
  - ci-cd
  - github-actions
  - pipelines
  - automation
required_tools:
  - forge-knowledge.search_wiki
---

## Purpose

This skill provides the agent with knowledge of CI/CD pipeline patterns and workflow configuration best practices. When the agent needs to create, modify, or review CI/CD pipelines (primarily GitHub Actions), it should apply these patterns to ensure reliable, secure, and efficient build and deployment automation.

## Instructions

When working on CI/CD pipelines:

1. **Pipeline Structure**:
   - Separate build, test, and deploy stages clearly
   - Use job dependencies to enforce execution order
   - Run independent jobs in parallel where possible
   - Use reusable workflows for shared pipeline logic
   - Keep pipeline definitions DRY with composite actions

2. **Build Stage**:
   - Pin action versions to specific SHA hashes for security
   - Cache dependencies (Gradle, npm, Docker layers) aggressively
   - Use matrix builds for multi-version testing
   - Build container images with multi-stage Dockerfiles
   - Tag images with git SHA and semantic version

3. **Test Stage**:
   - Run unit tests with coverage reporting
   - Run integration tests in isolated environments
   - Gate merges on minimum coverage thresholds
   - Use test splitting for large test suites
   - Archive test reports as artifacts

4. **Security Checks**:
   - Run dependency vulnerability scanning (Dependabot, Trivy)
   - Run static analysis (detekt for Kotlin, ESLint for TypeScript)
   - Scan container images for vulnerabilities before pushing
   - Use OIDC for cloud provider authentication (no long-lived secrets)
   - Store secrets in GitHub Secrets, never in workflow files

5. **Deployment Stage**:
   - Use environment protection rules for production deployments
   - Require manual approval for production releases
   - Implement canary or blue-green deployment where supported
   - Run smoke tests after deployment
   - Enable automatic rollback on smoke test failure

6. **Release Management**:
   - Use semantic versioning for releases
   - Generate changelogs automatically from conventional commits
   - Create GitHub releases with attached artifacts
   - Notify relevant channels (Slack, Teams) on deployment

## Quality Criteria

- All action versions are pinned to SHA hashes
- Secrets are accessed through GitHub Secrets, never hardcoded
- Dependency caching is configured to reduce build times
- Test coverage gates are enforced
- Production deployments require approval
- Rollback procedures are automated

## Anti-patterns

- Using `@main` or `@latest` for action versions
- Storing secrets in workflow YAML files
- Skipping tests to speed up the pipeline
- Deploying directly to production without staging
- Missing dependency caching (slow builds)
- No rollback mechanism for failed deployments
