---
name: requirement-engineering
description: "Skill for requirement analysis and PRD writing — stakeholder identification, user story mapping, acceptance criteria, PRD structure, and risk analysis."
stage: planning
type: delivery-skill
version: "3.0"
category: delivery
scope: platform
tags: [methodology, planning, requirements, prd]
note: "Merged from prd-writing + requirement-analysis. Covers the full requirement engineering lifecycle."
---

# Requirement Engineering Skill

## Purpose

Guides the SuperAgent through the full requirement engineering lifecycle: from raw feature requests to well-structured PRDs with testable acceptance criteria.

---

## Phase 1: Stakeholder & Context Analysis

### Stakeholder Identification

1. **Direct stakeholders**: requester, end users, dev team, QA, ops/support
2. **Indirect stakeholders**: dependent teams, downstream services, compliance/regulatory
3. **Map concerns to priorities**:

| Stakeholder     | Concern                        | Priority |
|-----------------|--------------------------------|----------|
| End User        | Usability, performance         | High     |
| Product Owner   | Business value, timeline       | High     |
| Dev Team        | Feasibility, maintainability   | Medium   |
| QA Team         | Testability, acceptance criteria| Medium  |
| Ops Team        | Deployability, observability   | Medium   |
| Security Team   | Security, compliance           | High     |

4. **RACI matrix** (for complex features): Responsible / Accountable / Consulted / Informed

---

## Phase 2: User Story Mapping

### Story Hierarchy

```
Epic (large feature)
├── Feature (deliverable capability)
│   ├── User Story (user-facing behavior)
│   │   ├── Acceptance Criteria (Given-When-Then)
│   │   └── Technical Tasks
│   └── User Story
└── Feature
    └── User Story
```

### User Story Format

```
As a [persona], I want to [action/goal], so that [benefit/value].
```

### INVEST Criteria

- **I**ndependent: deliverable on its own
- **N**egotiable: details refined during development
- **V**aluable: delivers user or business value
- **E**stimable: team can estimate effort
- **S**mall: completable within one sprint
- **T**estable: clear criteria to verify

### Acceptance Criteria (Given-When-Then)

```
Given [precondition/context],
When [action/trigger],
Then [expected outcome].
```

**Rules**:
- Use concrete values, not vague descriptions
- Cover happy path, error path, and edge cases
- Every criterion must be verifiable by an automated test or human
- Criteria must be independent of each other's ordering

---

## Phase 3: PRD Structure

Every PRD follows this structure (sections may be shortened for smaller features but never omitted):

### Required Sections

1. **Metadata**: author, status, created/updated dates, reviewers
2. **Background & Problem Statement**: why this feature exists, data-backed motivation
3. **Goals & Non-Goals**: specific measurable outcomes + explicitly excluded scope
4. **User Personas**: role, goals, pain points, technical proficiency
5. **User Stories & Acceptance Criteria**: epics → stories → GWT criteria → priority
6. **Functional Requirements**: FR-001 format with input/processing/output/validation
7. **Non-Functional Requirements**: performance, security, scalability, reliability targets
8. **Scope & Boundaries**: in-scope, out-of-scope (with reasons), future considerations
9. **Dependencies & Risks**: dependency table + risk register with mitigations
10. **Timeline & Milestones**: milestone table with target dates
11. **Success Metrics**: metric name, baseline, target, measurement method

### Writing Rules

| Bad                                | Good                                                         |
|------------------------------------|--------------------------------------------------------------|
| "The system should be fast"        | "API response time < 200ms at p95 under 1000 concurrent users"|
| "Handle errors gracefully"         | "On validation error, return HTTP 400 with error code, field name, and message" |
| "should"                           | "must" (required) or "may" (optional)                        |
| "etc."                             | List all items explicitly                                    |
| "appropriate"                      | Define what is appropriate                                    |

---

## Phase 4: Risk & Dependency Analysis

### Risk Categories

| Category   | Key Questions                                              |
|------------|-------------------------------------------------------------|
| Technical  | Technically feasible? Do we have the skills?               |
| Integration| External system dependencies? API stability?               |
| Data       | Data quality issues? Migration risks?                      |
| Performance| Can the system handle expected load?                       |
| Security   | New attack surfaces? Compliance implications?              |
| Schedule   | Achievable in target timeline?                             |

### Risk Assessment Matrix

| Probability / Impact | Low Impact  | Medium Impact | High Impact  |
|----------------------|-------------|---------------|--------------|
| High Probability     | Medium      | High          | Critical     |
| Medium Probability   | Low         | Medium        | High         |
| Low Probability      | Negligible  | Low           | Medium       |

### Mitigation Strategies

1. **Avoidance**: change approach to eliminate the risk
2. **Mitigation**: reduce probability or impact
3. **Transfer**: shift to another party (SLA, insurance)
4. **Acceptance**: monitor and accept the risk

---

## Non-Functional Requirements Checklist

- **Performance**: response times (p50/p95/p99), throughput, concurrent users
- **Security**: auth, authorization, encryption, PII handling, audit logging, compliance
- **Scalability**: growth rate, horizontal scaling, data partitioning
- **Reliability**: availability target, RTO, RPO, graceful degradation
- **Usability**: accessibility (WCAG 2.1), i18n, browser/device matrix
- **Maintainability**: logging, observability, config management, feature flags

---

## Quality Checklist (HITL Checkpoint)

### Completeness
- [ ] All template sections filled or marked N/A with reason
- [ ] Every user story has acceptance criteria
- [ ] NFRs are quantified
- [ ] Dependencies identified with owners
- [ ] Risks have mitigation strategies
- [ ] Success metrics have baselines and targets

### Consistency
- [ ] Terminology consistent throughout
- [ ] Personas match story references
- [ ] Scope aligns with goals and non-goals

### Testability
- [ ] Every functional requirement is verifiable
- [ ] Every acceptance criterion follows GWT format
- [ ] NFR targets have measurement methods

### Clarity
- [ ] No ambiguous words (should, appropriate, reasonable)
- [ ] No undefined jargon
- [ ] No unstated assumptions
