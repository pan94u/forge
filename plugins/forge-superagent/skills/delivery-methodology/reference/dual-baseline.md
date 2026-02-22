# Dual Baseline Cross-Validation

## Two Baselines, Two Purposes

### Design Baseline (Implementation-Driven)

- **Updated**: after each Phase ends, based on actual implementation
- **Records**: "what we built" — API endpoints, MCP tools, data models, configurations
- **Source of truth for**: current system state

### Planning Baseline (Design-Driven)

- **Updated**: during planning discussions, based on intended design
- **Records**: "what we intend to build" — Phase plans, feature scope, architecture targets
- **Source of truth for**: project roadmap and design intent

## Cross-Validation Process

After each Phase:

1. **Update design baseline** to reflect actual implementation
2. **Compare design baseline vs code**: are documented APIs, tools, configs accurate?
3. **Compare planning baseline vs design baseline**: does implementation match design intent?
4. **Identify discrepancies**:
   - Implementation exceeds plan → update plan or note scope creep
   - Implementation falls short of plan → plan remediation or adjust scope
   - Plan mentions features not in design baseline → implementation gap
   - Design baseline has features not in plan → unplanned additions

## Cross-Validation Checklist

1. [ ] Design baseline API endpoints match actual controller routes
2. [ ] Design baseline MCP tool names match actual tool implementations
3. [ ] Planning baseline Phase status reflects actual completion
4. [ ] Planning baseline metrics match actual measurements
5. [ ] No contradictions between the two baselines
6. [ ] No stale version annotations ("v1.3 added") — clean them up

## When to Full-Audit

- Every 3-4 sessions: quick cross-check (15 min)
- Every Phase end: full audit (30-60 min)
- After major refactoring: full audit
