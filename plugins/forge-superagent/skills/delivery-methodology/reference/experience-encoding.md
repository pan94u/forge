# Experience Encoding Pipeline — Detail

## Stage 1: Raw Observation (During Delivery)

### What to Capture

- **Bugs**: root cause, not just symptoms
  - "H2 database treats column names as case-insensitive, but JPA `@Column(name)` uses exact case"
  - NOT: "query failed"

- **Surprises**: unexpected behavior of tools/frameworks
  - "Spring's `@Value` can parse comma-separated strings but not YAML lists"

- **Workarounds**: when the standard approach doesn't work
  - "Docker Alpine image lacks bash; baseline scripts must use sh"

- **Effective Patterns**: approaches that worked well
  - "Creating test fixtures as companion objects avoids test data coupling"

### Where to Record

Record in session notes first (logbook). Do NOT immediately encode into skills or baselines.

## Stage 2: Validation (Across Sessions)

### Validation Criteria

| Criterion | How to Check |
|-----------|-------------|
| Reproducible | Same issue/pattern appeared in 2+ different sessions |
| Generalizable | Applies beyond the specific file/module where observed |
| Actionable | Can be expressed as a rule: "Always X" or "Never Y" |
| Non-obvious | Not already common knowledge (would trip up an experienced developer) |

### Exceptions

Skip validation for:
- User-explicit requests ("always use bun", "never auto-commit")
- Platform-critical traps (data loss, security issues)

## Stage 3: Encoding

### Skill Encoding

When a pattern is a reusable practice:

1. Determine if it fits an existing skill or needs a new one
2. Add to the appropriate section of SKILL.md
3. Mark as project-specific or universal:
   - Universal: "Always validate input at the boundary"
   - Project-specific: "Use `Money(BigDecimal)` not `Double` for amounts"

### Baseline Encoding

When a pattern is a quality check that can be automated:

1. Write a script that checks for the violation
2. Script exits 0 (pass) or 1 (fail) with details on stdout
3. Register in the relevant profile's baseline list

### Config Encoding (CLAUDE.md)

When a pattern is project-specific and cross-cutting:

1. Add to the "Known Traps" or "Conventions" section
2. Include the root cause and correct approach
3. Reference the session where it was discovered

## Stage 4: Feedback Loop

After encoding, verify effectiveness:

- [ ] Encoded knowledge is discoverable (search by relevant terms)
- [ ] Baseline catches the issue in a test scenario
- [ ] Next session that encounters the pattern benefits from the encoding
- [ ] No false positives from overly broad rules
