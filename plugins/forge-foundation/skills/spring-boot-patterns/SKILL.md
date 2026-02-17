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

## Async Processing (@Async + CompletableFuture)

```kotlin
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    override fun getAsyncExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 5
            maxPoolSize = 20
            queueCapacity = 100
            setThreadNamePrefix("forge-async-")
            setRejectedExecutionHandler(CallerRunsPolicy())
            initialize()
        }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler =
        AsyncUncaughtExceptionHandler { ex, method, params ->
            logger.error("Async error in ${method.name}: ${ex.message}", ex)
        }
}
```

```kotlin
@Service
class NotificationService(private val emailClient: EmailClient) {

    // Fire-and-forget — caller does not wait
    @Async
    fun sendOrderConfirmation(orderId: String) {
        emailClient.send(orderId, "order_confirmation")
    }

    // Caller can await the result
    @Async
    fun generateReport(criteria: ReportCriteria): CompletableFuture<Report> {
        val data = fetchReportData(criteria)
        return CompletableFuture.completedFuture(Report(data))
    }
}

// Composing async results in a service
@Service
class DashboardService(
    private val orderService: OrderService,
    private val analyticsService: AnalyticsService
) {
    fun buildDashboard(userId: String): Dashboard {
        val ordersFuture = orderService.getRecentOrdersAsync(userId)
        val metricsFuture = analyticsService.getUserMetricsAsync(userId)
        // Wait for both — total time = max(orders, metrics)
        return Dashboard(
            orders = ordersFuture.get(5, TimeUnit.SECONDS),
            metrics = metricsFuture.get(5, TimeUnit.SECONDS)
        )
    }
}
```

**Rules**:
- `@Async` methods MUST return `void` or `CompletableFuture<T>` — never return a raw value
- `@Async` MUST be called from a different bean (Spring proxy limitation)
- Always configure a custom executor — never rely on `SimpleAsyncTaskExecutor`
- Set timeouts on `CompletableFuture.get()` to prevent indefinite blocking

## Caching (@Cacheable)

```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager =
        CaffeineCacheManager().apply {
            setCacheNames(listOf("tools", "profiles", "systemConfig"))
            setCaffeine(
                Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .recordStats()
            )
        }
}
```

```kotlin
@Service
class ProfileService(private val profileRepository: ProfileRepository) {

    @Cacheable("profiles", key = "#profileId")
    fun getProfile(profileId: String): Profile =
        profileRepository.findById(profileId).orElseThrow()

    @CachePut("profiles", key = "#profile.id")
    fun updateProfile(profile: Profile): Profile =
        profileRepository.save(profile)

    @CacheEvict("profiles", key = "#profileId")
    fun deleteProfile(profileId: String) =
        profileRepository.deleteById(profileId)

    @CacheEvict("profiles", allEntries = true)
    @Scheduled(fixedRate = 3600000) // hourly
    fun evictAllProfiles() { /* scheduled cache refresh */ }
}
```

**Rules**:
- Cache ONLY read-heavy, write-infrequent data
- Always set TTL and max size — unbounded caches cause OOM
- Use `@CachePut` on writes to keep cache consistent
- `@CacheEvict(allEntries=true)` for bulk invalidation
- Never cache mutable objects — return copies or immutable types

## Event Publishing (ApplicationEvent)

```kotlin
// 1. Define domain events
data class OrderCreatedEvent(val orderId: String, val userId: String, val total: BigDecimal)
data class OrderShippedEvent(val orderId: String, val trackingNumber: String)

// 2. Publish from the service that owns the domain action
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        val order = orderRepository.save(Order.from(request))
        // Event is published within the transaction boundary
        eventPublisher.publishEvent(OrderCreatedEvent(order.id, order.userId, order.total))
        return order
    }
}

// 3. Listeners react — loose coupling between bounded contexts
@Component
class InventoryEventListener(private val inventoryService: InventoryService) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderCreated(event: OrderCreatedEvent) {
        inventoryService.reserveStock(event.orderId)
    }
}

@Component
class NotificationEventListener(private val notificationService: NotificationService) {

    @Async
    @EventListener
    fun onOrderShipped(event: OrderShippedEvent) {
        notificationService.sendShippingNotification(event.orderId, event.trackingNumber)
    }
}
```

**Rules**:
- Use `@TransactionalEventListener(AFTER_COMMIT)` for side effects that depend on commit
- Use `@EventListener` for non-transactional reactions
- Combine `@Async` + `@EventListener` for fire-and-forget side effects
- Events are in-process only — for cross-service, use message broker (Kafka, RabbitMQ)
- Keep events immutable (`data class`) with only IDs and essential data

## Advanced @ConfigurationProperties

```kotlin
@ConfigurationProperties(prefix = "forge.resilience")
@Validated
data class ResilienceConfig(
    val circuitBreaker: CircuitBreakerSettings = CircuitBreakerSettings(),
    val retry: RetrySettings = RetrySettings(),
    val timeout: TimeoutSettings = TimeoutSettings()
) {
    data class CircuitBreakerSettings(
        @field:DecimalMin("0.0") @field:DecimalMax("1.0")
        val failureRateThreshold: Double = 0.5,
        val waitDurationInOpenState: Duration = Duration.ofSeconds(30),
        @field:Min(5)
        val slidingWindowSize: Int = 10
    )

    data class RetrySettings(
        @field:Min(1) @field:Max(10)
        val maxAttempts: Int = 3,
        val waitDuration: Duration = Duration.ofMillis(500),
        val retryableExceptions: List<String> = listOf(
            "java.net.SocketTimeoutException",
            "java.io.IOException"
        )
    )

    data class TimeoutSettings(
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val readTimeout: Duration = Duration.ofSeconds(30),
        val writeTimeout: Duration = Duration.ofSeconds(10)
    )
}
```

**Rules**:
- Use nested `data class` for hierarchical config — mirrors YAML structure naturally
- Always provide sensible defaults — app should start without external config
- Use Bean Validation annotations for constraint enforcement at startup
- Use `Duration` type for time-based config (Spring auto-parses `5s`, `30m`, `1h`)
- Register with `@EnableConfigurationProperties(ResilienceConfig::class)` or `@ConfigurationPropertiesScan`
