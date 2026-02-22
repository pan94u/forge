---
name: detailed-design
description: "Skill for detailed design — class diagrams, sequence diagrams, API contracts, database schema design, and error handling strategy."
stage: design
type: delivery-skill
version: "3.0"
category: delivery
scope: platform
tags: [methodology, design, api, database, uml]
---

# Detailed Design Skill

## Purpose

Translates architecture-level decisions into implementation-ready detailed designs: class structures, interaction flows, API contracts, database schemas, and error handling.

---

## Class Diagram Design

### When to Create

- New domain model classes or significant refactoring
- Design patterns being introduced (Strategy, Observer, Factory, etc.)
- Complex service layer with multiple collaborating classes

### Design Principles

- **Single Responsibility**: each class has one reason to change
- **Dependency Inversion**: depend on abstractions, not concrete classes
- **Composition over Inheritance**: favor composition for code reuse
- **Interface Segregation**: prefer small, focused interfaces

### Guidelines

1. Show relationships clearly: association (-->), composition (*--), aggregation (o--), inheritance (--|>)
2. Include visibility modifiers: + public, - private, # protected
3. Show method signatures with parameter and return types
4. Focus on design intent — show important relationships, not every field

---

## Sequence Diagram Design

### When to Create

- Every API endpoint's primary flow (happy path + error paths)
- Complex multi-service interactions
- Asynchronous flows (event-driven, message queue)

### Guidelines

1. Show both happy path and error paths using `alt/else`
2. Label every message with method name and key parameters
3. Show return values on return arrows
4. One diagram per use case or API endpoint
5. Show database operations explicitly
6. Include async patterns where applicable

---

## API Contract Design

### Design Rules

1. **Resource naming**: plural nouns (`/orders`, `/users`), not verbs
2. **HTTP methods**: GET (read), POST (create), PUT (full update), PATCH (partial), DELETE
3. **Status codes**: use specific codes (201 Created, 404 Not Found, 422 Unprocessable)
4. **Pagination**: `page` + `size` params; return `totalElements` and `totalPages`
5. **Error format**: consistent ErrorResponse with code, message, correlationId, details
6. **Validation**: define constraints in schema (min, max, pattern, required)
7. **Versioning**: URL path versioning (`/api/v1/`)
8. **Examples**: include request/response examples for every endpoint

For full OpenAPI template, see `reference/openapi-template.md`.

---

## Database Schema Design

### Process

1. Identify entities from domain model
2. Map relationships: one-to-one, one-to-many, many-to-many
3. Define columns: types, constraints, defaults, indices
4. Design indices based on expected query patterns
5. Plan migrations with forward and rollback scripts

### Rules

1. Use UUIDs for primary keys (distributed-system friendly)
2. Always include `created_at` and `updated_at` timestamps
3. Use CHECK constraints for enums and ranges
4. Index based on WHERE clauses and JOIN conditions
5. Normalize to 3NF; denormalize for read-heavy patterns
6. Version migrations sequentially (V001, V002) with descriptive names
7. Every migration must have a corresponding rollback script

---

## Error Handling Design

### Exception Hierarchy Pattern

```
ApplicationException (abstract base)
├── BusinessException (HTTP 422) — domain rule violations
├── ValidationException (HTTP 400) — input validation errors
├── AuthenticationException (HTTP 401)
├── AuthorizationException (HTTP 403)
└── InfrastructureException (HTTP 503) — external service failures
```

### Rules

1. Map exceptions to HTTP status codes consistently
2. Never expose internal details in error responses
3. Use machine-readable error codes (e.g., `ORDER_NOT_FOUND`)
4. Log full exception server-side with correlation ID
5. Return correlation ID in error response for debugging
6. Validate at the boundary: validate input in controllers before passing to services
