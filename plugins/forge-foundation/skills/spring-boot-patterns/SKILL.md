---
name: spring-boot-patterns
description: >
  Spring Boot 3 patterns for enterprise applications. Covers layered architecture,
  constructor injection, configuration management, error handling with RFC 7807.
trigger: when working with Spring annotations, controllers, services, or configuration
tags: [spring-boot, spring, dependency-injection, configuration]
---

# Spring Boot Patterns

## Layered Architecture

```
Controller (HTTP) → Service (Business Logic) → Repository (Data Access)
     ↓                    ↓                          ↓
  Request/Response     Domain Model              Entity/JPA
  Validation           Transactions              Queries
  Error mapping        Business rules            Persistence
```

**Rules**:
- Controllers NEVER access repositories directly
- Services NEVER import HTTP-related classes (HttpServletRequest, ResponseEntity)
- Repositories NEVER contain business logic

## Constructor Injection

```kotlin
// GOOD — constructor injection (testable, immutable)
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val paymentService: PaymentService,
    private val eventPublisher: ApplicationEventPublisher
) { ... }

// BAD — field injection
@Service
class OrderService {
    @Autowired private lateinit var orderRepository: OrderRepository  // NEVER
}
```

## Configuration Management

```kotlin
// GOOD — @ConfigurationProperties (type-safe, validated)
@ConfigurationProperties(prefix = "forge.payment")
@Validated
data class PaymentConfig(
    @field:NotBlank val gatewayUrl: String,
    @field:Min(1) val timeoutSeconds: Int = 30,
    @field:Min(1) val maxRetries: Int = 3,
    val apiKey: String = ""  // injected via env var
)

// BAD — @Value scattered across classes
@Value("\${forge.payment.gateway-url}") private lateinit var gatewayUrl: String  // AVOID
```

## Error Handling with @ControllerAdvice + RFC 7807

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(ex.httpStatus, ex.message ?: "")
        problem.title = ex.errorCode
        problem.setProperty("correlationId", MDC.get("correlationId"))
        problem.setProperty("timestamp", Instant.now().toString())
        return problem
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY)
        problem.title = "VALIDATION_FAILED"
        problem.setProperty("errors", ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to it.defaultMessage)
        })
        return problem
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        logger.error("Unexpected error", ex)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"
        )
    }
}
```

## Profile-Based Configuration

```yaml
# application.yml — shared defaults
spring:
  application:
    name: order-service

# application-local.yml — local development
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/orders

# application-staging.yml
# application-production.yml
```

Activate with: `SPRING_PROFILES_ACTIVE=production`

## Health Indicators

```kotlin
@Component
class PaymentGatewayHealthIndicator(
    private val paymentConfig: PaymentConfig
) : HealthIndicator {
    override fun health(): Health = try {
        // check connectivity
        Health.up().withDetail("gateway", paymentConfig.gatewayUrl).build()
    } catch (e: Exception) {
        Health.down(e).build()
    }
}
```
