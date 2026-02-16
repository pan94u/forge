# Backend API Team

## Team Overview

We build and maintain RESTful microservices that power the core business logic. Our services handle order management, inventory, payments, notifications, and user management.

## Tech Stack

- **Language**: Kotlin 1.9+ on JDK 21
- **Framework**: Spring Boot 3.3+ with Spring WebFlux for reactive endpoints
- **Persistence**: Spring Data JPA with PostgreSQL; Redis for caching
- **Messaging**: Apache Kafka for event-driven communication
- **Build**: Gradle Kotlin DSL
- **CI/CD**: GitHub Actions with deployment to Kubernetes

## Build & Run

```bash
# Build all services
./gradlew build

# Run a specific service
./gradlew :services:order-service:bootRun

# Run tests
./gradlew test

# Run integration tests (requires Docker)
./gradlew integrationTest

# Generate API documentation
./gradlew :services:order-service:generateOpenApiDocs
```

## Coding Conventions

### API Design
- Follow RESTful conventions: proper HTTP verbs, status codes, resource naming
- API versioning via URL path: `/api/v1/`, `/api/v2/`
- Use pagination for all list endpoints (page, size, sort parameters)
- Return standard error response format: `{ "error": { "code": "...", "message": "...", "details": [...] } }`
- Include `X-Request-Id` header in all responses for tracing

### Code Structure
- **Controller**: Thin layer, delegates to service. Uses `@RestController`, `ResponseEntity<T>`
- **Service**: Business logic. Interface + implementation pattern. Uses `@Service`
- **Repository**: Data access. Spring Data JPA with custom `@Query` for complex queries
- **DTO**: Separate request/response DTOs. Never expose domain entities via API
- **Mapper**: Extension functions for DTO-to-entity mapping (no MapStruct)
- **Exception**: Custom exceptions extending `BaseException`. Global handler via `@ControllerAdvice`

### Naming
- Packages: `com.company.{service}.{layer}` (e.g., `com.company.order.controller`)
- Classes: PascalCase. Controllers end with `Controller`, services with `Service`
- Methods: camelCase. CRUD methods: `create*`, `get*`, `update*`, `delete*`, `list*`
- Database tables: snake_case, plural (e.g., `order_items`)
- API paths: kebab-case, plural (e.g., `/api/v1/order-items`)

### Testing
- Unit tests: JUnit 5 + MockK. One test class per service class
- Integration tests: `@SpringBootTest` with Testcontainers for PostgreSQL and Kafka
- Minimum 80% line coverage for service layer
- Test naming: `should {expected behavior} when {condition}`

## Security Rules

- NEVER hardcode credentials. Use environment variables or Spring Cloud Config
- All endpoints require authentication except health checks
- Use `@PreAuthorize` for method-level authorization
- Validate all input with Bean Validation (`@Valid`, `@NotBlank`, etc.)
- SQL injection prevention: always use parameterized queries
- Rate limiting on all public-facing endpoints

## Active Forge Plugins

- forge-foundation
- forge-superagent
- forge-knowledge
- forge-deployment
