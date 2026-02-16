---
name: java-conventions
description: >
  Java coding conventions for enterprise applications. Covers package naming,
  class naming suffixes, method ordering, null handling, exception hierarchy,
  and immutability patterns.
trigger: when writing or modifying .java files
tags: [java, conventions, naming, coding-standards]
---

# Java Conventions

## Package Naming

```
com.{org}.{domain}.{layer}
```

| Layer | Package | Example |
|-------|---------|---------|
| API/Controller | `.controller` | `com.forge.order.controller` |
| Service | `.service` | `com.forge.order.service` |
| Repository | `.repository` | `com.forge.order.repository` |
| Domain/Model | `.model` | `com.forge.order.model` |
| DTO | `.dto` | `com.forge.order.dto` |
| Configuration | `.config` | `com.forge.order.config` |
| Exception | `.exception` | `com.forge.order.exception` |
| Util | `.util` | `com.forge.common.util` |

## Class Naming Suffixes

| Type | Suffix | Example |
|------|--------|---------|
| REST Controller | `Controller` | `OrderController` |
| Service | `Service` | `OrderService` |
| Repository | `Repository` | `OrderRepository` |
| DTO (request) | `Request` | `CreateOrderRequest` |
| DTO (response) | `Response` | `OrderResponse` |
| Configuration | `Config` | `SecurityConfig` |
| Exception | `Exception` | `OrderNotFoundException` |
| Mapper | `Mapper` | `OrderMapper` |
| Validator | `Validator` | `OrderValidator` |
| Event | `Event` | `OrderCreatedEvent` |
| Listener | `Listener` | `OrderEventListener` |

## Method Ordering in Classes

```java
public class OrderService {
    // 1. Static fields
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    // 2. Instance fields
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;

    // 3. Constructor
    public OrderService(OrderRepository orderRepository, PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
    }

    // 4. Public API methods (business operations)
    public Order createOrder(CreateOrderRequest request) { ... }
    public Order getOrder(UUID id) { ... }
    public void cancelOrder(UUID id) { ... }

    // 5. Package-private / protected methods
    void validateOrder(Order order) { ... }

    // 6. Private helper methods
    private BigDecimal calculateTotal(List<OrderItem> items) { ... }

    // 7. Static factory/utility methods
    public static OrderService create(OrderRepository repo) { ... }
}
```

## Null Handling

### Return Types
- Use `Optional<T>` for methods that might not return a value
- NEVER return `null` from a public method — use `Optional.empty()`

```java
// GOOD
public Optional<Order> findById(UUID id) {
    return orderRepository.findById(id);
}

// BAD
public Order findById(UUID id) {
    return orderRepository.findById(id).orElse(null);  // NEVER
}
```

### Parameters
- Use `@NonNull` / `@Nullable` annotations (from `org.springframework.lang`)
- Validate non-null parameters at entry points

```java
public void processOrder(@NonNull Order order) {
    Objects.requireNonNull(order, "order must not be null");
    // ...
}
```

### Collections
- NEVER return `null` for collections — return empty collections
- Use `Collections.emptyList()`, `List.of()`, or `Collections.unmodifiableList()`

## Exception Hierarchy

```java
// Base business exception — maps to 4xx
public abstract class BusinessException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    protected BusinessException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}

// Base system exception — maps to 5xx
public abstract class SystemException extends RuntimeException {
    private final String errorCode;

    protected SystemException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}

// Specific exceptions
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(UUID orderId) {
        super("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND,
              "Order not found: " + orderId);
    }
}
```

## Immutability

- Prefer `final` fields wherever possible
- Use Java Records for DTOs (Java 16+)
- Use `Collections.unmodifiableList()` for collection fields
- Use builder pattern for complex objects

```java
// DTO as Record
public record CreateOrderRequest(
    @NotNull String customerId,
    @NotEmpty List<OrderItemRequest> items,
    @Valid PaymentInfo paymentInfo
) {}

// Entity with final fields
public class Order {
    private final UUID id;
    private final String customerId;
    private final List<OrderItem> items;
    private OrderStatus status;  // mutable state field

    public Order(UUID id, String customerId, List<OrderItem> items) {
        this.id = id;
        this.customerId = customerId;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.status = OrderStatus.CREATED;
    }
}
```

## Import Ordering

1. `java.*`
2. `javax.*`
3. `org.*`
4. `com.*`
5. Static imports last

No wildcard imports (`import java.util.*`) — always import specific classes.
