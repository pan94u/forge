---
name: delivery-methodology
description: "AI-assisted delivery methodology — Session-as-Work-Unit, dual-loop architecture, experience encoding, quality gate discipline, and parallel agent strategies."
stage: development
type: delivery-skill
version: "1.0"
category: delivery
scope: platform
tags: [methodology, delivery, pdca, learning-loop, experience]
---

# Delivery Methodology

## Purpose

Encodes a systematic approach to AI-assisted software delivery, validated across 25+ real delivery sessions. All patterns are universal — applicable to any project, any tech stack, any team.

---

## Core Concept: Session-as-Work-Unit

A **Session** is the atomic unit of delivery. Every session follows a fixed structure to ensure predictable, high-quality output.

### Session PDCA Cycle

```
Plan → Do → Check → Act (Knowledge Capture)
```

1. **Plan**: Declare a clear goal statement. What will this session produce?
2. **Do**: Implement the goal. Write code, create documents, configure infrastructure.
3. **Check**: Verify the output against quality gates (baselines, tests, acceptance criteria).
4. **Act**: Capture what was learned. Record decisions, bugs, and reusable patterns.

### Session Structure

Every session produces these artifacts:

| Artifact | Purpose |
|----------|---------|
| Goal statement | What this session will deliver (1-2 sentences) |
| File change table | Operations performed (create/modify/delete + file + rationale) |
| Bug log | Bugs discovered and their root causes |
| Experience notes | Patterns worth encoding into persistent knowledge |
| Statistics snapshot | Key metrics at session end (test count, coverage, etc.) |

### Why This Matters

Without session structure, AI-assisted delivery suffers from:
- **Context amnesia**: each session starts from scratch, repeating past mistakes
- **Quality drift**: no systematic verification of output quality
- **Knowledge loss**: insights discovered during delivery are never captured

---

## Dual-Loop Architecture

### Inner Loop: Delivery (OODA)

The inner loop drives each task to completion:

```
Observe → Orient → Decide → Act → [Baseline Check] → Deliver or Loop Back
```

- **Observe**: gather all relevant context (code, docs, history, constraints)
- **Orient**: load skills, analyze feasibility, identify risks
- **Decide**: choose approach, plan steps, identify HITL checkpoints
- **Act**: execute, then verify against quality gates
- If baseline fails → loop back to Observe with failure context
- Maximum 3 loops before escalating to human

### Outer Loop: Learning

The outer loop improves the system over time:

```
Deliver → Observe Patterns → Validate Across Sessions → Encode as Knowledge
```

- Delivery data (session logs, git history, baseline results) feeds into the outer loop
- Patterns observed across 2+ sessions are candidates for encoding
- Encoded knowledge becomes skills, baselines, or conventions
- Encoded knowledge feeds back into the inner loop

### Why Two Loops?

The inner loop ensures **each delivery is correct**. The outer loop ensures **the system gets better over time**. Without the outer loop, the same mistakes get repeated. Without the inner loop, quality has no enforcement mechanism.

---

## Experience Encoding Pipeline

Raw observations from delivery become persistent knowledge through this pipeline:

### Stage 1: Raw Observation

During delivery, record what happened:
- Bug discovered and root cause
- Surprising behavior or unexpected interaction
- Workaround needed for a tool/framework limitation
- Pattern that worked well and could be reused

### Stage 2: Cross-Session Validation

Before encoding, validate that the observation is:
- **Reproducible**: happened in 2+ sessions (not a one-off)
- **Generalizable**: applies beyond the specific context
- **Actionable**: can be turned into a concrete rule or practice

### Stage 3: Encoding

Encode validated observations into the appropriate knowledge layer:

| Destination | When to Use |
|-------------|-------------|
| Skill file (SKILL.md) | Reusable practice, convention, or pattern |
| Baseline script | Automated quality check that prevents regression |
| Project config (CLAUDE.md) | Project-specific trap or convention |
| Reference doc | Detailed background information |

### Stage 4: Feedback

After encoding, verify:
- The encoded knowledge is discoverable (correct tags, skill name)
- The baseline catches the issue it was designed to prevent
- Future sessions benefit from the encoded knowledge

---

## Quality Gate Discipline

### Principle

Baselines are the **automated quality floor**. They guarantee minimum quality regardless of AI model capability or human oversight level.

### Rules

1. **Run baselines after every Act phase** — not just at session end
2. **Never skip a failing baseline** — fix the issue, then re-run
3. **Maximum 3 fix loops** — if still failing after 3 attempts, escalate to human
4. **Baselines are non-negotiable** — they are the floor, not the ceiling
5. **Add new baselines when bugs slip through** — every escaped bug is a missing baseline

### Baseline Types

| Type | Examples | Purpose |
|------|----------|---------|
| Code style | ktlint, checkstyle | Consistent formatting |
| Security | secret scan, injection check | Prevent vulnerabilities |
| Architecture | layer violation, dependency check | Maintain structure |
| Test coverage | JaCoCo threshold | Ensure test adequacy |
| API contract | OpenAPI diff | Prevent breaking changes |

---

## AI Collaboration Patterns

### Parallel Agent Strategy

Use parallel agents (sub-tasks) when:
- Initializing or modifying 5+ independent files
- Running independent verification tasks
- Updating multiple documents simultaneously

Do NOT use parallel agents when:
- Tasks have sequential dependencies
- Shared state must be consistent
- A single coherent narrative is needed

### Context Management

- **Load context incrementally**: read what you need, when you need it
- **Skill progressive disclosure**: Level 1 (metadata) → Level 2 (SKILL.md) → Level 3 (reference files)
- **Avoid context pollution**: don't load all skills upfront; select based on task needs
- **Summarize before switching**: when transitioning between tasks, summarize current state

### Human-in-the-Loop (HITL)

HITL checkpoints exist at:
- Design decisions with significant trade-offs
- Code review before merge
- Deployment approval
- Any point where AI confidence is low

Protocol: Pause → Present deliverables clearly → Highlight risks → Wait for explicit approval

---

## Anti-Patterns

| Anti-Pattern | Why It's Harmful | Correct Pattern |
|-------------|------------------|-----------------|
| Skipping baselines "to save time" | Bugs accumulate; fixing later costs 10x | Always run baselines |
| No session goal statement | Sessions drift, produce partial/unclear output | Start every session with a goal |
| Encoding observations after 1 occurrence | One-off observations create noise | Wait for 2+ sessions validation |
| Loading all skills into context | Context window pollution, diluted guidance | Progressive disclosure |
| Ignoring failing tests | Tests lose credibility as quality signal | Every failure must be resolved |
| Large monolithic commits | Hard to bisect, review, or rollback | One logical change per commit |
