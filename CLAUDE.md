# Forge Platform — AI-Powered Intelligent Delivery Platform

## Quick Start

```bash
# Build all modules
./gradlew build

# Run MCP servers locally
docker-compose -f infrastructure/docker/docker-compose.yml up

# Run Web IDE backend
./gradlew :web-ide:backend:bootRun

# Run Web IDE frontend
cd web-ide/frontend && npm run dev

# Run skill tests
./gradlew :skill-tests:test

# Run agent evaluation
./gradlew :agent-eval:run
```

## Architecture

Forge is a Gradle monorepo (Kotlin DSL) with the following modules:

- **plugins/**: Claude Code plugin system (Skills, Profiles, Commands, Hooks)
- **mcp-servers/**: MCP Server implementations (Kotlin/Ktor, HTTP transport)
- **web-ide/**: Web IDE (Next.js 15 frontend + Spring Boot 3 backend)
- **cli/**: Forge CLI (Kotlin + GraalVM Native)
- **adapters/**: Adapter layer isolating stable/volatile concerns
- **agent-eval/**: SuperAgent evaluation framework
- **skill-tests/**: Skill validation framework
- **knowledge-base/**: Knowledge repository (profiles, docs, conventions)

## Key Design Decisions

1. **SuperAgent over Multi-Agent**: One intelligent agent dynamically switches Skill Profiles
2. **Skill over Prompt**: Professional knowledge encoded as reusable, composable Skills
3. **Baseline guarantees quality floor**: Baseline scripts must pass regardless of model
4. **Dual-loop architecture**: Delivery Loop (what) + Learning Loop (getting better)
5. **Adapter isolation**: Skills/baselines stable; models/runtime swappable via adapters

## Language & Conventions

- **Backend**: Kotlin 1.9+ on JDK 21, Spring Boot 3.3+
- **MCP Servers**: Kotlin + Ktor
- **Frontend**: TypeScript, React 19, Next.js 15 (App Router)
- **Build**: Gradle Kotlin DSL
- **Testing**: JUnit 5 + MockK + AssertJ (backend), Jest + Playwright (frontend)

## Security Rules

- NEVER hardcode credentials — use environment variables
- Database MCP: SELECT only, no DDL/DML, no production databases
- All MCP servers require OAuth2 Bearer Token authentication
- All requests include audit trail (user, timestamp, tool, parameters)

## Module Dependency Rules

- MCP servers depend on `forge-mcp-common` only
- Web IDE backend may depend on `forge-mcp-common` and `adapters`
- No circular dependencies between modules
- Plugins are standalone Markdown/JSON — no Kotlin compilation needed
