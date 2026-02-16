---
name: test-case-writing
description: "Skill for test case design — equivalence partitioning, boundary value analysis, test data management, and integration test scenarios."
stage: testing
type: delivery-skill
---

# Test Case Writing Skill

## Purpose

This skill guides the SuperAgent in designing comprehensive, well-structured test cases using systematic techniques. The goal is to maximize defect detection with a manageable number of test cases.

---

## Equivalence Partitioning

### Concept

Divide the input domain into classes (partitions) where all values in a class are expected to produce the same behavior. Test at least one value from each partition.

### Process

1. **Identify input parameters** for the function under test
2. **For each parameter, define partitions**:
   - Valid partitions (inputs that should be accepted)
   - Invalid partitions (inputs that should be rejected)
3. **Select one representative value** from each partition
4. **Combine partitions** — one test case per interesting combination

### Example: Order Quantity Validation

Input: `quantity` (integer, valid range: 1-999)

| Partition           | Values  | Expected Behavior     | Test Value |
|---------------------|---------|----------------------|------------|
| Below minimum       | < 1     | Validation error     | 0          |
| Valid range         | 1-999   | Accepted             | 50         |
| Above maximum       | > 999   | Validation error     | 1000       |
| Null/missing        | null    | Validation error     | null       |
| Negative            | < 0     | Validation error     | -1         |
| Non-integer (if applicable) | 1.5 | Type error      | 1.5        |

### Multi-Parameter Equivalence

When multiple parameters interact, use **each-choice** or **pairwise** combination:

```
Parameter A: {valid_a1, valid_a2, invalid_a1}
Parameter B: {valid_b1, invalid_b1}

Each-choice (minimum):
Test 1: valid_a1, valid_b1  → success
Test 2: valid_a2, invalid_b1 → error from B
Test 3: invalid_a1, valid_b1 → error from A

Pairwise (more thorough):
Test 1: valid_a1, valid_b1
Test 2: valid_a1, invalid_b1
Test 3: valid_a2, valid_b1
Test 4: valid_a2, invalid_b1
Test 5: invalid_a1, valid_b1
Test 6: invalid_a1, invalid_b1
```

---

## Boundary Value Analysis

### Concept

Bugs cluster at the boundaries of equivalence partitions. Test at exact boundary values and immediately adjacent values.

### Process

For each boundary between partitions, test:
1. The value just below the boundary
2. The boundary value itself
3. The value just above the boundary

### Example: Order Quantity (range 1-999)

| Boundary     | Test Values   | Expected                    |
|-------------|---------------|-----------------------------|
| Lower bound | 0, 1, 2       | error, pass, pass           |
| Upper bound | 998, 999, 1000| pass, pass, error           |

### Boundary Types to Check

- **Numeric ranges**: min-1, min, min+1, max-1, max, max+1
- **String lengths**: empty, 1 char, max-1 chars, max chars, max+1 chars
- **Collection sizes**: empty, 1 element, max-1 elements, max elements, max+1 elements
- **Date ranges**: day before start, start date, end date, day after end
- **State transitions**: just before trigger condition, at trigger condition, just after

---

## Happy Path + Error Path + Edge Cases

### Happy Path Tests

Every feature needs at least one happy-path test that verifies the complete successful flow:

```kotlin
@Test
fun `should create order successfully with valid input`() {
    // Setup: valid customer, valid products, sufficient stock
    // Action: create order
    // Verify: order created with correct status, items, total
    // Verify: payment processed
    // Verify: confirmation event published
}
```

**Happy path checklist**:
- [ ] Primary use case with typical input
- [ ] Verify return value/response
- [ ] Verify side effects (database changes, events, notifications)
- [ ] Verify response format and structure

### Error Path Tests

For every error scenario identified in the design:

```kotlin
@Test
fun `should return 404 when order does not exist`() {
    // Setup: non-existent order ID
    // Action: get order
    // Verify: OrderNotFoundException thrown / 404 response
    // Verify: error response format matches contract
}

@Test
fun `should return 400 when request body is invalid`() {
    // Setup: missing required fields
    // Action: create order
    // Verify: ValidationException / 400 response
    // Verify: field-level error details included
}

@Test
fun `should return 422 when business rule violated`() {
    // Setup: order with amount exceeding limit
    // Action: create order
    // Verify: BusinessException / 422 response
    // Verify: specific error code returned
}
```

**Error path checklist**:
- [ ] Every exception type in the error hierarchy has at least one test
- [ ] Every HTTP error status in the API contract has at least one test
- [ ] Error response format matches the contract
- [ ] Error messages contain useful information (without leaking internals)
- [ ] Error logging is verified

### Edge Case Tests

Test the unusual but valid scenarios:

```kotlin
// Empty collections
@Test
fun `should handle customer with no previous orders`()

// Maximum values
@Test
fun `should handle order with maximum allowed items (999)`()

// Concurrent operations
@Test
fun `should handle concurrent order creation for same customer`()

// Unicode and special characters
@Test
fun `should handle product names with unicode characters`()

// Timezone boundaries
@Test
fun `should correctly calculate daily limit across timezone boundary`()

// Null and optional fields
@Test
fun `should create order without optional notes field`()
```

**Edge case categories**:
- [ ] Empty/null inputs for optional fields
- [ ] Maximum allowed values
- [ ] Minimum allowed values
- [ ] Unicode, special characters, very long strings
- [ ] Concurrent access patterns
- [ ] Time zone and locale edge cases
- [ ] Zero amounts, negative amounts
- [ ] Single-element collections, maximum-size collections

---

## Test Data Management

### Test Data Builders

Use the Builder pattern for creating test data with sensible defaults:

```kotlin
class OrderTestDataBuilder {
    private var id: UUID = UUID.randomUUID()
    private var customerId: UUID = UUID.randomUUID()
    private var status: OrderStatus = OrderStatus.PENDING
    private var items: MutableList<OrderItem> = mutableListOf()
    private var totalAmount: BigDecimal = BigDecimal.ZERO
    private var createdAt: Instant = Instant.now()

    fun withId(id: UUID) = apply { this.id = id }
    fun withCustomerId(customerId: UUID) = apply { this.customerId = customerId }
    fun withStatus(status: OrderStatus) = apply { this.status = status }
    fun withItems(items: List<OrderItem>) = apply {
        this.items = items.toMutableList()
    }
    fun withTotalAmount(amount: BigDecimal) = apply { this.totalAmount = amount }
    fun withCreatedAt(createdAt: Instant) = apply { this.createdAt = createdAt }

    fun confirmed() = apply {
        this.status = OrderStatus.CONFIRMED
    }

    fun withSingleItem(productId: UUID = UUID.randomUUID(), quantity: Int = 1, unitPrice: BigDecimal = BigDecimal("9.99")) = apply {
        items.add(OrderItem(productId = productId, quantity = quantity, unitPrice = unitPrice))
        totalAmount = unitPrice * quantity.toBigDecimal()
    }

    fun build(): Order = Order(
        id = id,
        customerId = customerId,
        status = status,
        totalAmount = totalAmount,
        createdAt = createdAt
    ).also { order -> items.forEach { order.addItem(it) } }
}

// Usage
fun anOrder() = OrderTestDataBuilder()

val order = anOrder().confirmed().withSingleItem().build()
```

### Test Data Guidelines

1. **Use builders with defaults**: Every field has a sensible default value
2. **Named factory methods**: `anOrder()`, `aCustomer()`, `aProduct()`
3. **Scenario methods**: `anOrder().confirmed()`, `anOrder().withExpiredPayment()`
4. **Avoid shared mutable state**: Each test creates its own data
5. **Avoid database-dependent IDs**: Use UUIDs, not auto-increment IDs
6. **Clean up after integration tests**: Use `@Transactional` or explicit cleanup
7. **Separate test data from test logic**: Builders in a shared `testutil` package

---

## Integration Test Scenarios

### Controller Integration Tests

```kotlin
@WebMvcTest(OrderController::class)
class OrderControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockBean lateinit var orderService: OrderService

    @Nested
    inner class CreateOrder {
        @Test
        fun `should return 201 with order when valid request`() { /* ... */ }

        @Test
        fun `should return 400 when required field missing`() { /* ... */ }

        @Test
        fun `should return 400 when quantity is zero`() { /* ... */ }

        @Test
        fun `should return 400 when quantity exceeds maximum`() { /* ... */ }

        @Test
        fun `should return 422 when payment is declined`() { /* ... */ }

        @Test
        fun `should return 401 when not authenticated`() { /* ... */ }
    }
}
```

### Repository Integration Tests

```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryIntegrationTest {

    @Autowired lateinit var orderRepository: OrderRepository

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")
            .withDatabaseName("testdb")
    }

    @Test
    fun `should find orders by customer ID`() { /* ... */ }

    @Test
    fun `should return empty list when customer has no orders`() { /* ... */ }

    @Test
    fun `should find recent orders after given timestamp`() { /* ... */ }
}
```

### Service Integration Tests (with external dependencies)

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class OrderServiceIntegrationTest {

    @Autowired lateinit var orderService: OrderService
    @MockBean lateinit var paymentClient: PaymentClient

    @Test
    fun `should create order end-to-end with database persistence`() {
        // Uses real database, mocked external payment service
    }
}
```

### Integration Test Checklist

- [ ] Every API endpoint has at least one integration test
- [ ] Authentication and authorization is tested per endpoint
- [ ] Request validation is tested with invalid inputs
- [ ] Database queries return correct results with test data
- [ ] External service failures are handled gracefully
- [ ] Transaction boundaries behave correctly
- [ ] Pagination, sorting, and filtering work correctly
