---
name: forge-init
description: Initialize a project with Forge configuration
trigger: /forge-init
---

# /forge-init — Project Initialization

When the user runs `/forge-init`, perform these steps:

## Steps

1. **Detect Project Type**: Scan for `build.gradle.kts` (Kotlin/Gradle), `pom.xml` (Maven), `package.json` (Node.js)
2. **Generate CLAUDE.md**: Create project-level CLAUDE.md using the appropriate template from `docs/templates/claude-md/project-template.md`. Include:
   - Project name and description
   - Build commands (`./gradlew build`, `./gradlew test`)
   - Key module structure
   - Important architectural decisions
3. **Configure .mcp.json**: Create `.mcp.json` referencing the Forge MCP servers (knowledge, service-graph, database)
4. **Install Plugin References**: Add forge-foundation plugin reference
5. **Run Health Check**: Execute `forge doctor` equivalent to verify setup
6. **Generate Initial Profile**: Run codebase-profiler skill to create initial system profile

## Output
Confirm to user: "Forge initialized. CLAUDE.md created, MCP servers configured, plugins loaded."
