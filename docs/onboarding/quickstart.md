# Forge Platform — Quick Start Guide

Welcome to Forge. This guide walks you through setting up your local development
environment, initializing your first project, and running your first AI-assisted
task.

---

## 1. Prerequisites

Before you begin, ensure the following tools are installed on your machine.

### 1.1 Required Software

| Tool | Version | Purpose | Install |
|---|---|---|---|
| JDK | 21+ | Kotlin/Java compilation and runtime | `sdk install java 21-tem` (SDKMAN) |
| Kotlin | 1.9+ | Primary backend language | Bundled with Gradle |
| Node.js | 20+ | Web IDE frontend build | `nvm install 20` |
| Docker | 24+ | Local service dependencies | [docker.com](https://docker.com) |
| Docker Compose | 2.20+ | Multi-container orchestration | Bundled with Docker Desktop |
| Claude Code | latest | AI coding assistant | `npm install -g @anthropic-ai/claude-code` |
| Git | 2.40+ | Version control | Pre-installed on most systems |

### 1.2 Recommended Tools

| Tool | Purpose |
|---|---|
| IntelliJ IDEA | Kotlin/Java IDE with excellent Gradle support |
| SDKMAN | JDK version management |
| nvm | Node.js version management |
| jq | JSON processing in CLI scripts |
| httpie | HTTP client for testing MCP endpoints |

### 1.3 System Requirements

- **RAM**: Minimum 16 GB (Docker services consume ~4 GB).
- **Disk**: Minimum 20 GB free space for Docker images and build caches.
- **OS**: macOS 13+, Ubuntu 22.04+, or Windows 11 with WSL2.

### 1.4 Verify Prerequisites

Run the following to check your environment:

```bash
java -version          # Should show 21.x
node --version         # Should show v20.x or higher
docker --version       # Should show 24.x or higher
docker compose version # Should show 2.20+
claude --version       # Should show the installed Claude Code version
git --version          # Should show 2.40+
```

---

## 2. Clone the Repository

```bash
git clone https://github.com/your-org/forge-platform.git
cd forge-platform
```

If you are behind a corporate proxy, configure Git and Docker appropriately
before proceeding.

---

## 3. Installation

### 3.1 Start Local Infrastructure

Forge uses Docker Compose to run its local dependencies (PostgreSQL, MCP
servers, etc.):

```bash
cd infrastructure/docker
docker compose up -d
```

This starts the following services:

| Service | Port | Health Check |
|---|---|---|
| PostgreSQL 16 | 5432 | `pg_isready` |
| Knowledge MCP | 8081 | http://localhost:8081/health/ready |
| Database MCP | 8082 | http://localhost:8082/health/ready |
| Service Graph MCP | 8083 | http://localhost:8083/health/ready |
| Artifact MCP | 8084 | http://localhost:8084/health/ready |
| Observability MCP | 8085 | http://localhost:8085/health/ready |
| Web IDE Backend | 8080 | http://localhost:8080/actuator/health |

Wait for all services to become healthy:

```bash
docker compose ps
```

All services should show status `healthy` or `running`.

### 3.2 Build the Platform

From the repository root:

```bash
./gradlew build
```

This compiles all modules, runs unit tests, and produces build artifacts. The
first build downloads dependencies and may take 5-10 minutes.

### 3.3 Configure Claude Code

Ensure your Anthropic API key is set:

```bash
export ANTHROPIC_API_KEY="your-api-key-here"
```

Add this to your shell profile (`~/.zshrc` or `~/.bashrc`) for persistence.

---

## 4. Running `forge init`

The `forge init` command bootstraps a new project with Forge conventions,
including CLAUDE.md files and Skill Profile references.

### 4.1 Initialize a New Project

```bash
forge init --name my-service \
           --team payments \
           --type kotlin-service \
           --skills rest-api-kotlin,postgresql-repository
```

This creates:

```
my-service/
├── CLAUDE.md                    # Project-level context
├── build.gradle.kts             # Gradle build with standard plugins
├── settings.gradle.kts          # Project settings
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/example/myservice/
│   │   │       ├── Application.kt
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       └── config/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── kotlin/
│           └── com/example/myservice/
├── .forge/
│   ├── skills.yaml              # Active Skill Profile references
│   └── config.yaml              # Forge project configuration
└── docker-compose.yml           # Local dependencies for this service
```

### 4.2 Initialize for an Existing Project

If you have an existing project that you want to bring into Forge:

```bash
cd existing-project
forge init --existing \
           --team platform \
           --skills rest-api-kotlin
```

This adds `CLAUDE.md`, `.forge/` configuration, and detects the existing project
structure to populate context correctly.

---

## 5. First Use Walkthrough

### 5.1 Start an AI-Assisted Session

Open a terminal in your project directory and start Claude Code:

```bash
claude
```

Claude Code automatically detects and loads the CLAUDE.md files in your project
hierarchy (org → team → project). You should see confirmation in the startup
output.

### 5.2 Try a Simple Task

Type the following prompt:

```
Create a REST endpoint GET /api/health that returns {"status": "UP", "timestamp": "<current-time>"}
with appropriate tests.
```

Claude Code will:
1. Read your CLAUDE.md for coding conventions.
2. Check the Skill Profiles for REST API patterns.
3. Generate the controller, test, and any necessary configuration.
4. Run `./gradlew test` to verify the implementation.

### 5.3 Use the Web IDE (Optional)

Open your browser to http://localhost:8080 and log in. The Web IDE provides:

- A chat interface for interacting with Claude Code.
- Real-time streaming of terminal output.
- File browser with Monaco editor integration.
- Session history and telemetry dashboard.

---

## 6. Common Commands Reference

### 6.1 Forge CLI

| Command | Description |
|---|---|
| `forge init` | Initialize a new or existing project with Forge conventions. |
| `forge skill list` | List all available Skill Profiles. |
| `forge skill add <name>` | Add a Skill Profile to the current project. |
| `forge skill remove <name>` | Remove a Skill Profile from the current project. |
| `forge context show` | Display the assembled CLAUDE.md context for the current directory. |
| `forge context validate` | Validate CLAUDE.md files for correctness and completeness. |
| `forge mcp status` | Check the health of all configured MCP servers. |
| `forge update` | Update Forge CLI and Skill Profiles to the latest versions. |

### 6.2 Gradle Commands

| Command | Description |
|---|---|
| `./gradlew build` | Compile, test, and package all modules. |
| `./gradlew test` | Run all unit and integration tests. |
| `./gradlew :module:test` | Run tests for a specific module. |
| `./gradlew ktlintCheck` | Check Kotlin code style compliance. |
| `./gradlew ktlintFormat` | Auto-format Kotlin code. |
| `./gradlew bootRun` | Run the Spring Boot application locally. |
| `./gradlew dependencyUpdates` | Check for dependency updates. |

### 6.3 Docker Commands

| Command | Description |
|---|---|
| `docker compose up -d` | Start all local services in the background. |
| `docker compose down` | Stop and remove all local services. |
| `docker compose logs -f <service>` | Follow logs for a specific service. |
| `docker compose ps` | Show status of all services. |
| `docker compose pull` | Pull latest images for all services. |

---

## 7. Troubleshooting

### 7.1 Docker Services Not Starting

**Symptom**: `docker compose up` fails or services restart repeatedly.

**Solutions**:
- Ensure Docker Desktop is running and has sufficient resources allocated (at
  least 4 GB RAM, 2 CPUs).
- Check for port conflicts: `lsof -i :8080` (macOS/Linux).
- Review logs: `docker compose logs <service-name>`.
- Reset volumes if database is corrupted: `docker compose down -v && docker compose up -d`.

### 7.2 Gradle Build Failures

**Symptom**: `./gradlew build` fails with compilation or test errors.

**Solutions**:
- Ensure JDK 21 is active: `java -version`.
- Clear Gradle cache: `./gradlew clean build --no-build-cache`.
- Check for missing environment variables in `application.yml`.
- Run with debug output: `./gradlew build --stacktrace`.

### 7.3 Claude Code Not Loading Context

**Symptom**: Claude Code does not mention CLAUDE.md or Skill Profiles on startup.

**Solutions**:
- Verify `CLAUDE.md` exists in the current directory or a parent directory.
- Check that `.forge/skills.yaml` references valid Skill Profiles.
- Ensure MCP servers are running: `forge mcp status`.
- Restart Claude Code: exit and re-run `claude`.

### 7.4 MCP Server Connection Errors

**Symptom**: Claude Code reports errors connecting to MCP servers.

**Solutions**:
- Verify services are healthy: `docker compose ps`.
- Test connectivity manually: `curl http://localhost:8081/health/ready`.
- Check network configuration — all services should be on the `forge-network`.
- Review MCP server logs for authentication errors.

### 7.5 Web IDE WebSocket Disconnections

**Symptom**: The Web IDE terminal disconnects or shows stale output.

**Solutions**:
- Check that the backend is running on port 8080.
- Ensure no proxy or firewall is blocking WebSocket upgrades.
- Try a hard refresh in the browser (Ctrl+Shift+R).
- Check backend logs for session errors:
  `docker compose logs forge-web-ide-backend`.

---

## 8. Next Steps

After completing this quickstart, explore the following:

1. **Architecture Overview** — `docs/architecture/overview.md` for a deep dive
   into the Forge platform design.
2. **Skill Review Policy** — `docs/governance/skill-review-policy.md` to
   understand how to contribute Skill Profiles.
3. **CLAUDE.md Templates** — `docs/templates/claude-md/` for organization, team,
   and project-level templates.
4. **MCP Server Development** — Each MCP server module contains its own README
   with development instructions.
