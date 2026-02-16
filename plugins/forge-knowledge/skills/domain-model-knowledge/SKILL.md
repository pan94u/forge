---
name: domain-model-knowledge
version: 1.0.0
profile: knowledge
description: "Provides knowledge about the domain model, entities, relationships, and business rules"
tags:
  - domain-model
  - entities
  - business-rules
  - ddd
required_tools:
  - forge-knowledge.search_wiki
  - forge-knowledge.search_profiles
  - forge-database.query_schema
---

## Purpose

This skill equips the agent with deep understanding of the project's domain model, including entities, value objects, aggregates, relationships, and business rules. When working on features that touch the domain layer, the agent should use this skill to ensure consistency with the established domain model and business invariants.

## Instructions

When working on tasks that involve domain entities or business logic:

1. **Understand the domain context**: Before writing any domain code, retrieve relevant documentation:
   - Use `forge-knowledge.search_wiki` with the entity or business concept name
   - Use `forge-knowledge.search_profiles` for the service that owns the domain
   - Use `forge-database.query_schema` to inspect the current database schema

2. **Identify the aggregate boundary**: Every domain operation must respect aggregate boundaries:
   - Determine which aggregate root owns the entity being modified
   - Ensure all changes within a transaction stay within one aggregate
   - Use domain events for cross-aggregate communication

3. **Apply domain-driven design patterns**:
   - **Entities**: Mutable objects with identity; equality based on ID
   - **Value Objects**: Immutable objects; equality based on attributes
   - **Aggregates**: Consistency boundary; accessed only through aggregate root
   - **Domain Events**: Record state changes for downstream consumers
   - **Repository Pattern**: Persistence abstraction per aggregate root

4. **Enforce business invariants**: Check existing validation rules before adding new ones:
   - Search for existing validators and business rule implementations
   - Maintain invariants in the domain layer, not in controllers or services
   - Express business rules in domain-specific language

5. **Follow naming conventions from the ubiquitous language**:
   - Use terms from the domain glossary, not technical jargon
   - Match the naming used in existing entities and services

## Quality Criteria

- New entities follow existing naming conventions from the domain glossary
- Aggregate boundaries are respected
- Business invariants are enforced in the domain layer
- Value objects are immutable
- Domain events are emitted for significant state changes
- Database schema changes are backward compatible

## Anti-patterns

- Anemic domain models where all logic lives in service classes
- Cross-aggregate mutations within a single database transaction
- Using primitive types instead of value objects for domain concepts
- Skipping domain events for important state transitions
