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
