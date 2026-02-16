---
name: error-handling
description: >
  Error handling patterns. Exception hierarchies, Result types,
  structured error responses, retry strategies, circuit breakers.
trigger: when implementing error handling, try/catch, or exception classes
tags: [error-handling, exceptions, resilience, retry, circuit-breaker]
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
