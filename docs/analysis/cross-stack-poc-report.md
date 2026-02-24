# Cross-Stack Migration PoC Report: .NET → Java

## 1. Overview

**Objective**: Validate that Forge's AI Skills (`codebase-profiler`, `business-rule-extraction`, `code-generation`) can drive a cross-stack migration from .NET (C#/ASP.NET) to Java (Spring Boot), achieving ≥ 90% business rule coverage.

**Source System**: `OrderService` — an ASP.NET WebAPI module handling order lifecycle management.

**Target System**: Java 21 / Spring Boot 3.3 / Spring Data JPA equivalent.

**Method**: Skills-driven analysis → rule extraction → code generation → coverage verification.

**Conclusion**: 11 out of 11 business rules successfully identified and mapped to target implementation. **Coverage: 100% (11/11)**.

---

## 2. Source System Analysis (codebase-profiler)

### Input Files

| File | Lines | Purpose |
|------|-------|---------|
| `OrderController.cs` | 155 | ASP.NET WebAPI REST controller |
| `OrderService.cs` | 230 | Business logic and validation |
| `Order.cs` | 105 | Entity Framework entities + data annotations |

### Profile Summary

- **Language**: C# (.NET 8)
- **Framework**: ASP.NET Core WebAPI + Entity Framework Core
- **Architecture**: Controller → Service → EF Context (3-tier)
- **API Style**: REST with `[ApiController]` attribute routing
- **Data Access**: Entity Framework Core with data annotations
- **Key Patterns**:
  - Result object pattern (`OrderResult.Success` / `OrderResult.Failure`)
  - State machine for order lifecycle
  - Data annotation validation on entities
  - Async/await throughout

### Dependency Map

```
OrderController
  └── OrderService
        └── OrderDbContext (EF Core)
              ├── Orders (DbSet<Order>)
              ├── Customers (DbSet<Customer>)
              └── OrderLineItems (DbSet<OrderLineItem>)
```

---

## 3. Business Rule Extraction (business-rule-extraction)

The `business-rule-extraction` Skill identified **11 business rules** from the source system:

| Rule ID | Description | Source Location | Type |
|---------|-------------|-----------------|------|
| BR-ORDER-001 | Order total amount must be > 0 | `OrderService.cs:72`, `Order.cs:33` | Validation |
| BR-ORDER-002 | Order status transitions: Created → Confirmed → Shipped → Delivered | `OrderService.cs:104,131,148` | State Machine |
| BR-ORDER-003 | Cancellation only allowed before Shipped status | `OrderService.cs:167-172` | Business Logic |
| BR-ORDER-004 | VIP customers receive 15% discount | `OrderService.cs:62-67` | Pricing |
| BR-ORDER-005 | Orders > ¥1000 require supervisor approval | `OrderService.cs:87-92` | Authorization |
| BR-ORDER-006 | Customer credit limit enforcement | `OrderService.cs:75-78` | Financial |
| BR-ORDER-007 | Orders must have at least one line item | `OrderService.cs:42-43` | Validation |
| BR-ORDER-008 | Shipping address required for physical goods | `OrderService.cs:80-81` | Validation |
| BR-ORDER-009 | Line item quantity must be ≥ 1 | `OrderService.cs:48-49`, `Order.cs:74` | Validation |
| BR-ORDER-010 | Line item unit price must be > 0 | `OrderService.cs:50-51`, `Order.cs:79` | Validation |
| BR-ORDER-011 | Maximum 50 items per order | `OrderService.cs:45-46` | Business Logic |

### Rule Categories

- **Validation** (5): BR-001, 007, 008, 009, 010
- **State Machine** (1): BR-002
- **Business Logic** (2): BR-003, 011
- **Pricing** (1): BR-004
- **Authorization** (1): BR-005
- **Financial** (1): BR-006

---

## 4. Target Java Code (code-generation)

The `code-generation` Skill produced equivalent Spring Boot / Java classes:

### Generated Structure

```
target-java/
├── OrderController.java     ← @RestController, equivalent REST endpoints
├── OrderService.java        ← @Service, all business rules preserved
├── Order.java               ← @Entity with JPA annotations
├── OrderLineItem.java       ← @Entity, child relationship
├── Customer.java            ← @Entity with VIP and credit fields
├── OrderStatus.java         ← Enum (replaces string constants)
├── OrderRepository.java     ← Spring Data JPA repository
├── CustomerRepository.java  ← Spring Data JPA repository
├── CreateOrderRequest.java  ← DTO (@Valid + Jakarta constraints)
└── OrderResult.java         ← Result pattern preserved
```

### Key Migration Mappings

| .NET Concept | Java Equivalent |
|--------------|-----------------|
| `[ApiController] + [Route]` | `@RestController + @RequestMapping` |
| `Entity Framework` | Spring Data JPA + Hibernate |
| `[Required] / [Range]` | `@NotNull / @Min / @Max` (Jakarta Validation) |
| `[Column(TypeName = "decimal")]` | `@Column(precision = 18, scale = 2)` + `BigDecimal` |
| `async Task<>` | Direct method calls (Spring transactional) |
| `ILogger<T>` | `LoggerFactory.getLogger()` (SLF4J) |
| `DbContext` | `JpaRepository<T, ID>` interfaces |
| `[ForeignKey]` | `@ManyToOne + @JoinColumn` |
| Result object pattern | Same pattern (preserved) |

### State Machine Implementation

```java
// .NET original (string constants)
public static class OrderStatus {
    public const string Created = "Created";
    // ...
}

// Java target (type-safe enum with transition validation)
public enum OrderStatus {
    CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
```

---

## 5. Business Rule Coverage Matrix

| Rule ID | Description | Source (.NET) | Target (Java) | Test Coverage | Status |
|---------|-------------|---------------|---------------|---------------|--------|
| BR-ORDER-001 | Amount > 0 | `OrderService.cs:72` | `OrderService.java:createOrder()` — validates `totalAmount > 0` | `testCreateOrder_amountMustBePositive` | Covered |
| BR-ORDER-002 | State transitions | `OrderService.cs:104,131,148` | `OrderStatus.canTransitionTo()` + service methods | `testConfirm_onlyFromCreated`, `testShip_onlyFromConfirmed`, `testDeliver_onlyFromShipped` | Covered |
| BR-ORDER-003 | Cancel before shipped | `OrderService.cs:167-172` | `OrderService.java:cancelOrder()` — checks `status != SHIPPED && status != DELIVERED` | `testCancel_notAllowedAfterShipped` | Covered |
| BR-ORDER-004 | VIP 15% discount | `OrderService.cs:62-67` | `OrderService.java:createOrder()` — `if (customer.isVip()) discount = subtotal * VIP_DISCOUNT` | `testCreateOrder_vipDiscount` | Covered |
| BR-ORDER-005 | >¥1000 needs approval | `OrderService.cs:87-92` | `OrderService.java:createOrder()` — `if (totalAmount > APPROVAL_THRESHOLD) order.setRequiresApproval(true)` | `testCreateOrder_requiresApproval` | Covered |
| BR-ORDER-006 | Credit limit check | `OrderService.cs:75-78` | `OrderService.java:createOrder()` — validates `currentBalance + total <= creditLimit` | `testCreateOrder_creditLimitExceeded` | Covered |
| BR-ORDER-007 | ≥ 1 line item | `OrderService.cs:42-43` | `OrderService.java:createOrder()` — validates `!items.isEmpty()` | `testCreateOrder_mustHaveItems` | Covered |
| BR-ORDER-008 | Shipping address for physical | `OrderService.cs:80-81` | `OrderService.java:createOrder()` — validates shipping address for physical goods | `testCreateOrder_shippingRequired` | Covered |
| BR-ORDER-009 | Quantity ≥ 1 | `OrderService.cs:48-49` | `CreateOrderRequest.java` — `@Min(1)` on quantity | `testCreateOrder_invalidQuantity` | Covered |
| BR-ORDER-010 | Unit price > 0 | `OrderService.cs:50-51` | `CreateOrderRequest.java` — `@DecimalMin("0.01")` on unitPrice | `testCreateOrder_invalidPrice` | Covered |
| BR-ORDER-011 | Max 50 items | `OrderService.cs:45-46` | `OrderService.java:createOrder()` — validates `items.size() <= MAX_ITEMS` | `testCreateOrder_maxItemsExceeded` | Covered |

### Coverage Summary

| Metric | Value |
|--------|-------|
| Total business rules extracted | 11 |
| Rules implemented in target | 11 |
| Rules with test coverage | 11 |
| **Business rule coverage** | **100% (11/11)** |

---

## 6. Coverage Analysis

The migration achieves **100% business rule coverage**, exceeding the ≥ 90% target.

### Improvements in Target

The Java target includes several improvements over the .NET source:

1. **Type-safe OrderStatus enum** with `canTransitionTo()` method — replaces error-prone string constants
2. **Jakarta Bean Validation** (`@NotNull`, `@Min`, `@DecimalMin`) on DTOs — moves validation closer to API boundary
3. **Centralized state machine logic** — `OrderStatus.canTransitionTo()` vs distributed if-checks
4. **BigDecimal for monetary values** — prevents floating-point precision issues (consistent with .NET's `decimal`)

### Risks and Gaps

| Area | Risk | Mitigation |
|------|------|-----------|
| EF Core vs JPA | Lazy loading behavior differences | Explicit `@ManyToOne(fetch = LAZY)` + integration tests |
| Decimal precision | `decimal` → `BigDecimal` rounding | Use `HALF_UP` rounding mode, test edge cases |
| Async patterns | .NET `async/await` → Java sync | Spring `@Transactional` handles connection management |
| DI lifecycle | .NET scoped services → Spring singletons | Verify thread safety in service layer |

---

## 7. Lessons Learned

1. **Business rule extraction is highly effective**: The `business-rule-extraction` Skill correctly identified all 11 rules from a mix of code patterns (data annotations, if-checks, constants, state machines).

2. **Framework mapping is straightforward**: ASP.NET → Spring Boot has well-established patterns. The `code-generation` Skill produced idiomatic Java that follows Spring conventions.

3. **State machines benefit from migration**: Converting string-based status to Java enums with transition validation is a net improvement — the migration adds type safety.

4. **Data annotation → Bean Validation mapping is 1:1**: `[Required]` → `@NotNull`, `[Range]` → `@Min/@Max`, `[StringLength]` → `@Size` — these map cleanly.

5. **Result pattern ports directly**: The `OrderResult.Success/Failure` pattern works identically in both ecosystems.

6. **Credit limit / financial rules need extra care**: Monetary calculations should be verified with boundary-value tests to ensure `BigDecimal` rounding matches `decimal` behavior.

7. **Skills complement each other**: `codebase-profiler` → `business-rule-extraction` → `code-generation` forms a natural pipeline. Each Skill's output feeds the next.

---

*Generated by Forge Platform — Sprint 2C Cross-Stack Migration PoC*
