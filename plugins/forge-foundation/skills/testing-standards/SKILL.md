---
name: testing-standards
description: >
  Testing standards for enterprise applications. Covers naming conventions,
  AAA structure, mock strategies, Testcontainers, coverage targets.
trigger: when writing tests or containing "Test" keyword in context
tags: [testing, junit, mockk, testcontainers, coverage]
---

# Testing Standards

## Naming Convention

```
should_expectedBehavior_when_condition
```

```kotlin
@Test
fun should_createOrder_when_validRequest() { ... }

@Test
fun should_throwNotFoundException_when_orderDoesNotExist() { ... }

@Test
fun should_returnEmptyList_when_noOrdersForCustomer() { ... }
```

## AAA Structure (Arrange-Act-Assert)

```kotlin
@Test
fun should_calculateTotal_when_multipleItems() {
    // Arrange
    val items = listOf(
        OrderItem(productId = "P1", quantity = 2, unitPrice = Money(10.00)),
        OrderItem(productId = "P2", quantity = 1, unitPrice = Money(25.00))
    )

    // Act
    val total = orderService.calculateTotal(items)

    // Assert
    assertThat(total).isEqualTo(Money(45.00))
}
```

## Mock Strategy

- **MockK** for Kotlin, **Mockito** for Java
- Mock external dependencies, NOT the class under test
- Prefer `every { }` over `coEvery { }` unless suspending

```kotlin
@ExtendWith(MockKExtension::class)
class OrderServiceTest {
    @MockK private lateinit var orderRepository: OrderRepository
    @MockK private lateinit var paymentService: PaymentService
    @InjectMockKs private lateinit var orderService: OrderService

    @Test
    fun should_saveOrder_when_paymentSucceeds() {
        // Arrange
        every { paymentService.charge(any()) } returns PaymentResult.success()
        every { orderRepository.save(any()) } answers { firstArg() }

        // Act
        val order = orderService.createOrder(validRequest)

        // Assert
        assertThat(order.status).isEqualTo(OrderStatus.Created)
        verify(exactly = 1) { orderRepository.save(any()) }
    }
}
```

## Testcontainers for Integration Tests

```kotlin
@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_orders")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
```

## Coverage Targets

| Layer | Target | Rationale |
|-------|--------|-----------|
| Service | ≥ 80% | Core business logic |
| Controller | Integration test required | HTTP contract verification |
| Repository | Integration test for custom queries | SQL correctness |
| Domain model | ≥ 90% | Critical invariants |
| Utility | ≥ 90% | Edge cases matter |

## Test Behavior, Not Implementation

```kotlin
// GOOD — tests behavior
@Test
fun should_notAllowNegativeQuantity() {
    assertThrows<IllegalArgumentException> {
        OrderItem(productId = "P1", quantity = -1, unitPrice = Money(10.00))
    }
}

// BAD — tests implementation details
@Test
fun should_callRepositorySaveMethod() {
    orderService.createOrder(request)
    verify { orderRepository.save(any()) }  // too coupled to implementation
}
```
