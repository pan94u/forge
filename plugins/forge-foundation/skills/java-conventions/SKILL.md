---
name: java-conventions
description: >
  Java coding conventions for enterprise applications. Covers package naming,
  class naming suffixes, method ordering, null handling, and immutability patterns.
trigger: when writing or modifying .java files
tags: [java, conventions, naming, coding-standards]
version: "3.0"
category: foundation
scope: platform
note: "Forge project conventions. Other projects may adapt to their Java version and style."
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
| Event | `Event` | `OrderCreatedEvent` |

## Method Ordering in Classes

1. Static fields (logger, constants)
2. Instance fields (final dependencies)
3. Constructor
4. Public API methods (business operations)
5. Package-private / protected methods
6. Private helper methods
7. Static factory/utility methods

## Null Handling

- Return `Optional<T>` for methods that might not return a value — NEVER return `null` from public methods
- Use `@NonNull` / `@Nullable` annotations at entry points
- NEVER return `null` for collections — return empty collections

```java
// GOOD
public Optional<Order> findById(UUID id) {
    return orderRepository.findById(id);
}
```

## Immutability

- Prefer `final` fields wherever possible
- Use Java Records for DTOs (Java 16+)
- Use `Collections.unmodifiableList()` for collection fields

```java
public record CreateOrderRequest(
    @NotNull String customerId,
    @NotEmpty List<OrderItemRequest> items
) {}
```

## Import Ordering

1. `java.*`
2. `javax.*`
3. `org.*`
4. `com.*`
5. Static imports last

No wildcard imports — always import specific classes.
