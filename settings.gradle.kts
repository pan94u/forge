pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "forge-platform"

// ── MCP Servers ──────────────────────────────────────────────────────────────
include(":mcp-servers:forge-mcp-common")
include(":mcp-servers:forge-knowledge-mcp")
include(":mcp-servers:forge-database-mcp")
include(":mcp-servers:forge-service-graph-mcp")
include(":mcp-servers:forge-artifact-mcp")
include(":mcp-servers:forge-observability-mcp")

// ── Web IDE Backend ──────────────────────────────────────────────────────────
include(":web-ide:backend")

// ── CLI ──────────────────────────────────────────────────────────────────────
include(":cli")

// ── Adapters ─────────────────────────────────────────────────────────────────
include(":adapters:model-adapter")
include(":adapters:runtime-adapter")

// ── Evaluation & Testing ─────────────────────────────────────────────────────
include(":agent-eval")
include(":skill-tests")

// ── User Service (Account/Auth/SSO) ───────────────────────────────────────────
include(":services:user-service")

// ── API Gateway ───────────────────────────────────────────────────────────────
include(":services:gateway")
