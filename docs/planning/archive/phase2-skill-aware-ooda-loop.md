# Plan: Phase 2 — Skill-Aware OODA Loop（单环运作核心）

## Context

Phase 1.5 已完成 Docker 部署验证 + 设计基线冻结。当前 `ClaudeAgentService` 有一个**静态 system prompt**（14 行通用描述）和工作的 agentic loop（5 轮 tool calling）。Phase 2 的核心目标是让这个 loop 变得**智能**——根据用户意图动态加载 Skill Profile 和 Foundation Skills，组装上下文感知的 system prompt，实现真正的 OODA 单环运作。

**当前状态**：
- `ClaudeAgentService.kt` 第 42-54 行：硬编码的 `SYSTEM_PROMPT`（通用 AI 助手描述）
- `plugins/forge-superagent/skill-profiles/` 下有 5 个 Profile（planning/design/development/testing/ops）
- `plugins/forge-foundation/skills/` 下有 16 个 Foundation Skill（kotlin-conventions, spring-boot-patterns 等）
- `plugins/forge-superagent/skills/` 下有 8 个 Delivery Skill（code-generation, architecture-design 等）
- `plugins/forge-superagent/CLAUDE.md` 有完整的 Profile 路由逻辑规范（优先级链 1-4）

**目标**：用户发送消息 → ProfileRouter 检测意图 → SkillLoader 加载对应 Skills → SystemPromptAssembler 组装动态 prompt → ClaudeAgentService 使用该 prompt 调用 Claude API。

## 架构设计

### 集成方式：注入而非替换

新组件以 Spring Service 形式注入 `ClaudeAgentService`，不改变现有 agentic loop 的任何逻辑。**唯一修改点**是 `CompletionOptions.systemPrompt` 从静态常量变为动态构建。

```
User Message
    │
    ▼
ProfileRouter.route(message) → ProfileRoutingResult
    │                              ├── profileName: "development-profile"
    │                              ├── confidence: 0.85
    │                              └── reason: "detected '实现' keyword"
    ▼
SkillLoader.loadSkillsForProfile(profile) → List<SkillDefinition>
    │                                         ├── kotlin-conventions
    │                                         ├── spring-boot-patterns
    │                                         └── code-generation
    ▼
SystemPromptAssembler.assemble(profile, skills, phase) → String (dynamic system prompt)
    │
    ▼
ClaudeAgentService.streamMessage(..., systemPrompt=dynamicPrompt)
    │  (existing agentic loop unchanged)
    ▼
Streaming Response
```

### 包结构

```
com.forge.webide.service.skill/
    SkillLoader.kt              — 读取 SKILL.md / profile.md，解析 frontmatter
    ProfileRouter.kt            — 意图检测 → Profile 选择
    SystemPromptAssembler.kt    — Profile + Skills → 完整 system prompt
    SkillModels.kt              — SkillDefinition, ProfileDefinition 等数据类
```

## 实施步骤

### Step 1: SkillModels.kt — 领域模型

**新建** `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/SkillModels.kt`

```kotlin
data class SkillDefinition(
    val name: String,
    val description: String,
    val trigger: String?,
    val tags: List<String>,
    val content: String,          // frontmatter 之后的 markdown body
    val sourcePath: String
)

data class ProfileDefinition(
    val name: String,
    val description: String,
    val skills: List<String>,     // skill names to load
    val baselines: List<String>,
    val hitlCheckpoint: String,
    val oodaGuidance: String,     // markdown body (OODA 指导)
    val sourcePath: String
)

data class ProfileRoutingResult(
    val profile: ProfileDefinition,
    val confidence: Double,
    val reason: String
)
```

### Step 2: SkillLoader.kt — Skill 和 Profile 加载器

**新建** `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/SkillLoader.kt`

核心逻辑：
- 扫描 `plugins/` 目录下所有 `SKILL.md` 和 `skill-profiles/*.md`
- 解析 YAML frontmatter（需处理两种格式）：
  - Skill: 简单 key-value（`name: kotlin-conventions`, `tags: [kotlin, conventions]`）
  - Profile: YAML list（`skills:\n  - code-generation\n  - foundation-skills-all`）
- **参考现有代码**：`AssetFormatAdapter.extractFrontmatter()` (adapters/runtime-adapter) 处理简单 key-value；Profile 的 YAML list 需要增强解析
- 使用 Jackson YAML（已有 `jackson-module-kotlin` 依赖，需添加 `jackson-dataformat-yaml`）进行可靠的 YAML 解析
- 结果缓存在 `ConcurrentHashMap` 中

关键方法：
```kotlin
fun loadSkill(name: String): SkillDefinition?
fun loadProfile(name: String): ProfileDefinition?
fun loadAllFoundationSkills(): List<SkillDefinition>
fun loadSkillsForProfile(profile: ProfileDefinition): List<SkillDefinition>
fun reloadAll()
```

**配置**：`application.yml` 新增 `forge.plugins.base-path: ${FORGE_PLUGINS_PATH:plugins}`

**扫描路径**：
- `{base-path}/forge-foundation/skills/*/SKILL.md` → 16 个 Foundation Skills
- `{base-path}/forge-superagent/skills/*/SKILL.md` → 8 个 Delivery Skills
- `{base-path}/forge-superagent/skill-profiles/*.md` → 5 个 Profiles
- `{base-path}/forge-superagent/CLAUDE.md` → SuperAgent 系统指令

### Step 3: ProfileRouter.kt — Profile 路由

**新建** `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/ProfileRouter.kt`

**路由逻辑**严格遵循 `plugins/forge-superagent/CLAUDE.md` 第 13-48 行的优先级链：

1. **Priority 1: 显式标签** — `@规划`→planning, `@设计`→design, `@开发`→development, `@测试`→testing, `@运维`→ops
2. **Priority 2: 关键词自动检测** — 中英文关键词匹配（已在 CLAUDE.md 中定义完整列表）
3. **Priority 3: Workspace 上下文** — 分支名、文件类型等（Phase 2 简化实现，仅用分支名）
4. **Priority 4: 默认 development-profile**

### Step 4: SystemPromptAssembler.kt — Prompt 组装

**新建** `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/SystemPromptAssembler.kt`

组装结构：
```
[1] SuperAgent 角色定义（从 CLAUDE.md 提取核心段落）
[2] 当前 Profile 的 OODA 指导（profile.oodaGuidance）
[3] 加载的 Skills 内容（每个 skill.content 作为独立 section）
[4] Baseline 执行规则（profile.baselines）
[5] HITL 检查点规则（profile.hitlCheckpoint）
[6] 可用 MCP 工具列表（从 McpProxyService 获取）
```

**Prompt 大小管理**：
- 每个 Skill 内容约 100-350 行，10 个 Skills ≈ 15K-20K tokens
- Claude 200K 上下文窗口，充裕
- 仍需设置上限（如 50K tokens），超出时截断低优先级 Skills

### Step 5: 修改 ClaudeAgentService.kt — 集成

**修改** `web-ide/backend/src/main/kotlin/com/forge/webide/service/ClaudeAgentService.kt`

改动极小：
1. 构造函数注入 `SkillLoader` + `ProfileRouter` + `SystemPromptAssembler`（3 个新依赖）
2. `sendMessage()` 和 `streamMessage()` 中，将 `systemPrompt = SYSTEM_PROMPT` 替换为：
   ```kotlin
   val routing = profileRouter.route(message)
   val skills = skillLoader.loadSkillsForProfile(routing.profile)
   val systemPrompt = systemPromptAssembler.assemble(routing.profile, skills)
   ```
3. 在 `done` 事件中增加 `activeProfile` 和 `loadedSkills` 字段（给前端显示）
4. 保留 `SYSTEM_PROMPT` 作为 fallback（SkillLoader 找不到文件时降级）

### Step 6: 前端 Profile 显示

**修改** `web-ide/frontend/src/components/AiChatSidebar.tsx`

在 `done` 事件中读取新字段，在聊天消息下方显示当前 Profile 和加载的 Skills：
```
🎯 development-profile | kotlin-conventions, spring-boot-patterns, code-generation
```

**修改** `web-ide/backend/src/main/kotlin/com/forge/webide/controller/AiChatController.kt`

新增 2 个 REST 端点：
- `GET /api/skills` → 列出所有可用 Skills
- `GET /api/profiles` → 列出所有可用 Profiles

### Step 7: 测试

**新建** 测试文件（在 `web-ide/backend/src/test/kotlin/com/forge/webide/service/skill/`）：

| 测试文件 | 覆盖内容 |
|---------|---------|
| `SkillLoaderTest.kt` | frontmatter 解析、Skill 加载、Profile 加载、缓存、错误处理 |
| `ProfileRouterTest.kt` | 显式标签路由、关键词检测、默认 fallback、中英文支持 |
| `SystemPromptAssemblerTest.kt` | prompt 组装结构、大小限制、skill 注入 |

遵循现有测试模式：MockK + AssertJ + JUnit 5。

**集成测试**：`SkillLoaderIntegrationTest.kt` 指向真实的 `plugins/` 目录，验证所有 16 Foundation Skills + 5 Profiles 正确加载。

### Step 8: 配置更新

**修改** `web-ide/backend/src/main/resources/application.yml`

```yaml
forge:
  plugins:
    base-path: ${FORGE_PLUGINS_PATH:plugins}
```

**修改** `web-ide/backend/build.gradle.kts`

添加依赖：
```kotlin
implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
```

## 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| **新建** | `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/SkillModels.kt` | 领域模型 |
| **新建** | `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/SkillLoader.kt` | Skill/Profile 加载器 |
| **新建** | `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/ProfileRouter.kt` | Profile 路由 |
| **新建** | `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/SystemPromptAssembler.kt` | Prompt 组装器 |
| **修改** | `web-ide/backend/src/main/kotlin/com/forge/webide/service/ClaudeAgentService.kt` | 注入新服务，替换静态 prompt |
| **修改** | `web-ide/backend/src/main/kotlin/com/forge/webide/controller/AiChatController.kt` | 新增 /api/skills, /api/profiles 端点 |
| **修改** | `web-ide/frontend/src/components/AiChatSidebar.tsx` | 显示当前 Profile 和 Skills |
| **修改** | `web-ide/backend/build.gradle.kts` | 添加 jackson-dataformat-yaml |
| **修改** | `web-ide/backend/src/main/resources/application.yml` | 添加 plugins 配置 |
| **新建** | `web-ide/backend/src/test/kotlin/com/forge/webide/service/skill/SkillLoaderTest.kt` | 单元测试 |
| **新建** | `web-ide/backend/src/test/kotlin/com/forge/webide/service/skill/ProfileRouterTest.kt` | 单元测试 |
| **新建** | `web-ide/backend/src/test/kotlin/com/forge/webide/service/skill/SystemPromptAssemblerTest.kt` | 单元测试 |
| **新建** | `web-ide/backend/src/test/kotlin/com/forge/webide/service/skill/SkillLoaderIntegrationTest.kt` | 集成测试 |

共 8 个新建 + 5 个修改 = 13 个文件。

## 暂不实现（本次 scope 外）

| 项目 | 原因 |
|------|------|
| OodaLoopController（OODA 状态机） | Phase 2 第一步先实现 profile-aware prompt，OODA 状态跟踪在第二步迭代 |
| 3 个新 Baseline 脚本实现 | 属于 CI/CD 集成，不阻塞 Skill-aware loop 核心 |
| 3 个新 Foundation Skill 创建 | 先确保 SkillLoader 正确加载现有 24 个 Skill |
| MCP Server 增强 | 现有 stub tools 足够验证 agentic loop |
| 跨栈迁移 PoC | 需要 Skill-aware loop 先跑通 |
| agent-eval 增强 | 需要 OODA loop 运行数据 |

## 验证方式

1. **单元测试通过**：所有新增测试 pass
2. **集成测试通过**：SkillLoader 从真实 `plugins/` 加载 16 + 8 + 5 = 29 个文件
3. **编译通过**：`./gradlew :web-ide:backend:build` 无错误
4. **运行时验证**：
   - 启动 backend → 日志显示 "Loaded X skills, Y profiles"
   - 发送消息 "帮我写一个 Kotlin 的订单服务" → ProfileRouter 路由到 development-profile
   - 发送消息 "@设计 设计一个支付系统架构" → ProfileRouter 路由到 design-profile
   - `done` 事件中包含 `activeProfile` 和 `loadedSkills` 字段
5. **前端验证**：聊天消息下方显示当前 Profile 标签
