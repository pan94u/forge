# Project-Level CLAUDE.md Template

> Copy this template to the root of your project directory and customize it.
> This is the most specific layer in the CLAUDE.md hierarchy and provides
> project-level context that Claude Code uses during every session.

---

```markdown
# CLAUDE.md — Project: [Project Name]

## Overview

[1-2 sentences describing what this project/service does and its role in the
system.]

Example:
> The Payment Gateway service processes financial transactions by routing them
> to the appropriate payment processor (Stripe or Adyen) based on transaction
> type and region. It exposes REST APIs consumed by the Checkout and
> Subscriptions services.

## Quick Start

```bash
# Start local dependencies (PostgreSQL, Kafka, WireMock)
docker compose up -d

# Build the project
./gradlew build

# Run the application locally
./gradlew bootRun

# Run tests
./gradlew test

# Run only integration tests
./gradlew integrationTest

# Check code style
./gradlew ktlintCheck
```

The service starts on http://localhost:8080. API docs are at
http://localhost:8080/swagger-ui.html.

## Key Architectural Decisions

| Decision | Rationale |
|---|---|
| Kotlin + Spring Boot 3 | Organization standard; team expertise. |
| WebFlux (reactive) | High throughput required for payment processing. |
| PostgreSQL | ACID compliance for financial transactions. |
| Kafka | Async event publishing for downstream consumers. |
| Resilience4j | Circuit breaking for external payment processor calls. |
| Flyway | Database migration management. |

## Module Map

```
src/main/kotlin/com/example/paymentgateway/
├── PaymentGatewayApplication.kt    # Spring Boot entry point
├── config/
│   ├── SecurityConfig.kt           # OAuth2 + CORS configuration
│   ├── WebFluxConfig.kt            # WebFlux customization
│   ├── KafkaConfig.kt              # Kafka producer configuration
│   └── ResilienceConfig.kt         # Circuit breaker settings
├── controller/
│   ├── PaymentController.kt        # POST /api/v1/payments
│   ├── RefundController.kt         # POST /api/v1/refunds
│   └── WebhookController.kt        # POST /api/webhooks/stripe
├── service/
│   ├── PaymentService.kt           # Core payment orchestration
│   ├── RefundService.kt            # Refund processing logic
│   ├── RoutingService.kt           # Payment processor selection
│   └── FraudCheckService.kt        # Fraud detection integration
├── repository/
│   ├── PaymentRepository.kt        # Payment transaction CRUD
│   ├── RefundRepository.kt         # Refund record CRUD
│   └── IdempotencyRepository.kt    # Idempotency key storage
├── domain/
│   ├── Payment.kt                  # Payment entity
│   ├── Refund.kt                   # Refund entity
│   ├── PaymentStatus.kt            # Status enum
│   └── PaymentMethod.kt            # Payment method value object
├── dto/
│   ├── CreatePaymentRequest.kt     # API request DTO
│   ├── PaymentResponse.kt          # API response DTO
│   └── WebhookPayload.kt           # Stripe webhook DTO
├── adapter/
│   ├── StripeAdapter.kt            # Stripe API integration
│   ├── AdyenAdapter.kt             # Adyen API integration
│   └── FraudServiceAdapter.kt      # Internal fraud service client
├── event/
│   ├── PaymentEvent.kt             # Domain event definitions
│   └── PaymentEventPublisher.kt    # Kafka event publisher
└── exception/
    ├── PaymentException.kt         # Domain exceptions
    └── GlobalExceptionHandler.kt   # @ControllerAdvice error handler
```

## Important Files

| File | Why It Matters |
|---|---|
| `src/main/resources/application.yml` | All configuration — DB, Kafka, external APIs, feature flags. |
| `src/main/resources/db/migration/` | Flyway migrations — review carefully before modifying. |
| `build.gradle.kts` | Dependencies and build configuration. |
| `docker-compose.yml` | Local development dependencies. |
| `src/main/resources/resilience4j.yml` | Circuit breaker and retry configuration. |
| `src/test/resources/wiremock/` | WireMock stubs for external API testing. |

## Testing Instructions

### Unit Tests

```bash
./gradlew test
```

Unit tests cover service and repository logic. They use:
- **MockK** for mocking dependencies.
- **AssertJ** for assertions.
- **JUnit 5** as the test framework.

### Integration Tests

```bash
./gradlew integrationTest
```

Integration tests cover:
- Controller endpoints using `@WebFluxTest` or `@SpringBootTest`.
- Database operations using Testcontainers (PostgreSQL).
- Kafka publishing using embedded Kafka.
- External API calls using WireMock stubs.

### Test Data

- Use `PaymentTestFixtures` (in `src/test/kotlin/.../fixtures/`) for creating
  test payment objects.
- Use `TestDataBuilder` for constructing complex test scenarios.
- Stripe test card numbers: `4242424242424242` (success), `4000000000000002`
  (decline).

### Coverage

Run coverage report:
```bash
./gradlew jacocoTestReport
```

Report is generated at `build/reports/jacoco/test/html/index.html`. Minimum
threshold is 80% line coverage.

## Deployment Notes

### Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/payments` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | (none — required) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `STRIPE_API_KEY` | Stripe API secret key | (none — required) |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret | (none — required) |
| `ADYEN_API_KEY` | Adyen API key | (none — required) |
| `FRAUD_SERVICE_URL` | Internal fraud check service URL | `http://fraud-service:8080` |
| `OTEL_EXPORTER_ENDPOINT` | OpenTelemetry collector endpoint | `http://localhost:4317` |

### Deployment Pipeline

1. PR merged to `main` triggers CI (build + test + lint + security scan).
2. On CI success, a Docker image is built and pushed to the container registry.
3. ArgoCD detects the new image and deploys to staging.
4. After staging validation (automated smoke tests), promote to production via
   ArgoCD sync.

### Health Endpoints

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- Full health: `GET /actuator/health` (includes DB, Kafka, external service
  checks)

### Rollback

If a deployment causes issues:
1. Revert the ArgoCD sync to the previous image tag.
2. Verify health endpoints return 200.
3. Investigate the root cause using the observability stack (Grafana dashboards,
   Kibana logs).
```

---

## Usage Notes

- Place this file at the root of your project directory (e.g.,
  `services/payment-gateway/CLAUDE.md`).
- Keep this file updated as the project evolves — stale context degrades AI
  output quality.
- Review and update whenever significant architectural changes are made.
- This file is the most frequently consulted by Claude Code; prioritize accuracy
  and clarity.
