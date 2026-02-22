# Session Micro-PDCA Pattern

## Detailed Session Lifecycle

### 1. Plan Phase (Session Start)

**Input**: User's goal for this session (or continuation of prior work)

**Actions**:
- Declare session goal in 1-2 sentences
- Review relevant prior session notes (if continuing)
- Identify files that will be modified
- Estimate scope: is this achievable in one session?

**Output**: Goal statement + rough plan

### 2. Do Phase (Implementation)

**Actions**:
- Execute the plan step by step
- Record each file change (operation + file + rationale)
- Run baselines after significant changes (not just at the end)
- When blocked: investigate, don't guess

**Output**: Modified files + file change table

### 3. Check Phase (Verification)

**Actions**:
- Run all applicable baselines
- Run tests (unit → integration)
- Verify output against acceptance criteria
- Cross-check against design documents

**Decision Point**:
- All pass → proceed to Act
- Any failure → loop back to Do with failure context
- 3 failures → escalate to human

**Output**: Verification report (pass/fail + details)

### 4. Act Phase (Knowledge Capture)

**Actions**:
- Record bugs discovered and root causes
- Note patterns worth encoding (for future sessions)
- Update statistics snapshot
- Commit with descriptive message

**Output**: Session record (file changes, bugs, experiences, stats)

## Session Size Guidelines

| Session Size | Typical Scope | Duration Hint |
|-------------|---------------|---------------|
| Small | Bug fix, config change | 30-60 min |
| Medium | Feature implementation | 1-2 hours |
| Large | Multi-file refactoring | 2-3 hours |
| Extra-Large | Split into 2+ sessions | N/A |

**Rule**: If scope exceeds "Large", split into multiple focused sessions.
