---
name: api-design
description: >
  REST API design patterns. URL naming, HTTP status codes, pagination,
  RFC 7807 error responses, versioning, and OpenAPI.
trigger: when designing or implementing REST APIs, controllers, or endpoints
tags: [api, rest, openapi, http, pagination]
---

# REST API Design

## URL Naming
- Lowercase, hyphens for multi-word: `/order-items`
- Plural nouns for collections: `/orders`, `/users`
- Nested resources: `/orders/{orderId}/items`
- No verbs in URLs (use HTTP methods): `POST /orders` not `POST /create-order`

## HTTP Methods & Status Codes

| Method | Success | Use |
|--------|---------|-----|
| GET | 200 OK | Retrieve resource(s) |
| POST | 201 Created + Location header | Create resource |
| PUT | 200 OK or 204 No Content | Full replace |
| PATCH | 200 OK | Partial update |
| DELETE | 204 No Content | Delete resource |

### Error Status Codes
| Code | When |
|------|------|
| 400 | Malformed request syntax |
| 401 | Missing/invalid authentication |
| 403 | Authenticated but not authorized |
| 404 | Resource not found |
| 409 | Conflict (duplicate, state transition) |
| 422 | Valid syntax but semantic validation failed |
| 429 | Rate limit exceeded |
| 500 | Unexpected server error |

## Pagination (Cursor-Based Preferred)

```json
GET /orders?cursor=eyJpZCI6MTAwfQ&limit=20

{
  "data": [...],
  "pagination": {
    "nextCursor": "eyJpZCI6MTIwfQ",
    "hasMore": true,
    "limit": 20
  }
}
```

## Error Response (RFC 7807)

```json
{
  "type": "https://forge.example.com/errors/order-not-found",
  "title": "ORDER_NOT_FOUND",
  "status": 404,
  "detail": "Order abc-123 not found",
  "instance": "/orders/abc-123",
  "correlationId": "req-xyz-789",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

## Versioning
- URL path for major versions: `/v1/orders`, `/v2/orders`
- Headers for minor/compatible changes
- OpenAPI spec is the source of truth — code must match spec

## API Contract Comparison (OpenAPI Diff)

When evolving APIs, compare old vs new OpenAPI specs to detect breaking changes.

### Breaking Changes (MUST NOT happen without major version bump)
| Change | Impact |
|--------|--------|
| Remove endpoint | Consumers get 404 |
| Remove required response field | Consumers missing expected data |
| Add required request field | Existing requests become invalid |
| Change field type (string → number) | Deserialization failures |
| Narrow enum values | Consumers sending removed values get errors |
| Change URL path | All consumers break |

### Non-Breaking Changes (safe to ship)
| Change | Impact |
|--------|--------|
| Add optional request field | Backward-compatible |
| Add response field | Consumers ignore unknown fields |
| Add new endpoint | No impact on existing consumers |
| Widen enum values | Consumers may ignore new values |
| Add optional query parameter | Existing requests still work |

### Comparison Workflow
```bash
# Use openapi-diff or oasdiff to compare specs
oasdiff breaking old-api.yaml new-api.yaml
oasdiff changelog old-api.yaml new-api.yaml --format markdown
```

In code review, verify:
1. All breaking changes map to a new major version URL
2. Deprecation header added before removal (`Sunset: <date>`)
3. Migration guide written for consumers

## Cross-Stack API Migration Mapping

When migrating systems between technology stacks (e.g., .NET → Java, Python → Kotlin), map source APIs to target APIs systematically.

### Migration Mapping Template

```markdown
| Source (.NET) | Target (Spring Boot) | Notes |
|---------------|---------------------|-------|
| `[HttpGet("api/orders/{id}")]` | `@GetMapping("/api/orders/{id}")` | Direct 1:1 |
| `[Authorize(Roles = "Admin")]` | `@PreAuthorize("hasRole('ADMIN')")` | Auth attribute mapping |
| `IActionResult` → `Ok(data)` | `ResponseEntity.ok(data)` | Return type mapping |
| `[FromQuery] string filter` | `@RequestParam filter: String` | Parameter binding |
| `[FromBody] CreateOrderDto` | `@RequestBody request: CreateOrderRequest` | Body binding |
| `[ProducesResponseType(404)]` | Swagger `@ApiResponse(responseCode = "404")` | Doc annotation |
```

### Migration Checklist
1. **Inventory**: List all source endpoints (method + path + auth + request/response types)
2. **Map**: Create 1:1 target endpoint definitions
3. **Verify contracts**: Ensure request/response JSON shapes are identical
4. **Test**: Run integration tests against both old and new endpoints
5. **Shadow traffic**: Route a percentage of live traffic to new endpoints, compare responses
6. **Cutover**: Switch DNS/load balancer, keep old endpoints alive with deprecation headers

### Common Framework Mappings
| Concept | ASP.NET Core | Spring Boot | FastAPI | Go (gin) |
|---------|-------------|-------------|---------|----------|
| Route definition | `[Route("api/[controller]")]` | `@RequestMapping("/api/orders")` | `@app.get("/api/orders")` | `r.GET("/api/orders", handler)` |
| Path parameter | `{id}` | `{id}` | `{id}` | `:id` |
| Query parameter | `[FromQuery]` | `@RequestParam` | `Query()` | `c.Query("key")` |
| Request body | `[FromBody]` | `@RequestBody` | Pydantic model param | `c.ShouldBindJSON(&req)` |
| Auth middleware | `[Authorize]` | `@PreAuthorize` | `Depends(get_current_user)` | `authMiddleware()` |
| Response type | `IActionResult` | `ResponseEntity<T>` | `Response model` | `c.JSON(200, data)` |
| Validation | `[Required]`, `FluentValidation` | `@Valid`, `@Validated` | Pydantic validators | `binding:"required"` |
| DI | `builder.Services.AddScoped<>()` | Constructor injection | `Depends()` | Manual / wire |
