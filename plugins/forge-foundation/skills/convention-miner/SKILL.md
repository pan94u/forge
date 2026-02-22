---
name: convention-miner
description: >
  Extract actual coding conventions from existing codebases. Discover naming
  patterns, package structure, test conventions. Auto-generate skill drafts.
trigger: when running convention analysis or onboarding a new codebase
tags: [conventions, mining, analysis, knowledge-mining, extraction]
version: "3.0"
category: foundation
scope: platform
---

# Convention Miner

## Purpose

Scan existing code to discover actual (not prescribed) conventions. This is the **learning loop entry point**: mined conventions become skill drafts that, after human review, join the skill repository.

## Workflow

```
1. Run mining scripts → JSON output
2. Review mining results → identify conventions
3. Generate skill draft → SKILL.md template
4. Human review → approve/modify/reject
5. Add to skill repository → knowledge encoded
```

## Mining Categories

### 1. Naming Patterns

- Class suffixes (Service, Controller, Repository, etc.)
- Method naming conventions (camelCase, snake_case, prefixes)
- Package/module structure

**Script**: `scripts/mine_naming.py [PROJECT_ROOT]`

### 2. Test Conventions

- Test naming patterns (should_X_when_Y, backtick descriptions, testMethodName)
- Framework usage (JUnit 5, MockK, Testcontainers)
- Test structure compliance (AAA pattern)

### 3. Architecture Patterns

- Layer structure (controller → service → repository)
- Dependency direction between packages
- Cross-layer violation detection

### 4. Configuration Patterns

- Properties vs YAML usage
- Environment variable patterns
- Profile-specific overrides

## Scripts

| Script | Input | Output |
|--------|-------|--------|
| `mine_naming.py` | Project root path | JSON: class suffixes, method patterns, test naming |
| `generate_practice_skill.py` | Mining JSON | SKILL.md draft (to stdout or file) |

## Learning Loop Closure

The `generate_practice_skill.py` script is the **closure point** of the learning loop:

```
mine_naming.py → mining_result.json → generate_practice_skill.py → SKILL.md draft → human review → skill repository
```

This pipeline converts raw codebase data into structured, reviewable knowledge.
