# Organization-Level CLAUDE.md Template

> Copy this template to the root of your organization's monorepo or configuration
> repository and customize the values for your organization.

---

```markdown
# CLAUDE.md — Organization Standards

This file defines organization-wide standards and constraints that apply to ALL
projects and teams. Every Claude Code session loads this file automatically.

## Language and Runtime

- **Primary backend language**: Kotlin (targeting JDK 21).
- **Secondary backend language**: Java 21 (for legacy modules only; new code must
  be Kotlin).
- **Frontend language**: TypeScript (strict mode enabled).
- **Runtime**: JDK 21 (Temurin distribution).
- Do NOT use Java features that have Kotlin equivalents (e.g., use Kotlin data
  classes, not Java records in Kotlin modules).

## Build System

- **Build tool**: Gradle 8.x with Kotlin DSL (`build.gradle.kts`).
- **Build command**: `./gradlew build` (compiles, tests, packages).
- **Test command**: `./gradlew test` (unit + integration tests).
- **Single module test**: `./gradlew :module-name:test`.
- **Lint check**: `./gradlew ktlintCheck` (Kotlin) or `./gradlew checkstyleMain`
  (Java).
- **Lint format**: `./gradlew ktlintFormat`.
- Always use the Gradle wrapper (`./gradlew`), never a system-installed Gradle.

## Architecture Rules

- Follow **layered architecture**: controller → service → repository.
- **No circular dependencies** between modules or packages.
- **No direct database access** from controllers — always go through a service
  and repository layer.
- Use **dependency injection** (constructor injection only, no field injection).
- All REST endpoints must be defined in `controller/` package classes.
- All database operations must be in `repository/` package classes.
- Business logic belongs in `service/` package classes.
- Cross-cutting concerns (logging, metrics, auth) use Spring AOP or middleware,
  not manual code in every class.
- **DTO separation**: API request/response DTOs must be separate from domain
  entities. Never expose JPA entities directly in API responses.

## Security Red Lines

These rules are NON-NEGOTIABLE. Violating any of these will block the PR.

- **No hardcoded credentials** — no passwords, API keys, tokens, or secrets in
  source code, configuration files, or comments. Use environment variables or a
  secrets manager.
- **Parameterized queries only** — all database queries must use parameterized
  statements or JPA/Spring Data methods. Never concatenate user input into SQL.
- **Input validation** — all external input (request bodies, path params, query
  params, headers) must be validated using Bean Validation annotations or
  explicit validation logic.
- **No `*` imports in Kotlin/Java** — use explicit imports only.
- **No `@SuppressWarnings` without justification** — if suppressing a warning,
  add a comment explaining why.
- **HTTPS only** — all external HTTP calls must use HTTPS. No plain HTTP.
- **No `System.out.println`** — use the structured logging framework (SLF4J +
  Logback).
- **No disabling CSRF/CORS without approval** — security configurations must be
  reviewed by the security team.

## Code Style

### Kotlin
- Follow the official Kotlin coding conventions.
- Enforced by **ktlint** with the standard rule set.
- Use `val` over `var` wherever possible.
- Use expression bodies for single-expression functions.
- Use `data class` for DTOs and value objects.
- Use `sealed class` or `sealed interface` for restricted hierarchies.
- Maximum line length: 120 characters.

### Java (legacy modules)
- Follow Google Java Style Guide.
- Enforced by **Checkstyle** with the organization's configuration.
- Maximum line length: 120 characters.

### General
- All public classes and functions must have KDoc/Javadoc comments.
- TODO comments must include a Jira ticket reference: `// TODO(JIRA-1234): ...`.
- No commented-out code in production branches.

## Testing Requirements

- **Minimum code coverage**: 80% line coverage per module.
- **Unit tests**: Required for all service and repository classes.
- **Integration tests**: Required for all controller classes (using
  `@SpringBootTest` or `@WebMvcTest`).
- **Test naming**: Use descriptive names —
  `should return 404 when user not found` not `test1`.
- **Test isolation**: Tests must not depend on external services or shared state.
  Use testcontainers for database tests.
- **No `@Disabled` tests without a Jira ticket** — disabled tests must reference
  a ticket for re-enabling.
- Run tests before committing: `./gradlew test`.

## Dependencies

- All dependencies must be declared in the version catalog
  (`gradle/libs.versions.toml`).
- No dependency version overrides in individual `build.gradle.kts` files.
- New dependencies require team lead approval (add a comment in the PR).
- No SNAPSHOT dependencies in production branches.

## Git Conventions

- Branch naming: `feature/JIRA-1234-short-description`,
  `bugfix/JIRA-5678-short-description`.
- Commit messages: Start with the Jira ticket ID —
  `JIRA-1234: Add user registration endpoint`.
- PRs must have at least one approval before merging.
- Squash merge to `main` — keep a clean linear history.
```

---

## Usage Notes

- Place this file at the root of your organization's repository or in a shared
  configuration repository that all projects reference.
- Teams and projects layer their own `CLAUDE.md` on top of this file to add
  specificity without contradicting organization-level rules.
- Review and update this file quarterly or when organizational standards change.
