---
name: development-profile
description: "Skill Profile for the Development delivery stage. Loads core coding skills plus keyword-triggered domain/foundation skills on demand."
skills:
  - delivery-methodology
  - code-generation
  - bug-fix-workflow
  - kotlin-conventions
  - spring-boot-patterns
  - database-patterns
  - testing-standards
  - error-handling
  - api-design
baselines:
  - code-style-baseline
  - security-baseline
hitl-checkpoint: "Code Review — submit PR with all changes for human review before merge."
---

# Development Profile — OODA Guidance

## Overview

The Development Profile is the most detailed and frequently used profile. It activates when the SuperAgent enters the Development delivery stage. The primary objective is to generate high-quality, convention-compliant, tested code that implements the design.

This profile enforces two baselines:
- **code-style-baseline**: Validates code style via ktlint/checkstyle
- **security-baseline**: Scans for security vulnerabilities (hardcoded secrets, injection, XSS)

Additionally, this profile dynamically loads:
- **All relevant Foundation Skills**: language conventions, framework patterns, testing standards
- **Relevant Domain Skills**: domain-specific patterns for the modules being modified

---

## Observe

Deeply understand what needs to be coded:

1. **Read the design documents**:
   - Architecture design (C4 diagrams, component responsibilities)
   - Detailed design (class diagrams, sequence diagrams)
   - API contracts (OpenAPI specs)
   - Database schema designs
   - ADRs that constrain implementation choices

2. **Read the existing codebase**:
   - Module structure and package organization
   - Existing patterns and conventions (how similar features are implemented)
   - Dependency injection setup
   - Configuration management approach
   - Error handling patterns
   - Logging conventions

3. **Load Foundation Skills** (auto-detected):
   - Scan `build.gradle.kts` / `pom.xml` → load Kotlin/Java conventions
   - Scan for `@SpringBootApplication` → load Spring Boot patterns
   - Scan for test framework → load testing standards (JUnit 5, Kotest, etc.)
   - Scan for `Dockerfile` → load Docker patterns
   - Load git conventions always

4. **Load Domain Skills** (context-detected):
   - Identify which domain modules the task touches
   - Query `forge-context-server.getProjectStructure()` for module boundaries
   - Load domain skill files for each relevant module
   - Note domain-specific validation rules and business logic patterns

5. **Query MCP servers**:
   - `forge-context-server.getRecentChanges(7)` — recent changes that might conflict
   - `forge-context-server.getConventions()` — project conventions
   - `forge-knowledge-server.searchKnowledge(feature)` — related past implementations
   - `forge-metrics-server.getCoverage(module)` — current coverage to understand baseline

6. **Check current branch state**:
   - `git status` — any uncommitted changes?
   - `git log` — recent commits on this branch
   - Are there open PRs that might conflict?

### Observe Checklist
- [ ] Design documents fully read
- [ ] Existing code patterns understood
- [ ] Foundation Skills loaded for detected stack
- [ ] Domain Skills loaded for affected modules
- [ ] MCP server context queried
- [ ] Git state verified clean

---

## Orient

Form a coding plan:

1. **Load conventions and standards**:
   - Code style rules from Foundation Skills
   - Naming conventions (classes, methods, variables, packages)
   - File organization patterns
   - Import ordering rules
   - Documentation requirements (KDoc, Javadoc)

2. **Map design to code**:
   - Design components → Kotlin/Java classes
   - Sequence diagrams → method call chains
   - API contracts → controller endpoints + DTOs
   - Database schema → entity classes + repositories
   - Error scenarios → exception hierarchy

3. **Identify what to create vs. modify**:
   - New files that need to be created
   - Existing files that need modification
   - Files that should NOT be modified (stability boundary)
   - Shared utilities that might be reusable

4. **Plan the test strategy**:
   - Unit tests for every new service method
   - Integration tests for every new controller endpoint
   - Contract tests for external API calls
   - Test data setup and teardown

5. **Identify risks**:
   - Complex business logic that needs extra validation
   - Performance-sensitive code paths
   - Security-sensitive operations (auth, data access)
   - Areas with poor existing test coverage

6. **Determine implementation order**:
   - Data layer first (entities, repositories)
   - Domain/service layer second (business logic)
   - API layer third (controllers, DTOs)
   - Tests alongside each layer
   - Configuration and wiring last

### Orient Output
A detailed coding plan with file list, implementation order, and test strategy.

---

## Decide

Make implementation decisions:

1. **Coding approach**:
   - Incremental implementation (layer by layer) vs. feature slice (vertical)
   - For complex features: prefer layer-by-layer with tests at each layer
   - For simple features: prefer vertical slices

2. **Design pattern choices**:
   - Which patterns from Foundation Skills apply?
   - Which Domain Skill patterns to follow?
   - Where to apply SOLID principles
   - Where to use functional vs. OOP style

3. **Error handling strategy**:
   - Exception types to use or create
   - Error response format (following API contract)
   - Retry logic for external calls
   - Circuit breaker configuration

4. **Test decisions**:
   - Test framework and assertion library
   - Mocking strategy (what to mock, what to use real)
   - Test data generation approach
   - Integration test infrastructure (testcontainers, embedded DB)

5. **PR structure**:
   - Single PR or split into multiple?
   - Commit organization (logical commits)
   - PR description content

### Decide Output
Concrete implementation decisions ready for execution.

---

## Act

Generate the code:

### ⚠️ STOP: Planning Mode Gate

**在执行任何代码生成之前，必须先判断是否触发 Planning Mode。**

- 需求中含"实现/重构/完成/做/开发/加入/升级"等大范围词汇 → **必须**先调用 `plan_create`
- 涉及 3+ 文件或预计 100+ 行改动 → **必须**先调用 `plan_create`
- 需求模糊 → **必须**先调用 `plan_ask_user`

> **🚫 禁止在 `plan_create` 返回 "Plan approved" 之前调用 `workspace_write_file`**

如已确认是小任务（单文件 / <50 行），可跳过此检查，直接执行以下步骤。

### Step 1: Data Layer
1. Create/modify entity classes matching the database schema design
2. Create/modify repository interfaces
3. Write database migration scripts if needed
4. Write unit tests for custom repository methods

### Step 2: Domain/Service Layer
1. Create/modify service classes implementing business logic
2. Follow the sequence diagrams from detailed design
3. Implement error handling per the error strategy
4. Apply Domain Skill patterns for business rules
5. Write comprehensive unit tests:
   - Happy path for every public method
   - Error paths for every exception scenario
   - Edge cases identified in Orient phase
   - Use meaningful test names that describe behavior

### Step 3: API Layer
1. Create/modify controller classes matching the API contract
2. Create/modify DTO classes for request/response
3. Implement validation annotations on DTOs
4. Implement authentication/authorization per endpoint
5. Write integration tests:
   - Every endpoint with valid input
   - Every endpoint with invalid input (validation errors)
   - Authentication/authorization scenarios
   - Use MockMvc or WebTestClient

### Step 4: Configuration
1. Add configuration properties
2. Update application.yml / application.properties
3. Add feature flags if needed
4. Update dependency injection configuration

### Step 5: Build & Verify (标准交付流程)

> **Baseline 豁免条件**：以下场景**跳过** baseline 执行：
> - 纯 git 操作（status / diff / log / pull / push）
> - 只读查询（代码阅读、知识检索、文档生成）
> - 用户明确说明无需验证的场景

执行以下完整交付流程，每步使用对应工具：

1. **编码** → `workspace_write_file`（写入代码文件）
2. **编译** → `workspace_compile`（验证编译/语法检查通过）
3. **底线** → 自动运行 `code-style-baseline` + `security-baseline` 检查
4. **测试** → `workspace_test`（运行测试分析）
5. **[HITL]** → 暂停，等待用户审查代码和测试结果
6. **总结** → 输出执行报告（文件清单 + 编译结果 + 测试结果 + 底线结果）

如果编译失败，修复后重新编译。如果底线失败，修复后重新检查。循环直到全部通过或达到最大轮次。

**详细验证步骤**:
1. **Compile check**: 调用 `workspace_compile` 确保无语法错误
2. **Run tests**: 调用 `workspace_test` 分析测试覆盖
3. **Run baselines**:
   - `code-style-baseline.sh` — fix any style violations
   - `security-baseline.sh` — fix any security issues
4. **On failure**:
   - Read the failure details carefully
   - Loop back to Observe with failure context
   - Fix the specific issue
   - Run verification again
   - Maximum 3 fix loops before escalating to human

### Step 6: Prepare PR
1. Organize commits logically
2. Write clear commit messages
3. Prepare PR description:
   - What changed and why
   - Design decisions made
   - Test coverage summary
   - Screenshots if UI changes
4. Present at HITL checkpoint

### Act Output
Complete, tested, baseline-passing code ready for code review.

---

## HITL Checkpoint: Code Review

**When**: After all code is generated, tests pass, and baselines pass.

**Present to the user**:
1. Summary of changes (files created/modified)
2. Key implementation decisions
3. Test coverage summary
4. Baseline results (all passing)
5. Any deviations from the design and rationale
6. Open questions or concerns

**Wait for**: Explicit approval to merge, or revision requests.

**On rejection**: Incorporate review feedback, return to **Orient** phase for the specific issues raised.

---

## Foundation Skills Integration

### Always Load
- Git conventions (commit messages, branching)
- Code review standards
- Testing standards for detected language

### Conditionally Load (auto-detected)
| Detected Signal              | Foundation Skill to Load     |
|------------------------------|------------------------------|
| `build.gradle.kts`          | kotlin-conventions           |
| `@SpringBootApplication`    | spring-boot-patterns         |
| `package.json` + React      | react-patterns               |
| `Dockerfile`                | docker-patterns              |
| `*.k8s.yaml`               | kubernetes-patterns          |
| `.github/workflows/*`       | ci-cd-patterns               |
| `pom.xml`                   | java-conventions             |
| `tsconfig.json`             | typescript-conventions       |

### Conflict Resolution
If a Foundation Skill conflicts with a Domain Skill, the **Domain Skill wins** — project-specific decisions override general conventions.

---

## Domain Skills Integration

Domain Skills provide module-specific business logic patterns. They are loaded based on which packages/modules the current task touches.

### Example
If the task involves the `order-service` module:
1. Load `forge-domain-skills/skills/order-management/SKILL.md`
2. Apply order-specific validation rules
3. Follow order state machine patterns
4. Use order-specific error codes

### Domain Skill Signals
- Package imports in affected files
- Module name in file paths
- Design document references to domain concepts
- ADRs mentioning domain-specific patterns

---

## Baseline Details

### code-style-baseline
- Runs `ktlint` for Kotlin files
- Runs `checkstyle` for Java files
- Checks import ordering
- Validates naming conventions
- Exit 1 on any violation with file:line details

### security-baseline
- Scans for hardcoded credentials (passwords, API keys, tokens)
- Checks for SQL injection patterns (string concatenation in queries)
- Validates XSS prevention (output encoding)
- Checks dependency vulnerabilities (if dependency-check configured)
- Exit 1 on any finding with severity and location

