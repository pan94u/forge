---
name: bug-fix-workflow
description: "Structured bug diagnosis and repair workflow using the 5-Whys root cause analysis method. Ensures fixes include tests, documentation, and knowledge capture."
tags: [bug, fix, debug, diagnose, error, repair, troubleshoot]
stage: development
type: delivery-skill
category: DELIVERY
scope: PLATFORM
version: "1.0"
author: forge-team
---

# Bug Fix Workflow Skill

## Purpose

Provides a structured, repeatable process for diagnosing and fixing bugs. Goes beyond "find and fix" to include root cause analysis, regression prevention, and knowledge capture.

## When to Use

- User reports a bug: "修这个 bug" / "这个报错是什么原因"
- Error diagnosis: "为什么这个接口这么慢"
- Configuration issues: "Docker 部署后 XXX 不工作"
- Any defect remediation task

## Bug Fix OODA Flow

### Observe: Understand the Bug
1. **Read the bug report** carefully — extract:
   - Expected behavior
   - Actual behavior
   - Steps to reproduce
   - Environment details
2. **Locate related code**:
   - Use `workspace_list_files` to find relevant source files
   - Use `workspace_read_file` to read the suspect code
   - Check recent changes in the area
3. **Check knowledge base**:
   - `search_knowledge` for similar past bugs
   - Check CLAUDE.md "known pitfalls" section
4. **Review error logs/traces** if available

### Orient: Root Cause Analysis (5 Whys)
Apply the 5-Whys method systematically:

```
Bug: [Observed symptom]
Why 1: [First-level cause]
Why 2: [Deeper cause]
Why 3: [Even deeper]
Why 4: [Structural cause]
Why 5: [Root cause]
→ Root Cause: [The fundamental issue]
```

Categories of root causes:
- **Logic Error**: Incorrect algorithm or condition
- **Data Error**: Wrong data format, missing validation
- **Integration Error**: Interface mismatch between components
- **Configuration Error**: Wrong settings or missing config
- **Concurrency Error**: Race condition, deadlock
- **Resource Error**: Memory leak, connection exhaustion

### Decide: Fix Strategy
1. **Choose fix approach**:
   - Minimal fix (patch the symptom)
   - Root cause fix (address the underlying issue)
   - Preventive fix (add guards to prevent recurrence)
2. **Assess blast radius**: Which other code might be affected?
3. **Plan regression test**: What tests need to be added/updated?
4. **Decide on documentation**: Bug report, knowledge entry, or CLAUDE.md update?

### Act: Implement Fix
1. **Write the fix** using `workspace_write_file`
2. **Add regression test** covering the exact bug scenario
3. **Run existing tests** via `workspace_test` to check for regressions
4. **Run baselines** to ensure fix doesn't introduce new issues
5. **Document the fix**:

#### Bug Report Template
```markdown
## Bug Fix Report

### Bug ID
[Reference ID if available]

### Symptom
[What was observed]

### Root Cause (5 Whys)
[The root cause analysis chain]

### Fix Applied
- Files modified: [list]
- Nature of fix: [logic/data/config/etc.]

### Regression Prevention
- Tests added: [list]
- Baseline updated: [yes/no]

### Knowledge Capture
- Should this be added to CLAUDE.md "Known Pitfalls"? [yes/no]
- Similar bugs to watch for: [patterns]
```

## Systematic Bug Triage

When a bug reveals a **systemic issue** (e.g., all enums have the same serialization problem):

1. **Identify the pattern**: What makes this bug systematic?
2. **Global scan**: Search the entire codebase for similar instances
3. **Fix all at once**: Don't leave similar bugs for later
4. **Add a baseline check**: Prevent this class of bug from recurring
5. **Document in CLAUDE.md**: Add to "Known Pitfalls" section

## Integration

- **Profile**: development-profile (primary)
- **Baselines**: Runs code-style-baseline + security-baseline after fix
- **Tools used**: `workspace_list_files`, `workspace_read_file`, `workspace_write_file`, `workspace_compile`, `workspace_test`, `run_baseline`, `search_knowledge`
