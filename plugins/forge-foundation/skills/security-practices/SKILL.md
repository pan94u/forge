---
name: security-practices
description: >
  Security practices for enterprise applications. Spring Security,
  parameterized queries, input validation, secret management, OWASP Top 10.
trigger: when working with authentication, authorization, input validation, or security configuration
tags: [security, spring-security, owasp, authentication, authorization]
version: "2.0"
scope: platform
category: foundation
---

# Security Practices

## Spring Security Configuration

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }  // disable for API-only services
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health/**").permitAll()
            it.requestMatchers("/api/v1/**").authenticated()
            it.anyRequest().denyAll()
        }
        .oauth2ResourceServer { it.jwt { } }
        .build()
}
```

## Parameterized Queries — ALWAYS

```kotlin
// GOOD — parameterized
@Query("SELECT o FROM Order o WHERE o.customerId = :customerId")
fun findByCustomerId(@Param("customerId") customerId: String): List<Order>

// NEVER — string concatenation (SQL injection!)
"SELECT * FROM orders WHERE customer_id = '$customerId'"  // FATAL SECURITY BUG
```

## Input Validation

```kotlin
data class CreateOrderRequest(
    @field:NotBlank val customerId: String,
    @field:NotEmpty val items: List<@Valid OrderItemRequest>,
    @field:Size(max = 500) val notes: String? = null
)

@PostMapping("/orders")
fun createOrder(@Valid @RequestBody request: CreateOrderRequest): ResponseEntity<Order> { ... }
```

## Secret Management
- NEVER hardcode credentials in source code
- Use environment variables: `${DB_PASSWORD}`, `${API_KEY}`
- Use Spring `@ConfigurationProperties` to bind env vars
- Rotate secrets regularly
- `.gitignore` all `.env`, `*.key`, `*.pem`, `credentials.json` files

## OWASP Top 10 Checklist
1. Injection → Parameterized queries only
2. Broken Auth → OAuth2/JWT, no custom auth
3. Sensitive Data → Encrypt at rest/transit, never log secrets
4. XXE → Disable external entity processing
5. Broken Access Control → Role-based + resource-level checks
6. Misconfiguration → Security headers, disable unused endpoints
7. XSS → Content-Type headers, input sanitization
8. Insecure Deserialization → Whitelist allowed types
9. Known Vulnerabilities → Regular dependency scanning
10. Insufficient Logging → Audit all auth events
