---
name: progress-evaluation
description: "Evaluates project progress by analyzing workspace structure, code completeness, and comparing against planned milestones. Outputs structured progress reports."
tags: [progress, status, evaluation, assessment, milestone, report]
stage: evaluation
type: delivery-skill
category: DELIVERY
scope: PLATFORM
version: "1.0"
author: forge-team
---

# Progress Evaluation Skill

## Purpose

Provides structured project progress assessment by analyzing the workspace state and comparing against planned milestones. This is a **read-only** skill — it observes and reports without modifying code.

## When to Use

- User asks "看看进展到哪了" / "project status" / "进度如何"
- User requests a progress report or milestone assessment
- User wants to understand what's been completed vs. what's remaining

## Evaluation Process

### Step 1: Project Structure Analysis
1. Use `workspace_list_files` to map the full directory tree
2. Count files by type: source (`.kt`, `.java`, `.ts`, `.tsx`), test, config, docs
3. Identify key modules and their sizes
4. Detect technology stack from build files

### Step 2: Completeness Assessment
1. Check for standard project components:
   - Source code modules
   - Test coverage (test files exist?)
   - Documentation (README, docs/)
   - Configuration (build configs, Docker, CI/CD)
   - Database migrations
2. Read key README/CHANGELOG for stated features
3. Compare against any planning documents found

### Step 3: Code Quality Indicators
1. File count and distribution across modules
2. Test-to-source ratio
3. Documentation coverage
4. Configuration completeness

### Step 4: Risk Identification
1. Missing tests for source modules
2. Missing documentation
3. Large files that might need refactoring
4. Missing standard configs (CI/CD, linting, etc.)

## Report Template

```markdown
## Project Progress Report

### Overview
- **Project**: [name from build config]
- **Tech Stack**: [detected languages/frameworks]
- **Assessment Date**: [current date]
- **Overall Status**: [GREEN/YELLOW/RED]

### Project Structure
| Category | Count | Details |
|----------|-------|---------|
| Source files | X | .kt, .java, .ts, .tsx |
| Test files | Y | Test coverage ratio |
| Config files | Z | Build, Docker, CI |
| Documentation | W | README, docs/ |

### Completed Work
- [Feature/module 1] — [evidence]
- [Feature/module 2] — [evidence]

### In Progress / Incomplete
- [Gap 1] — [what's missing]
- [Gap 2] — [what's missing]

### Code Quality Snapshot
- Test/Source ratio: X:Y
- Documentation: [complete/partial/missing]
- CI/CD: [configured/missing]

### Risks & Recommendations
1. [Risk] — [Suggested action]
2. [Risk] — [Suggested action]

### Suggested Next Steps
1. [Priority action]
2. [Priority action]
```

## Integration

- **Profile**: evaluation-profile (primary), any profile (on-demand via `read_skill`)
- **Tools used**: `workspace_list_files`, `workspace_read_file`, `search_knowledge`, `get_session_history`
- **Output**: Structured Markdown report in chat (not written to files unless requested)
