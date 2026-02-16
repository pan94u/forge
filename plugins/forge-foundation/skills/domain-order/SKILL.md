---
name: domain-order
description: >
  Order domain expertise. Domain model, order lifecycle state machine,
  business rules, code entry points, FAQ.
trigger: when working with order, order-item, cart, or checkout code
tags: [domain, order, cart, checkout, lifecycle]
---

# Order Domain

## Domain Model

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> CONFIRMED : confirm()
    CREATED --> CANCELLED : cancel()
    CONFIRMED --> PROCESSING : startProcessing()
    CONFIRMED --> CANCELLED : cancel()
    PROCESSING --> SHIPPED : ship(trackingNumber)
    SHIPPED --> DELIVERED : markDelivered()
    DELIVERED --> [*]
    CANCELLED --> [*]
```

## Business Rules
1. **Order minimum**: Orders must have at least 1 item and total ≥ $1.00
2. **Cancellation window**: Orders can only be cancelled in CREATED or CONFIRMED state
3. **Stock reservation**: Stock is reserved on CONFIRMED, released on CANCELLED
4. **Idempotency**: Create order uses idempotency key from checkout session

## Code Entry Points
- `OrderController` — `/api/v1/orders`
- `OrderService` — Order lifecycle management
- `OrderRepository` — JPA persistence
- `OrderEventPublisher` — Publishes OrderCreated, OrderConfirmed, etc.
