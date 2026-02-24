---
name: evaluation-profile
description: "Skill Profile for analysis, evaluation, and knowledge management. Read-only by default — observes, analyzes, and reports without modifying workspace code."
mode: read-only
skills:
  - delivery-methodology
  - progress-evaluation
  - knowledge-distillation
  - document-generation
  - codebase-profiler
baselines: []
hitl-checkpoint: ""
---

# Evaluation Profile — OODA Guidance

## Overview

The Evaluation Profile activates when the user requests analysis, progress assessment, knowledge management, or document generation tasks. Unlike delivery profiles, this profile operates in **read-only mode** by default — it observes and reports rather than modifying code.

This profile covers four capability dimensions:
- **A. Analysis & Evaluation**: Project progress, code quality, architecture assessment, tech debt
- **B. Content Generation**: Architecture diagrams, manuals, reports, API docs
- **C. Knowledge Management**: Experience distillation, convention extraction, decision records, postmortems
- **D. Progress Reporting**: Status summaries, milestone tracking, risk identification

No baselines or HITL checkpoints are required — analysis operations are non-destructive.

---

## Observe

Gather comprehensive context for analysis:

1. **Read workspace structure**:
   - Use `workspace_list_files` to understand the full project structure
   - Identify key directories, modules, and configuration files
   - Count files by type (source, test, config, docs)

2. **Read key project files**:
   - Use `workspace_read_file` to read build configs (build.gradle.kts, package.json)
   - Read README, CHANGELOG, or documentation files
   - Read key source files to understand architecture

3. **Query memory and history**:
   - Use `get_session_history` to review past sessions and their outcomes
   - Check workspace memory for accumulated project facts
   - Review stage memory for profile-specific context

4. **Search knowledge base**:
   - Use `search_knowledge` to find relevant ADRs, runbooks, and conventions
   - Cross-reference with project documentation

5. **Analyze execution records**:
   - Review recent tool call patterns
   - Identify frequently used skills and their success rates
   - Note any recurring issues or failures

### Observe Checklist
- [ ] Project structure mapped
- [ ] Key configuration files read
- [ ] Session history reviewed
- [ ] Knowledge base queried
- [ ] Current state understood

---

## Orient

Analyze observations and form assessment:

1. **For Progress Evaluation**:
   - Map planned features vs. implemented features
   - Identify completed milestones and pending work
   - Calculate approximate completion percentage
   - Note blockers and dependencies

2. **For Code Quality Analysis**:
   - Assess module organization and separation of concerns
   - Check for test coverage indicators
   - Identify code patterns and anti-patterns
   - Note documentation completeness

3. **For Knowledge Distillation**:
   - Identify patterns from execution history (failures → fixes)
   - Extract recurring problem-solution pairs
   - Detect implicit conventions not yet documented
   - Note knowledge gaps (questions that couldn't be answered)

4. **For Document Generation**:
   - Determine the appropriate document template
   - Gather all necessary data points
   - Structure the outline

### Orient Output
A structured analysis framework ready for report generation.

---

## Decide

Choose the appropriate output format:

1. **Progress Report**: Structured markdown with sections for overview, completed work, in-progress items, risks, and recommendations
2. **Quality Report**: Code metrics, pattern analysis, improvement suggestions
3. **Knowledge Entry**: Structured experience record suitable for knowledge base
4. **Generated Document**: Template-based document (architecture diagram in Mermaid, API doc, manual)

### Report Templates

**Progress Report Structure**:
```markdown
## Project Progress Report

### Overview
- Project: [name]
- Assessment Date: [date]
- Overall Status: [GREEN/YELLOW/RED]

### Completed Work
[List of completed features/tasks]

### In Progress
[Current work items and their status]

### Code Quality Snapshot
- Source files: X
- Test files: Y
- Documentation: Z

### Risks & Blockers
[Identified risks with severity]

### Recommendations
[Actionable next steps]
```

---

## Act

Generate the output:

1. **Compile the report/analysis** in structured Markdown format
2. **Present in chat** — do NOT write to workspace files unless explicitly asked
3. If the user requests saving:
   - Use `workspace_write_file` to save the report
   - Suggest appropriate file paths (e.g., `docs/reports/`, `docs/analysis/`)
4. For Mermaid diagrams, present the source code in a code block first

### Important Constraints
- **Default: chat-only output** — do not create files unless asked
- **No code modifications** — this profile does not write/modify source code
- **Factual reporting** — base all assessments on observed data, not assumptions
- **Structured output** — always use consistent report templates
