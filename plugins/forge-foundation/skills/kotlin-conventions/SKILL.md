---
name: kotlin-conventions
description: >
  Kotlin coding conventions for enterprise applications. Covers data class
  guidelines, coroutine patterns, sealed classes, scope functions, extension
  functions, and null safety.
trigger: when writing or modifying .kt files
tags: [kotlin, conventions, coroutines, sealed-classes]
---

# Kotlin Conventions

## Data Class Guidelines

Use data classes for DTOs, value objects, and configuration holders:

```kotlin
// DTO
data class CreateOrderRequest(
    val customerId: String,
    val items: List<OrderItemRequest>,
    val paymentMethod: PaymentMethod
) {
    init {
        require(items.isNotEmpty()) { "Order must have at least one item" }
        require(customerId.isNotBlank()) { "Customer ID must not be blank" }
    }
}

// Value Object
data class Money(val amount: BigDecimal, val currency: Currency) {
    init {
        require(amount >= BigDecimal.ZERO) { "Amount must be non-negative" }
    }
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies" }
        return Money(amount + other.amount, currency)
    }
}
```

**When NOT to use data class**: Entities with mutable state, classes with inheritance hierarchies, classes with large numbers of fields (>7).

## Coroutine Patterns

### Structured Concurrency
```kotlin
class OrderService(
    private val orderRepository: OrderRepository,
    private val inventoryClient: InventoryClient,
    private val paymentClient: PaymentClient
) {
    // Use coroutineScope for parallel operations
    suspend fun createOrder(request: CreateOrderRequest): Order = coroutineScope {
        val stockCheck = async { inventoryClient.checkStock(request.items) }
        val paymentAuth = async { paymentClient.authorize(request.payment) }

        val stock = stockCheck.await()
        val payment = paymentAuth.await()

        require(stock.isAvailable) { "Insufficient stock" }
        require(payment.isAuthorized) { "Payment not authorized" }

        orderRepository.save(Order.from(request, payment))
    }
}
```

### SupervisorScope for Independent Operations
```kotlin
suspend fun notifyAll(order: Order) = supervisorScope {
    launch { emailService.sendConfirmation(order) }   // failure won't cancel others
    launch { smsService.sendNotification(order) }
    launch { analyticsService.trackOrder(order) }
}
```

## Sealed Class Hierarchies

Use sealed classes/interfaces for domain modeling:

```kotlin
sealed interface OrderStatus {
    data object Created : OrderStatus
    data object Confirmed : OrderStatus
    data class Processing(val startedAt: Instant) : OrderStatus
    data class Shipped(val trackingNumber: String) : OrderStatus
    data class Delivered(val deliveredAt: Instant) : OrderStatus
    data class Cancelled(val reason: String, val cancelledAt: Instant) : OrderStatus
}

// Exhaustive when — compiler enforces all cases
fun Order.nextActions(): List<Action> = when (status) {
    is OrderStatus.Created -> listOf(Action.CONFIRM, Action.CANCEL)
    is OrderStatus.Confirmed -> listOf(Action.PROCESS, Action.CANCEL)
    is OrderStatus.Processing -> listOf(Action.SHIP)
    is OrderStatus.Shipped -> listOf(Action.DELIVER)
    is OrderStatus.Delivered -> emptyList()
    is OrderStatus.Cancelled -> emptyList()
}
```

## Scope Functions — When to Use Each

| Function | Object ref | Return | Use when |
|----------|-----------|--------|----------|
| `let` | `it` | Lambda result | Null check + transform |
| `run` | `this` | Lambda result | Object config + compute result |
| `with` | `this` | Lambda result | Grouping calls on object |
| `apply` | `this` | Object itself | Object configuration (builder) |
| `also` | `it` | Object itself | Side effects (logging, validation) |

```kotlin
// let — null-safe transformation
val orderResponse = order?.let { it.toResponse() }

// apply — object configuration
val config = HttpClientConfig().apply {
    connectTimeout = Duration.ofSeconds(5)
    readTimeout = Duration.ofSeconds(30)
    retryCount = 3
}

// also — side effects
return orderRepository.save(order).also {
    logger.info("Order saved: id={}", it.id)
    eventPublisher.publish(OrderCreatedEvent(it))
}

// run — compute on object
val summary = order.run {
    "Order $id: $itemCount items, total=$total, status=$status"
}
```

## Extension Functions

```kotlin
// Good — adds natural behavior
fun Order.isEditable(): Boolean = status in listOf(OrderStatus.Created, OrderStatus.Confirmed)

// Good — domain-specific formatting
fun Money.toDisplayString(): String = "${currency.symbol}${amount.setScale(2)}"

// Bad — don't use extensions to add unrelated behavior
// fun Order.sendEmail() — this doesn't belong on Order
```

## Null Safety

```kotlin
// Prefer ?.let for optional chaining
user?.address?.let { address ->
    geocodingService.lookup(address)
}

// Use ?: elvis for defaults
val name = user?.name ?: "Anonymous"

// Use requireNotNull at boundaries
fun processOrder(orderId: String?) {
    val id = requireNotNull(orderId) { "Order ID is required" }
    // id is now non-null
}

// NEVER use !! in production code (except tests)
// val name = user!!.name  // AVOID — throws NPE
```

## Companion Object Conventions

```kotlin
class Order private constructor(
    val id: UUID,
    val customerId: String,
    val items: List<OrderItem>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(Order::class.java)

        fun create(customerId: String, items: List<OrderItem>): Order {
            require(items.isNotEmpty()) { "Must have items" }
            return Order(UUID.randomUUID(), customerId, items)
        }
    }
}
```
