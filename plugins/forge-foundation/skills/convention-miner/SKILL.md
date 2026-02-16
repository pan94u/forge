---
name: convention-miner
description: >
  Extract actual coding conventions from existing codebases. Compare actual
  patterns vs prescribed conventions. Auto-update Foundation Skills.
trigger: when running convention analysis or during monthly convention-sync CI
tags: [conventions, mining, analysis, knowledge-mining]
---

# Convention Miner

## Purpose
Scan existing code to discover actual (not prescribed) conventions, compare with Foundation Skills, and generate update PRs.

## Mining Categories

### 1. Naming Patterns
- Class/method/variable naming frequency analysis
- Package structure conventions
- Suffix/prefix patterns (e.g., all services end with "Service")

### 2. Exception Handling Patterns
- Which exceptions are caught and where
- Custom exception class hierarchies
- Error response formats

### 3. Layering Patterns
- Actual dependency direction between layers
- Cross-layer violations
- Common architectural patterns

### 4. Configuration Patterns
- How configuration is structured (properties vs YAML)
- Environment variable usage patterns
- Profile-specific overrides

### 5. Test Patterns
- Test naming conventions actually used
- Mock frameworks in use
- Test structure (AAA compliance)
- Coverage distribution

## Output
- `knowledge-base/conventions/{date}-report.md` — Diff report
- Auto-generated PR updating Foundation Skill files when drift detected
- Dashboard metrics: convention compliance score per module
