---
name: requirement-analysis
description: "Skill for analyzing requirements — stakeholder identification, user story mapping, acceptance criteria definition, and risk analysis."
stage: planning
type: delivery-skill
---

# Requirement Analysis Skill

## Purpose

This skill guides the SuperAgent through the process of transforming raw feature requests, problem statements, or business needs into well-structured, analyzable requirements. The output feeds directly into PRD writing.

---

## Stakeholder Identification

### Process

1. **Identify direct stakeholders**:
   - Who requested this feature/change?
   - Who will directly use the feature?
   - Who will build the feature? (development team)
   - Who will test the feature? (QA team)
   - Who will support the feature? (operations/support team)

2. **Identify indirect stakeholders**:
   - Which other teams depend on the affected systems?
   - Which downstream services will be impacted?
   - Are there external partners or customers affected?
   - Are there regulatory or compliance stakeholders?

3. **Map stakeholder concerns**:

| Stakeholder     | Concern                        | Priority |
|-----------------|--------------------------------|----------|
| End User        | Usability, performance         | High     |
| Product Owner   | Business value, timeline       | High     |
| Dev Team        | Feasibility, maintainability   | Medium   |
| QA Team         | Testability, acceptance criteria| Medium  |
| Ops Team        | Deployability, observability   | Medium   |
| Security Team   | Security, compliance           | High     |

4. **Define RACI matrix** (for complex features):
   - **R**esponsible: Who does the work?
   - **A**ccountable: Who makes the final decision?
   - **C**onsulted: Who provides input?
   - **I**nformed: Who needs to know?

---

## User Story Mapping

### Story Hierarchy

```
Epic (large feature)
├── Feature (deliverable capability)
│   ├── User Story (user-facing behavior)
│   │   ├── Acceptance Criteria
│   │   └── Technical Tasks
│   └── User Story
└── Feature
    └── User Story
```

### User Story Format

```
As a [persona],
I want to [action/goal],
so that [benefit/value].
```

### Writing Guidelines

1. **Focus on the user**: Stories describe what users need, not how the system works
2. **Keep stories independent**: Each story should be deliverable on its own
3. **Make stories negotiable**: Details are refined during development
4. **Make stories valuable**: Every story delivers user or business value
5. **Make stories estimable**: The team can reasonably estimate effort
6. **Make stories small**: Completable within one sprint/iteration
7. **Make stories testable**: Clear criteria to verify completion

### Story Mapping Process

1. **Identify personas** from stakeholder analysis
2. **Map the user journey** for each persona:
   - What is their goal?
   - What steps do they take?
   - Where do they start and finish?
3. **Organize stories by journey step**:
   - Backbone: high-level user activities (left to right)
   - Walking skeleton: minimum stories for each activity
   - Iterations: additional stories layered below by priority
4. **Identify dependencies** between stories
5. **Define the MVP boundary**: draw a line separating must-have from nice-to-have

---

## Acceptance Criteria Definition

### Format: Given-When-Then

```
Given [precondition/context],
When [action/trigger],
Then [expected outcome].
```

### Guidelines

1. **Be specific**: Use concrete values, not vague descriptions
   - Bad: "The system should respond quickly"
   - Good: "The API responds within 200ms at p95 under 1000 concurrent users"

2. **Be complete**: Cover all scenarios
   - Happy path: normal successful flow
   - Error path: what happens when things go wrong
   - Edge cases: boundary conditions, empty states, maximums

3. **Be testable**: Every criterion must be verifiable
   - Can you write an automated test for this criterion?
   - Can a human verify this criterion unambiguously?

4. **Be independent**: Criteria should not depend on each other's ordering

### Example

```
Feature: User Login

Story: As a registered user, I want to log in with my email and password,
       so that I can access my account.

Acceptance Criteria:

1. Given a registered user with email "user@example.com" and valid password,
   When they submit the login form with correct credentials,
   Then they are redirected to the dashboard and a session cookie is set.

2. Given a registered user,
   When they submit the login form with an incorrect password,
   Then they see an error message "Invalid email or password" and no session is created.

3. Given a user account that is locked after 5 failed attempts,
   When they submit correct credentials,
   Then they see an error message "Account locked. Please contact support."

4. Given a login form,
   When the email field is empty or the password field is empty,
   Then the submit button is disabled and validation messages are shown.
```

---

## Non-Functional Requirements Checklist

For every feature, explicitly address each category:

### Performance
- [ ] Response time targets (p50, p95, p99)
- [ ] Throughput targets (requests per second)
- [ ] Concurrent user capacity
- [ ] Data volume expectations
- [ ] Batch processing time limits

### Security
- [ ] Authentication requirements
- [ ] Authorization requirements (role-based, attribute-based)
- [ ] Data encryption requirements (at rest, in transit)
- [ ] PII handling requirements
- [ ] Audit logging requirements
- [ ] Compliance requirements (GDPR, SOC2, HIPAA)

### Scalability
- [ ] Expected growth rate
- [ ] Horizontal scaling requirements
- [ ] Data partitioning needs
- [ ] Geographic distribution requirements

### Reliability
- [ ] Availability target (99.9%, 99.99%)
- [ ] Recovery Time Objective (RTO)
- [ ] Recovery Point Objective (RPO)
- [ ] Graceful degradation requirements
- [ ] Circuit breaker requirements

### Usability
- [ ] Accessibility standards (WCAG 2.1)
- [ ] Internationalization requirements
- [ ] Browser/device support matrix
- [ ] Response time perception targets

### Maintainability
- [ ] Logging and observability requirements
- [ ] Configuration management requirements
- [ ] Feature flag requirements
- [ ] Documentation requirements

### Compatibility
- [ ] Backward compatibility requirements
- [ ] API versioning requirements
- [ ] Data migration requirements
- [ ] Third-party integration constraints

---

## Risk and Dependency Analysis

### Risk Identification

For each requirement, assess:

| Risk Category   | Questions to Ask                                           |
|-----------------|------------------------------------------------------------|
| Technical       | Is this technically feasible? Do we have the skills?       |
| Integration     | Do we depend on external systems? Are their APIs stable?   |
| Data            | Are there data quality issues? Migration risks?            |
| Performance     | Can the system handle the expected load?                   |
| Security        | Are there new attack surfaces? Compliance implications?    |
| Schedule        | Can this be delivered in the target timeline?              |
| Resource        | Do we have enough people with the right skills?            |

### Risk Assessment Matrix

| Probability / Impact | Low Impact  | Medium Impact | High Impact  |
|----------------------|-------------|---------------|--------------|
| High Probability     | Medium Risk | High Risk     | Critical     |
| Medium Probability   | Low Risk    | Medium Risk   | High Risk    |
| Low Probability      | Negligible  | Low Risk      | Medium Risk  |

### Dependency Mapping

1. **Internal dependencies**:
   - Other teams' features that must be completed first
   - Shared infrastructure that must be provisioned
   - Database schema changes that affect other services

2. **External dependencies**:
   - Third-party API availability and stability
   - Vendor delivery timelines
   - Open-source library compatibility

3. **Temporal dependencies**:
   - Order of operations constraints
   - Time-sensitive releases (market events, regulatory deadlines)
   - Seasonal traffic patterns

### Mitigation Strategies

For each identified risk, define:
1. **Avoidance**: Can we change the approach to eliminate the risk?
2. **Mitigation**: Can we reduce the probability or impact?
3. **Transfer**: Can we shift the risk to another party (insurance, SLA)?
4. **Acceptance**: Is the risk acceptable with monitoring?

### Output Format

```markdown
## Risk Register

| ID   | Risk Description | Category  | Probability | Impact | Rating   | Mitigation Strategy            | Owner |
|------|------------------|-----------|-------------|--------|----------|--------------------------------|-------|
| R001 | ...              | Technical | High        | Medium | High     | ...                            | ...   |
| R002 | ...              | Schedule  | Medium      | High   | High     | ...                            | ...   |
```
