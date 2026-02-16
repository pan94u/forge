---
name: design-profile
description: "Skill Profile for the Design delivery stage. Focused on architecture decisions, detailed design, and API contract definition."
skills:
  - architecture-design
  - detailed-design
  - api-design
baselines:
  - architecture-baseline
  - api-contract-baseline
hitl-checkpoint: "Architecture review — present architecture design and ADRs for technical review and approval before proceeding to Development."
---

# Design Profile — OODA Guidance

## Overview

The Design Profile activates when the SuperAgent enters the Design delivery stage. The primary objective is to translate a PRD into a concrete technical design that includes architecture decisions, detailed component design, and API contracts.

This stage enforces two baselines:
- **architecture-baseline**: Validates structural correctness (no cross-layer calls, correct dependency direction)
- **api-contract-baseline**: Validates that API specifications are complete and consistent

---

## Observe

Gather the design context:

1. **Read the PRD**:
   - Requirements and acceptance criteria
   - Non-functional requirements (performance, scalability, security)
   - User flow diagrams
   - Scope boundaries

2. **Read existing architecture**:
   - Current system architecture diagrams
   - Existing service graph via `forge-context-server.getServiceGraph()`
   - Module dependency structure
   - Database schemas
   - Existing API contracts (OpenAPI specs)

3. **Query knowledge base**:
   - `forge-knowledge-server.getADRs(component)` — existing architecture decisions
   - `forge-knowledge-server.getPatterns(domain)` — applicable design patterns
   - `forge-knowledge-server.getLessonsLearned("architecture")` — past design mistakes

4. **Identify constraints**:
   - Technology stack constraints (from ADRs or project conventions)
   - Infrastructure constraints (cloud provider, resource limits)
   - Team capability constraints
   - Timeline constraints from the PRD

5. **Impact analysis**:
   - Which existing services will be affected?
   - What new services or components are needed?
   - What data migrations are required?
   - What breaking changes might occur?

### Observe Checklist
- [ ] PRD fully read and requirements understood
- [ ] Current architecture mapped
- [ ] Existing ADRs reviewed
- [ ] Service dependencies understood
- [ ] Impact scope identified

---

## Orient

Analyze the design space and form architectural opinions:

1. **Architecture pattern selection**:
   - Does this feature fit within the existing architecture?
   - Is a new service needed, or should this extend an existing one?
   - What communication pattern is appropriate (sync REST, async messaging, event-driven)?
   - What data storage pattern fits (relational, document, cache, event store)?

2. **Impact analysis via service graph**:
   - Trace the call chains that will be affected
   - Identify coupling points and potential bottlenecks
   - Map data flow through the system
   - Assess blast radius of the change

3. **Non-functional requirement mapping**:
   - Performance: latency targets → caching strategy, async processing
   - Scalability: load targets → horizontal scaling, partitioning
   - Security: threat model → authentication, authorization, encryption
   - Reliability: availability targets → redundancy, circuit breakers, retries

4. **Technology selection** (if new tech is needed):
   - Evaluate against project conventions
   - Check team familiarity
   - Assess operational burden
   - Document in ADR format

5. **Trade-off analysis**:
   - Identify at least 2 viable approaches
   - Compare on: complexity, performance, maintainability, time-to-deliver
   - Prepare recommendation with rationale

### Orient Output
A clear architectural direction with documented trade-offs and rationale.

---

## Decide

Make and document architecture decisions:

1. **Architecture decisions** (document as ADRs):
   - Decision title and context
   - Options considered with pros/cons
   - Decision and rationale
   - Consequences and trade-offs

2. **Component design decisions**:
   - New components to create
   - Existing components to modify
   - Interface contracts between components
   - Data models and relationships

3. **API design decisions**:
   - Endpoint structure and naming
   - Request/response schemas
   - Authentication and authorization model
   - Versioning strategy
   - Error response format

4. **Data design decisions**:
   - Schema changes required
   - Migration strategy
   - Data consistency model
   - Caching strategy

### Decide Output
A set of documented decisions (ADRs) and a design blueprint.

---

## Act

Generate the design artifacts:

1. **Architecture design document**:
   - C4 Context diagram (system-level)
   - C4 Container diagram (service-level)
   - C4 Component diagram (for affected services)
   - All diagrams in Mermaid format

2. **Architecture Decision Records**:
   - One ADR per significant decision
   - Follow the standard ADR template from architecture-design skill
   - Link ADRs to relevant PRD requirements

3. **Detailed design**:
   - Class diagrams for new/modified components
   - Sequence diagrams for key interaction flows
   - State diagrams for stateful entities
   - Database schema design (DDL or migration scripts)

4. **API contracts**:
   - OpenAPI 3.0+ specifications
   - Request/response examples
   - Error response catalog
   - Authentication requirements per endpoint

5. **Run baselines**:
   - `architecture-baseline.sh` — verify structural correctness
   - `api-contract-baseline.sh` — verify API spec completeness
   - On failure: loop back to Observe with failure details

6. **Present at HITL checkpoint**:
   - Architecture summary with key diagrams
   - ADR list with decisions
   - API contract overview
   - Identified risks
   - Wait for approval

### Act Output
Complete design documentation that passes architecture and API contract baselines.

---

## HITL Checkpoint: Architecture Review

**When**: After all design artifacts are generated and baselines pass.

**Present to the user**:
1. Architecture summary (key diagrams inline)
2. List of ADRs with one-line summaries
3. API surface area overview
4. Risk assessment
5. Baseline results (all passing)
6. Estimated development effort

**Wait for**: Explicit approval or revision requests.

**On rejection**: Incorporate feedback, return to **Orient** phase with new constraints.

---

## Baseline Details

### architecture-baseline
Validates:
- No direct calls from presentation layer to data layer (must go through service layer)
- Dependency direction follows the dependency rule (inner layers do not depend on outer layers)
- No circular dependencies between modules
- Service boundaries are respected (no cross-service database access)

### api-contract-baseline
Validates:
- Every endpoint has request and response schemas defined
- All required fields are documented
- Error responses follow the standard format
- Authentication requirements are specified
- API versioning is consistent
