---
name: forge-profile
description: Switch SuperAgent Skill Profile
trigger: /forge-profile
---

# /forge-profile — Switch Skill Profile

Switch the active SuperAgent Skill Profile:

Usage: `/forge-profile [profile-name]`

Available profiles:
- `planning` — Requirement analysis, PRD writing
- `design` — Architecture design, detailed design, API design
- `development` — Code generation + all Foundation Skills + Domain Skills
- `testing` — Test case writing, test execution
- `ops` — Deployment, Kubernetes patterns, CI/CD

If no profile specified, auto-detect from current context.
