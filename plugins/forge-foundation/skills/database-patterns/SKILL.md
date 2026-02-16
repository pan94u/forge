---
name: database-patterns
description: >
  Database patterns for JPA/Spring Data. Schema naming, Flyway migrations,
  N+1 prevention, transaction boundaries, indexing strategy.
trigger: when working with JPA, SQL, Flyway, database schemas, or repositories
tags: [database, jpa, flyway, sql, schema, transactions]
---

# Database Patterns

## Schema Naming
- `snake_case` for all identifiers
- Singular table names: `order`, `order_item` (not `orders`)
- Foreign keys: `{referenced_table}_id` (e.g., `order_id`)
- Indexes: `idx_{table}_{columns}` (e.g., `idx_order_customer_id`)
- Audit columns on every table: `created_at`, `updated_at`, `created_by`, `updated_by`

## Flyway Migrations

Format: `V{version}__{description}.sql`
```
V1__create_order_table.sql
V2__add_order_status_column.sql
V3__create_payment_table.sql
```

Rules:
- NEVER modify an existing migration after it's been applied
- ALWAYS add new migrations for schema changes
- Include rollback comments for complex migrations
- Test migrations against a copy of production schema

## N+1 Prevention

```kotlin
// BAD — N+1 problem
@Entity class Order(
    @OneToMany(mappedBy = "order")
    val items: List<OrderItem> = emptyList()
)
// Accessing order.items triggers N additional queries

// GOOD — EntityGraph
@EntityGraph(attributePaths = ["items"])
fun findByCustomerId(customerId: String): List<Order>

// GOOD — JOIN FETCH
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.customerId = :customerId")
fun findWithItemsByCustomerId(customerId: String): List<Order>

// GOOD — batch size
@BatchSize(size = 25)
@OneToMany(mappedBy = "order")
val items: List<OrderItem> = emptyList()
```

## Transaction Boundaries
- `@Transactional` on **service layer** only, NEVER on controllers or repositories
- Use `@Transactional(readOnly = true)` for read operations
- Keep transactions as short as possible — no HTTP calls inside transactions
- Propagation.REQUIRES_NEW for audit logging that must survive rollback
