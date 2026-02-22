# Parallel Agent Usage Strategy

## When to Use Parallel Agents

### Good Candidates

| Scenario | Why Parallel Works |
|----------|-------------------|
| Initializing 5+ independent files | No data dependencies between files |
| Frontend + backend in separate modules | Changes don't interact until integration |
| Independent test scenarios | Each test validates a different aspect |
| Document batch updates | Logbook, baseline, acceptance test are independent |
| Multi-module build/test | Each module compiles independently |

### Bad Candidates

| Scenario | Why Serial is Better |
|----------|---------------------|
| Sequential API calls (create → update → delete) | Each step depends on prior result |
| Database migration + code change | Migration must succeed before code runs |
| Shared state modification | Race conditions, inconsistent state |
| Complex debugging | Need to observe cause-effect chain |
| Single coherent document | Narrative consistency requires single author |

## Parallel Agent Patterns

### Pattern 1: Frontend/Backend Split

```
Main Agent: coordinates overall implementation
├── Agent A: frontend components (React/Next.js)
└── Agent B: backend service + controller (Kotlin/Spring)
```

**Merge point**: integration test that calls API from frontend

### Pattern 2: Independent Test Suites

```
Main Agent: identifies test scenarios
├── Agent A: unit tests for Service A
├── Agent B: unit tests for Service B
└── Agent C: integration tests
```

**Merge point**: all tests pass

### Pattern 3: Documentation Batch

```
Main Agent: identifies documents to update
├── Agent A: update logbook with session record
├── Agent B: update design baseline
└── Agent C: update acceptance test document
```

**Merge point**: consistency check across documents

## Guidelines

1. **Define clear boundaries**: each agent's scope must be independent
2. **Define merge criteria**: what must be true before integrating results
3. **Limit parallelism**: 2-3 agents maximum; more creates coordination overhead
4. **Share context**: each agent needs sufficient context to work independently
5. **Serial fallback**: if agents produce conflicting changes, fall back to serial execution
