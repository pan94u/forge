# Team-Level CLAUDE.md Template

> Copy this template into your team's shared directory or repository root and
> customize it for your team. This file layers on top of the organization-level
> CLAUDE.md and adds team-specific context.

---

```markdown
# CLAUDE.md — Team Context

## Team Information

- **Team name**: [e.g., Payments Team]
- **Domain**: [e.g., Payment processing, billing, invoicing]
- **Slack channel**: [e.g., #team-payments]
- **Team lead**: [e.g., @jane-doe]
- **On-call rotation**: [e.g., PagerDuty schedule "payments-oncall"]

## Domain Context

[Provide 2-3 paragraphs describing what this team owns and is responsible for.
This helps Claude understand the business domain when generating code, writing
tests, or making architectural decisions.]

Example:
> The Payments team owns all services related to processing financial
> transactions, managing payment methods, and handling billing cycles. We
> integrate with external payment processors (Stripe, Adyen) and provide
> internal APIs consumed by the Checkout, Subscriptions, and Invoicing teams.
>
> Our domain has strict regulatory requirements (PCI-DSS compliance) that affect
> how we handle card data, log information, and structure our services. All
> payment data must be tokenized before storage, and raw card numbers must never
> appear in logs or error messages.

## Module Structure

Our team owns the following modules in the monorepo:

| Module | Purpose | Key Contacts |
|---|---|---|
| `services/payment-gateway` | Core payment processing service | @alice, @bob |
| `services/billing-engine` | Recurring billing and invoice generation | @carol |
| `services/payment-methods` | Payment method CRUD and tokenization | @alice |
| `libs/payment-common` | Shared DTOs, exceptions, and utilities | @bob |

## Key Dependencies

### Upstream (we depend on)
- **Identity Service** (`services/identity`) — User authentication and profile
  data. Contact: @identity-team.
- **Product Catalog** (`services/catalog`) — Product and pricing information.
  Contact: @catalog-team.

### Downstream (depends on us)
- **Checkout Service** (`services/checkout`) — Calls our payment processing API.
  Contact: @checkout-team.
- **Subscriptions Service** (`services/subscriptions`) — Uses our billing engine.
  Contact: @subscriptions-team.
- **Reporting Service** (`services/reporting`) — Reads from our event stream.
  Contact: @data-team.

### External Dependencies
- **Stripe API** (v2023-10-16) — Primary payment processor.
- **Adyen API** (v71) — Secondary payment processor for EU transactions.
- **Payment tokenization vault** — Internal service for PCI-compliant token
  storage.

## Domain-Specific Conventions

### API Design
- All payment amounts use `BigDecimal` with explicit currency codes (ISO 4217).
- Never use `Double` or `Float` for monetary values.
- All payment API responses include an idempotency key for retry safety.
- Error responses follow RFC 7807 (Problem Details for HTTP APIs).

### Database
- Payment transaction tables use UUID primary keys (not auto-increment).
- All tables include `created_at` and `updated_at` timestamp columns.
- Soft delete only — use `deleted_at` column, never hard delete payment records.
- Database migrations use Flyway with the naming convention
  `V{YYYYMMDD}_{HHmmss}__{description}.sql`.

### Event Streaming
- Domain events are published to Kafka topic `payments.events.v1`.
- Event schema is defined in Avro and registered in the schema registry.
- All events include: `event_id`, `event_type`, `aggregate_id`, `timestamp`,
  `payload`.

### Security
- PCI-DSS Level 1 compliance is mandatory for all payment services.
- Raw card numbers (PAN) must NEVER appear in logs, error messages, or database
  fields. Always use tokens.
- All external API calls to payment processors must use mTLS.
- Payment amounts above $10,000 trigger additional fraud check integration.

### Testing
- Payment processing tests must use Stripe/Adyen test mode credentials (never
  live keys).
- Integration tests use WireMock to stub external payment processor APIs.
- All payment flows require both success and failure path test coverage.
- Use the `PaymentTestFixtures` class for creating test payment objects.

## Common Gotchas

1. **Currency precision** — JPY and other zero-decimal currencies do not use
   decimal points. The `MoneyUtils.toMinorUnits()` helper handles this — always
   use it instead of manual conversion.

2. **Idempotency** — All payment mutation endpoints must be idempotent. Use the
   `@Idempotent` annotation on controller methods and store idempotency keys in
   the `idempotency_keys` table.

3. **Stripe webhook signatures** — Webhook handlers MUST validate the Stripe
   signature header before processing. Use `StripeWebhookValidator`, not manual
   validation.

4. **Database connection pool** — The payment-gateway service uses a separate
   connection pool for read replicas. Ensure read-only queries use the
   `@ReadOnlyTransaction` annotation to route to replicas.

5. **Timezone handling** — All timestamps are stored in UTC. Never use local
   timezone conversions in service code. Timezone conversion is a presentation
   concern handled by the frontend.

6. **Migration ordering** — When multiple team members create Flyway migrations
   simultaneously, coordinate on Slack to avoid version conflicts. The timestamp-
   based naming helps, but merges can still cause issues.

7. **Circuit breaker configuration** — External payment processor calls use
   Resilience4j circuit breakers. Default config is in
   `payment-common/src/main/resources/resilience4j.yml`. Override per-service if
   needed, but document why.
```

---

## Usage Notes

- Place this file in your team's top-level directory in the monorepo (e.g.,
  `services/payments/CLAUDE.md`) or in a shared team configuration location.
- Update this file when team ownership changes, new modules are added, or domain
  conventions evolve.
- Review quarterly in alignment with the organization-level CLAUDE.md review
  cycle.
