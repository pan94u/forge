---
name: logging-observability
description: >
  Logging and observability patterns. MDC propagation, log levels,
  Micrometer metrics, distributed tracing, structured JSON logging.
trigger: when configuring logging, adding metrics, tracing, or health checks
tags: [logging, observability, metrics, tracing, micrometer, mdc]
version: "2.0"
scope: platform
category: foundation
---

# Logging & Observability

## MDC Propagation

Every log entry MUST include: `traceId`, `correlationId`, `userId`, `requestId`.

```kotlin
@Component
class MdcFilter : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val correlationId = req.getHeader("X-Correlation-Id") ?: UUID.randomUUID().toString()
        MDC.put("correlationId", correlationId)
        MDC.put("requestId", UUID.randomUUID().toString())
        try { chain.doFilter(req, res) } finally { MDC.clear() }
    }
}
```

MDC does NOT propagate to child threads — use `MdcTaskDecorator` for `@Async` and `MDCContext()` for coroutines.

## Log Levels

| Level | Meaning | Example |
|-------|---------|---------|
| ERROR | System broken, pager-worthy | Database connection lost |
| WARN | Degraded but functional | Retry succeeded, circuit breaker open |
| INFO | Normal business events | Order created, payment processed |
| DEBUG | Dev diagnostics (never in prod) | SQL queries, payloads |

## Micrometer Metrics

- `snake_case` names with units: `order_processing_seconds`, `payment_total`
- Use Counter for events, Timer for durations, Gauge for current values
- Tag with: `service`, `method`, `status`
- Expose via `/actuator/prometheus`

## Distributed Tracing

Use Micrometer Tracing with OpenTelemetry exporter. Trace context propagates automatically via W3C `traceparent` header.

## Structured JSON Logging

Use LogstashEncoder in production for JSON output with MDC fields included.

## Anti-Patterns
- Never log sensitive data (passwords, tokens, credit cards)
- Never use `System.out.println()` — use SLF4J logger
- Never use string concatenation in log messages — use parameterized logging
