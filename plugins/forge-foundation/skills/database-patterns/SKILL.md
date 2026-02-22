---
name: database-patterns
description: >
  Database patterns for JPA/Spring Data. Schema naming, Flyway migrations,
  N+1 prevention, transaction boundaries, indexing strategy.
trigger: when working with JPA, SQL, Flyway, database schemas, or repositories
tags: [database, jpa, flyway, sql, schema, transactions]
version: "2.0"
scope: platform
category: foundation
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

## Multi-DataSource Configuration

When a service needs to access multiple databases (e.g., primary + legacy, read replica).

```kotlin
@Configuration
class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    fun primaryDataSource(): DataSource = DataSourceBuilder.create().build()

    @Bean
    @ConfigurationProperties("spring.datasource.legacy")
    fun legacyDataSource(): DataSource = DataSourceBuilder.create().build()
}

@Configuration
@EnableJpaRepositories(
    basePackages = ["com.forge.repository.primary"],
    entityManagerFactoryRef = "primaryEntityManagerFactory",
    transactionManagerRef = "primaryTransactionManager"
)
class PrimaryJpaConfig {
    @Bean @Primary
    fun primaryEntityManagerFactory(
        @Qualifier("primaryDataSource") dataSource: DataSource,
        builder: EntityManagerFactoryBuilder
    ): LocalContainerEntityManagerFactoryBean =
        builder.dataSource(dataSource)
            .packages("com.forge.entity.primary")
            .persistenceUnit("primary")
            .build()

    @Bean @Primary
    fun primaryTransactionManager(
        @Qualifier("primaryEntityManagerFactory") emf: EntityManagerFactory
    ): PlatformTransactionManager = JpaTransactionManager(emf)
}

@Configuration
@EnableJpaRepositories(
    basePackages = ["com.forge.repository.legacy"],
    entityManagerFactoryRef = "legacyEntityManagerFactory",
    transactionManagerRef = "legacyTransactionManager"
)
class LegacyJpaConfig {
    @Bean
    fun legacyEntityManagerFactory(
        @Qualifier("legacyDataSource") dataSource: DataSource,
        builder: EntityManagerFactoryBuilder
    ): LocalContainerEntityManagerFactoryBean =
        builder.dataSource(dataSource)
            .packages("com.forge.entity.legacy")
            .persistenceUnit("legacy")
            .build()

    @Bean
    fun legacyTransactionManager(
        @Qualifier("legacyEntityManagerFactory") emf: EntityManagerFactory
    ): PlatformTransactionManager = JpaTransactionManager(emf)
}
```

```yaml
spring:
  datasource:
    primary:
      url: jdbc:postgresql://primary-db:5432/forge
      username: ${PRIMARY_DB_USER}
      password: ${PRIMARY_DB_PASSWORD}
    legacy:
      url: jdbc:sqlserver://legacy-db:1433;databaseName=OldSystem
      username: ${LEGACY_DB_USER}
      password: ${LEGACY_DB_PASSWORD}
```

**Rules**:
- Always mark one DataSource as `@Primary`
- Use separate packages for entities and repositories per data source
- Each data source gets its own `EntityManagerFactory` and `TransactionManager`
- Cross-database transactions require distributed transaction manager (JTA) — avoid if possible
- Flyway: configure separate `Flyway` beans per data source with distinct migration locations

## Entity Framework → JPA Migration Mapping

Reference for teams migrating .NET Entity Framework code to Spring Data JPA.

### Core Concept Mapping
| Entity Framework (C#) | Spring Data JPA (Kotlin) |
|----------------------|-------------------------|
| `DbContext` | `@Configuration` + `EntityManagerFactory` |
| `DbSet<Order>` | `JpaRepository<Order, String>` |
| `modelBuilder.Entity<Order>()` | `@Entity` + annotations or `@EntityListeners` |
| `HasKey(o => o.Id)` | `@Id` |
| `HasOne(o => o.Customer).WithMany(c => c.Orders)` | `@ManyToOne` / `@OneToMany(mappedBy = ...)` |
| `HasMany(o => o.Items).WithOne(i => i.Order)` | `@OneToMany(mappedBy = ...)` / `@ManyToOne` |
| `Property(o => o.Name).IsRequired().HasMaxLength(100)` | `@Column(nullable = false, length = 100)` |
| `HasIndex(o => o.Email).IsUnique()` | `@Table(indexes = [@Index(columnList = "email", unique = true)])` |
| `ToTable("orders")` | `@Table(name = "orders")` |
| `HasDefaultValue(0)` | `@ColumnDefault("0")` or Flyway migration |
| `IQueryable<T>` LINQ queries | `@Query("SELECT ...")` JPQL or Criteria API |
| `AsNoTracking()` | `@Transactional(readOnly = true)` |

### EF Migration → Flyway Migration
| EF Migration | Flyway Equivalent |
|-------------|-------------------|
| `Add-Migration CreateOrders` | `V1__create_orders.sql` (hand-written SQL) |
| `Update-Database` | `./gradlew flywayMigrate` or auto on startup |
| `modelBuilder.HasData(seedData)` | `V999__seed_data.sql` or `afterMigrate.sql` |
| `Down()` method | Flyway `U1__undo_create_orders.sql` (Teams edition) |
| Auto-generated migration | Always hand-written — Flyway does not auto-generate |

### Common Pitfalls
- **Lazy loading**: EF lazy-loads by default; JPA also does, but requires open session — use `@EntityGraph` or `JOIN FETCH` to avoid N+1
- **Change tracking**: EF tracks entity changes automatically; JPA does too within a transaction, but `detach()` / read-only transactions disable it
- **Value objects**: EF `OwnsOne()` maps to JPA `@Embedded` / `@Embeddable`
- **Enum mapping**: EF stores enums as integers by default; JPA `@Enumerated(STRING)` stores as string (recommended)
- **Soft delete**: EF global query filter `HasQueryFilter(e => !e.IsDeleted)` → JPA `@Where(clause = "is_deleted = false")` (Hibernate) or `@SQLRestriction`
