---
name: knowledge-distillation
description: "Extracts reusable patterns and lessons from execution history and project experience. Generates structured knowledge entries for the knowledge base."
tags: [knowledge, distill, extract, lesson, pattern, experience, postmortem]
stage: evaluation
type: delivery-skill
category: KNOWLEDGE
scope: PLATFORM
version: "1.0"
author: forge-team
---

# Knowledge Distillation Skill

## Purpose

Analyzes execution history, session summaries, and project artifacts to extract reusable patterns, lessons learned, and knowledge entries. This corresponds to the **asset-extractor** component in the Forge learning loop architecture.

## When to Use

- User says "总结经验" / "提炼 pattern" / "写入知识库"
- After a series of bug fixes to identify recurring patterns
- When a complex task is completed and lessons should be captured
- For postmortem analysis after incidents

## Distillation Process

### Step 1: Gather Source Material
1. Use `get_session_history` to retrieve recent session summaries
2. Search workspace memory for accumulated facts
3. Identify failure→fix chains in execution history
4. Read any relevant project docs or changelogs

### Step 2: Pattern Recognition
1. **Recurring Problems**: Same type of error appearing multiple times
2. **Effective Solutions**: Patterns that consistently resolve issues
3. **Convention Gaps**: Implicit rules not yet documented
4. **Tool Usage Patterns**: Which tools work best for which tasks
5. **Architectural Insights**: Design decisions and their outcomes

### Step 3: Structure the Knowledge

#### Experience Record Template
```markdown
## [Title]: [Brief description of the pattern/lesson]

### Context
- When does this apply?
- What conditions trigger this?

### Problem
- What goes wrong without this knowledge?
- What are the symptoms?

### Solution
- Step-by-step resolution
- Code examples if applicable

### Prevention
- How to avoid this in the future
- Suggested Skill/Baseline updates

### Evidence
- Session(s) where this was observed
- Number of occurrences
```

#### ADR (Architecture Decision Record) Template
```markdown
## ADR-XXX: [Decision Title]

### Status
Accepted | Proposed | Deprecated

### Context
[What situation prompted this decision?]

### Decision
[What was decided?]

### Consequences
[Positive and negative impacts]

### Alternatives Considered
[What else was evaluated?]
```

#### Runbook Template
```markdown
## Runbook: [Operation Name]

### Prerequisites
- [Required access/tools]

### Steps
1. [Step with exact commands]
2. [Step with expected output]

### Troubleshooting
| Symptom | Cause | Fix |
|---------|-------|-----|
| ... | ... | ... |

### Rollback
[How to undo if something goes wrong]
```

### Step 4: Suggest Improvements
1. **Skill Updates**: New rules or patterns to add to Foundation Skills
2. **Baseline Additions**: New quality checks to automate
3. **Knowledge Base Entries**: New docs to create
4. **CLAUDE.md Updates**: New "known pitfalls" to record

## Output

Present the distilled knowledge in chat as structured Markdown. If the user requests, save to:
- Knowledge base: `knowledge-base/[category]/[name].md`
- Workspace docs: `docs/[type]/[name].md`

## Integration

- **Profile**: evaluation-profile (primary)
- **Tools used**: `get_session_history`, `search_knowledge`, `workspace_read_file`
- **Learning Loop**: Feeds into `skill-feedback-analyzer` for continuous improvement
