---
name: business-rule-extraction
description: >
  Extract business rules, boundary conditions, and exception handling strategies
  from source code across any language. Produces structured, auditable documentation
  for knowledge preservation, migration planning, and team onboarding.
trigger: when the user requests business rule extraction, domain logic analysis, or migration knowledge mining
tags: [business-rules, domain-logic, knowledge-mining, migration, analysis]
profile: planning
supported-languages: [java, kotlin, csharp, python, go, typescript]
---

# Business Rule Extraction

## Purpose

Systematically extract and document business rules embedded in source code. This is critical for:
- **Migration projects**: Understanding what the legacy system actually does before rebuilding
- **Knowledge preservation**: Capturing tribal knowledge locked in code
- **Compliance audits**: Mapping business policies to their code implementations
- **Team onboarding**: Giving new developers a business-level understanding of the system

## When to Use

- Before a major technology migration (e.g., .NET → Java)
- When inheriting an undocumented legacy system
- During domain modeling for a new architecture
- When business stakeholders ask "what does the system actually do?"

## Extraction Process

### Step 1: Identify Business-Critical Code

Scan for classes/functions that contain domain logic (not infrastructure):

| Language | Look For |
|----------|----------|
| Java/Kotlin | `*Service.java`, `*Validator.java`, `*Rule*.java`, `*Policy*.java`, `*Calculator*.java` |
| C# | `*Service.cs`, `*Validator.cs`, `*Handler.cs` (MediatR), `*Specification.cs` |
| Python | `services/*.py`, `rules/*.py`, `validators/*.py`, `domain/*.py` |
| Go | `*_service.go`, `*_handler.go`, `*_validator.go` |

Skip infrastructure code: controllers, repositories, DTOs, config, migrations.

### Step 2: Extract Rules Per Method

For each business method, extract:

#### A. Business Rules
Conditions and logic that enforce business policies.

```
Rule: BR-ORDER-001
Source: OrderService.kt:45 — calculateTotal()
Description: Orders over $500 qualify for free shipping
Condition: order.subtotal > 500.00
Action: shipping cost set to 0
```

#### B. Boundary Conditions
Edge cases and limits the system enforces.

```
Boundary: BC-ORDER-001
Source: OrderService.kt:52 — validateOrder()
Description: Maximum 99 items per order
Condition: order.items.size > 99
Action: throw MaxItemsExceededException
```

#### C. Exception / Error Handling Strategy
How the system responds to failures.

```
Error Strategy: ES-PAYMENT-001
Source: PaymentService.kt:78 — processPayment()
Trigger: Payment gateway returns HTTP 503
Strategy: Retry 3 times with exponential backoff, then mark order as "payment_pending"
Compensation: Scheduled job retries after 15 minutes
```

### Step 3: Group by Domain / Module

Organize extracted rules by bounded context or business domain:

```
orders/
  business-rules.md      — All order-related rules
  boundary-conditions.md — All order limits and edge cases
  error-strategies.md    — All order error handling
payments/
  business-rules.md
  boundary-conditions.md
  error-strategies.md
inventory/
  ...
```

### Step 4: Cross-Reference and Validate

- Link related rules across domains (e.g., order rules that trigger inventory rules)
- Identify rule conflicts or redundancies
- Flag rules that appear duplicated in multiple services (potential consistency issue)
- Mark rules that have no corresponding test (risk indicator)

## Output Format

### Per-Domain Business Rules File

```markdown
# {Domain} Business Rules

## Summary
- Total rules extracted: {N}
- Total boundary conditions: {N}
- Total error strategies: {N}
- Test coverage: {covered}/{total} rules have corresponding tests

## Business Rules

### BR-{DOMAIN}-001: {Short Description}
- **Source**: `{file}:{line}` — `{methodSignature}`
- **Description**: {Business-language description of the rule}
- **Condition**: `{code condition or pseudocode}`
- **Action**: {What happens when condition is true}
- **Test**: {link to test or "UNTESTED"}
- **Confidence**: HIGH | MEDIUM | LOW
- **Notes**: {Any ambiguity or assumptions}

### BR-{DOMAIN}-002: ...

## Boundary Conditions

### BC-{DOMAIN}-001: {Short Description}
- **Source**: `{file}:{line}` — `{methodSignature}`
- **Type**: MIN_VALUE | MAX_VALUE | REQUIRED | FORMAT | STATE_PREREQUISITE
- **Constraint**: `{the actual constraint}`
- **Violation Response**: {exception thrown or error returned}
- **Test**: {link to test or "UNTESTED"}

## Error Handling Strategies

### ES-{DOMAIN}-001: {Short Description}
- **Source**: `{file}:{line}` — `{methodSignature}`
- **Trigger**: {What error condition triggers this strategy}
- **Strategy**: RETRY | COMPENSATE | FAIL_FAST | DEGRADE | ESCALATE
- **Details**: {Retry count, backoff policy, fallback behavior, etc.}
- **Compensation**: {Rollback or compensation action, if any}
- **Alert**: {Whether ops is notified}
```

### Cross-Domain Summary File

```markdown
# Business Rule Extraction Summary

## Extraction Metadata
- System: {system name}
- Date: {extraction date}
- Extractor: Forge AI + {human reviewer}
- Source languages: {languages found}
- Modules analyzed: {count}

## Rule Statistics
| Domain | Business Rules | Boundaries | Error Strategies | Test Coverage |
|--------|---------------|------------|-----------------|---------------|
| Orders | 15 | 8 | 5 | 73% |
| Payments | 12 | 6 | 8 | 80% |
| Inventory | 9 | 4 | 3 | 56% |
| **Total** | **36** | **18** | **16** | **70%** |

## Cross-Domain Dependencies
- Order creation triggers inventory reservation (BR-ORDER-003 → BR-INV-001)
- Payment failure triggers order cancellation (ES-PAY-002 → BR-ORDER-010)

## Risk Areas
- {Rules with LOW confidence — need human validation}
- {Rules with no tests — high migration risk}
- {Conflicting rules across domains}
```

## HITL Approval Checklist

Before the extraction is considered complete, a human domain expert MUST review:

- [ ] **Completeness**: All Service classes analyzed (none skipped)
- [ ] **Accuracy**: Business-language descriptions match actual code behavior
- [ ] **Missing rules**: Any known business rules NOT found in code (implemented elsewhere or undocumented)
- [ ] **Confidence ratings**: LOW-confidence rules investigated and resolved
- [ ] **Cross-references**: Domain dependencies correctly identified
- [ ] **Test gaps**: UNTESTED rules flagged for test creation before migration

## Quality Criteria

| Criterion | Threshold |
|-----------|-----------|
| Coverage | 100% of Service/Handler classes analyzed |
| Completeness | Every extracted rule has: source, description, condition, action |
| Traceability | Every rule links to exact file:line |
| Reviewability | Descriptions use business language, not code jargon |
| Confidence | No more than 20% of rules rated LOW confidence |
