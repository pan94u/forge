---
name: code-generation
description: "Skill for code generation — design-first generation, convention adherence, incremental coding, and test co-generation."
stage: development
type: delivery-skill
---

# Code Generation Skill

## Purpose

This skill guides the SuperAgent in generating high-quality, production-ready code. The core principle is: **read the design first, then generate code that precisely implements the design while following all loaded conventions and producing tests alongside the implementation.**

---

## Core Principles

### 1. Design-First Generation

Never generate code without first reading and understanding:
- The architecture design (C4 diagrams, ADRs)
- The detailed design (class diagrams, sequence diagrams)
- The API contracts (OpenAPI spec)
- The database schema design (DDL, migrations)

If no design exists, escalate to the Design profile first. Do not infer architecture from thin air.

### 2. Convention Adherence

Before writing a single line of code:
- Load Foundation Skills for the detected language and framework
- Load Domain Skills for the affected modules
- Read at least 2-3 existing files in the same package to understand local patterns
- Match: naming conventions, file organization, import ordering, documentation style

### 3. Implementation + Tests Together

For every unit of code generated:
- Write the implementation
- Write the corresponding tests immediately
- Verify the tests pass before moving on
- Never submit code without tests

### 4. Incremental Generation

- Do NOT rewrite existing code unless explicitly modifying it
- Add new classes/methods alongside existing ones
- Modify existing files minimally — change only what the design requires
- Preserve existing comments, formatting, and structure in unchanged sections

### 5. Compilation Verification

Before any HITL checkpoint or baseline run:
- Verify the code compiles: `./gradlew compileKotlin` or equivalent
- Fix all compilation errors before proceeding
- Never submit code that does not compile

---

## Generation Process

### Step 1: Read and Internalize the Design

```
Read design docs → Build mental model → Identify all classes/interfaces to create
```

Create a generation checklist:

| Design Element        | Code to Generate              | Test to Generate              |
|-----------------------|-------------------------------|-------------------------------|
| Entity class diagram  | Entity + value objects        | Entity unit tests             |
| Repository interface  | Repository interface + impl   | Repository integration tests  |
| Service sequence      | Service class + methods       | Service unit tests            |
| Controller endpoint   | Controller + DTOs + mappers   | Controller integration tests  |
| Error scenarios       | Exception classes + handler   | Error handling tests          |
| Database schema       | Migration scripts             | Migration verification tests  |

### Step 2: Generate Data Layer

**Entities**:
```kotlin
// Follow the class diagram exactly
// Use JPA annotations matching the database schema
// Include validation annotations from the API contract
// Add KDoc documentation for non-obvious fields

@Entity
@Table(name = "orders")
class Order(
    @Id
    @Column(name = "id")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf()

    fun addItem(item: OrderItem) {
        items.add(item)
        recalculateTotal()
    }

    fun cancel() {
        require(status in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED)) {
            "Cannot cancel order in status $status"
        }
        status = OrderStatus.CANCELLED
        updatedAt = Instant.now()
    }

    private fun recalculateTotal() {
        totalAmount = items.sumOf { it.unitPrice * it.quantity.toBigDecimal() }
        updatedAt = Instant.now()
    }
}
```

**Repositories**:
```kotlin
// Interface-based, following Spring Data conventions
// Add custom queries only when Spring Data naming is insufficient
// Use @Query with JPQL for complex queries, never native SQL unless necessary

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByCustomerId(customerId: UUID): List<Order>
    fun findByStatus(status: OrderStatus): List<Order>
    fun findByCustomerIdAndStatus(customerId: UUID, status: OrderStatus): List<Order>

    @Query("SELECT o FROM Order o WHERE o.createdAt >= :since ORDER BY o.createdAt DESC")
    fun findRecentOrders(@Param("since") since: Instant): List<Order>
}
```

### Step 3: Generate Service Layer

Follow the sequence diagrams precisely:

```kotlin
// Service class structure:
// 1. Constructor injection for all dependencies
// 2. Public methods matching the sequence diagram operations
// 3. Private helper methods for internal logic
// 4. Transaction boundaries on public methods
// 5. Error handling per the error handling strategy

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val paymentClient: PaymentClient,
    private val eventPublisher: EventPublisher
) {
    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        logger.info("Creating order for customer: {}", request.customerId)

        validateOrder(request)

        val order = Order(customerId = request.customerId)
        request.items.forEach { item ->
            order.addItem(OrderItem(
                productId = item.productId,
                quantity = item.quantity,
                unitPrice = item.unitPrice
            ))
        }

        val savedOrder = orderRepository.save(order)

        val paymentResult = paymentClient.processPayment(
            orderId = savedOrder.id,
            amount = savedOrder.totalAmount
        )

        return when (paymentResult) {
            PaymentResult.SUCCESS -> {
                savedOrder.status = OrderStatus.CONFIRMED
                val confirmedOrder = orderRepository.save(savedOrder)
                eventPublisher.publish(OrderConfirmedEvent(confirmedOrder.id))
                confirmedOrder
            }
            PaymentResult.FAILED -> {
                savedOrder.status = OrderStatus.PAYMENT_FAILED
                orderRepository.save(savedOrder)
                eventPublisher.publish(OrderPaymentFailedEvent(savedOrder.id))
                throw PaymentDeclinedException(savedOrder.id)
            }
        }
    }

    fun getOrder(id: UUID): Order {
        return orderRepository.findById(id).orElseThrow {
            OrderNotFoundException(id)
        }
    }

    private fun validateOrder(request: CreateOrderRequest) {
        require(request.items.isNotEmpty()) { "Order must have at least one item" }
        // Additional business validations
    }
}
```

### Step 4: Generate API Layer

Match the OpenAPI contract exactly:

```kotlin
// Controller structure:
// 1. Map to OpenAPI paths and operations
// 2. Use DTO classes for request/response (never expose entities)
// 3. Use mappers to convert between DTOs and domain objects
// 4. Validation annotations on request DTOs matching OpenAPI schema
// 5. Consistent response format

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderMapper: OrderMapper
) {
    @PostMapping
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequestDto
    ): ResponseEntity<OrderResponseDto> {
        val order = orderService.createOrder(orderMapper.toDomain(request))
        val response = orderMapper.toDto(order)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: UUID): ResponseEntity<OrderResponseDto> {
        val order = orderService.getOrder(id)
        return ResponseEntity.ok(orderMapper.toDto(order))
    }
}
```

### Step 5: Generate Tests

**Unit tests for services**:
```kotlin
// Test structure:
// - One test class per service class
// - Nested classes grouping tests by method
// - Descriptive test names using backticks or @DisplayName
// - Arrange-Act-Assert pattern
// - Mock external dependencies, use real domain objects

@ExtendWith(MockitoExtension::class)
class OrderServiceTest {

    @Mock lateinit var orderRepository: OrderRepository
    @Mock lateinit var paymentClient: PaymentClient
    @Mock lateinit var eventPublisher: EventPublisher

    @InjectMocks lateinit var orderService: OrderService

    @Nested
    inner class CreateOrder {
        @Test
        fun `should create and confirm order when payment succeeds`() {
            // Arrange
            val request = CreateOrderRequest(/* ... */)
            whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }
            whenever(paymentClient.processPayment(any(), any()))
                .thenReturn(PaymentResult.SUCCESS)

            // Act
            val result = orderService.createOrder(request)

            // Assert
            assertThat(result.status).isEqualTo(OrderStatus.CONFIRMED)
            verify(eventPublisher).publish(any<OrderConfirmedEvent>())
        }

        @Test
        fun `should throw PaymentDeclinedException when payment fails`() {
            // Arrange
            val request = CreateOrderRequest(/* ... */)
            whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] }
            whenever(paymentClient.processPayment(any(), any()))
                .thenReturn(PaymentResult.FAILED)

            // Act & Assert
            assertThrows<PaymentDeclinedException> {
                orderService.createOrder(request)
            }
            verify(eventPublisher).publish(any<OrderPaymentFailedEvent>())
        }
    }
}
```

**Integration tests for controllers**:
```kotlin
@WebMvcTest(OrderController::class)
class OrderControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockBean lateinit var orderService: OrderService
    @MockBean lateinit var orderMapper: OrderMapper

    @Test
    fun `POST orders should return 201 with created order`() {
        // Arrange
        val requestBody = """{"customerId": "...", "items": [...]}"""
        whenever(orderService.createOrder(any())).thenReturn(testOrder())
        whenever(orderMapper.toDto(any())).thenReturn(testOrderDto())

        // Act & Assert
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }
}
```

---

## Code Quality Rules

### Before Submitting Any Code

1. **Compiles without errors**: Run `./gradlew compileKotlin`
2. **All tests pass**: Run `./gradlew test`
3. **No TODO/FIXME without ticket**: Every TODO must reference an issue
4. **No commented-out code**: Delete it; git has history
5. **No unused imports**: IDE or ktlint will catch these
6. **Meaningful names**: Variables, methods, classes describe their purpose
7. **Functions are focused**: Each function does one thing well
8. **Error messages are helpful**: Include context (IDs, values) in error messages
9. **Logging is appropriate**: INFO for business events, DEBUG for details, ERROR for failures
10. **No hardcoded values**: Use configuration or constants

### Incremental Generation Checklist

- [ ] Only new/modified files are included in the changeset
- [ ] Existing files preserve unchanged sections exactly
- [ ] New code follows the same patterns as adjacent existing code
- [ ] Import ordering matches the project convention
- [ ] File placement matches the project package structure
- [ ] No duplicate logic — reuse existing utilities where possible
