---
name: test-case-writing
description: "Skill for test case design — equivalence partitioning, boundary value analysis, test data builders, and test coverage strategy."
stage: testing
type: delivery-skill
version: "3.0"
category: delivery
scope: platform
tags: [methodology, testing, test-design]
---

# Test Case Writing Skill

## Purpose

Guides systematic test case design to maximize defect detection with a manageable number of test cases.

---

## Equivalence Partitioning

Divide input domain into classes where all values produce the same behavior. Test at least one value from each partition.

### Process

1. Identify input parameters
2. For each parameter, define valid and invalid partitions
3. Select one representative value from each partition
4. Combine partitions — one test case per interesting combination

### Example: Order Quantity (valid range: 1-999)

| Partition | Values | Expected | Test Value |
|-----------|--------|----------|------------|
| Below minimum | < 1 | Validation error | 0 |
| Valid range | 1-999 | Accepted | 50 |
| Above maximum | > 999 | Validation error | 1000 |
| Null/missing | null | Validation error | null |
| Negative | < 0 | Validation error | -1 |

### Multi-Parameter Strategy

- **Each-choice** (minimum): one test per partition, ensuring each partition appears at least once
- **Pairwise** (thorough): all 2-way combinations of partitions

---

## Boundary Value Analysis

Bugs cluster at partition boundaries. Test exact boundary values and adjacent values.

### For each boundary, test:

1. Value just below the boundary
2. The boundary value itself
3. Value just above the boundary

### Boundary Types

- **Numeric ranges**: min-1, min, min+1, max-1, max, max+1
- **String lengths**: empty, 1, max-1, max, max+1
- **Collection sizes**: empty, 1, max-1, max, max+1
- **Date ranges**: day before start, start, end, day after end
- **State transitions**: just before trigger, at trigger, just after

---

## Test Coverage Strategy

### Required Tests per Feature

1. **Happy path**: primary use case with typical input — verify return value, side effects, response format
2. **Error paths**: one test per exception type in the error hierarchy, one per HTTP error status
3. **Edge cases**: empty/null optionals, max values, unicode, concurrent access, timezone boundaries

### Edge Case Categories

- [ ] Empty/null inputs for optional fields
- [ ] Maximum and minimum allowed values
- [ ] Unicode, special characters, very long strings
- [ ] Concurrent access patterns
- [ ] Timezone and locale boundaries
- [ ] Zero amounts, negative amounts
- [ ] Single-element and maximum-size collections

---

## Test Data Builders

Use the Builder pattern for test data with sensible defaults:

```kotlin
class OrderTestDataBuilder {
    private var id: UUID = UUID.randomUUID()
    private var status: OrderStatus = OrderStatus.PENDING
    private var items: MutableList<OrderItem> = mutableListOf()

    fun withStatus(status: OrderStatus) = apply { this.status = status }
    fun confirmed() = apply { this.status = OrderStatus.CONFIRMED }

    fun withSingleItem(
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("9.99")
    ) = apply {
        items.add(OrderItem(quantity = quantity, unitPrice = unitPrice))
    }

    fun build(): Order = Order(id = id, status = status, items = items)
}

// Usage: anOrder().confirmed().withSingleItem().build()
```

### Guidelines

1. Every field has a sensible default
2. Named factory: `anOrder()`, `aCustomer()`, `aProduct()`
3. Scenario methods: `.confirmed()`, `.withExpiredPayment()`
4. Each test creates its own data — avoid shared mutable state
5. Use UUIDs, not auto-increment IDs
