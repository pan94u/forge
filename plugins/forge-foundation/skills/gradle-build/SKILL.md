---
name: gradle-build
description: >
  Gradle Kotlin DSL patterns. Version catalogs, multi-module setup,
  dependency management, build cache, and custom tasks.
trigger: when modifying build.gradle.kts, settings.gradle.kts, or version catalogs
tags: [gradle, build, kotlin-dsl, dependencies, multi-module]
version: "2.0"
scope: platform
category: foundation
---

# Gradle Kotlin DSL

## Version Catalogs (libs.versions.toml)

```toml
[versions]
kotlin = "1.9.25"
spring-boot = "3.3.5"
ktor = "2.3.7"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

## Dependency Scopes
| Scope | When |
|-------|------|
| `implementation` | Internal dependency, not exposed to consumers |
| `api` | Exposed to consumers (use sparingly, only in libraries) |
| `compileOnly` | Compile-time only (annotations, provided) |
| `runtimeOnly` | Runtime only (JDBC drivers, logging impl) |
| `testImplementation` | Test dependencies |

## Multi-Module Conventions
- Root `build.gradle.kts` defines shared plugins and versions
- Each module has its own `build.gradle.kts` with specific dependencies
- Use `project(":module-name")` for inter-module dependencies
- Enable parallel builds: `org.gradle.parallel=true`
- Enable build cache: `org.gradle.caching=true`
