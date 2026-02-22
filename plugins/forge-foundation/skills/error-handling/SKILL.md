---
name: error-handling
description: >
  Error handling patterns. Exception hierarchies, Result types,
  structured error responses, retry strategies, circuit breakers.
trigger: when implementing error handling, try/catch, or exception classes
tags: [error-handling, exceptions, resilience, retry, circuit-breaker]
version: "2.0"
scope: platform
category: foundation
---

# Error Handling

## Golden Rule: Never Catch Generic Exceptions

```kotlin
// NEVER
try { processPayment(order) }
catch (e: Exception) { logger.error("Failed", e) }

// DO — catch specific exceptions
try { processPayment(order) }
catch (e: PaymentDeclinedException) { order.markFailed(e.reason) }
catch (e: PaymentGatewayTimeoutException) { scheduleRetry(order) }
```

The ONLY place for catching `Exception` is `@ControllerAdvice` global handler.

## Exception Hierarchy

```kotlin
// Business exceptions (4xx — client can fix)
abstract class BusinessException(
    val errorCode: String, val httpStatus: HttpStatus, message: String
) : RuntimeException(message)

// System exceptions (5xx — infrastructure problem)
abstract class SystemException(
    val errorCode: String, message: String, cause: Throwable
) : RuntimeException(message, cause)
```

## Result<T> for Expected Failures

```kotlin
sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: DomainError) : OperationResult<Nothing>
}

sealed interface DomainError {
    data class NotFound(val resource: String, val id: String) : DomainError
    data class ValidationFailed(val violations: List<String>) : DomainError
    data class Conflict(val message: String) : DomainError
}
```

## Structured Error Responses with Correlation ID

Every error response MUST include `correlationId` from MDC for distributed tracing.

## Retry Rules
- ONLY retry on transient infrastructure errors
- NEVER retry on business exceptions (4xx)
- NEVER retry non-idempotent operations without idempotency key
- Always use exponential backoff with jitter
- Max 3-5 attempts

## Circuit Breaker (Resilience4j)
```
CLOSED → (failure threshold 50%) → OPEN
OPEN → (wait 30s) → HALF_OPEN
HALF_OPEN → (test calls succeed) → CLOSED
```

## Distributed System Error Handling

### Saga Pattern (Choreography)

For multi-service operations where each step must be compensable on failure.

```
Order Service          Payment Service        Inventory Service
     |                       |                       |
     |--- OrderCreated ----->|                       |
     |                       |--- PaymentCharged --->|
     |                       |                       |--- StockReserved ---> DONE
     |                       |                       |
     |                  (if payment fails)           |
     |<-- PaymentFailed ----|                        |
     |--- OrderCancelled                             |
     |                                               |
     |                  (if stock fails)             |
     |                       |<-- StockFailed -------|
     |                       |--- PaymentRefunded    |
     |<-- OrderCancelled ----|                       |
```

```kotlin
// Each service defines its action + compensation
data class SagaStep<T>(
    val name: String,
    val action: suspend () -> T,
    val compensation: suspend (T) -> Unit
)

// Orchestration-based saga coordinator
class SagaOrchestrator {
    private val completedSteps = mutableListOf<Pair<String, Any>>()

    suspend fun <T> execute(step: SagaStep<T>): T {
        return try {
            val result = step.action()
            completedSteps.add(step.name to result as Any)
            result
        } catch (e: Exception) {
            logger.error("Saga step '${step.name}' failed, compensating...")
            compensateAll()
            throw SagaRollbackException("Saga failed at '${step.name}'", e)
        }
    }

    private suspend fun compensateAll() {
        // Compensate in reverse order
        completedSteps.asReversed().forEach { (name, result) ->
            try {
                // invoke stored compensation
                logger.info("Compensating: $name")
            } catch (e: Exception) {
                logger.error("Compensation failed for '$name' — manual intervention required", e)
                // Alert ops team — this is a critical inconsistency
            }
        }
    }
}
```

**Rules**:
- Every action MUST have a compensation (no "fire and forget" in sagas)
- Compensations must be idempotent — they may be retried
- Log all saga steps with correlation ID for audit trail
- If compensation fails → alert ops team, do NOT silently swallow

### Outbox Pattern (Reliable Event Publishing)

Guarantee that domain events are published even if the message broker is temporarily down.

```kotlin
// 1. Write event to outbox table within the same transaction as the domain change
@Transactional
fun createOrder(request: CreateOrderRequest): Order {
    val order = orderRepository.save(Order.from(request))
    outboxRepository.save(OutboxEvent(
        aggregateType = "Order",
        aggregateId = order.id,
        eventType = "OrderCreated",
        payload = objectMapper.writeValueAsString(order),
        status = "PENDING"
    ))
    return order
}

// 2. Scheduled job polls outbox and publishes to broker
@Scheduled(fixedDelay = 1000)
@Transactional
fun publishPendingEvents() {
    val events = outboxRepository.findByStatus("PENDING")
    events.forEach { event ->
        try {
            messageBroker.publish(event.eventType, event.payload)
            event.status = "PUBLISHED"
        } catch (e: Exception) {
            event.retryCount++
            if (event.retryCount > MAX_RETRIES) event.status = "FAILED"
        }
        outboxRepository.save(event)
    }
}
```

### Dead Letter Queue (DLQ) Handling

```kotlin
@Component
class DlqHandler {

    @KafkaListener(topics = ["orders.DLQ"])
    fun handleDeadLetter(record: ConsumerRecord<String, String>) {
        logger.error("DLQ message: topic=${record.topic()}, key=${record.key()}")

        // 1. Log for investigation
        dlqRepository.save(DlqEntry(
            originalTopic = record.headers().lastHeader("original-topic")?.value()?.let { String(it) },
            key = record.key(),
            payload = record.value(),
            errorReason = record.headers().lastHeader("error-reason")?.value()?.let { String(it) },
            receivedAt = Instant.now()
        ))

        // 2. Alert if DLQ depth exceeds threshold
        val depth = dlqRepository.countByOriginalTopicAndResolvedFalse(record.topic())
        if (depth > DLQ_ALERT_THRESHOLD) {
            alertService.notify("DLQ depth for ${record.topic()} is $depth — investigate")
        }
    }
}
```

## .NET Exception → Java Exception Mapping

Reference for teams migrating .NET exception handling to Java/Kotlin.

### Exception Hierarchy Mapping
| .NET Exception | Java/Kotlin Equivalent | Notes |
|---------------|----------------------|-------|
| `Exception` | `Exception` | Base class |
| `SystemException` | `RuntimeException` | Unchecked |
| `ApplicationException` | `RuntimeException` (custom) | Deprecated in .NET too |
| `ArgumentNullException` | `IllegalArgumentException` | Or Kotlin's `require()` |
| `ArgumentOutOfRangeException` | `IllegalArgumentException` | With range info in message |
| `InvalidOperationException` | `IllegalStateException` | Object in wrong state |
| `NotImplementedException` | `NotImplementedError` | Kotlin built-in |
| `NotSupportedException` | `UnsupportedOperationException` | |
| `NullReferenceException` | `NullPointerException` | Kotlin null safety prevents most |
| `KeyNotFoundException` | `NoSuchElementException` | |
| `HttpRequestException` | `RestClientException` (Spring) | HTTP client errors |
| `TaskCanceledException` | `CancellationException` (coroutines) | Async cancellation |
| `TimeoutException` | `java.util.concurrent.TimeoutException` | |
| `DbUpdateException` (EF) | `DataAccessException` (Spring) | Database errors |
| `DbUpdateConcurrencyException` | `OptimisticLockingFailureException` | |
| `ValidationException` | `ConstraintViolationException` (Jakarta) | Bean Validation |

### Pattern Migration
```csharp
// .NET pattern
try {
    await _orderService.CreateOrderAsync(request);
}
catch (DbUpdateException ex) when (ex.InnerException is PostgresException pgEx && pgEx.SqlState == "23505") {
    throw new ConflictException($"Order already exists: {request.OrderId}");
}
catch (HttpRequestException ex) when (ex.StatusCode == HttpStatusCode.ServiceUnavailable) {
    throw new ServiceUnavailableException("Payment gateway unavailable", ex);
}
```

```kotlin
// Kotlin equivalent
try {
    orderService.createOrder(request)
} catch (e: DataIntegrityViolationException) {
    // Spring wraps database constraint violations
    throw ConflictException("Order already exists: ${request.orderId}", e)
} catch (e: RestClientException) {
    throw ServiceUnavailableException("Payment gateway unavailable", e)
}
```

### Key Differences
- **.NET exception filters** (`catch when`): No direct Kotlin equivalent — use `when` expression inside catch block
- **.NET `AggregateException`**: Kotlin coroutines throw `CancellationException`; use `supervisorScope` for partial failure handling
- **.NET `ExceptionDispatchInfo.Capture()`**: Kotlin uses standard `throw` — stack trace preserved automatically
- **Global handler**: .NET `IExceptionFilter` → Spring `@ControllerAdvice` + `@ExceptionHandler`
