# Forge Platform — Skill Review Policy

This document defines the process and criteria for contributing, reviewing, and
managing Skill Profiles within the Forge platform. All contributors must follow
this policy to ensure consistency, quality, and security across the skill
ecosystem.

---

## 1. Contribution Process

### 1.1 Overview

Skill Profiles are submitted via pull request to the `forge-platform` repository
under the `skills/` directory. Each PR must follow the standard review workflow
and meet all quality criteria before merging.

### 1.2 Submission Steps

1. **Fork or branch** — Create a feature branch from `main`:
   `git checkout -b skill/<domain>/<skill-name>`.
2. **Create or update** the Skill Profile under `skills/<domain>/<skill-name>/`.
3. **Validate locally** — Run `forge skill validate skills/<domain>/<skill-name>`
   to check structure and content.
4. **Write tests** — Include at least one test case in the `tests/` directory
   that exercises the skill's patterns.
5. **Open a pull request** — Target `main` and use the PR template provided.
6. **Respond to review feedback** — Address all comments before approval.
7. **Merge** — Once approved by the required reviewers, the PR is merged and the
   skill becomes available in the registry.

### 1.3 PR Template

Every Skill Profile PR must include:

- **Summary**: What the skill teaches the AI to do.
- **Domain**: Which domain this skill belongs to (e.g., `foundation`, `payments`,
  `platform`).
- **Dependencies**: Other skills this skill depends on.
- **Testing**: How the skill was validated (local testing, sample outputs).
- **Breaking changes**: Whether this update changes existing behavior.

---

## 2. Review Criteria

Every Skill Profile is reviewed against the following criteria. All criteria must
pass for the PR to be approved.

### 2.1 Structure Compliance

- [ ] Contains a valid `skill.yaml` with all required metadata fields.
- [ ] Directory structure follows the standard layout:
  ```
  skills/<domain>/<skill-name>/
  ├── skill.yaml
  ├── context/
  ├── patterns/
  ├── guardrails/
  ├── examples/
  └── tests/
  ```
- [ ] All files use UTF-8 encoding without BOM.
- [ ] File names use kebab-case (e.g., `rest-controller-pattern.md`).

### 2.2 Content Quality

- [ ] Context documents are accurate and up to date.
- [ ] Patterns include clear explanations of when and why to use them.
- [ ] Examples show realistic input and expected output.
- [ ] Guardrails are specific and actionable (not vague guidance).
- [ ] No duplicate content that already exists in other skills — reference
  dependencies instead.

### 2.3 Security and Compliance

- [ ] No hardcoded secrets, API keys, passwords, or tokens anywhere in the skill.
- [ ] No internal hostnames, IP addresses, or environment-specific URLs.
- [ ] No personally identifiable information (PII) in examples or context.
- [ ] Patterns follow the organization's security standards (parameterized
  queries, input validation, etc.).
- [ ] License-compatible — no copy-pasted code from incompatible open-source
  licenses.

### 2.4 Testing

- [ ] At least one test case in `tests/` validates the skill produces correct
  output.
- [ ] Tests can be run with `forge skill test <skill-name>`.
- [ ] Tests pass in CI without manual intervention.

---

## 3. CODEOWNERS Rules

The `CODEOWNERS` file enforces that the correct team reviews each Skill Profile
PR. Ownership is determined by skill category.

```
# Foundation Skills — require platform-team approval
skills/foundation/          @org/platform-team

# Domain Skills — require respective domain-team approval
skills/payments/            @org/payments-team
skills/identity/            @org/identity-team
skills/notifications/       @org/notifications-team
skills/lending/             @org/lending-team
skills/platform/            @org/platform-team

# Skill metadata schema — require platform-team approval
skills/schema/              @org/platform-team

# Governance documents — require platform-team lead approval
docs/governance/            @org/platform-leads
```

### 3.1 Approval Requirements

| Skill Category | Required Approvers | Minimum Approvals |
|---|---|---|
| Foundation Skills | `@org/platform-team` | 2 |
| Domain Skills | Respective `@org/<domain>-team` | 1 |
| Cross-domain Skills | `@org/platform-team` + affected domain teams | 2 |
| Schema changes | `@org/platform-leads` | 2 |

---

## 4. Foundation Skills

Foundation Skills are core skills that are widely used across the organization.
They define baseline patterns, conventions, and guardrails that all other skills
build upon.

### 4.1 Examples of Foundation Skills

- `kotlin-service` — Base Kotlin service conventions.
- `spring-boot-web` — Spring Boot web application patterns.
- `error-handling` — Standard error handling and response formatting.
- `logging` — Structured logging conventions.
- `testing` — Unit and integration testing patterns.
- `security-basics` — Authentication, authorization, and input validation.

### 4.2 Foundation Skill Review Requirements

Because Foundation Skills affect every team and project:

- **Two approvals** from `@org/platform-team` members are required.
- **Impact assessment** must be included in the PR description, documenting which
  downstream skills and teams may be affected.
- **Migration guide** must be provided if the change is backward-incompatible.
- **Announcement** to all teams via the `#forge-announcements` channel after
  merge.

---

## 5. Domain Skills

Domain Skills encode knowledge specific to a business domain (e.g., payments,
lending, identity). They build on Foundation Skills and add domain-specific
patterns, guardrails, and examples.

### 5.1 Domain Skill Review Requirements

- **One approval** from the respective domain team is required.
- The domain team is responsible for ensuring technical accuracy and alignment
  with their domain's architecture.
- Cross-domain dependencies must be explicitly declared in `skill.yaml` and
  validated by the platform team.

### 5.2 Domain Skill Ownership

Each domain team owns their skills and is responsible for:

- Keeping skills up to date as domain architecture evolves.
- Responding to review requests within 2 business days.
- Deprecating outdated skills (see Section 6).

---

## 6. Versioning and Deprecation Policy

### 6.1 Versioning Scheme

Skill Profiles use semantic versioning (`MAJOR.MINOR.PATCH`):

- **MAJOR**: Breaking changes to patterns, guardrails, or structure that require
  downstream updates.
- **MINOR**: New patterns, examples, or guardrails that are backward-compatible.
- **PATCH**: Typo fixes, clarifications, and minor corrections.

The version is declared in `skill.yaml`:

```yaml
name: rest-api-kotlin
version: 2.1.0
domain: foundation
```

### 6.2 Backward Compatibility

- MINOR and PATCH updates must be backward-compatible.
- MAJOR updates must include a migration guide in the PR description.
- The previous MAJOR version remains available for 90 days after a new MAJOR
  version is released.

### 6.3 Deprecation Process

When a skill is no longer recommended:

1. Add `deprecated: true` and `deprecation-notice` to `skill.yaml`:
   ```yaml
   deprecated: true
   deprecation-notice: "Use rest-api-kotlin-v2 instead. Migration guide: <link>"
   superseded-by: rest-api-kotlin-v2
   ```
2. The skill remains available but Claude Code will display a deprecation warning
   when it is loaded.
3. After 90 days, the skill is archived and no longer loaded by default.
4. Archived skills can still be explicitly referenced for legacy projects.

### 6.4 Deprecation Announcements

- Deprecation notices are posted to `#forge-announcements`.
- Teams using the deprecated skill are notified directly via their team channel.
- The deprecation timeline is tracked in the Forge governance dashboard.

---

## 7. Enforcement

### 7.1 CI Checks

The following automated checks run on every Skill Profile PR:

- **Structure validation**: `forge skill validate` checks directory layout and
  `skill.yaml` schema.
- **Secret scanning**: Detects hardcoded secrets, tokens, and internal URLs.
- **Test execution**: Runs all tests in the `tests/` directory.
- **CODEOWNERS check**: Verifies that the required reviewers are assigned.

### 7.2 Non-Compliance

PRs that fail any review criterion are blocked from merging. Repeated
non-compliance may result in:

- Additional mandatory reviewers added to future PRs.
- Mandatory training on Forge skill authoring conventions.
- Temporary restriction of skill contribution privileges.
