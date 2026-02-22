---
name: planning-profile
description: "Skill Profile for the Planning delivery stage. Focused on requirement analysis, stakeholder alignment, and PRD authoring."
skills:
  - delivery-methodology
  - requirement-engineering
baselines: []
hitl-checkpoint: "PRD human confirmation — present complete PRD for stakeholder review and approval before proceeding to Design."
---

# Planning Profile — OODA Guidance

## Overview

The Planning Profile activates when the SuperAgent enters the Planning delivery stage. The primary objective is to transform raw requirements, feature requests, or problem statements into a clear, actionable Product Requirements Document (PRD).

This stage has **no automated baselines** because its outputs are documentation artifacts that require human judgment. Quality is enforced through the HITL checkpoint.

---

## Observe

Gather all inputs that define what needs to be built:

1. **Read the source material**:
   - User request or feature description
   - Linked GitHub issues or tickets
   - Existing product documentation
   - Customer feedback or support tickets (if referenced)

2. **Query context servers**:
   - `forge-context-server.getProjectStructure()` — understand the current system
   - `forge-knowledge-server.searchKnowledge(topic)` — find related past decisions
   - `forge-knowledge-server.getADRs(component)` — check architectural constraints

3. **Identify stakeholders**:
   - Who requested this feature?
   - Who will use it?
   - Who will be impacted by it?
   - Who needs to approve it?

4. **Check for existing artifacts**:
   - Is there already a partial PRD?
   - Are there related PRDs that overlap?
   - Are there design docs that constrain the solution space?

### Observe Checklist
- [ ] Source requirements fully read and understood
- [ ] Project context loaded from MCP servers
- [ ] Stakeholders identified
- [ ] Existing related artifacts located
- [ ] Ambiguities and unknowns listed

---

## Orient

Analyze feasibility and align with project context:

1. **Feasibility assessment**:
   - Can this be built with the current technology stack?
   - What is the estimated complexity (S/M/L/XL)?
   - Are there hard technical constraints?
   - What dependencies exist on other teams or systems?

2. **Knowledge base cross-reference**:
   - Have we built something similar before? What lessons were learned?
   - Are there domain patterns that apply?
   - Do any ADRs constrain the approach?

3. **Scope analysis**:
   - What is the Minimum Viable scope?
   - What are nice-to-have features vs. must-haves?
   - What is explicitly out of scope?

4. **Risk identification**:
   - Technical risks (new technology, complex integration)
   - Business risks (market timing, regulatory)
   - Resource risks (availability, skill gaps)
   - Dependency risks (external APIs, third-party services)

5. **Non-functional requirements**:
   - Performance expectations
   - Security requirements
   - Scalability needs
   - Accessibility standards
   - Compliance requirements

### Orient Output
A structured understanding of what is feasible, what the risks are, and what the scope boundaries should be.

---

## Decide

Formulate the PRD plan:

1. **Define the PRD structure**:
   - Select appropriate sections based on feature complexity
   - Decide on diagram types needed (user flows, state diagrams)
   - Determine the level of detail for acceptance criteria

2. **Make scope decisions**:
   - Draw the line between MVP and future iterations
   - Resolve any ambiguities (ask user if critical, make reasonable assumption if minor)
   - Define explicit exclusions

3. **Plan user stories**:
   - Identify personas
   - Map user journeys
   - Define story hierarchy (epics → stories → tasks)

4. **Define success metrics**:
   - How will we know this feature is successful?
   - What metrics will we track?
   - What are the acceptance thresholds?

### Decide Output
A PRD outline with clear decisions on scope, structure, and success criteria.

---

## Act

Generate the PRD:

1. **Write the PRD** following the prd-writing skill guidance:
   - Use the standard PRD template
   - Write specific, measurable, unambiguous requirements
   - Include Mermaid diagrams for user flows
   - Define acceptance criteria for every user story
   - List non-functional requirements explicitly

2. **Include supporting artifacts**:
   - User flow diagrams (Mermaid)
   - Data model sketches (if relevant)
   - API surface area estimates (if relevant)
   - Risk register

3. **Self-review the PRD**:
   - Is every requirement testable?
   - Are there contradictions?
   - Is the scope clearly bounded?
   - Are success metrics quantifiable?

4. **Present at HITL checkpoint**:
   - Summarize the PRD for the stakeholder
   - Highlight key decisions and trade-offs
   - List open questions that need human input
   - Wait for approval before transitioning to Design

### Act Output
A complete PRD ready for human review.

---

## HITL Checkpoint: PRD Confirmation

**When**: After the PRD is fully drafted.

**Present to the user**:
1. PRD summary (1 paragraph)
2. Key scope decisions
3. Risk highlights
4. Open questions requiring human judgment
5. The full PRD document

**Wait for**: Explicit approval ("approved", "LGTM", "proceed") or feedback for revision.

**On rejection**: Incorporate feedback, return to **Orient** phase with the new constraints.
