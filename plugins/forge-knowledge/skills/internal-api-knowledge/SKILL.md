---
name: internal-api-knowledge
version: 1.0.0
profile: knowledge
description: "Provides knowledge about internal APIs, service contracts, and integration patterns"
tags:
  - api
  - integration
  - service-contracts
required_tools:
  - forge-knowledge.search_wiki
  - forge-knowledge.search_profiles
  - forge-service-graph.get_dependencies
---

## Purpose

This skill equips the agent with the ability to retrieve and apply knowledge about internal APIs and service contracts. When working on tasks that involve calling other services, the agent should use this skill to understand API contracts, authentication requirements, error handling patterns, and versioning strategies.

## Instructions

When the current task involves interacting with internal APIs:

1. **Identify the target service**: Determine which internal service(s) the task requires integration with.

2. **Retrieve the service profile**: Use `forge-knowledge.search_profiles` to get the service profile:
   - Check the API style (REST, gRPC, GraphQL, event-driven)
   - Note the authentication mechanism (JWT, API key, mTLS)
   - Review the listed key patterns

3. **Check the service graph**: Use `forge-service-graph.get_dependencies` to understand:
   - Direct dependencies between services
   - Communication protocols in use
   - Known circuit breaker configurations

4. **Look up API documentation**: Use `forge-knowledge.search_wiki` to find:
   - API endpoint documentation
   - Request/response schema examples
   - Rate limiting policies

5. **Apply integration patterns consistently**:
   - Use the project's standard HTTP client configuration
   - Implement retry logic with exponential backoff
   - Add circuit breaker if calling a service marked as unreliable
   - Include correlation IDs in all cross-service calls
   - Log all external API calls with timing information

6. **Handle errors according to conventions**:
   - Map upstream 4xx errors to appropriate downstream responses
   - Wrap upstream 5xx errors with circuit breaker logic
   - Never expose internal service details in external API responses

## Quality Criteria

- All service calls use the project's standard HTTP client
- Authentication tokens come from the configured credential store, never hardcoded
- Retry and circuit breaker patterns match the service profile's recommendations
- Error responses follow the project's standard error format
- Cross-service calls include correlation ID headers

## Anti-patterns

- Hardcoding service URLs instead of using service discovery
- Ignoring circuit breaker recommendations from the service profile
- Not propagating correlation IDs across service boundaries
- Catching and silently swallowing upstream errors
