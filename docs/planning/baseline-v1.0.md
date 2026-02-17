# Forge — 面向 AI 时代的智能交付平台

> 规划基线 v1.1 | 基线日期: 2026-02-17
> v1.1 修订：融入跨栈迁移场景、Claude Code 脱离路径、质量改进基线

---

## 一、目标与定位

### 1.1 平台目标

构建一套**面向全软件交付生命周期的智能交付平台**，实现：

1. **交付提效**：通过 SuperAgent + Skill 体系，将 Scrum 5-7 人团队的交付能力压缩到 1-2 人 + AI 协同完成
2. **知识沉淀**：将散落在代码、文档、团队经验中的隐性知识编码为可复用的 Skill 和结构化数据
3. **持续进化**：建立"交付环 + 进化环"双环机制，让平台在使用中自动变好
4. **全员可用**：开发者通过 CLI/IDE 使用，产品团队通过 Web IDE 使用，共享同一套智能和知识
5. **跨栈迁移**：化解"无人懂旧技术栈"的死结，AI 做知识抢救 + 跨语言翻译，目标栈聚焦 Java/Kotlin

### 1.2 平台定位

```
                    不是什么                              是什么
            ─────────────────────────         ─────────────────────────────
            给每个人配一个 AI 助手              重构交付流程本身的智能交付系统
            5 个角色各一个独立 Agent            1 个 SuperAgent 动态切换 Skill
            一次性工具部署                      带进化环的自我改进系统
            仅面向开发者的工具                   面向全交付角色的统一平台
            仅支持同栈开发                      同栈开发 + 跨栈迁移双场景支持
```

### 1.3 核心判断

- **Skill 是未来的核心竞争力**：硅基智能通过 Skill 系统性学习碳基智能的护城河
- **Code is the universal interface**：从"多个专用 Agent"进化为"一个通用 Agent + 多种 Skill"（Anthropic 最新演进方向）
- **双环 > 单环**：只有交付没有进化 = 不会变好；交付 + 进化 = 飞轮效应
- **Runtime 无关是长期战略**：Skill/知识/底线是平台的"稳态"，Agent Runtime（Claude Code / ForgeNative / 其他）是可替换的"敏态"
- **质量底线 > 开发速度**：logbook 证明——38K 行代码中 CORS 通配符直接进 commit。速度不缺（2400 行/小时），缺的是安全网

---

## 二、设计原则

| # | 原则 | 说明 | 反模式 |
|---|------|------|--------|
| 1 | **SuperAgent 优于多 Agent** | 一个智能体通过 Skill 动态切换角色 | 为每个角色建独立 Agent |
| 2 | **Skill 优于 Prompt** | 将专业知识编码为可复用、可组合、可回放的 Skill | 靠临时 Prompt 传递知识 |
| 3 | **底线保障质量下限** | 不管模型怎么变，底线脚本必须通过 | 依赖模型"自觉"保证质量 |
| 4 | **稳态固化，敏态抽象** | Skill/底线/流程不变，模型/Runtime 通过适配器可插拔 | 把 Skill 和模型耦合 |
| 5 | **双环驱动** | 交付环解决"做什么"，进化环解决"越做越好" | 只交付不沉淀 |
| 6 | **每阶段可验证** | 每个交付阶段结束后有可运行的系统和可度量的指标 | 前 12 周全在搭架子 |
| 7 | **双入口统一体验** | CLI 和 Web IDE 共享同一套 SuperAgent + 知识 + MCP | 两套独立系统 |
| 8 | **Runtime 无关** | Skill 内容不引用特定 Runtime 的工具名（Read/Write/Bash） | 在 SKILL.md 里写 `use the Read tool` |
| 9 | **多语言源码理解** | 平台能*读*懂任何语言的源码（.NET/Python/Go），目标*写*聚焦 Java/Kotlin | 只支持单一技术栈的代码理解 |
| 10 | **经验数据驱动** | 用 logbook/执行日志的真实数据校验设计，而非理论推演 | 凭感觉评估提效比 |

---

## 三、整体方案

### 3.1 双环架构模型

源自 Gartner Zero-Friction SDLC + 组织 AI 战略思考：

```
╔══════════════════════════════════════════════════════════════════════════╗
║                                                                        ║
║    环 1：交付环 (Delivery Loop)                                         ║
║                                                                        ║
║    人类意图 ───→  规划 → 设计 → 开发 → 测试 → 运维 → 运营  ───→ 软件产出  ║
║    (声明式)              ↑ HITL 人在回路审批 ↑                (可交付物)  ║
║                                                                        ║
║    ◄───────────────── 反馈 / 新一轮迭代 ──────────────────────┘        ║
║                                                                        ║
╠══════════════════════════════════════════════════════════════════════════╣
║                                                                        ║
║    环 2：进化环 (Learning Loop)                                         ║
║                                                                        ║
║    每次交付 → 生产数据 → 资产提取 → 私域知识沉淀                         ║
║         ↑                                    │                         ║
║         └──── 技能更新 ← 消费数据 ← 强化学习 ←┘                         ║
║                                                                        ║
║    效果：知识库自然增长 / Skill 自动优化 / 底线通过率趋势上升             ║
║                                                                        ║
╚══════════════════════════════════════════════════════════════════════════╝
```

- **环 1（交付环）**：人类指定 WHAT（声明式意图），SuperAgent 解决 HOW（命令式执行），HITL 人在关键节点审批
- **环 2（进化环）**：每次交付产生数据 → 提取为数字资产 → 沉淀到私域知识 → 反哺 SuperAgent → 下次交付更好

### 3.2 SuperAgent + Skill Profile

**核心思路**：不是 5 个独立的角色 Agent，而是 **1 个 SuperAgent** 通过 **Skill Profile** 动态切换能力组合。

```
                       SuperAgent（唯一实例）
                               │
                  Skill Profile Router（自动/手动切换）
                               │
         ┌──────────┬──────────┼──────────┬──────────┐
         ▼          ▼          ▼          ▼          ▼
     规划 Profile  设计 Profile 开发 Profile 测试 Profile 运维 Profile
     ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
     │需求分析 │  │架构设计 │  │代码生成 │  │用例编写 │  │部署运维 │
     │PRD编写  │  │详细设计 │  │+全部    │  │用例执行 │  │K8s模式 │
     │        │  │API设计  │  │Foundation│ │        │  │        │
     └────────┘  └────────┘  │Skills   │  └────────┘  └────────┘
                              └────────┘
                                  +
                          Domain Skills (支付/订单/库存...)
                                  +
                          跨栈迁移 Skills（codebase-profiler-multilang, business-rule-extraction）
```

每个 Profile 定义：加载哪些 Skill → OODA 循环指引 → 底线检查清单 → HITL 审批点。

> Profile 加载的 Skill 集合与 Agent Runtime 无关，Profile 定义的是"能力组合"而非"Runtime 指令"。

### 3.3 SuperAgent OODA 内循环

```
    Observe（观察）─→ Orient（分析）─→ Decide（决策）─→ Act（执行）
        ↑                                                    │
        │              底线检查 ✅ → 进入 HITL 审批            │
        └── 底线检查 ❌ → 自动回到 Observe 修复 ──────────────┘
```

### 3.4 四阶段演进路线

| 阶段 | 关键词 | 团队模型 | Forge 能力 | Runtime |
|------|--------|---------|------------|---------|
| **阶段一** | AI 工具提效 + 跨栈探路 | Scrum 5-7 人 + AI | Foundation Skills + MCP + Web IDE 实连 + 跨栈画像 | Claude Code |
| **阶段二** | 单环运转 + Runtime 初步脱离 | 2-3 人 + SuperAgent | OODA + 底线 + SkillLoader/ProfileRouter in Web IDE | Claude Code + ForgeNativeRuntime (PoC) |
| **阶段三** | 双环闭合 + Runtime 完全抽象 | 1-2 人 + SuperAgent | 进化环 + ForgeNativeRuntime 完整 + RuntimeAdapter 切换 | Claude Code 或 ForgeNative（可选） |
| **阶段四** | 持续进化 + 生态开放 | 1 人 + SuperAgent 全自主 | 多 Runtime 支持 + Skill 生态 + 垂域模型 | 任意 Runtime |

### 3.5 跨栈迁移工作流

> 这是 Forge 的"甜点场景"：提效 10-13x 人天（详见 `simulation-dotnet-to-java-migration.md`）

五步工作流：

1. **AI 代码考古**：codebase-profiler 解析 .sln/.csproj → 项目结构、API 端点、服务摘要、数据库 Schema、领域模型、设计模式识别。产出用业务语言 + Java 对照描述的"项目说明书"，让不懂旧技术栈的人也能审核
2. **业务规则提取**：business-rule-extraction 逐 Service 类分析 → 提取业务规则、边界条件、异常处理、权限规则、数据校验、可配置行为。按领域分组输出。**这是整个迁移最关键的 HITL 审批点**
3. **架构设计**：基于画像和业务规则，设计 Java/Spring Boot 目标架构。.NET 概念 → Java 映射（Controller→@RestController, EF→JPA, DI→Spring DI 等）。按依赖拓扑排列迁移顺序
4. **逐模块迁移**：SuperAgent（development-profile）按业务规则文档逐模块生成 Java 代码（全新编写，非逐行翻译），加载全部 Foundation Skills 保证规范一致性，底线检查全部通过后进入 HITL Code Review
5. **行为等价验证**：API 契约对比（.NET OpenAPI vs Java OpenAPI）+ 业务规则覆盖度检查 + 边界条件测试 + 数据库 Schema 兼容性验证

### 3.6 Runtime 脱离路线

> 详见 `analysis-claude-code-independence.md`

当前状态：大约 60% 的"代码基础设施"是独立的，但 100% 的"智能层"绑定在 Claude Code 上（Skills/Profiles/Hooks/CLAUDE.md）。

**路径 A（CLI）**：Claude Code 强依赖 → Phase 3 后可选
```
Phase 0-2: 开发者 → Claude Code CLI → 加载 Plugin → Skill/Command/Hook → Claude API
Phase 3+:  Claude Code 变为"可选项"而非"必选项"
```

**路径 B（Web IDE）**：Phase 1 已基本独立 → Phase 2 补全智能层 → Phase 3 完全独立
```
Phase 1:   用户 → Web IDE → Spring Boot 后端 → ClaudeAdapter → Claude API（简单 chat proxy）
Phase 2:   + SkillLoader + ProfileRouter → 独立的 Skill 加载和 Profile 切换
Phase 3:   + AgentLoop + HookEngine + ContextBuilder → 完整 ForgeNativeRuntime
```

**ForgeNativeRuntime 需替换的 5 个核心能力**：SkillLoader、ProfileRouter、AgentLoop、HookEngine、ContextBuilder

---

## 四、架构设计

### 4.1 系统架构总览

```
┌─ 用户交互层 ───────────────────────────────────────────────────────┐
│                                                                     │
│  入口 A: CLI / VS Code               入口 B: Forge Web IDE (浏览器)  │
│  (开发者/架构师/测试/运维)              (产品团队/全角色)               │
│                                                                     │
│  Plugins + CLAUDE.md + .mcp.json      Monaco + AI Chat + 知识浏览    │
│                                       + 可视化工作流 + 服务依赖图      │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌─ SuperAgent 层 ──────────▼──────────────────────────────────────────┐
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                  SuperAgent（超级智能体）                       │  │
│  │  Skill Profiles × 5 + Foundation Skills × 10 + Domain Skills  │  │
│  │  Baseline Runner（底线引擎）                                    │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  技能市场 (Skill Market)  ←→  能力市场 (Capability Market / MCP)     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌─ Agent Runtime（统一运行基座）──────────────────────────────────────┐
│  协议 (MCP)  │  工具 (Tool Calling)  │  沙箱  │  部署  │  评测       │
│  安全围栏     │  可观测体系            │  SuperAgent 评估             │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌─ 数据与模型层（进化环数据流）───────────────────────────────────────┐
│  公域模型 (SOTA)         私域数据 (数字化资产)        垂域模型        │
│  Claude Opus/Sonnet      知识库 + 代码画像 + 规范     训练微调模型    │
│  MCP Gateway → Knowledge / Database / ServiceGraph / Artifact       │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 SuperAgent 详细设计

#### 4.2.1 Skill Profile 路由机制

| 信号 | 触发方式 | 示例 |
|------|---------|------|
| **交付阶段** | 工作流引擎自动传入当前阶段 | 处于"开发"阶段 → 自动加载 development-profile |
| **用户上下文** | 用户 `@规划`/`@设计`/`@开发` 显式指定 | 用户输入 `@设计 帮我做架构设计` |
| **任务类型** | AI 根据任务内容自主判断 | 用户说"写个单测" → 自动选择 testing-profile |

#### 4.2.2 各 Skill Profile 的 OODA 行为定义

| 交付阶段 | Profile | Observe | Orient | Decide | Act | HITL 审批 |
|---------|---------|---------|--------|--------|-----|----------|
| **规划** | planning | 读取需求/Issue | 分析可行性 + 查知识库 | 制定方案 | 输出 PRD | PRD 人工确认 |
| **设计** | design | 读取 PRD + 存量架构 | 影响分析（service-graph） | 架构决策 | 输出设计文档 | 架构评审 |
| **开发** | development | 读取设计 + 代码上下文 | 理解规范（foundation skills） | 编码方案 | 生成代码 | Code Review |
| **测试** | testing | 读取需求 + 代码 | 识别边界条件 | 用例设计 | 编写/执行测试 | 测试报告确认 |
| **运维** | ops | 读取部署配置 | 风险评估 | 部署策略 | 执行部署 | 上线审批 |

### 4.3 Skill 体系设计

#### 4.3.1 Skill 分层

| 层级 | 数量 | 说明 |
|------|------|------|
| 交付阶段 Skill | 8 个 | requirement-analysis, prd-writing, architecture-design, detailed-design, code-generation, test-case-writing, test-execution, deployment-ops |
| Foundation Skill | 10 个 | java-conventions, kotlin-conventions, spring-boot-patterns, gradle-build, testing-standards, api-design, database-patterns, error-handling, logging-observability, security-practices |
| Domain Skill | 按业务域扩展 | domain-payment, domain-order, domain-inventory |
| 知识挖掘 Skill | 3 个 | codebase-profiler（多语言支持：Java/Kotlin/.NET/Python/Go 项目结构解析）, convention-miner（跨语言规范提取：源语言规范 → 目标语言映射） |
| 跨栈迁移 Skill | 1 个 | business-rule-extraction（业务规则提取 + 边界条件识别） |

### 4.4 底线体系设计

| 底线脚本 | 适用 Profile | 检查内容 | 阻断级别 |
|---------|-------------|---------|---------|
| `code-style-baseline.sh` | development | ktlint + checkstyle 零违规 | 阻断提交 |
| `security-baseline.sh` | development | 无硬编码凭证、无 SQL 注入、无 XSS | 阻断提交 |
| `test-coverage-baseline.sh` | development, testing | Service 层 ≥ 80%，Controller 有集成测试 | 阻断 merge |
| `api-contract-baseline.sh` | design, development | OpenAPI Spec 与代码一致 | 阻断 merge |
| `architecture-baseline.sh` | design, development | 无跨层调用、依赖方向正确 | 阻断 merge |

### 4.5 适配器层设计

| 适配维度 | 接口 | 当前实现 | 未来可切换 |
|---------|------|---------|----------|
| 模型适配 | `ModelAdapter.kt` | `ClaudeAdapter` (Opus/Sonnet/Haiku) | BedrockAdapter / LocalModelAdapter |
| 资产格式适配 | `AssetFormatAdapter.kt` | SKILL.md v1 格式 | 未来格式版本迁移 |

**Runtime 适配（重点）**：

| 适配维度 | 接口 | 当前实现 | Phase 2 目标 | Phase 3 目标 |
|---------|------|---------|-------------|-------------|
| Runtime 适配 | `RuntimeAdapter.kt` | 空壳接口 | ForgeNativeRuntime PoC（SkillLoader + ProfileRouter） | ForgeNativeRuntime 完整（AgentLoop + HookEngine + ContextBuilder） |

### 4.6 进化环设计

```
SuperAgent 每次执行
    │
    ▼
execution-logger（执行日志采集）
    │  记录：输入/输出/Skill 选择/底线结果/MCP 调用链/耗时
    ▼
asset-extractor（资产提取）
    │  ├─ 发现新代码规范 → 更新 convention Skill
    │  ├─ 发现知识空白 → 触发 doc-generator 补文档
    │  ├─ 发现 Skill 效果差 → 标记需人工优化
    │  └─ 发现新领域模式 → 更新 domain-* Skill
    ▼
knowledge-base/（私域知识沉淀）
    │
    ▼
skill-feedback-analyzer（Skill 效果分析）
    │  生成优化建议 → 人工 Review → 合入 Skill
    ▼
SuperAgent 下次执行时自动获得更好的知识和技能
```

### 4.7 ForgeNativeRuntime 组件设计

| 组件 | 职责 | 优先级 |
|------|------|--------|
| SkillLoader | 读取 SKILL.md → 按 frontmatter trigger 条件 → 拼接到 system prompt | Phase 2 |
| ProfileRouter | 解析用户意图 → 选择 Profile → 加载对应 Skill 集 | Phase 2 |
| AgentLoop | 消息 → 模型 → tool_use → 执行 → tool_result → 循环（OODA） | Phase 3 |
| HookEngine | 在 tool 调用前后执行检查脚本 | Phase 3 |
| ContextBuilder | 读取三级上下文 .md 文件 → 合并 → 注入 system prompt | Phase 3 |

四个胶水机制：

| 机制 | 触发方式 | 效果 |
|------|---------|------|
| **知识写入通道** | Skill/Agent 产出知识时 | 结构化内容 → Wiki；草稿 → knowledge-base/ Git → 人工 Review |
| **知识空白检测** | AI Chat 搜不到答案时 | 自动记录空白 → 触发 doc-generator → 提交文档 PR |
| **规范同步闭环** | 每月定时 CI | convention-miner 扫描 → diff 实际规范 vs Skill → 自动提 PR 更新 |
| **Domain Skill 保鲜** | 每次 PR merge | 检查被修改代码是否命中 domain-*.SKILL.md 引用 → 通知 Review |

---

## 五、关键设计

### 5.1 MCP Server 清单

| MCP Server | 端口 | 核心 Tool | 用途 |
|-----------|------|----------|------|
| `forge-knowledge-mcp` | 8081 | wiki_search / adr_search / runbook_search / api_doc_search / page_create / gap_log | 知识库读写 + 空白检测 |
| `forge-database-mcp` | 8082 | schema_inspect / query_execute (SELECT only) / data_dict_search | 数据库元信息查询 |
| `forge-service-graph-mcp` | 8083 | service_list / service_dependencies / impact_analysis / call_chain / owner | 服务依赖图谱 + 影响分析 |
| `forge-artifact-mcp` | 8084 | dependency_search / vulnerability_scan / version_recommend | 制品库 + CVE 扫描 |
| `forge-observability-mcp` | 8085 | log_search / metrics_query / trace_search | 日志/指标/链路查询 |

### 5.2 Forge Web IDE 技术选型

| 层 | 技术 | 理由 |
|---|---|---|
| 前端框架 | React 19 + Next.js 15 (App Router) | 成熟 IDE 组件生态 |
| 代码编辑 | Monaco Editor | VS Code 内核 |
| UI 组件 | shadcn/ui + TailwindCSS | 快速开发 |
| 状态管理 | Zustand + React Query | 客户端 + 服务端缓存 |
| 可视化画布 | React Flow (xyflow) | 工作流编辑 |
| 架构图 | Mermaid.js | 渲染架构图 |
| 后端 | Spring Boot 3 (Kotlin) + WebSocket | 与组织技术栈一致 |
| AI 引擎 | Claude Agent SDK | 流式响应 + MCP 集成 |
| AI Runtime | ForgeNativeRuntime (Phase 2+) | 自建 Agent Loop，不依赖 Claude Code CLI |

### 5.3 CLAUDE.md 三级模板

| 级别 | 路径 | 内容 |
|------|------|------|
| 组织级 | `~/.claude/CLAUDE.md` | 语言规范、构建命令、架构规则、安全红线 |
| 团队级 | 团队插件 | 领域上下文、模块结构、上下游依赖 |
| 项目级 | 项目根 `CLAUDE.md` | Quick Start、关键决策、Gotchas |

### 5.4 跨栈迁移关键设计

**codebase-profiler 多语言解析策略**：
- .sln/.csproj → 模块结构和项目依赖图
- C# AST → API/Service/Entity/Config 提取
- 支持 .NET/Python/Go 项目结构解析（读懂任何语言，写聚焦 Java/Kotlin）

**business-rule-extraction 输出格式**：
- 按领域分组（Order/Payment/Inventory/...）
- 逐方法提取：业务规则 + 边界条件 + 异常处理
- 标记规则来源（源码行号 + 方法签名）

**概念映射表模板**：

| .NET | Java/Spring |
|------|-------------|
| Controller | @RestController |
| Service | @Service |
| DbContext / EF | Spring Data JPA |
| DI (Constructor) | Spring DI (构造器注入) |
| Options Pattern | @ConfigurationProperties |
| Middleware | Filter / Interceptor |
| FluentValidation | Jakarta Validation |

**行为等价性验证**：
- API 契约对比（.NET OpenAPI vs Java OpenAPI）
- 业务规则覆盖度检查（逐条规则 → 对应测试用例）
- 边界条件测试（从源码 catch/throw 提取的边界）
- 数据库 Schema 兼容性验证

---

## 六、项目仓库结构

```
forge-platform/                          # Gradle Monorepo (Kotlin DSL)
├── settings.gradle.kts
├── build.gradle.kts
├── CLAUDE.md
│
├── plugins/
│   ├── forge-foundation/                # 核心 Foundation 插件
│   │   ├── .claude-plugin/plugin.json
│   │   ├── .mcp.json
│   │   ├── skills/                      # 15 Skills (10 Foundation + 2 Mining + 3 Domain)
│   │   ├── commands/                    # 6 Commands
│   │   ├── agents/                      # 4 Agents
│   │   └── hooks/                       # Security hooks
│   │
│   ├── forge-superagent/                # SuperAgent 插件（双环架构核心）
│   │   ├── CLAUDE.md                    # SuperAgent 系统指令
│   │   ├── skill-profiles/              # 5 Skill Profiles
│   │   ├── skills/                      # 8 交付阶段 Skills
│   │   ├── baselines/                   # 底线脚本 + runner
│   │   └── learning-loop/               # 进化环组件
│   │
│   ├── forge-knowledge/                 # 知识集成插件
│   ├── forge-deployment/                # 部署运维插件
│   └── forge-team-templates/            # 团队模板
│
├── mcp-servers/
│   ├── forge-mcp-common/                # 共享基础库
│   ├── forge-knowledge-mcp/             # 知识库 MCP
│   ├── forge-database-mcp/              # 数据库 MCP
│   ├── forge-service-graph-mcp/         # 服务图谱 MCP
│   ├── forge-artifact-mcp/              # 制品库 MCP
│   └── forge-observability-mcp/         # 可观测性 MCP
│
├── web-ide/
│   ├── frontend/                        # React / Next.js 15
│   └── backend/                         # Spring Boot 3 (Kotlin)
│
├── cli/                                 # Forge CLI
├── adapters/                            # 适配器层
├── agent-eval/                          # 评估体系
├── skill-tests/                         # Skill 验证框架
├── knowledge-base/                      # 知识沉淀层
├── managed-config/                      # 企业托管配置
├── .github/workflows/                   # CI/CD
├── docs/                                # 文档
└── infrastructure/                      # Docker + K8s
```

---

## 七、分阶段实施计划

### Phase 0：基础骨架（第 1-3 周）— ✅ 已完成

**目标**：搭建项目骨架，交付首批 3 个可用 Skill + 知识库 MCP，让 5 名种子用户能在真实项目中使用。

| # | 交付物 | 说明 |
|---|--------|------|
| 1 | Gradle monorepo 脚手架 | 所有模块的 build.gradle.kts + settings.gradle.kts |
| 2 | `forge-mcp-common` 共享库 | McpServerBase + AuthProvider + HealthCheck |
| 3 | `forge-foundation` 插件 + 3 个 Foundation Skill | java-conventions + kotlin-conventions + testing-standards |
| 4 | `forge-knowledge-mcp` Server | wiki_search + adr_search + page_create + gap_log |
| 5 | `/forge-init` 命令 | 一键初始化项目 |
| 6 | 3 级 CLAUDE.md 模板 | 组织级 + 团队级 + 项目级 |
| 7 | `codebase-profiler` Skill | 存量系统画像 |
| 8 | `convention-miner` Skill | 规范提取 |
| 9 | `knowledge-base/` 仓库 | 知识存储 |
| 10 | CI workflow | ci-skills.yml |
| 11 | `forge-superagent` 插件骨架 | development-profile 先行 |
| 12 | 底线脚本 MVP | code-style + security + baseline-runner |

**验收标准**：
- [x] 5 名种子用户运行 `forge init`，Claude Code 自动加载 3 个 Skill
- [x] 编写 Java/Kotlin 代码时，conventions Skill 自动触发
- [x] `codebase-profiler` 对 3 个核心系统生成画像
- [x] `forge-knowledge-mcp` 部署可用
- [x] 底线脚本在 CI 中运行通过

### Phase 1：Web IDE 实连 + 跨栈基础能力（第 4-8 周）

**目标**：Web IDE 与 Claude API 真实连通；codebase-profiler 支持 .NET 源码理解；4 个 Foundation Skill 深化。

| # | 关键交付物 | 说明 |
|---|-----------|------|
| 1 | ClaudeAdapter Tool Calling 支持 | Models + StreamEvent + tool_use 解析 |
| 2 | ClaudeAgentService 真流式 + Agentic Loop | 替代假流式 |
| 3 | MCP Proxy 端点修复 | `/tools/list` → `/mcp/tools/list` |
| 4 | 聊天数据库持久化 | JPA + Flyway + H2/PostgreSQL |
| 5 | codebase-profiler 多语言扩展 | 增加 .NET/.sln/.csproj/C# 解析指引 |
| 6 | 4 个 Foundation Skill 深化 | spring-boot-patterns, api-design, database-patterns, error-handling |
| 7 | business-rule-extraction Skill 初版 | 业务规则提取 + 边界条件识别 |
| 8 | 核心测试 | 单元测试 + 集成测试 |

**验收标准**：
- [ ] Web IDE 发送消息 → 收到 Claude API 真实 streaming token（无 20ms 间隔）
- [ ] Tool calling agentic loop 工作（最多 5 轮）
- [ ] codebase-profiler 能对 .NET 项目生成有效画像
- [ ] 底线脚本在 CI 中运行通过
- [ ] 10-15 名 CLI 试点用户

### Phase 2：单环运转 + Runtime 初步脱离（第 9-14 周）

**目标**：交付环在开发阶段完整运转；Web IDE 后端具备独立的 Skill 加载和 Profile 切换能力；跨栈迁移流程验证。

| # | 关键交付物 | 说明 |
|---|-----------|------|
| 1 | SuperAgent development-profile 完整实现 | OODA + 底线 + HITL |
| 2 | SkillLoader.kt | 读取 SKILL.md → 注入 system prompt |
| 3 | ProfileRouter.kt | 根据用户意图选择 Profile |
| 4 | design-profile + testing-profile | 扩展到设计和测试阶段 |
| 5 | forge-service-graph-mcp + forge-database-mcp 完善 | MCP Server 增强 |
| 6 | convention-miner 跨语言增强 | 源语言规范 → 目标语言映射 |
| 7 | 全部 10+ Foundation Skill | 补齐剩余 Skill |
| 8 | 端到端跨栈迁移 PoC | 小规模 .NET → Java |
| 9 | agent-eval 评估体系 | SuperAgent 评估框架 |
| 10 | 度量基线采集 | 效率/质量基线数据 |

**验收标准**：
- [ ] SuperAgent OODA 循环运转，底线一次通过率 ≥ 70%
- [ ] Web IDE 后端 SkillLoader 独立加载 Skill（不依赖 Claude Code）
- [ ] 跨栈迁移 PoC：小型 .NET 模块 → Java，业务规则覆盖率 ≥ 90%
- [ ] Web IDE 可访问：SSO → 知识搜索 → AI 对话 → Skill 感知

### Phase 3：双环闭合 + Runtime 完全抽象（第 15-20 周）

**目标**：进化环闭合；ForgeNativeRuntime 完整；Claude Code 变为"可选"。

| # | 关键交付物 | 说明 |
|---|-----------|------|
| 1 | AgentLoop.kt | 完整 OODA 循环 + tool calling |
| 2 | HookEngine.kt | 底线检查集成 |
| 3 | ContextBuilder.kt | 三级上下文合并 |
| 4 | RuntimeAdapter 完整实现 + 配置切换 | Claude Code ↔ ForgeNative 一行配置 |
| 5 | 进化环组件 | execution-logger, asset-extractor, skill-feedback-analyzer |
| 6 | convention-sync / doc-generation / domain-skill-staleness CI | 四个胶水机制 |
| 7 | planning-profile + ops-profile | 补齐全部 5 个 Profile |
| 8 | Top 3 Domain Skills | 按业务域扩展 |
| 9 | Web IDE 代码浏览 + 可视化工作流 | Web IDE 功能完善 |
| 10 | 50 名开发者 CLI 部署 | 规模化推广 |

**验收标准**：
- [ ] ForgeNativeRuntime 在 Web IDE 中端到端运行（不依赖 Claude Code CLI）
- [ ] RuntimeAdapter 配置切换：Claude Code ↔ ForgeNative 一行配置
- [ ] 知识库增长率 ≥ 5 条/周
- [ ] 底线一次通过率 ≥ 80%
- [ ] PR 周期缩短 ≥ 20%，审查时间缩短 ≥ 30%

### Phase 4：全面上线 + 持续进化（第 21 周+）

**目标**：CLI 全组织部署，Web IDE 全开放，SuperAgent 全 5 Profile，持续进化。

**方向**：
- 多 Runtime 支持（Claude Code / ForgeNative / 其他框架）
- 跨栈迁移工具链成熟化
- Skill 生态开放（组织外共享）
- 月度质量对比报告（vs logbook 基线数据）
- CI/CD 深度集成、垂域模型探索

**验收标准**：
- [ ] CLI 周活 ≥ 60% 开发者
- [ ] Web IDE 产品团队周活 ≥ 50%
- [ ] 底线一次通过率 ≥ 85%
- [ ] PR 周期缩短 ≥ 30%，审查时间缩短 ≥ 40%
- [ ] 生产 Bug 率下降 ≥ 15%

---

## 八、实施进度基线

### 基线快照 (2026-02-17)

| 模块 | 文件数 | 状态 | 说明 |
|------|--------|------|------|
| Root (build, settings, CLAUDE.md) | 6 | ✅ 完成 | Gradle monorepo 骨架 |
| mcp-servers/forge-mcp-common | 7 | ✅ 完成 | McpServerBase + Auth + Health + Metrics + Audit + Protocol |
| mcp-servers/forge-knowledge-mcp | 9 | ✅ 完成 | 6 tools: WikiSearch, AdrSearch, RunbookSearch, ApiDocSearch, PageCreate, GapLog |
| mcp-servers/forge-database-mcp | 8 | ✅ 完成 | 3 tools + QuerySanitizer + AccessControl |
| mcp-servers/forge-service-graph-mcp | 12 | ✅ 完成 | 5 tools + 4 indexers |
| mcp-servers/forge-artifact-mcp | 6 | ✅ 完成 | 3 tools: DependencySearch, VulnerabilityScan, VersionRecommend |
| mcp-servers/forge-observability-mcp | 6 | ✅ 完成 | 3 tools: LogSearch, MetricsQuery, TraceSearch |
| plugins/forge-foundation | ~30 | ✅ 完成 | plugin.json + Skills/commands/agents/hooks |
| plugins/forge-superagent | 24 | ✅ 完成 | CLAUDE.md + 5 profiles + 8 skills + 5 baselines + runner + 3 learning-loop |
| plugins/forge-knowledge | 8 | ✅ 完成 | 3 commands + 3 skills |
| plugins/forge-deployment | 5 | ✅ 完成 | 2 skills + 2 commands |
| plugins/forge-team-templates | 3 | ✅ 完成 | 3 team CLAUDE.md templates |
| web-ide/frontend | 25 | ✅ 完成 | Next.js 15 + all components + lib |
| web-ide/backend | 16 | ✅ 完成 | Spring Boot 3 + controllers + services + WebSocket |
| cli | 7 | ✅ 完成 | 5 commands + main + build |
| adapters | 9 | ✅ 完成 | ModelAdapter + RuntimeAdapter + implementations |
| agent-eval | 11 | ✅ 完成 | EvalRunner + Reporter + 6 eval sets |
| skill-tests | 4 | ✅ 完成 | Validator + Parser + Runner |
| knowledge-base | 4 | ✅ 完成 | Directory structure + README |
| managed-config | 2 | ✅ 完成 | managed-settings.json + managed-mcp.json |
| .github/workflows | 6 | ✅ 完成 | 6 CI workflows |
| docs | 4+ | ✅ 完成 | baseline + 分析文档 |
| infrastructure | 2+ | ✅ 完成 | Docker + K8s 配置 |
| **总计** | **244+** | | **38,600+ 行** |

### 关键里程碑

| 里程碑 | 计划日期 | 状态 |
|--------|---------|------|
| Phase 0 骨架完成 | Week 1 | ✅ 完成（244 文件，38,600+ 行） |
| Phase 0 → Phase 1 转换 | Week 3 | ✅ 完成（三轮设计验证 + 基线更新） |
| Phase 1 Web IDE 实连 | Week 8 | ⏳ 待开始 |
| Phase 2 单环运转 + Runtime 脱离 | Week 14 | ⏳ 待开始 |
| Phase 3 双环闭合 + 完全抽象 | Week 20 | ⏳ 待开始 |
| Phase 4 全面上线 | Week 21+ | ⏳ 待开始 |

---

## 九、验证方式

| 验证对象 | 验证方法 | 命令/操作 |
|---------|---------|----------|
| Skill 结构 | 自动化校验 | `./gradlew :skill-tests:test` |
| MCP Server | 构建 + 集成测试 | `./gradlew :mcp-servers:forge-knowledge-mcp:test` |
| CLI | 单测 + 端到端 | `./gradlew :cli:test` + `forge doctor` |
| Web IDE 前端 | 单测 + E2E | `cd web-ide/frontend && npm test` + Playwright |
| Web IDE 后端 | 集成测试 | `./gradlew :web-ide:backend:test` |
| SuperAgent OODA | 手动测试 | 给定任务 → 观察 OODA → 检查底线 |
| 底线体系 | CI 运行 | PR 提交自动触发 baseline-runner |
| 进化环健康度 | 评估框架 | `./gradlew :agent-eval:run` |

---

## 十、价值度量体系

| 层 | 时间线 | 指标 | 目标 |
|---|-------|------|------|
| **活跃度** | 上线即采集 | 日活率 / Skill 触发次数 / MCP 调用量 / Web IDE 会话 | 4 周 60%+ 渗透 |
| **效率** | 4-8 周对比 | PR 周期 / 审查时间 / 新人上手 / Bug 修复时间 | 缩短 30-60% |
| **质量** | 8-12 周趋势 | 生产 Bug 率 / 规范一致性 / 测试覆盖 / 架构合规 | 持续改善 |
| **迁移效率** | 迁移项目启动时 | 跨栈迁移人天对比（传统 vs Forge） | 10x+ 人天提效 |
| **Agent 可靠性** | 上线即采集 | OODA 一次通过率（vs logbook 67% 基线） | ≥ 85% |
| **质量安全网** | 上线即采集 | 安全漏洞逃逸数（vs logbook CORS 事件） | 零逃逸 |
| **知识持久性** | 4 周后对比 | 跨 session 信息损失率 | → 0 |
| **Runtime 独立性** | Phase 2+ | Web IDE 不依赖 Claude Code 的功能覆盖率 | Phase 3 达到 100% |

数据来源：

| 指标 | 数据来源 |
|------|---------|
| 迁移效率 | 迁移项目实际记录 |
| Agent 可靠性 | execution-logger |
| 质量安全网 | security-baseline 拦截记录 |
| 知识持久性 | knowledge-base 查询覆盖率 |
| Runtime 独立性 | 功能矩阵对比 |

---

> 基线版本: v1.1
> 基线日期: 2026-02-17
> 下次评审: Phase 1 结束时（第 8 周）

---

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| v1.0 | 2026-02-17 | 初始基线 |
| v1.1 | 2026-02-17 | 融入三个新战略维度——跨栈迁移场景、Runtime 脱离路径、质量改进基线。重新划分 Phase 1-4 边界。 |
