---
name: domain-payment
description: >
  Payment domain expertise. Domain model, business rules, state machine,
  code entry points, FAQ, and data flow.
trigger: when working with payment, refund, transaction, or billing code
tags: [domain, payment, refund, transaction, billing]
---

# Payment Domain

## Domain Model

```mermaid
classDiagram
    Payment "1" --> "1..*" Transaction
    Payment "1" --> "0..*" Refund
    Payment "1" --> "1" PaymentMethod

    class Payment {
        +UUID id
        +UUID orderId
        +Money amount
        +PaymentStatus status
        +PaymentMethod method
        +String idempotencyKey
        +Instant createdAt
    }

    class Transaction {
        +UUID id
        +UUID paymentId
        +TransactionType type
        +Money amount
        +String gatewayRef
        +TransactionStatus status
    }

    class Refund {
        +UUID id
        +UUID paymentId
        +Money amount
        +String reason
        +RefundStatus status
    }
```

## Business Rules
1. **Idempotency**: Every payment request MUST have an idempotency key. Duplicate keys return existing result.
2. **State Machine**: PENDING → AUTHORIZED → CAPTURED → (REFUNDED). No backward transitions.
3. **Partial Refunds**: Allowed up to original captured amount. Sum of refunds ≤ captured amount.
4. **Currency Consistency**: All amounts in a payment must use the same currency.
5. **Timeout**: Authorization expires after 7 days. Must capture before expiry.

## Code Entry Points
- `PaymentController` — `/api/v1/payments`
- `PaymentService` — Core business logic
- `PaymentGatewayClient` — External gateway integration
- `PaymentEventListener` — Handles async events (OrderCreated → InitiatePayment)

## FAQ
- **Q: How to test payment locally?** A: Use gateway sandbox mode via `PAYMENT_GATEWAY_SANDBOX=true`
- **Q: What happens on gateway timeout?** A: Retry with exponential backoff (3 attempts), then mark as FAILED
- **Q: How are refunds processed?** A: Async via RefundService. Gateway webhook confirms completion.
