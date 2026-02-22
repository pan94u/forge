# OpenAPI 3.0 Template Reference

Full OpenAPI template for API contract design. Use as a starting point.

```yaml
openapi: 3.0.3
info:
  title: [Service Name] API
  version: 1.0.0
  description: [Service description]

servers:
  - url: /api/v1
    description: API v1

paths:
  /orders:
    post:
      operationId: createOrder
      summary: Create a new order
      tags: [Orders]
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateOrderRequest'
      responses:
        '201':
          description: Order created successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OrderResponse'
        '400':
          description: Invalid request
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '401':
          description: Authentication required
        '422':
          description: Business rule violation

components:
  schemas:
    ErrorResponse:
      type: object
      required: [code, message]
      properties:
        code:
          type: string
          description: Machine-readable error code
        message:
          type: string
          description: Human-readable error message
        correlationId:
          type: string
        details:
          type: array
          items:
            type: object
            properties:
              field:
                type: string
              message:
                type: string

    Money:
      type: object
      properties:
        amount:
          type: string
          format: decimal
        currency:
          type: string

  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```
