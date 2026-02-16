---
name: codebase-profiler
description: >
  Automated codebase profiling skill. Scans existing systems to generate
  module dependencies, domain models, API inventory, DB relationships,
  and business flow catalogs.
trigger: when running /forge-profile or analyzing an existing codebase
tags: [profiling, analysis, knowledge-mining, architecture]
---

# Codebase Profiler

## Purpose
Automatically scan and profile existing codebases to generate structured knowledge for the knowledge-base.

## Profiling Steps

### 1. Module Dependency Analysis
- Scan `settings.gradle.kts` / `build.gradle.kts` for module structure
- Extract inter-module dependencies from `project(":module")` declarations
- Generate Mermaid dependency graph

### 2. Domain Model Catalog
- Find all `@Entity` classes and `data class` models
- Map relationships (`@OneToMany`, `@ManyToOne`, `@ManyToMany`)
- Generate Mermaid class diagram
- Document field types, constraints, validations

### 3. API Inventory
- Scan all `@Controller` / `@RestController` classes
- Extract endpoints: method, path, request/response types
- Map to OpenAPI spec if available
- Document authentication requirements per endpoint

### 4. Database Relationship Map
- Scan Flyway migrations for schema structure
- Extract foreign key relationships
- Generate ER diagram (Mermaid)
- Document indexes and constraints

### 5. Business Flow Catalog
- Identify service method call chains
- Map event-driven flows (listeners, publishers)
- Document async vs sync communication patterns
- Generate sequence diagrams for key flows

## Output Format
Save to `knowledge-base/profiles/{system-name}/`:
- `overview.md` — System summary
- `modules.md` — Module dependency graph
- `domain-model.md` — Entity/model catalog
- `api-inventory.md` — API endpoint catalog
- `database-schema.md` — ER diagram + schema docs
- `business-flows.md` — Key flow sequence diagrams
