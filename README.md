# Forge — AI-Native IT Delivery & Governance Infrastructure

<div align="center">

**Not an AI coding assistant — an AI-Native infrastructure that deeply embeds AI across the entire software delivery lifecycle.**

[![License](https://img.shields.io/badge/license-Source%20Available-lightgrey.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21-orange.svg)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org/)
[![Next.js](https://img.shields.io/badge/Next.js-15-black.svg)](https://nextjs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)

</div>

<div align="center">

![Forge Brand Positioning](assets/images/01-forge-brand-positioning.jpg)

</div>

---

## Overview

Forge is an **AI-Native IT infrastructure** with a dual-layer collaborative architecture:

- **Execution Layer (Web IDE)**: SuperAgent + 35 Skills + 24 MCP tools, three-loop architecture (human control loop + intelligent execution loop + evolutionary learning loop), compressing the delivery capacity of a 5-7 person team down to 1-2 people + AI
- **Governance Layer (Enterprise Console)**: Governance AI covering IT governance domains (org/member/provider management), with Forge execution data driving governance insights and decisions

<div align="center">

![Forge Dual-Layer Solution](assets/images/06-forge-dual-layer-solution.jpg)

</div>

### Core Philosophy

| Principle | Description |
|-----------|-------------|
| **SuperAgent over Multi-Agent** | One agent dynamically switches roles via Skills, avoiding multi-agent coordination complexity |
| **Skills over Prompts** | Professional knowledge encoded into 35 reusable, composable Skill assets |
| **Baselines guarantee quality floor** | Quality baseline scripts must pass regardless of model capability changes |
| **Dual-loop drives continuous improvement** | Delivery Loop solves "what to do", Learning Loop solves "getting better" |
| **Human-In-The-Loop (HITL)** | Critical decisions require human approval, not a fully automated black box |

<div align="center">

![Forge What It Is Not](assets/images/02-forge-what-it-is-not.jpg)

</div>

---

## Key Features

### SuperAgent Autonomous Execution Engine

Users declare intent; SuperAgent autonomously completes the full cycle of planning → coding → verification → delivery:

- **50-turn autonomous execution**: No step-by-step instructions needed — the agent decides tool calls, file operations, and code generation independently
- **OODA Loop**: Observe → Orient → Decide → Act, with real-time visualization of each phase
- **24 MCP tools**: File operations, knowledge search, database queries, code compilation, test execution, service management, and more
- **Self-repair on failure**: When baseline checks fail, automatically analyzes the cause, modifies code, and re-validates (up to 2 rounds)
- **Self-managed context**: Automatically compresses message history when approaching token limits, without interrupting execution

### 6 Skill Profiles with Intelligent Routing

| Profile | Responsibility | Typical Skills |
|---------|---------------|----------------|
| **planning** | Requirements analysis, PRD writing | requirement-engineering, delivery-methodology |
| **design** | Architecture design, ADR writing | architecture-design, api-design, database-patterns |
| **development** | Code implementation, code generation | code-generation, kotlin-conventions, spring-boot-patterns |
| **testing** | Test strategy, test case writing | test-case-writing, testing-standards |
| **ops** | Deployment, operations, troubleshooting | deployment-ops, logging-observability |
| **evaluation** | Progress evaluation, knowledge distillation, doc generation | progress-evaluation, knowledge-distillation |

Routing supports 4 priority levels: explicit tags (`@development`) → keyword detection → branch name patterns → default fallback.

### 35-Skill Asset Library

| Plugin Package | Skills | Description |
|----------------|--------|-------------|
| **forge-superagent** | 14 | architecture-design, bug-fix-workflow, code-generation, delivery-methodology, deployment-ops, detailed-design, document-generation, git-workflow, knowledge-distillation, knowledge-generator, planning-mode, progress-evaluation, requirement-engineering, test-case-writing |
| **forge-foundation** | 16 | api-design, business-rule-extraction, codebase-profiler, convention-miner, database-patterns, deployment-readiness-check, design-baseline-guardian, environment-parity, error-handling, gradle-build, java-conventions, kotlin-conventions, logging-observability, security-practices, spring-boot-patterns, testing-standards |
| **forge-knowledge** | 3 | domain-model-knowledge, internal-api-knowledge, runbook-knowledge |
| **forge-deployment** | 2 | ci-cd-patterns, kubernetes-patterns |

### Three-Layer Cross-Session Memory System

Solves the core pain point of AI agents "starting fresh every time":

| Layer | Name | Scope | Capacity |
|-------|------|-------|----------|
| Layer 1 | **Workspace Memory** | Workspace-level | 4,000 chars |
| Layer 2 | **Stage Memory** | Profile × Workspace | 8,000 chars |
| Layer 3 | **Session Summary** | Single session | 2,000 chars/entry |

Effect: New sessions immediately have project context, saving 30-40% token consumption.

### Quality Assurance System

- **Automatic baseline checks**: After code generation, automatically runs code-style / security / api-contract / architecture baselines
- **HITL approval checkpoints**: Pauses at key delivery nodes for human approval — supports Approve / Reject / Modify
- **Four-dimensional evaluation learning loop**: Intent understanding + Completion + Quality + Experience, auto-generates improvement suggestions
- **Prompt Caching**: System Prompt cached for 5 minutes, 90% cost savings on cache hits

### Forge Eval — Agent Evaluation & Quality Guard

AI Agent outputs are non-deterministic. "It worked once" does not mean "it works reliably." Forge Eval is a **continuous quality guard** that turns "I think the agent works" into "data proves the agent works — consistently."

#### Three Trust Layers

| Layer | Meaning | Metric | Graduation Criteria |
|-------|---------|--------|-------------------|
| **Capability** | Can do it | Pass@5 > 0 (at least 1 success in 5 attempts) | — |
| **Regression** | Does it reliably | Pass^5 ≈ 1.0 (all 5 attempts succeed) | 5 consecutive runs ≥ 95% pass rate, Pass^3 ≥ 90% |
| **Saturated** | No longer needs watching | 20 consecutive runs all-pass | Auto-reduces eval frequency, freeing resources for new capabilities |

Each eval task has its own **lifecycle** managed by `LifecycleManager`, automatically progressing from exploring capability boundaries → guarding existing capabilities → releasing human attention.

#### Composite Grading Architecture

Three grader types, composable via `CompositeGrader`:

| Grader | When to Use | Characteristics |
|--------|------------|-----------------|
| **Code-Based** | Clear right/wrong criteria | Deterministic, zero cost, 11 assertion types |
| **Model-Based** | Subjective quality judgment | LLM-as-Judge with rubric scoring + confidence |
| **Human** | Calibrate the first two | Deferred scoring via review queue |

**11 Code-Based assertion types** across three dimensions:

| Dimension | Assertions | What it Evaluates |
|-----------|-----------|-------------------|
| Output correctness | `contains`, `not_contains`, `matches_pattern`, `json_schema`, `json_path` | Is the result correct? |
| Behavioral process | `tool_used`, `tool_not_used`, `tool_call_count`, `tool_call_order` | Is the execution path correct? |
| Efficiency | `turn_count_max` | Did the agent waste steps? |

#### Statistical Rigor

- **Pass@k** (unbiased estimator): Probability of at least 1 success in k trials — measures **capability**
- **Pass^k**: Probability of all k trials succeeding — measures **reliability**
- **Regression detection**: Binomial test with Wilson Score confidence intervals distinguishes real regressions from random variance
- **Human review triggers**: Auto-flags grades for review when LLM confidence < 0.7, Code vs Model score divergence > 0.3, random 10% sampling, or first 3 runs of new tasks
- **Cohen's Kappa**: Measures human-machine agreement rate for calibrating automated graders

#### Key Capabilities

| Capability | How | Outcome |
|------------|-----|---------|
| **Assess new agent capabilities** | Create Suite + Tasks, run multi-trial evaluations | Pass rates, Pass@k, per-assertion breakdowns |
| **Model upgrade A/B comparison** | Run same Suite before/after, regression detection | Per-task regression report with statistical significance |
| **External agent evaluation** | Configure SSE/REST endpoint (Dify, Coze, OpenAI-compatible, etc.) | Same scoring standard for any agent |
| **Production quality audit** | Submit historical transcripts for post-hoc grading | Quality scoring on conversations that already happened |
| **Trend monitoring** | Time-series dashboard of pass rates across runs | Spot quality inflection points before users do |
| **Lifecycle management** | Auto-graduation from Capability → Regression → Saturated | Saturated tasks auto-reduce frequency, focus on new problems |

#### Architecture

```
forge-eval/
├── eval-protocol/   # Data model & types (Platform, AgentType, Lifecycle, Difficulty, etc.)
├── eval-engine/     # Execution engine + graders + stats + lifecycle
│   ├── grader/      # CodeBasedGrader, ModelBasedGrader, CompositeGrader
│   ├── stats/       # PassMetrics (Pass@k, Pass^k, Wilson CI), RegressionDetector
│   ├── lifecycle/   # LifecycleManager (CAPABILITY → REGRESSION → SATURATED)
│   ├── review/      # ReviewTriggerRules (4 auto-trigger conditions)
│   └── harness/     # External agent harness (SSE/REST protocols)
└── eval-api/        # 17 REST endpoints + JPA persistence + review queue
```

**17 API endpoints** covering suites, tasks, runs, transcripts, regressions, trends, lifecycle, and human reviews — usable from the Eval Dashboard UI or directly via API for CI/CD integration.

### Web IDE

```
┌─────────────────────────────────────────────────────────────────┐
│ Header — Role Switch + Model Selector + User Menu               │
├──────────┬──────────────────────────────┬───────────────────────┤
│ File     │ Monaco Editor                │ AI Chat Sidebar       │
│ Explorer │   - 25+ language highlights  │  4-Tab:               │
│          │   - Multi-tab file editing   │  [Chat|Quality|Skills │
│  CRUD:   │   - AI Explain button        │   |Memory]            │
│  Create  │   - 5s auto-save             │   - Streaming output  │
│  Rename  ├──────────────────────────────┤   - Tool call expand  │
│  Delete  │ Terminal Panel (collapsible)  │   - OODA indicator    │
└──────────┴──────────────────────────────┴───────────────────────┘
```

Frontend routes: `workspaces` (list), `workspace/[id]` (main IDE), `knowledge` (knowledge base), `skills` (skill management), `workflows` (workflow), `evaluations` (evaluation reports), `login`

### Enterprise Console (Governance Dashboard)

An independently deployed enterprise governance interface covering:
- **Organization Management** (`/orgs`): Multi-org CRUD, member management
- **Invitation Management** (`/invite`): Invitation link creation and acceptance
- **Provider Management**: AI model provider configuration and health checks

### Multi-Model Support

| Provider | Supported Models | Context Window |
|----------|-----------------|----------------|
| **Anthropic Claude** | Opus 4.6 / Sonnet 4.5 / Haiku 4.5 | 200K |
| **Google Gemini** | Gemini Pro | 30K |
| **Alibaba Qwen** | Qwen2.5-7B / 72B | 32K |
| **AWS Bedrock** | Claude via AWS | Provider-specific |
| **MiniMax** | M2.5 / M2.5-lightning / M2.5-highspeed | 1M |
| **OpenAI-compatible** | Any service compatible with OpenAI API | Provider-specific |

---

## Architecture

<div align="center">

![Forge Full Architecture](assets/images/11-forge-full-architecture.jpg)

</div>

### System Architecture Diagram

```
┌─ User Interaction Layer ─────────────────────────────────────────┐
│  Web IDE (Next.js 15)  │  Enterprise Console (Next.js 15)        │
│  CLI (Kotlin + GraalVM Native)                                   │
└──────────────────────────────────────────────────────────────────┘
                          │
┌─ Application Layer ──────────────────────────────────────────────┐
│  Spring Boot 3 Backend                                            │
│  ├─ AgenticLoopOrchestrator (50-turn autonomous execution)        │
│  ├─ ProfileRouter (6 Profile intelligent routing)                 │
│  ├─ SkillLoader (35 Skills dynamic loading)                       │
│  ├─ SystemPromptAssembler (dynamic prompt assembly)               │
│  ├─ McpProxyService (24 tool call proxy)                          │
│  ├─ MemoryContextLoader (three-layer memory injection)            │
│  ├─ HitlCheckpointManager (HITL approval management)             │
│  └─ LearningLoopPipelineService (learning feedback loop)         │
└──────────────────────────────────────────────────────────────────┘
                          │
┌─ MCP Tool Layer ─────────────────────────────────────────────────┐
│  forge-knowledge-mcp:8081  │  forge-database-mcp:8082            │
│  forge-service-graph-mcp   │  forge-artifact-mcp                 │
│  forge-observability-mcp   │  (built-in workspace/baseline tools) │
└──────────────────────────────────────────────────────────────────┘
                          │
┌─ Model Adapter Layer ────────────────────────────────────────────┐
│  ClaudeAdapter  │  GeminiAdapter  │  QwenAdapter                 │
│  BedrockAdapter │  OpenAIAdapter  │  (unified ModelAdapter interface) │
└──────────────────────────────────────────────────────────────────┘
```

<div align="center">

![Forge Three-Loop Architecture](assets/images/05-forge-three-loop-architecture.jpg)

</div>

### Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend Language | Kotlin | 1.9+ |
| Backend Framework | Spring Boot | 3.3+ |
| Runtime | JDK | 21 |
| Frontend Framework | Next.js + React | 15 + 19 |
| Frontend Language | TypeScript | 5.x |
| Database (Dev) | H2 file persistence | — |
| Database (Prod) | PostgreSQL | 16 |
| DB Migration | Flyway | 8 versions (V1-V8) |
| Code Editor | Monaco Editor | 4.6+ |
| Workflow Canvas | ReactFlow | 12.3+ |
| Diagram Rendering | Mermaid | 11.4+ |
| Authentication | Keycloak | 24.0 |
| Monitoring | Prometheus + Micrometer | — |
| Containerization | Docker Compose | 7 containers |

### Gradle Module Structure

```
forge-platform/
├── web-ide/
│   ├── backend/          # Spring Boot 3 backend (Kotlin, 22 Controllers)
│   └── frontend/         # Next.js 15 frontend (TypeScript, 8 routes)
├── enterprise-console/   # Enterprise governance console (Next.js 15, standalone)
├── mcp-servers/
│   ├── forge-mcp-common/        # MCP protocol common library
│   ├── forge-knowledge-mcp/     # Knowledge base MCP server
│   ├── forge-database-mcp/      # Database MCP server
│   ├── forge-service-graph-mcp/ # Service topology MCP server
│   ├── forge-artifact-mcp/      # Build artifact MCP server
│   └── forge-observability-mcp/ # Observability MCP server
├── adapters/
│   ├── model-adapter/    # Model adapters (Claude/Gemini/Qwen/Bedrock)
│   └── runtime-adapter/  # Runtime adapter
├── plugins/
│   ├── forge-foundation/  # Foundation Skills (16: Kotlin/Java/Spring/API conventions)
│   ├── forge-superagent/  # SuperAgent Skills (14) + 6 Profiles
│   ├── forge-knowledge/   # Knowledge Skills (3)
│   ├── forge-deployment/  # Deployment Skills (2: K8s/CI-CD)
│   └── forge-team-templates/ # Team templates (backend/data/mobile)
├── cli/                   # Forge CLI (Kotlin + GraalVM Native)
├── forge-eval/            # Unified evaluation engine (eval-protocol + eval-engine + eval-api)
├── agent-eval/            # Legacy YAML-based evaluation framework (bridged to forge-eval)
├── skill-tests/           # Skill validation framework
├── knowledge-base/        # Knowledge base documents (13+ docs)
└── infrastructure/
    └── docker/            # Docker Compose deployment configs (4 environments)
```

---

## Quick Start

### Prerequisites

| Item | Requirement | Verification |
|------|-------------|-------------|
| JDK | **21** (required! JDK 8/17 will fail to compile) | `java -version` |
| Docker Engine | 24+ | `docker --version` |
| Docker Compose | v2+ | `docker compose version` |
| Node.js | 20+ | `node --version` |
| Available Memory | ≥ 8 GB (allocated to Docker) | Docker Desktop → Resources |
| API Key | At least 1 Provider | See configuration below |

### Step 1: Clone the Repository

```bash
git clone git@github.com:pan94u/forge.git
cd forge
```

### Step 2: Configure Environment Variables

```bash
cp .env.example infrastructure/docker/.env
```

Edit `infrastructure/docker/.env` and fill in at least one Provider's API Key:

```bash
# Anthropic Claude (recommended)
ANTHROPIC_API_KEY=sk-ant-api03-your-key

# Or Google Gemini
# GEMINI_API_KEY=AIza...

# Or Alibaba DashScope (Qwen)
# DASHSCOPE_API_KEY=sk-...

# Or MiniMax
# MINIMAX_API_KEY=...

# Or OpenAI-compatible (Ollama/vLLM/LocalAI, etc.)
# LOCAL_MODEL_URL=http://localhost:11434
# LOCAL_MODEL_NAME=llama3.1:8b
```

### Step 3: Build Locally

```bash
# Ensure JDK 21 (macOS example)
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Build backend JAR (skip tests for speed)
./gradlew :web-ide:backend:bootJar -x test --no-daemon

# Build frontend
cd web-ide/frontend && npm install && npm run build && cd ../..

# Build Enterprise Console (optional)
cd enterprise-console && npm install && npm run build && cd ..
```

### Step 4: Start with Docker

```bash
cd infrastructure/docker
docker compose -f docker-compose.trial.yml --env-file .env up --build -d
```

### Step 5: Verify Startup

```bash
# Check all 7 containers (all should be healthy or running)
docker compose -f docker-compose.trial.yml ps

# Test API
curl -s http://localhost:19000/api/models | python3 -m json.tool
```

### Step 6: Access the Platform

| Service | URL | Description |
|---------|-----|-------------|
| **Web IDE** | http://localhost:19000 | Main IDE interface |
| **Enterprise Console** | http://localhost:19001 | Enterprise governance dashboard |
| **Knowledge MCP** | http://localhost:19081 | Knowledge base MCP service |
| **Database MCP** | http://localhost:19082 | Database MCP service |

---

## Technical Specifications

| Dimension | Value |
|-----------|-------|
| REST API Controllers | 22 |
| REST API Endpoints | 70+ |
| SSE Event Types | 14 |
| MCP Tools | 24 (5 categories: builtin/knowledge/workspace/memory/planning) |
| JPA Entities | 12 |
| Flyway Migrations | 8 versions (V1-V8) |
| Total Skills | 35 (4 plugin packages, 6 Profiles) |
| Unit Tests | 156 (all passing) |
| Docker Containers | 7 (postgres + knowledge-mcp + database-mcp + backend + frontend + enterprise-console + nginx) |
| Knowledge Base Docs | 13+ |
| Code Volume | ~50K+ lines (Kotlin + TypeScript) |
| Frontend Routes | 8 (Web IDE) + 2 (Enterprise Console) |
| Supported Models | 6 Providers (13+ models) |
| Context Window | Up to 200K tokens (Claude Opus) |
| Autonomous Execution Turns | Up to 50 |

<div align="center">

![Forge Current Status Phase 18](assets/images/08-forge-current-status-phase18.jpg)

</div>

---

## Roadmap

<div align="center">

![Forge 8 Core Goals](assets/images/03-forge-8-core-goals.jpg)

</div>

| Phase | Keyword | Status | Key Capabilities |
|-------|---------|--------|-----------------|
| Phase 0 | Foundation | ✅ | Foundation Skills + MCP Servers + CLI + Plugin system |
| Phase 1 | Web IDE Connected | ✅ | Real streaming + Agentic Loop + Cross-stack profiling |
| Phase 1.5 | Design Guard + Docker | ✅ | Docker deployment + E2E validation + Design baseline freeze |
| Phase 1.6 | AI Delivery Loop + SSO | ✅ | AI→Workspace file writing + Keycloak SSO + Context Picker |
| Phase 2 | Quality Infrastructure | ✅ | CI/CD + SkillLoader + Real MCP services + Multi-model |
| Phase 3 | HITL + Memory | ✅ | Three-layer memory + HITL approval + Learning loop + Quality panel |
| Phase 4 | Skill Architecture | ✅ | Progressive loading + Skill management + Usage tracking |
| Phase 5 | Product Usability | ✅ | Workspace persistence + Git Clone + User API Keys |
| Phase 6 | Knowledge Write + Multi-model | ✅ | MiniMax + Local knowledge write + 50-turn Agentic Loop |
| Phase 7 | Async + Knowledge Scope | ✅ | Async Git Clone + Three-tier knowledge scope + CRUD |
| Phase 8 | Enterprise Console | ✅ | Governance dashboard + Org management + Provider management |
| Phase 9+ | Learning Loop Closure | 🔄 | ForgeNativeRuntime + Methodology platformization |

<div align="center">

![Forge Implementation Roadmap](assets/images/07-forge-implementation-roadmap.jpg)

</div>

<div align="center">

![Forge Next Steps Enterprise Console](assets/images/09-forge-next-steps-enterprise-console.png)

</div>

---

## Competitive Advantages

<div align="center">

![Forge 5 Competitive Advantages](assets/images/04-forge-5-competitive-advantages.jpg)

</div>

---

## Ecosystem Vision: Forge × Synapse AI

<div align="center">

![Forge x Synapse AI Same Mission](assets/images/10-forge-synapse-ai-same-mission.jpg)

</div>

Same mission, different battlefields: Forge serves the IT organization domain (execution + governance), [Synapse AI](https://github.com/pan94u/Synapse-AI) covers the business organization domain (RaaS · key minority priority · industry brain), connected via MCP bridging protocol for IT data → business decisions.

---

## Development Guide

### Run Unit Tests

```bash
# Backend tests
./gradlew :web-ide:backend:test :adapters:model-adapter:test

# Evaluation framework tests
./gradlew :agent-eval:test
```

### Local Development Mode (without Docker)

```bash
# Start backend
cd web-ide/backend
./gradlew bootRun

# Start frontend (new terminal)
cd web-ide/frontend
npm run dev
# Access http://localhost:3000

# Start Enterprise Console (new terminal)
cd enterprise-console
npm run dev
# Access http://localhost:19001
```

### Common Issues

| Issue | Solution |
|-------|---------|
| JDK version error | Must use JDK 21: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21` |
| Frontend type errors | Use `npm run build` not `npm run dev` (dev doesn't check types) |
| WebSocket CORS | `forge.websocket.allowed-origins` must be a comma-separated string |
| Enum serialization | All enums must add `@JsonValue` returning lowercase |
| Empty string vs null | Use `isNullOrBlank()` instead of `?: default` |
| workspaceId propagation | REST API and WebSocket are two independent paths — both require workspaceId |

---

## Documentation Index

| Document | Path | Description |
|----------|------|-------------|
| Product Feature List | `docs/product/feature-list.md` | Complete feature description (user-facing) |
| Design Baseline | `docs/baselines/design-baseline-v1.md` | Validated UI/API/data model baseline |
| Planning Baseline | `docs/baselines/planning-baseline-v1.5.md` | Design-driven planning document |
| Development Logbook | `docs/planning/dev-logbook.md` | Complete session-by-session development records |
| Architecture Overview | `docs/architecture/overview.md` | System architecture documentation |
| Trial Guide | `docs/product/TRIAL-GUIDE.md` | Internal trial operation manual |
| Eval Handbook | `docs/guides/eval-handbook.md` | Complete eval usage guide (why, what, how) |
| Eval Value Paths | `docs/acceptance-tests/forge-eval-value-paths.md` | E2E test procedures for 5 eval value paths |
| Acceptance Tests | `docs/acceptance-tests/` | Phase-by-phase acceptance test reports |
| Bug List | `docs/analysis/buglist.md` | Known issue tracking |

---

## Contributing

Contributions are welcome! Here's how you can participate:

- **Submit Issues**: Report bugs, suggest features, or propose improvements
- **Submit Pull Requests**: Fix bugs, improve documentation, add new Skills
- **Extend Skills**: Add new Skills or Profiles in the `plugins/` directory
- **Enrich Knowledge Base**: Add documentation in the `knowledge-base/` directory

Before submitting a PR, please ensure:
1. All unit tests pass: `./gradlew :web-ide:backend:test`
2. Frontend builds without errors: `cd web-ide/frontend && npm run build`
3. Code follows existing style conventions (see `plugins/forge-foundation/skills/kotlin-conventions/`)

> **Note**: By submitting a contribution, you agree to license your contribution to The Forge Contributors as described in the [LICENSE](LICENSE).

---

## License

This project is licensed under the [Forge Source Available License v1.0](LICENSE).

- ✅ Permitted: View source, personal learning, non-commercial use and modification, submit contributions
- ❌ Restricted: Commercial use (SaaS, internal business tooling, consulting services) requires written authorization

Commercial licensing inquiries: https://github.com/pan94u/forge/issues

---

<div align="center">

**Forge — Making AI truly participate in software delivery, not just assist with coding**

</div>
