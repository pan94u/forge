# Forge — AI 驱动的智能交付平台

> 规划基线 v2.2 | 基线日期: 2026-02-23 | 变更: v2.1→v2.2 Session 31-32（MiniMax 多模型端到端 + Evaluation Profile + 知识库本地写入 + Context Usage 增强 + 学习闭环），数据校准（18 MCP / 156 测试 / 32 Skill / 6 Profile / 6 Provider / V1-V8 migration / 设计基线 v12）

---

## 一、目标与定位

### 1.1 平台目标

构建一套 **AI 驱动的智能交付平台**（AI Delivery Platform），面向全软件交付生命周期，实现：

1. **交付提效**：通过 SuperAgent + Skill 体系，将 Scrum 5-7 人团队的交付能力压缩到 1-2 人 + AI 协同完成
2. **知识沉淀**：将散落在代码、文档、团队经验中的隐性知识编码为可复用的 Skill 和结构化数据
3. **持续进化**：建立"交付环 + 进化环"双环机制，让平台在使用中自动变好
4. **全员可用**：开发者通过 CLI/IDE 使用，产品团队通过 Web IDE 使用，共享同一套智能和知识
5. **跨栈迁移**：化解"无人懂旧技术栈"的死结，AI 做知识抢救 + 跨语言翻译，目标栈聚焦 Java/Kotlin
6. **方法论内化**：将人+AI 协同验证过的交付方法论（四维记忆、PDCA 循环、经验编码）编码为平台自动能力，让用户无需手动维护即可获得相同的质量保障

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
- **手动验证的方法论是平台能力的种子**：18 Session 实践出的四维记忆系统（Logbook → execution-logger，双基线 → design-baseline-tracker，验收测试 → CI baseline，经验编码 → skill-feedback-analyzer）先手动验证有效性，再编码为平台自动能力。Phase 0-2 验证方法论，Phase 3 将其平台化

---

## 二、设计原则

| # | 原则 | 说明 | 反模式 |
|---|------|------|--------|
| 1 | SuperAgent 优于多 Agent | 一个智能体通过 Skill 动态切换角色 | 为每个角色建独立 Agent |
| 2 | Skill 优于 Prompt | 将专业知识编码为可复用、可组合、可回放的 Skill | 靠临时 Prompt 传递知识 |
| 3 | 底线保障质量下限 | 不管模型怎么变，底线脚本必须通过 | 依赖模型"自觉"保证质量 |
| 4 | 稳态固化，敏态抽象 | Skill/底线/流程不变，模型/Runtime 通过适配器可插拔 | 把 Skill 和模型耦合 |
| 5 | 双环驱动 | 交付环解决"做什么"，进化环解决"越做越好" | 只交付不沉淀 |
| 6 | 每阶段可验证 | 每个交付阶段结束后有可运行的系统和可度量的指标 | 前 12 周全在搭架子 |
| 7 | 双入口统一体验 | CLI 和 Web IDE 共享同一套 SuperAgent + 知识 + MCP | 两套独立系统 |
| 8 | Runtime 无关 | Skill 内容不引用特定 Runtime 的工具名（Read/Write/Bash） | 在 SKILL.md 里写 `use the Read tool` |
| 9 | 多语言源码理解 | 平台能*读*懂任何语言的源码（.NET/Python/Go），目标*写*聚焦 Java/Kotlin | 只支持单一技术栈的代码理解 |
| 10 | 经验数据驱动 | 用 logbook/执行日志的真实数据校验设计，而非理论推演 | 凭感觉评估提效比 |
| 11 | 已验证设计不可隐式退化 | 经过 E2E 验证的设计决策必须有文档冻结 + 测试守护，修改必须显式评审 | 修改代码时无意改变已验证的行为/外观/契约，且无人察觉 |
| 12 | 开发者痛点即平台能力 | 开发中遇到的低效模式应编码为 Skill/底线自动化解决，让未来的开发者无感地获得保护 | 只解决当下问题，不沉淀为平台能力；同样的坑反复踩 |
| 13 | 场景先行验收 | 每个 Phase **编码前**先写验收场景标题 + 关键预期，编码后填充细节并交叉验证 | 先写代码后补验收测试（Session 13 发现 16 处错误就因为跳过了交叉验证） |
| 14 | 本地验证优先 | 代码修改后先跑本地测试（单元测试 + `npm run build`），确认无误后再 Docker 重建 | 每改一行都 Docker 重建 3-5 分钟（Session 15 的 4 次低效重建） |

> 原则 11-12 源于 Phase 1.5 Docker 部署调试经验——90 分钟、13 次尝试、10 个计划外问题。详见 `dev-logbook.md` Session 5。
> 原则 13-14 源于 Phase 1.6 验收测试 + 交付方法论分析（18 个 Session 实践总结）。详见 `docs/delivery-methodology-analysis.md`。

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

**核心思路**：不是 6 个独立的角色 Agent，而是 **1 个 SuperAgent** 通过 **Skill Profile** 动态切换能力组合。

```
                       SuperAgent（唯一实例）
                               │
                  Skill Profile Router（自动/手动切换）
                               │
         ┌──────────┬──────────┼──────────┬──────────┬──────────┐
         ▼          ▼          ▼          ▼          ▼          ▼
     规划 Profile  设计 Profile 开发 Profile 测试 Profile 运维 Profile 评估 Profile
     ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
     │需求分析 │  │架构设计 │  │代码生成 │  │用例编写 │  │部署运维 │  │进度评估 │
     │PRD编写  │  │详细设计 │  │+全部    │  │用例执行 │  │K8s模式 │  │知识蒸馏 │
     │        │  │API设计  │  │Foundation│ │        │  │        │  │状态复盘 │
     └────────┘  └────────┘  │Skills   │  └────────┘  └────────┘  └────────┘
                              └────────┘
                                  +
                          Domain Skills (支付/订单/库存...)
                                  +
                          跨栈迁移 Skills（codebase-profiler-multilang, business-rule-extraction）
```

每个 Profile 定义：加载哪些 Skill → OODA 循环指引 → 底线检查清单 → HITL 审批点。

> Profile 加载的 Skill 集合与 Agent Runtime 无关，Profile 定义的是"能力组合"而非"Runtime 指令"。
> 6 个 Profile 已全部实现（前端 RoleSelector 手动切换 + ProfileRouter 关键词路由），后端智能路由待进一步增强。

### 3.3 SuperAgent OODA 内循环

```
    Observe（观察）─→ Orient（分析）─→ Decide（决策）─→ Act（执行）
        ↑                                                    │
        │              底线检查 ✅ → 进入 HITL 审批            │
        └── 底线检查 ❌ → 自动回到 Observe 修复 ──────────────┘
```

### 3.4 演进路线

| 阶段 | 关键词 | 状态 | Forge 能力 | Runtime |
|------|--------|------|------------|---------|
| Phase 0 | 基础骨架 | ✅ | Foundation Skills + MCP Server 代码 + CLI + 插件体系 | Claude Code |
| Phase 1 | Web IDE 实连 + 跨栈基础 | ✅ | 真流式 + Agentic Loop + 跨栈画像 + 37 测试 | Claude Code |
| Phase 1.5 | 设计守护 + Docker 部署 | ✅ | Docker 3 容器 + E2E 验证 + 设计基线冻结 + 设计守护底线 | Claude Code |
| Phase 1.6 | AI 交付闭环 + SSO + UX 增强 | ✅ | AI→Workspace 写文件 + Keycloak SSO + Context Picker + CRUD + 自动保存 | Claude Code |
| Phase 2 | 质量基础设施 + OODA 增强 + 多模型 + 内部试用 | ✅ | Sprint 2.1（CI/Playwright/Bug 修复）+ Sprint 2.2（SkillLoader/MCP 真实服务）+ Sprint 2.3（多模型适配：Bedrock/Gemini/Qwen）+ Sprint 2.4（内部试用 + 反馈） | Claude Code |
| Phase 3 | 人机协作闭环 + 方法论平台化 | ✅ | HITL 全量暂停点 + 执行透明度 + 编译/测试管道 + 质量度量面板 + 学习循环集成（execution-logger / skill-feedback-analyzer Spring 化） | Claude Code |
| Phase 4 | Skill 架构改造（对齐 Anthropic 标准） | ✅ | Metadata-first 渐进式加载（55K→20-25K prompt）、Skill 质量治理（32→28）、管理 API（9 端点）+ /skills 前端、使用追踪 + 分析度量 | 任意 Runtime |
| Phase 5 | 记忆与上下文管理 | ✅ | 3 层记忆（Workspace Memory + Stage Memory + Session Summary）、消息压缩 3 阶段、Memory REST API（6 端点）、4-Tab 右侧面板、Rate Limit 退避 | 任意 Runtime |
| Phase 6 | 产品可用性加固 | ✅ | Workspace 持久化（DB+磁盘）、Git 仓库载入、用户 API Key per-request override、codebase-profiler + analyze_codebase、架构重构（2 神类→9 服务）| 任意 Runtime |
| Phase 7 | 多模型 + 评估 + 知识增强 | ✅ | MiniMax 端到端 + Evaluation Profile + 知识库本地写入 + Context Usage 增强 + 学习闭环管道 | 任意 Runtime |
| Phase 8 | 生产就绪 | 📋 | PostgreSQL 切换、多租户、安全加固、性能优化、CI/CD 完善 | 任意 Runtime |
| Phase 9 | 团队协作 | 📋 | 多用户实时协作、权限管理、通知系统、审计日志 | 任意 Runtime |
| Phase 10 | 生态扩展 | 📋 | CLI 工具、第三方集成、Marketplace、自定义 Skill 分发 | 任意 Runtime |

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

**路径 B（Web IDE）**：逐阶段独立
```
Phase 1:   ✅ 用户 → Web IDE → Spring Boot 后端 → ClaudeAdapter → Claude API（真流式 + Agentic Loop）
Phase 1.5: ✅ Docker 部署 + E2E 验证 + 设计基线冻结
Phase 1.6: ✅ AI→Workspace 交付闭环 + Keycloak SSO + Context Picker + FileExplorer CRUD + 自动保存
Phase 2:   + SkillLoader 增强 + ProfileRouter 后端 → 独立的 Skill 加载和 Profile 切换
Phase 3:   + AgentLoop + HookEngine + ContextBuilder → 完整 ForgeNativeRuntime
Phase 4:   ✅ Skill 架构改造（渐进式加载 + 管理 UI + 度量）
Phase 5:   ✅ 记忆与上下文管理（3 层记忆 + 消息压缩 + Memory UI）
Phase 6:   ✅ 产品可用性加固（Workspace 持久化 + Git 载入 + API Key + codebase-profiler + 架构重构）
Phase 7:   ✅ MiniMax 多模型端到端 + Evaluation Profile + 学习闭环管道
```

**ForgeNativeRuntime 5 个核心组件**：

| 组件 | 职责 | 对应的手动实践 | 目标阶段 | 当前状态 |
|------|------|--------------|---------|---------|
| SkillLoader | 读取 SKILL.md → 按 frontmatter trigger → 拼接 system prompt | 手动加载 Skill 到提示词 | Phase 2 | ✅ 基础版（SystemPromptAssembler） |
| ProfileRouter | 解析用户意图 → 选择 Profile → 加载 Skill 集 | 手动 `@角色` 切换 | Phase 2 | ✅ 基础版（前端 RoleSelector） |
| AgentLoop | 消息 → 模型 → tool_use → 执行 → tool_result → OODA 循环 | Session PDCA 循环 | Phase 3 | ⏳ 待实现 |
| HookEngine | tool 调用前后执行底线检查脚本 | 纪律 4 防腐规则 | Phase 3 | ⏳ 待实现 |
| ContextBuilder | 三级上下文 .md → 合并 → 注入 system prompt | CLAUDE.md 手动维护 | Phase 3 | ⏳ 待实现 |

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
│  │  Skill Profiles × 6 + Foundation Skills × 13 + Domain Skills  │  │
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
| 交付阶段 | 工作流引擎自动传入当前阶段 | 处于"开发"阶段 → 自动加载 development-profile |
| 用户上下文 | 用户 `@规划`/`@设计`/`@开发`/`@评估` 显式指定 | 用户输入 `@设计 帮我做架构设计`；用户输入 `@评估 项目进度` → evaluation-profile |
| 任务类型 | AI 根据任务内容自主判断 | 用户说"写个单测" → 自动选择 testing-profile |

#### 4.2.2 各 Skill Profile 的 OODA 行为定义

| 交付阶段 | Profile | Observe | Orient | Decide | Act | HITL 审批 |
|---------|---------|---------|--------|--------|-----|----------|
| 规划 | planning | 读取需求/Issue | 分析可行性 + 查知识库 | 制定方案 | 输出 PRD | PRD 人工确认 |
| 设计 | design | 读取 PRD + 存量架构 | 影响分析（service-graph） | 架构决策 | 输出设计文档 | 架构评审 |
| 开发 | development | 读取设计 + 代码上下文 | 理解规范（foundation skills） | 编码方案 | 生成代码 | Code Review |
| 测试 | testing | 读取需求 + 代码 | 识别边界条件 | 用例设计 | 编写/执行测试 | 测试报告确认 |
| 运维 | ops | 读取部署配置 | 风险评估 | 部署策略 | 执行部署 | 上线审批 |
| 评估 | evaluation | 读取执行日志 + 进度数据 | 多维度分析（4D 评分） | 评估方案 | 输出评估报告 + 知识蒸馏 | 评估结论确认 |

### 4.3 Skill 体系设计

| 层级 | 数量 | 说明 |
|------|------|------|
| 交付阶段 Skill | 12 个 | requirement-analysis, prd-writing, architecture-design, detailed-design, code-generation, test-case-writing, test-execution, deployment-ops, bug-fix-workflow, document-generation, knowledge-distillation, progress-evaluation |
| Foundation Skill | 13 个 | java-conventions, kotlin-conventions, spring-boot-patterns, gradle-build, testing-standards, api-design, database-patterns, error-handling, logging-observability, security-practices, deployment-readiness-check, environment-parity, design-baseline-guardian |
| Domain Skill | 按业务域扩展 | domain-payment, domain-order, domain-inventory |
| 知识挖掘 Skill | 3 个 | codebase-profiler（多语言支持：Java/Kotlin/.NET/Python/Go 项目结构解析）, convention-miner（跨语言规范提取：源语言规范 → 目标语言映射） |
| 跨栈迁移 Skill | 1 个 | business-rule-extraction（业务规则提取 + 边界条件识别） |

其中 3 个 Foundation Skill 来源于 Phase 1.5 Docker 部署调试中发现的低效模式（遵循原则 12）：

| Skill | 说明 | 来源痛点 |
|-------|------|---------|
| `deployment-readiness-check` | 部署前飞行检查：lockfile 存在性、Dockerfile 与构建策略一致性、工具链版本对齐（JDK/Node）、环境变量映射完整性、构建产物存在性 | Phase 1.5 首次部署成功率仅 7.7%（1/13），10 个问题中 6 个可被 pre-flight 拦截 |
| `environment-parity` | 检测 local/Docker/CI/prod 环境差异：JDK 版本、Node.js 版本、DB 类型/版本、安全配置差异、网络策略差异 | Docker 内 Gradle TLS 握手失败、JDK 8 vs 21、H2 vs PostgreSQL 配置差异 |
| `design-baseline-guardian` | 在修改 UI/API/数据模型前，自动加载 `design-baseline-v1.md` 作为上下文；修改后自动运行回归检查；发现非预期变更时阻断并通知 Review | 保护已验证设计不被未来迭代无意退化（原则 11） |

### 4.4 底线体系设计

| 底线脚本 | 适用 Profile | 检查内容 | 阻断级别 |
|---------|-------------|---------|---------|
| `code-style-baseline.sh` | development | ktlint + checkstyle 零违规 | 阻断提交 |
| `security-baseline.sh` | development | 无硬编码凭证、无 SQL 注入、无 XSS | 阻断提交 |
| `test-coverage-baseline.sh` | development, testing | Service 层 ≥ 80%，Controller 有集成测试 | 阻断 merge |
| `api-contract-baseline.sh` | design, development | OpenAPI Spec 与代码一致 | 阻断 merge |
| `architecture-baseline.sh` | design, development | 无跨层调用、依赖方向正确 | 阻断 merge |
| `deployment-preflight-baseline.sh` | ops, development | Lockfile 存在、Dockerfile 与当前构建策略一致、工具链版本匹配、必需环境变量已声明、构建产物存在且非空 | 阻断部署 |
| `runtime-health-baseline.sh` | ops | 启动应用 → 等待就绪 → 验证关键端点可达 → Bean 注入完整性 → 数据库连接可用 | 阻断发布 |
| `design-regression-baseline.sh` | development | API 契约快照对比 + UI 路由完整性 + 数据模型未缩减 + 行为测试通过 | 阻断 merge |

> 底线 6-8 源于 Phase 1.5 调试经验（13 次构建尝试中，缺 lockfile、TypeScript 编译错误、缺 WebClient bean 等问题均可被这些底线提前拦截）。**Gap**：8 个底线脚本已设计，尚未集成到 CI。

### 4.5 适配器层设计

| 适配维度 | 接口 | 当前实现 | 未来可切换 |
|---------|------|---------|----------|
| 模型适配 | `ModelAdapter.kt` | `ClaudeAdapter` (Opus/Sonnet/Haiku) + `LocalModelAdapter` (Ollama/OpenAI 兼容) + `BedrockAdapter`(AWS) + `GeminiAdapter`(Google) + `QwenAdapter`(阿里) + `MiniMaxAdapter`(MiniMax, 复用 ClaudeAdapter) — 6 个 Provider | — |
| 资产格式适配 | `AssetFormatAdapter.kt` | SKILL.md v1 格式 | 未来格式版本迁移 |
| Runtime 适配 | `RuntimeAdapter.kt` | 空壳接口（SkillLoader/ProfileRouter 基础版已独立实现） | Phase 3 ForgeNativeRuntime（方法论执行引擎） |

### 4.6 进化环设计

```
SuperAgent 每次执行
    │
    ▼
execution-logger（执行日志采集）
    │  记录：输入/输出/Skill 选择/底线结果/MCP 调用链/耗时
    │
    ├─ 记录"编译通过但运行失败"的案例 → build-run-gap 知识库
    ├─ 记录"失败→修复→再失败"的链式模式 → troubleshooting 知识库
    ├─ 记录部署环境差异导致的失败 → environment-parity 知识库
    │
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
design-baseline-tracker
    │  每次 merge 后：
    │  ├─ 对比 API 契约快照（Controller 端点变化）
    │  ├─ 对比 UI 路由（页面路由是否完整）
    │  ├─ 对比数据模型（Entity/Migration 变化）
    │  如发现非预期变更 → 通知 Review + 更新设计基线
    ▼
skill-feedback-analyzer（Skill 效果分析）
    │  生成优化建议 → 人工 Review → 合入 Skill
    ▼
SuperAgent 下次执行时自动获得更好的知识和技能
```

> 进化环（环 2）已部分实现：InteractionEvaluation 全栈（Entity/Repository/Service/Controller）、SkillFeedbackService 4D 评分聚合、LearningLoopPipelineService 学习闭环管道已在 Session 32 落地。asset-extractor 自动化和 design-baseline-tracker CI 化尚未实现。
> **关键洞察**：进化环的 4 个核心组件与我们手动实践的四维记忆系统一一对应（详见 §11）。Phase 0-2 用手动方式验证了这套方法论的有效性（18 Session 零中断、92% 验收通过率），Phase 3 的核心目标就是将其平台化。

**手动实践 → 平台自动化的映射**：

| 手动实践（Phase 0-2 已验证） | 平台组件（Phase 3 自动化） | 解决的问题 |
|---|---|---|
| Logbook（时间维度记忆） | `execution-logger` 自动采集执行日志 | AI 遗忘 → 自动记录 |
| 双基线交叉校验（空间维度） | `design-baseline-tracker` 自动检测 API/UI/数据模型偏移 | 设计偏移 → 自动守护 |
| 经验编码管道（知识维度） | `asset-extractor` + `skill-feedback-analyzer` 自动提取经验更新 Skill | 知识流失 → 自动沉淀 |
| 验收测试 + Buglist（质量维度） | CI baseline + Playwright + Bug 模式识别 | 质量腐化 → 自动拦截（Phase 2 已部分实现） |

四个胶水机制：

| 机制 | 触发方式 | 效果 |
|------|---------|------|
| 知识写入通道 | Skill/Agent 产出知识时 | 结构化内容 → Wiki；草稿 → knowledge-base/ Git → 人工 Review |
| 知识空白检测 | AI Chat 搜不到答案时 | 自动记录空白 → 触发 doc-generator → 提交文档 PR |
| 规范同步闭环 | 每月定时 CI | convention-miner 扫描 → diff 实际规范 vs Skill → 自动提 PR 更新 |
| Domain Skill 保鲜 | 每次 PR merge | 检查被修改代码是否命中 domain-*.SKILL.md 引用 → 通知 Review |

---

## 五、关键设计

### 5.1 MCP 工具体系

#### 后端 MCP Server（5 个，20 个细粒度 Tool）

| MCP Server | 端口 | 核心 Tool | 用途 |
|-----------|------|----------|------|
| `forge-knowledge-mcp` | 8081 | wiki_search / adr_search / runbook_search / api_doc_search / page_create / gap_log | 知识库读写 + 空白检测 + page_create 本地模式（写入 knowledge-base/&lt;space&gt;/） |
| `forge-database-mcp` | 8082 | schema_inspect / query_execute (SELECT only) / data_dict_search | 数据库元信息查询 |
| `forge-service-graph-mcp` | 8083 | service_list / service_dependencies / impact_analysis / call_chain / owner | 服务依赖图谱 + 影响分析 |
| `forge-artifact-mcp` | 8084 | dependency_search / vulnerability_scan / version_recommend | 制品库 + CVE 扫描 |
| `forge-observability-mcp` | 8085 | log_search / metrics_query / trace_search | 日志/指标/链路查询 |

#### Web IDE 聚合层（17 个 Tool，通过 McpProxyService → 5 Handler 拆分路由）

实际 Web IDE 中，后端通过 `McpProxyService` 将 MCP Server 工具聚合为 17 个高层 Tool，通过 SSE 暴露给 Claude API：

| # | Tool | 来源 | 说明 |
|---|------|------|------|
| 1 | `search_knowledge` | knowledge-mcp | 统一知识搜索 |
| 2 | `query_schema` | database-mcp | 数据库 Schema 查询 |
| 3 | `get_service_info` | service-graph-mcp | 服务信息查询 |
| 4 | `read_file` | 内置 | 读取知识库/workspace/插件文件 |
| 5 | `run_baseline` | 内置 | 执行底线检查脚本 |
| 6 | `list_baselines` | 内置 | 列出可用底线脚本 |
| 7 | `workspace_write_file` | 内置 | AI 写文件到 workspace |
| 8 | `workspace_read_file` | 内置 | AI 读 workspace 文件 |
| 9 | `workspace_list_files` | 内置 | AI 列出 workspace 文件 |
| 10 | `workspace_compile` | 内置 | 编译/构建项目，自动检测项目类型 |
| 11 | `workspace_test` | 内置 | 运行测试，自动检测测试框架 |
| 12 | `read_skill` | 内置 | 按需读取 SKILL.md 或子文件内容 |
| 13 | `run_skill_script` | 内置 | 执行 Skill 脚本，60s 超时 |
| 14 | `list_skills` | 内置 | 列出可用 Skill metadata |
| 15 | `update_workspace_memory` | 内置 | Agent 主动更新 workspace 记忆 |
| 16 | `get_session_history` | 内置 | 读取历史 session 摘要 |
| 17 | `analyze_codebase` | 内置 | 对 workspace 执行结构分析，返回 JSON |

**设计决策**：聚合策略降低了 Claude API 的 tool_use 复杂度（20→17 聚合），同时保持后端与各 MCP Server 的独立通信。5 个 MCP Server 代码保持不变，聚合发生在 `McpProxyService` → 5 Handler 拆分路由层。此外 page_create 支持本地模式，可直接写入 knowledge-base/ 目录。

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
| 认证 | Keycloak 24.0 (OIDC PKCE) | SSO 统一认证 |
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

### 5.5 设计守护体系

> 源于原则 11"已验证设计不可隐式退化"

```
                          设计守护体系
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
      design-baseline-v1.md  design-regression   design-baseline
      (设计基线文档)          -baseline.sh        -guardian Skill
              │               (底线脚本)          (Foundation Skill)
              │                │                  │
              ▼                ▼                  ▼
      冻结已验证设计：         merge 前自动检查：    修改代码时自动：
      - UI 路由结构           - API 契约快照对比   - 加载基线文档
      - API 契约清单          - UI 路由完整性     - 提醒开发者
      - 数据模型定义          - 数据模型未缩减     - 修改后运行回归
      - 架构决策              - 行为测试通过       - 阻断非预期变更
```

详见：`docs/baselines/design-baseline-v1.md`（当前 v12）

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
│   │   ├── skills/                      # 18 Skills (13 Foundation + 2 Mining + 3 Domain)
│   │   ├── commands/                    # 6 Commands
│   │   ├── agents/                      # 4 Agents
│   │   └── hooks/                       # Security hooks
│   │
│   ├── forge-superagent/                # SuperAgent 插件（双环架构核心）
│   │   ├── CLAUDE.md                    # SuperAgent 系统指令
│   │   ├── skill-profiles/              # 6 Skill Profiles（全部已实现，含 evaluation-profile）
│   │   ├── skills/                      # 12 交付阶段 Skills（+bug-fix-workflow, document-generation, knowledge-distillation, progress-evaluation）
│   │   ├── baselines/                   # 底线脚本 + runner (8 个)
│   │   └── learning-loop/               # 进化环组件 (含 design-baseline-tracker)
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
├── knowledge-base/                      # 知识沉淀层 (12+ 文档)
├── managed-config/                      # 企业托管配置
├── .github/workflows/                   # CI/CD
├── docs/                                # 文档 (design-baseline-v1.md, phase1.6-e2e-acceptance-test.md 等)
└── infrastructure/                      # Docker + K8s + Keycloak + Nginx
```

---

## 七、分阶段实施计划

### Phase 0：基础骨架 — ✅ 已完成

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

### Phase 1：Web IDE 实连 + 跨栈基础能力 — ✅ 代码完成

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
- [x] Web IDE 发送消息 → 收到 Claude API 真实 streaming token（无 20ms 间隔）
- [x] Tool calling agentic loop 工作（最多 5 轮）
- [x] codebase-profiler 能对 .NET 项目生成有效画像
- [x] 底线脚本在 CI 中运行通过
- [x] 37 单元/集成测试全部通过

> 注：代码完成 + 测试通过，但尚未做过端到端真实运行验证 → 已由 Phase 1.5 解决

### Phase 1.5：设计守护 + Docker 部署 — ✅ 已完成

**定位**：Phase 1 的端到端验收 + 设计守护体系建立 + Phase 2 的入口条件。

**验收标准**：
- [x] `docker compose up` 一键启动（3 容器 running, backend healthy）
- [x] `http://localhost:9000` 可访问（前端 200, API 返回数据）
- [x] 前端页面加载 + 后端 API 返回数据
- [x] 设计基线文档完成（`docs/design-baseline-v1.md`）
- [x] 设计守护底线设计完成（本文档 §4.4）

**遗留项**（已移至后续阶段）：
- AI Chat 流式响应 → Phase 1.6 验收
- 3-5 人实际使用 ≥ 3 天 → Phase 2 前置

### Phase 1.6：AI 交付闭环 + SSO + UX 增强 — ✅ 已完成

> Phase 1.6 是规划基线中未预见的迭代。实际开发过程中发现，在进入 Phase 2（SkillLoader + ProfileRouter）之前，需要先让 Web IDE 具备完整的 AI→Workspace 交付闭环、用户认证、以及基本的文件操作能力。这些是 Phase 2 智能层的前置基础设施。

**定位**：Phase 1.5 → Phase 2 之间的桥梁。让 Web IDE 从"能对话"升级到"能交付"。

| # | 功能 | 说明 |
|---|------|------|
| 1 | AI→Workspace 交付闭环 | workspace_write_file / read_file / list_files 三个工具，AI 可直接读写 workspace 文件 |
| 2 | file_changed 事件驱动 | AI 写文件后通过 SSE 推送 file_changed 事件 → 前端文件树自动刷新 + 编辑器自动打开 |
| 3 | Keycloak SSO | OIDC PKCE 登录流程，4 容器部署（+keycloak），realm 自动导入，支持 FORGE_SECURITY_ENABLED 开关 |
| 4 | Context Picker 实连 | /api/context/search 端点，4 个类别（files/knowledge/schema/services）实际数据 |
| 5 | 代码块 Apply 按钮 | 聊天中代码块可一键写入 workspace，20+ 语言→扩展名映射 |
| 6 | FileExplorer CRUD | 右键菜单：新建文件/文件夹、重命名、删除 |
| 7 | 未保存标记 + 自动保存 | 文件 tab 蓝色圆点标记 + 5 秒防抖自动保存 |
| 8 | System Prompt 交付指导 | AI 被指导必须将代码写入 workspace 而非仅在聊天中展示 |

**额外交付**：
- 知识库新增 5 篇文档（git-workflow、code-review-checklist、forge-mcp-tools、troubleshooting-guide、ADR-004）
- 130+ 单元/集成测试（+9 workspace tool tests, +ContextControllerTest）
- 89 个 E2E 验收用例（24 场景，336 checkboxes）
- 设计基线 v5（`design-baseline-v1.md` 1007 行）

**验收标准**：
- [x] AI 发送包含代码的回复 → 代码自动写入 workspace 文件 → 文件树刷新 → 编辑器自动打开
- [x] Keycloak 管理后台可访问（http://localhost:8180）
- [x] 4 容器全部 healthy（backend + frontend + nginx + keycloak）
- [x] Context Picker 搜索返回 workspace 文件和知识库文档
- [x] 代码块 Apply 按钮点击后文件出现在 workspace
- [x] FileExplorer 右键菜单：新建/重命名/删除均工作
- [x] 编辑后 tab 出现蓝色圆点，5 秒后自动保存
- [x] 知识库 12+ 文档全部可搜索
- [x] 130+ 测试通过

**关键偏离**（相对原始规划）：
- MCP Tools 从规划的 20 个细粒度 → 实际 9 个聚合（详见 §5.1）
- Docker 从 3 容器 → 4 容器（+keycloak）
- 5 个 Skill Profile 全部提前完成（原计划 planning-profile + ops-profile 在 Phase 3）

### Phase 2：质量基础设施 + OODA 增强 + 多模型适配 + 内部试用 — ✅ 已完成

> 详细实施计划：`docs/phase2-implementation-plan.md`（v1.1）
> 关键决策：混合路线（Sprint 拆分）、ForgeNativeRuntime 推迟到 Phase 3、MCP 做 1-2 个真实服务、多模型适配（Bedrock/Gemini/Qwen）、组织 3-5 人试用

**目标**：建立自动化质量保障体系；增强 OODA 引擎智能性；支持多模型选择；让真人用起来并收集反馈。

**前置条件**：

| 条件 | 状态 |
|------|------|
| Phase 0~1.6 全部完成 | ✅ |
| 设计基线已冻结 | ✅（v5.1） |
| Phase 1.6 Metrics 报告 | ✅（`docs/metrics-report-phase1.6.md`） |
| 交付方法论分析 | ✅（`docs/delivery-methodology-analysis.md`） |
| E2E 验收通过率 ≥ 90% | ✅（92.0%，80/87） |

**原始 12 交付物对照**：

| # | 原始交付物 | 状态 | Phase 2 行动 |
|---|-----------|------|-------------|
| 1 | SuperAgent OODA 引擎 | ⚠️ 基础版 | Sprint 2.2 增强（底线自动检查） |
| 2 | SkillLoader.kt 增强 | ⚠️ 基础版 | Sprint 2.2 增强（frontmatter trigger） |
| 3 | ProfileRouter.kt 后端 | ⚠️ 基础版 | 已实现关键词+标签路由，可选增强 |
| 4 | MCP Server 完善 | ⚠️ 骨架代码 | Sprint 2.2 做 knowledge + database 真实服务 |
| 5 | convention-miner 跨语言增强 | 📄 SKILL.md | **推迟到 Phase 3** |
| 6 | 全部 13 Foundation Skill | ✅ 32 Skills | 已超额完成 |
| 7 | 端到端跨栈迁移 PoC | ✅ 100% 覆盖 | 已完成（11/11 业务规则） |
| 8 | agent-eval 评估体系 | ⚠️ 框架已搭建 | Sprint 2.1 填充评估集 |
| 9 | 度量基线采集 | ⚠️ MetricsService | 已完成 metrics 报告 + Sprint 2.2 Dashboard |
| 10 | 底线脚本 CI 集成 | ❌ 未开始 | Sprint 2.1 核心交付 |
| 11 | Playwright E2E | ❌ 未开始 | Sprint 2.1 核心交付 |
| 12 | 内部用户试用 | ❌ 未开始 | Sprint 2.4 核心交付 |
| 13 | 多模型适配（Bedrock/Gemini/Qwen） | ❌ 未开始 | Sprint 2.3 核心交付（新增） |

**Sprint 2.1：质量基础设施 — "让系统可信赖"**

| # | 交付物 | 优先级 |
|---|--------|--------|
| 1 | GitHub Actions CI Pipeline（构建 + 测试 + lint） | P0 |
| 2 | 底线脚本 CI 集成（至少 code-style + security + test-coverage 先行） | P0 |
| 3 | Playwright E2E（20-30 个核心验收场景自动化） | P1 |
| 4 | BUG-016 修复（agentic loop 耗尽后无输出） | P1 |
| 5 | Cmd+K 命令面板（Header.tsx 键盘监听器） | P2 |
| 6 | agent-eval 评估集填充（5 Profile × 2-3 场景） | P2 |

**Sprint 2.2：OODA 引擎增强 + MCP 真实服务 — "让智能更真实"**

| # | 交付物 | 优先级 |
|---|--------|--------|
| 1 | SkillLoader frontmatter trigger 增强（keywords/file-patterns/profile 动态过滤） | P0 |
| 2 | knowledge-mcp 真实服务（Ktor HTTP，对接真实知识库目录） | P0 |
| 3 | McpProxyService 真实 HTTP 调用（knowledge/database 从内置改为 HTTP） | P0 |
| 4 | AgentLoop 底线自动检查（Act 后自动运行底线，失败回到 Observe） | P1 |
| 5 | database-mcp 真实服务（Ktor HTTP，对接 H2/PostgreSQL） | P1 |
| 6 | Docker 扩容 4→6 容器（+knowledge-mcp + database-mcp） | P1 |
| 7 | Metrics Dashboard（Grafana 或内置简易面板） | P2 |

**Sprint 2.3：多模型适配 — "让模型可选择"**

| # | 交付物 | 优先级 |
|---|--------|--------|
| 1 | BedrockAdapter 完善（AWS SDK Converse API，流式 + 工具调用） | P0 |
| 2 | GeminiAdapter 新建（Google Gemini API + Function Calling） | P0 |
| 3 | QwenAdapter 新建（阿里 DashScope API，OpenAI 兼容格式） | P0 |
| 4 | 工具调用兼容层（不同模型的工具格式差异处理） | P0 |
| 5 | ModelRegistry 模型注册中心（能力矩阵 + 健康状态） | P1 |
| 6 | ClaudeConfig → ModelConfig 重构（多 Provider 并存，运行时切换） | P1 |
| 7 | 前端模型选择器（下拉选择，按 Provider 分组） | P1 |

**Sprint 2.4：内部试用 + 反馈闭环 — "让真人用起来"**

| # | 交付物 | 优先级 |
|---|--------|--------|
| 1 | 试用准备文档（用户手册 + 环境搭建 + FAQ） | P0 |
| 2 | 试用环境部署（稳定的共享/独立 Docker 环境） | P0 |
| 3 | 试用执行（3-5 人，≥ 3 天） | P0 |
| 4 | Top 问题修复（根据反馈修 Top 3-5 问题） | P0 |
| 5 | 反馈收集机制（结构化反馈表，含模型偏好维度） | P1 |
| 6 | 验收测试更新（基于试用新场景扩展） | P1 |

**验收标准**：
- [x] GitHub Actions CI 自动运行 build + test + 底线检查（Sprint 2.1）
- [x] Playwright E2E ≥ 20 个场景自动通过（Sprint 2.1，5 个 spec）
- [x] BUG-016 修复 + Cmd+K 可用（Sprint 2.1）
- [x] Skill 按 frontmatter trigger 条件动态加载（Sprint 2.2）
- [x] knowledge-mcp + database-mcp 作为独立容器运行（Sprint 2.2，6 容器）
- [x] McpProxyService 通过 HTTP 调用真实 MCP Server（Sprint 2.2）
- [x] Bedrock + Gemini + Qwen 三大模型适配器可用（Sprint 2.3，13+ 模型）
- [x] 前端可切换模型，工具调用兼容（Sprint 2.3，ModelSelector + ModelSettingsDialog）
- [x] 内部试用执行 + 结构化反馈收集（Sprint 2.4）
- [x] 所有 Bug ≤ 1 个挂起（29 个，28 已修复，BUG-016 挂起）

**Sprint 2.4 试用反馈（4 条核心反馈，驱动 Phase 3 重构）**：
1. 无完整管道 — 代码生成后断裂，不能编译/测试/部署
2. 过度自动化 — AI 跑满 8 轮无人介入，HITL 设计了但未强制执行
3. 黑盒感 — 不知道 AI 在做什么、将做什么
4. 无完成度度量 — 数据采集了但无可视化

> 详细试用记录：`docs/sprint2.4-trial-discussion-record.md`

**不在 Phase 2 范围（移至 Phase 3）**：
- 人机协作闭环（HITL 全量暂停点 + 审批 UI）→ Phase 3 模块 2
- 执行透明度（sub_step 事件 + 活动日志）→ Phase 3 模块 1
- 编译/测试管道（workspace_compile / workspace_test）→ Phase 3 模块 3
- 质量度量面板（Dashboard API + 前端面板）→ Phase 3 模块 4
- 进化环组件 Spring 化 → Phase 3 模块 5
- ForgeNativeRuntime（AgentLoop.kt / HookEngine.kt / ContextBuilder.kt）→ Phase 3+
- Domain Skills 扩展 / convention-miner 跨语言增强 → Phase 3+

### Phase 3：人机协作闭环 + 方法论平台化 — ✅ 已实现

> **核心理念**：Sprint 2.4 内部试用暴露了平台"AI 独舞"模式的根本问题——缺少"计划预览 → 人工审批 → 分步执行 → 结果度量"闭环。Phase 3 优先解决人机协作体验（HITL + 透明度 + 管道 + 度量），同时将 Phase 0-2 手动验证的方法论（execution-logger / skill-feedback-analyzer）Spring 化集成。
>
> 详细实施计划：`docs/planning/phase3-implementation-plan.md`

**目标**：实现完整的人机协作闭环；5 Profile 全量 HITL 暂停点；真实编译/测试管道；质量度量面板；学习循环集成。

**六大模块（16 步）**：

| 模块 | 步骤 | 核心交付 | 解决的反馈 |
|------|------|---------|-----------|
| 模块 1：执行透明度 | Step 1-3 | sub_step 细粒度事件、活动日志面板、Baseline 结果卡片 | 反馈 3（黑盒感） |
| 模块 2：HITL 全量暂停点 | Step 4-7 | 5 Profile checkpoint、DB 持久化审批状态、WebSocket 双向审批、审批交互 UI | 反馈 2（过度自动化） |
| 模块 3：编译/测试管道 | Step 8-10 | workspace_compile / workspace_test 真实工具、development-profile 完整交付流程 | 反馈 1（无完整管道） |
| 模块 4：质量度量面板 | Step 11-13 | 执行记录持久化、Dashboard API（3 端点）、前端质量面板（4 区域） | 反馈 4（无度量） |
| 模块 5：学习循环集成 | Step 14-15 | ExecutionLoggerService + SkillFeedbackService Spring 化 | 方法论平台化 |
| 模块 6：文档与验收 | Step 16 | 6 场景 24 TC 验收测试、文档更新 | — |

**执行依赖**：
```
模块 1（透明度）     ──┐
模块 2（HITL）       ──┼── 三路并行
模块 3（编译测试）    ──┘
          │
模块 4（度量面板）    ── 依赖模块 2
          │
模块 5（学习循环）    ── 依赖模块 4
          │
模块 6（文档验收）    ── 依赖全部
```

**关键文件清单**：

| 类型 | 修改 | 新建 |
|------|------|------|
| 后端 | 5 文件（ClaudeAgentService, ChatWebSocketHandler, SkillModels, McpProxyService, MetricsService） | 7 文件（HitlCheckpointEntity, HitlCheckpointRepository, ExecutionRecordEntity, ExecutionRecordRepository, DashboardController, ExecutionLoggerService, SkillFeedbackService） |
| 前端 | 3 文件（AiChatSidebar, claude-client, ChatMessage） | 2 文件（HitlApprovalPanel, QualityPanel） |
| 配置/文档 | 3 文件（development-profile.md, TRIAL-GUIDE.md, baseline-v1.5.md） | 1 文件（phase3-acceptance-test.md） |

**验收标准**：
- [ ] 活动日志实时显示 10+ 条 sub_step，OODA 指示器显示 Turn X/8 + 工具名
- [ ] 5 Profile 分别触发 HITL 暂停 → 审批面板出现 → approve/reject/modify 三种操作正常
- [ ] 断线重连后 HITL 审批面板恢复
- [ ] workspace_compile / workspace_test 真实执行编译和测试
- [ ] Dashboard API 返回 baseline/tool/profile/ooda/hitl 聚合统计
- [ ] 前端质量面板 4 区域渲染（卡片 + 柱状图 + 折线图 + 表格）
- [ ] 执行记录持久化到 DB + 文件系统
- [ ] SkillFeedbackAnalyzer 定时任务可手动触发
- [ ] 6 容器 healthy，端到端闭环（编码→编译→测试→底线→HITL审批→度量）

**Phase 3 之后待做（移至 Phase 3+ / Phase 4）**：
- ForgeNativeRuntime 完整体（AgentLoop.kt / HookEngine.kt / ContextBuilder.kt）— 当前 ClaudeAgentService 已有基础 OODA Loop，完整 Runtime 抽象推迟
- RuntimeAdapter 配置切换（Claude Code ↔ ForgeNative）
- asset-extractor 自动化（从执行日志提取知识资产）
- design-baseline-tracker CI 化（merge 自动对比 API 契约 / UI 路由）
- 四个胶水机制 CI 化（convention-sync / doc-generation / domain-skill-staleness / knowledge-gap）
- Domain Skills 扩展 / convention-miner 跨语言增强
- 50 名开发者 CLI 规模化部署

### Phase 4：Skill 架构改造 — 对齐 Anthropic Agent Skills 标准 — ⏳ 已规划

> 详细实施计划：`docs/phase4-implementation-plan.md`

**目标**：将 Forge Skill 架构从"system prompt 注入物"改造为**对齐 Anthropic 标准的渐进式披露生态**，实现 metadata-first 发现、按需加载、可执行脚本、用户可管理、效果可追踪。

**核心架构改造**（对齐 [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)）：

```
Level 1: Metadata（始终在 system prompt，~100 tokens/skill）
  ↓ Agent 按需触发
Level 2: SKILL.md 指令（< 500 行，通过 read_skill MCP 工具读取）
  ↓ 需要深入参考
Level 3: 子文件 + 可执行脚本（reference/ + examples/ + scripts/）
```

**4 个 Sprint**：

| Sprint | 目标 | 关键产出 |
|--------|------|----------|
| 4.1 | Metadata 架构 + 渐进加载 | SkillDefinition 扩展、SystemPromptAssembler 改造（只注入 metadata）、+3 MCP 工具（read_skill / run_skill_script / list_skills） |
| 4.2 | 可执行脚本 + 目录改造 | 15 个 SKILL.md 精简（只写 Claude 不知道的）、~20 个验证脚本（Python）、Anthropic 标准目录结构 |
| 4.3 | Skill 管理 API + 前端 UI | REST CRUD API、用户 Skill 可视/可选/可创/可删、system Skill 保护 |
| 4.4 | 生态追踪 + 验收 | 使用效果追踪（skill_usage 表）、排行榜/趋势 API、进化建议、24 TC 验收 |

**预期效果**：
- System prompt: ~78K → ~20K chars（75% 缩减）
- Skill 可执行脚本: 0 → ~20 个
- 用户 Skill 管理: 不可见 → 可视/可选/可创/可删
- Skill 数量上限: ~10（受 prompt 限制）→ 无上限
- MCP 工具数: 11 → 14

**验收标准**：
- [ ] System prompt < 25K chars（所有 Profile）
- [ ] Agent 通过 MCP 工具按需读取 Skill 内容
- [ ] 可执行脚本提供确定性质量验证
- [ ] 用户可创建/删除自定义 Skill
- [ ] Skill 使用效果可追踪、可排名
- [ ] 24 TC 验收测试通过

### Phase 5：记忆与上下文管理 — ✅ 已完成

> 详见 Session 27-28。3 层记忆架构（Workspace Memory + Stage Memory + Session Summary）+ 消息压缩 3 阶段 + Memory REST API 6 端点 + 4-Tab 右侧面板 + Rate Limit 退避。

### Phase 6：产品可用性加固 — ✅ 已完成

> 详见 Session 29。Workspace 持久化（DB+磁盘）+ Git clone 载入 + API Key per-request override + codebase-profiler Skill + analyze_codebase MCP 工具 + 架构重构（2 神类→9 服务，最大 547 LOC）。

### Phase 7：多模型 + 评估 + 知识增强 — ✅ 已完成

> 详见 Session 30-32。设计基线 v12。

**目标**：MiniMax 多模型端到端打通；新增 Evaluation Profile（第 6 个）；知识库本地写入能力；Context Usage 增强；学习闭环管道。

**Session 30 交付**（品牌重定位 + 知识库 Scope + Git Clone 异步化）：
- 品牌定位统一为"AI 驱动的智能交付平台"
- 知识库 Scope 三层分层（global / workspace / user）
- Git Clone 异步化（WebSocket 进度推送）
- Flyway V7-V8 migration

**Session 31 交付**（H2 持久化 + MiniMax 多模型）：
- H2 持久化恢复（Docker volume for backend data）
- MiniMax 模型支持（3 个模型：MiniMax-M2.5 / M2.5-lightning / M2.5-highspeed）
- 模型选择端到端打通（frontend selectedModel → WebSocket modelId → ChatWebSocketHandler → ClaudeAgentService 动态适配 → AgenticLoopOrchestrator）
- ModelRegistry +providerForModel()
- 前端 ModelSettingsDialog +MiniMax provider，ModelSelector +MiniMax label
- Model providers: 5→6（+MiniMax）

**Session 32 交付**（Evaluation Profile + 学习闭环 + 知识写入）：
- Docker backend crash fix（DB_DRIVER default 回退 org.h2.Driver）
- PageCreateTool 本地模式（知识写入：Markdown 写入 knowledge-base/&lt;space&gt;/）
- LocalKnowledgeProvider +reload() 用于重新索引
- knowledge-base volume 改为可写
- MAX_AGENTIC_TURNS 8→50
- context_usage 每轮发送（含 turn 字段）
- 前端 Context Usage 卡片始终可见
- 新增 evaluation-profile（只读分析模式）— 第 6 个 Skill Profile
- 4 个新 Skill：bug-fix-workflow、document-generation、knowledge-distillation、progress-evaluation
- ProfileRouter evaluation 关键词路由（@评估/进度/状态/复盘）
- SystemPromptAssembler 只读模式行为引导
- SkillFeedbackService 4D 评分聚合
- InteractionEvaluation 全栈（Entity/Repository/Service/Controller）
- LearningLoopPipelineService 学习闭环管道
- 设计基线 v12
- Buglist BUG-029~032

**验收标准**：
- [x] MiniMax 3 个模型可从前端选择并端到端对话
- [x] Evaluation Profile 通过 @评估/@进度/@状态/@复盘 关键词自动路由
- [x] PageCreateTool 本地模式可写入 knowledge-base/ 目录
- [x] Context Usage 卡片每轮更新，始终可见
- [x] InteractionEvaluation CRUD API 可用
- [x] LearningLoopPipelineService 可触发学习闭环
- [x] 156 单元测试全部通过
- [x] 6 容器 healthy（Docker backend H2 持久化恢复）
- [x] 设计基线 v12 发布

---

## 八、实施进度基线

### 基线快照 (2026-02-23)

| 模块 | 文件数 | 完成阶段 | 说明 |
|------|--------|---------|------|
| Root (build, settings, CLAUDE.md) | 6 | Phase 0 | Gradle monorepo 骨架 |
| mcp-servers/forge-mcp-common | 7 | Phase 0 | McpServerBase + Auth + Health + Metrics + Audit + Protocol |
| mcp-servers/forge-knowledge-mcp | 9 | Phase 0 | 6 tools: WikiSearch, AdrSearch, RunbookSearch, ApiDocSearch, PageCreate, GapLog |
| mcp-servers/forge-database-mcp | 8 | Phase 0 | 3 tools + QuerySanitizer + AccessControl |
| mcp-servers/forge-service-graph-mcp | 12 | Phase 0 | 5 tools + 4 indexers |
| mcp-servers/forge-artifact-mcp | 6 | Phase 0 | 3 tools: DependencySearch, VulnerabilityScan, VersionRecommend |
| mcp-servers/forge-observability-mcp | 6 | Phase 0 | 3 tools: LogSearch, MetricsQuery, TraceSearch |
| plugins/forge-foundation | ~30 | Phase 0 | plugin.json + Skills/commands/agents/hooks |
| plugins/forge-superagent | 35+ | Phase 7 | CLAUDE.md + 6 profiles（+evaluation）+ 14 skills（+bug-fix-workflow, document-generation, knowledge-distillation, progress-evaluation）+ 5 baselines + runner + 3 learning-loop |
| plugins/forge-knowledge | 8 | Phase 0 | 3 commands + 3 skills |
| plugins/forge-deployment | 5 | Phase 0 | 2 skills + 2 commands |
| plugins/forge-team-templates | 3 | Phase 0 | 3 team CLAUDE.md templates |
| web-ide/frontend | 30+ | Phase 1.6 | +auth.ts, login/callback, Apply button, CRUD, auto-save, file_changed |
| web-ide/backend | 50+ | Phase 6 | +WorkspaceEntity, GitService, 4 拆分服务（Orchestrator/Hitl/Baseline/BuiltinTool/WorkspaceTool/SkillTool/MemoryTool）, Memory, V6 migration |
| cli | 7 | Phase 0 | 5 commands + main + build |
| adapters | 14+ | Phase 1 | + StreamEvent, tool calling 扩展, tests |
| agent-eval | 11 | Phase 0 | EvalRunner + Reporter + 6 eval sets |
| skill-tests | 4 | Phase 0 | Validator + Parser + Runner |
| knowledge-base | 13 | Phase 1.6 | +git-workflow, code-review-checklist, forge-mcp-tools, troubleshooting-guide, ADR-004 |
| managed-config | 2 | Phase 0 | managed-settings.json + managed-mcp.json |
| .github/workflows | 6 | Phase 0 | 6 CI workflows |
| docs | 35+ | Phase 7 | design-baseline v12, planning-baseline v2.2, logbook Session 32, phase3-6 acceptance tests, delivery-analysis |
| infrastructure | 8+ | Phase 1.6 | keycloak realm-export, nginx-trial.conf, docker-compose.trial.yml (4 容器) |
| **总计** | **~360+** | | **~55,000+ 行** |

### 里程碑

```
Phase 0       Phase 1          Phase 1.5         Phase 1.6            Phase 2                         Phase 3            Phase 4           Phase 5          Phase 6           Phase 7
骨架搭建       实连+跨栈基础      设计守护+Docker    AI交付闭环+SSO        质量+OODA+多模型+试用              人机协作闭环          Skill改造         记忆管理          可用性加固         多模型+评估+知识
244 files     37 tests         3容器+基线冻结      4容器+9工具+147测试   6容器+13模型+Sprint2.1-2.4       HITL+管道+度量      28Skill+/skills  3层记忆+压缩     DB持久化+Git+17工具 MiniMax+EvalProfile+32Skill
──── ✅ ── ── ──── ✅ ── ── ── ──── ✅ ── ── ── ──── ✅ ── ── ── ──── ✅ ── ── ── ──── ✅ ── ── ──── ✅ ── ── ──── ✅ ── ── ──── ✅ ── ── ──── ✅ ── 👈 Phase 8
  W1-3          W4-8                                                     W9-14                          W15-20            W21-24           W25-26           W27-29           W30-32
```

| 里程碑 | 状态 |
|--------|------|
| Phase 0 骨架完成 | ✅ 完成（244 文件，38,600+ 行） |
| Phase 0 → Phase 1 转换 | ✅ 完成（三轮设计验证 + 基线更新） |
| Phase 1 代码完成 | ✅ 完成（+41 files, 4,027 insertions, 37 tests） |
| Phase 1.5 Docker E2E 验证 | ✅ 完成（3 容器运行，前后端路由正常） |
| Phase 1.5 设计守护体系 | ✅ 完成（设计基线 + baseline v1.3 + 3 新底线 + 3 新 Skill） |
| Phase 1.6 AI 交付闭环 + SSO | ✅ 完成（4 容器，9 工具，147 测试，87 验收用例 92% 通过） |
| Phase 1.6 Metrics 报告 | ✅ 完成（7 个 Prometheus 指标已采集，度量报告已生成） |
| Phase 1.6 交付方法论分析 | ✅ 完成（18 Session 全量分析，评分 4.4/5.0） |
| Phase 2 Sprint 2.1 质量基础设施 | ✅ 完成（CI/CD + Playwright + 底线集成，34/34 验收通过） |
| Phase 2 Sprint 2.2 OODA + MCP 真实服务 | ✅ 完成（6 容器 + SkillLoader 增强 + 底线自动检查，24/24 验收通过） |
| Phase 2 Sprint 2.3 多模型适配 | ✅ 完成（5 Provider，13+ 模型，Bedrock/Gemini/Qwen） |
| Phase 2 Sprint 2.4 内部试用 | ✅ 完成（试用执行 + 4 条核心反馈收集） |
| Phase 3 人机协作闭环 | ✅ 完成（6 模块 16 步 + Session 23-24 验收：28 Bug 修复，规划→设计→开发全流程闭环验证通过） |
| Phase 4 Skill 架构改造 | ✅ 完成（Metadata-first 渐进式加载 55K→20-25K、Skill 质量治理 32→28、管理 API 9 端点 + /skills 前端、使用追踪 + 度量，Session 25-26） |
| Phase 5 记忆与上下文管理 | ✅ 完成（3 层记忆架构 + 消息压缩 3 阶段 + Memory REST API 6 端点 + 4-Tab 右侧面板 + Rate Limit 退避，Session 27-28） |
| Phase 6 产品可用性加固 | ✅ 完成（Workspace 持久化 DB+磁盘 + Git clone 载入 + API Key per-request override + codebase-profiler Skill + analyze_codebase 第 17 个 MCP 工具 + 架构重构 2 神类→9 服务，Session 29） |
| Phase 7 多模型 + 评估 + 知识增强 | ✅ 完成（MiniMax 多模型端到端 + Evaluation Profile 第 6 个 Profile + 4 新 Skill + PageCreateTool 本地模式 + Context Usage 增强 + InteractionEvaluation 全栈 + LearningLoopPipelineService + SkillFeedbackService 4D 评分 + H2 持久化恢复 + MAX_AGENTIC_TURNS 8→50 + 设计基线 v12，Session 30-32） |

### 已识别 Gap

| Gap | 说明 | 目标阶段 | 状态 |
|-----|------|---------|------|
| ~~底线 CI 集成~~ | ~~8 个底线脚本 CI 集成~~ | ~~Phase 2~~ | ✅ Sprint 2.1 |
| ~~Playwright E2E~~ | ~~自动化 20-30 个核心场景~~ | ~~Phase 2~~ | ✅ Sprint 2.1（5 spec） |
| ~~BUG-016~~ | ~~Agentic loop 耗尽后无文字输出~~ | ~~Phase 2~~ | ✅ Sprint 2.1 修复 |
| ~~MCP Server 真实化~~ | ~~knowledge + database 真实服务~~ | ~~Phase 2~~ | ✅ Sprint 2.2（6 容器） |
| ~~多模型适配~~ | ~~Bedrock/Gemini/Qwen 适配器~~ | ~~Phase 2~~ | ✅ Sprint 2.3（13+ 模型） |
| ~~内部用户试用~~ | ~~3-5 人实际使用~~ | ~~Phase 2~~ | ✅ Sprint 2.4（试用 + 4 条反馈） |
| ~~HITL 无运行时暂停~~ | ~~仅靠 system prompt 提示，AI 自行决定是否暂停~~ | ~~Phase 3~~ | ✅ 模块 2（HitlCheckpointEntity + CompletableFuture 暂停） |
| ~~前端缺 4 种事件处理~~ | ~~baseline_check、tool_use_start、hitl_checkpoint、sub_step~~ | ~~Phase 3~~ | ✅ 模块 1-2（claude-client.ts + AiChatSidebar.tsx） |
| ~~无审批回传通道~~ | ~~WebSocket 仅支持 message 和 ping 入站~~ | ~~Phase 3~~ | ✅ 模块 2（hitl_response 消息类型） |
| ~~编译/测试工具不存在~~ | ~~McpProxyService 无 compile / test~~ | ~~Phase 3~~ | ✅ 模块 3（workspace_compile + workspace_test 语法分析） |
| ~~度量无可视化~~ | ~~Micrometer 数据仅在 JMX/Actuator~~ | ~~Phase 3~~ | ✅ 模块 4（DashboardController 3 端点 + QualityPanel） |
| ~~Learning Loop 未集成~~ | ~~3 个组件是独立 .kt 文件~~ | ~~Phase 3~~ | ✅ 模块 5（ExecutionLoggerService + SkillFeedbackService Spring 化） |
| ~~Skill 架构不对齐 Anthropic 标准~~ | ~~渐进式加载 + 可执行脚本 + 管理 UI~~ | ~~Phase 4~~ | ✅ Phase 4（Session 25-26） |
| ~~无记忆系统~~ | ~~AI 跨 session 遗忘~~ | ~~Phase 5~~ | ✅ Phase 5（3 层记忆 + 消息压缩） |
| ~~Workspace 内存存储~~ | ~~重启数据丢失~~ | ~~Phase 6~~ | ✅ Sprint 6.1（DB + 磁盘持久化） |
| ~~无 Git 仓库载入~~ | ~~只能用空 workspace~~ | ~~Phase 6~~ | ✅ Sprint 6.1（git clone --depth 1） |
| ~~用户 API Key 不生效~~ | ~~ClaudeAdapter singleton 永远用 env var~~ | ~~Phase 6~~ | ✅ Sprint 6.2（per-request override） |
| ~~无代码转知识能力~~ | ~~载入仓库后无法分析~~ | ~~Phase 6~~ | ✅ Sprint 6.3（codebase-profiler + analyze_codebase） |
| ~~神类过大~~ | ~~ClaudeAgentService 1097行 / McpProxyService 1515行~~ | ~~Phase 6~~ | ✅ Sprint 6.4（拆分为 9 个服务，最大 547 LOC） |
| ~~MiniMax 模型支持~~ | ~~仅 5 Provider，无 MiniMax~~ | ~~Phase 7~~ | ✅ Session 31（MiniMax 3 模型端到端，复用 ClaudeAdapter） |
| ~~无评估 Profile~~ | ~~仅 5 Profile，缺少只读分析/评估能力~~ | ~~Phase 7~~ | ✅ Session 32（evaluation-profile + ProfileRouter 关键词路由） |
| ~~知识库无本地写入~~ | ~~page_create 仅 MCP Server 模式~~ | ~~Phase 7~~ | ✅ Session 32（PageCreateTool 本地模式，写入 knowledge-base/） |
| ~~学习闭环未落地~~ | ~~InteractionEvaluation / LearningLoop 无实现~~ | ~~Phase 7~~ | ✅ Session 32（InteractionEvaluation 全栈 + LearningLoopPipelineService + SkillFeedbackService 4D 评分） |
| ~~Context Usage 不持续~~ | ~~仅初次返回，后续无更新~~ | ~~Phase 7~~ | ✅ Session 32（每轮发送 context_usage + turn 字段，前端始终可见） |
| ForgeNativeRuntime | AgentLoop.kt / HookEngine.kt / ContextBuilder.kt 完整抽象 | Phase 8+ | 已由直接实现替代，远期评估 |
| asset-extractor 自动化 | 从执行日志提取知识资产 → 自动更新 Skill / 知识库 | Phase 8+ | 待评估 |
| PostgreSQL 切换 | H2 → PostgreSQL 生产数据库 | Phase 8 | 待启动 |

---

## 九、验证方式

| 验证对象 | 验证方法 | 命令/操作 | 状态 |
|---------|---------|----------|------|
| Skill 结构 | 自动化校验 | `./gradlew :skill-tests:test` | ✅ |
| MCP Server | 构建 + 集成测试 | `./gradlew :mcp-servers:forge-knowledge-mcp:test` | ✅ |
| CLI | 单测 + 端到端 | `./gradlew :cli:test` + `forge doctor` | ✅ |
| Web IDE 前端 | 单测 + E2E | `cd web-ide/frontend && npm test` + Playwright | ✅ 单测 / ⏳ Playwright |
| Web IDE 后端 | 集成测试 | `./gradlew :web-ide:backend:test` | ✅ 156 tests |
| Workspace 工具 | 单元测试 | McpProxyServiceTest — 40+ 测试用例（workspace + MCP 工具） | ✅ |
| Context API | 单元测试 | ContextControllerTest — 4 个类别搜索测试 | ✅ |
| Keycloak SSO | 容器健康检查 | docker-compose.trial.yml — 4 容器全部 healthy | ✅ |
| Phase 1.6 验收 | E2E 手动测试 | `docs/phase1.6-e2e-acceptance-test.md` — 21 场景 87 用例 | ✅ 80/87 通过（92%） |
| Prometheus 指标 | Actuator 端点 | `curl localhost:9000/actuator/prometheus \| grep forge_` | ✅ 7 个 forge.* 指标 |
| 度量报告 | 文档 | `docs/metrics-report-phase1.6.md` | ✅ 已生成 |
| SuperAgent OODA | 手动测试 | 给定任务 → 观察 OODA → 检查底线 | ⏳ Phase 2 |
| 底线体系 | CI 运行 | PR 提交自动触发 baseline-runner | ⏳ Phase 2 |
| 进化环健康度 | 评估框架 | `./gradlew :agent-eval:run` | ⏳ Phase 3 |
| 设计回归 | 底线脚本 | `design-regression-baseline.sh` | ⏳ 待 CI 集成 |
| 部署就绪 | 底线脚本 | `deployment-preflight-baseline.sh` | ⏳ 待 CI 集成 |
| 运行时健康 | 底线脚本 | `runtime-health-baseline.sh` | ⏳ 待 CI 集成 |

---

## 十、价值度量体系

| 层 | 时间线 | 指标 | 目标 | Phase 1.6 实际 |
|---|-------|------|------|---------------|
| 活跃度 | 上线即采集 | 日活率 / Skill 触发次数 / MCP 调用量 / Web IDE 会话 | 4 周 60%+ 渗透 | — |
| 效率 | 4-8 周对比 | PR 周期 / 审查时间 / 新人上手 / Bug 修复时间 | 缩短 30-60% | — |
| 质量 | 8-12 周趋势 | 生产 Bug 率 / 规范一致性 / 测试覆盖 / 架构合规 | 持续改善 | — |
| 迁移效率 | 迁移项目启动时 | 跨栈迁移人天对比（传统 vs Forge） | 10x+ 人天提效 | PoC 100%（11/11 规则覆盖） |
| Agent 可靠性 | 上线即采集 | OODA 一次通过率（vs logbook 67% 基线） | ≥ 85% | OODA 5 阶段全采集（observe:9/orient:9/decide:9/act:10/complete:7） |
| 质量安全网 | 上线即采集 | 安全漏洞逃逸数（vs logbook CORS 事件） | 零逃逸 | ✅ 零逃逸 |
| 知识持久性 | 4 周后对比 | 跨 session 信息损失率 | → 0 | 18 Session 零中断（logbook + 双基线 + CLAUDE.md） |
| Runtime 独立性 | Phase 2+ | Web IDE 不依赖 Claude Code 的功能覆盖率 | Phase 3 达到 100% | — |
| 设计保真度 | Phase 1.5+ | 设计基线回归测试通过率 | 100%（非预期变更 = 0） | 手动 100%，自动化待 CI |
| 部署效率 | Phase 1.5+ | 首次部署成功率（pre-flight check 后） | ≥ 90%（vs 当前 7.7%，1/13） | ✅ Session 14: 一次成功 |
| 调试效率 | Phase 1.5+ | 构建失败到定位根因的平均时间 | ≤ 5 分钟（vs 当前 ~7 分钟/次） | — |
| E2E 验收通过率 | Phase 1.6+ | 验收测试通过 / 总数 | ≥ 90% | ✅ 92.0%（80/87） |
| Bug 修复率 | Phase 1.6+ | 已修复 / 已发现 | ≥ 95% | ✅ 95%（19/20） |

> Phase 1.6 实际度量数据详见 `docs/metrics-report-phase1.6.md`，包含 7 个 Prometheus forge.* 指标的完整采集数据。

数据来源：

| 指标 | 数据来源 |
|------|---------|
| 迁移效率 | 迁移项目实际记录 |
| Agent 可靠性 | execution-logger |
| 质量安全网 | security-baseline 拦截记录 |
| 知识持久性 | knowledge-base 查询覆盖率 |
| Runtime 独立性 | 功能矩阵对比 |
| 设计保真度 | design-regression-baseline.sh 执行日志 |
| 部署效率 | deployment-preflight-baseline.sh 拦截率 + 部署成功率 |
| 调试效率 | execution-logger 中失败→修复的时间差 |

---

## 十一、交付方法论

> 详细分析：`docs/delivery-methodology-analysis.md` | 综合评分：4.4/5.0
> 核心理念：**用文档对抗 AI 遗忘，用验收测试对抗质量腐化，用双基线对抗设计偏移。**

### 11.1 四维项目记忆系统

| 记忆维度 | 载体 | 回答的问题 |
|---------|------|-----------|
| 时间维度 | `dev-logbook.md`（Logbook） | 过去发生了什么？为什么这样做？ |
| 空间维度 | 设计基线 + 规划基线（双基线） | 系统当前的全貌是什么？ |
| 质量维度 | 验收测试 + `buglist.md` | 什么能工作？什么不能？ |
| 知识维度 | `CLAUDE.md` + 经验沉淀 | 我们学到了什么？ |

### 11.2 Session 微型 PDCA 循环

每个 Session（~2-3 小时 Claude Code 对话）遵循固定结构：

```
目标声明 → 实施（代码/文档） → Bug 修复 → 文件变更表 → 经验沉淀 → 统计快照 → Git 提交
```

Session 结束时 Claude 主动提醒用户完成所有环节。

### 11.3 经验编码管道

```
logbook 经验沉淀 → 跨 2+ Session 验证 → 编码到 CLAUDE.md（已知陷阱/纪律/方法论）
```

### 11.4 关键观察（18 Session 数据）

- **"写完代码"只是交付的一半**：Session 12 之后的 6 个 Session（33%）全部用于测试、修 Bug 和文档治理
- **文档债务需要定期清理**：每 5 个 Session 做一次全量审查（格式统一、去重、清理版本批注）
- **CLAUDE.md 持续进化**：67 行 → 228 行，编码了交互偏好、架构知识、9 条已知陷阱、四大开发纪律、交付方法论

### 11.5 方法论平台化路径

手动验证有效 → 编码为平台能力，是 Forge 的核心进化逻辑：

```
Phase 0-2：手动验证方法论有效性
  │  Logbook + 双基线 + 验收测试 + 经验编码
  │  → 18 Session 零中断、92% 验收通过率、20 Bug 闭环追踪
  │
  ▼
Phase 3：将方法论编码为平台自动能力
  │  execution-logger + design-baseline-tracker + asset-extractor + skill-feedback-analyzer
  │  → 用户无需手动维护即可获得相同质量保障
  │
  ▼
Phase 4：飞轮效应
     平台自动记录 → 自动提取知识 → 自动优化 Skill → 交付质量持续提升
     → 更多数据 → 更好的知识 → 更强的 Skill → ♻️
```

这也是目标 6"方法论内化"（§1.1）和核心判断"手动验证的方法论是平台能力的种子"（§1.3）的具体落地路径。

---

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| v1.0 | 2026-02-17 | 初始基线 |
| v1.1 | 2026-02-17 | 融入跨栈迁移、Runtime 脱离路径、质量改进基线 |
| v1.2 | 2026-02-17 | Phase 1 完成状态更新；新增 Phase 1.5 内部试用计划；统一路线图；更新进度基线和里程碑 |
| v1.3 | 2026-02-18 | 设计守护体系：新增原则 11-12；新增 3 底线脚本 + 3 Foundation Skill；增强进化环；创建设计基线文档；新增 3 度量指标 |
| v1.4 | 2026-02-19 | 文档重构 + Phase 1.6 纳入：新增 Phase 1.6 阶段（AI 交付闭环 / Keycloak SSO / Context Picker / CRUD / Apply / 自动保存 / 知识库扩展）；记录 MCP 工具聚合策略（20→9）；Docker 3→4 容器（+Keycloak）；5 Profile 全完成；更新进度基线（320+ 文件 / 45K+ 行 / 130+ 测试）；标注 5 个 Gap；消除散落的版本批注，统一格式 |
| v1.4.1 | 2026-02-19 | 数据校准：修正 MCP 聚合工具名（与 McpProxyService 代码对齐：knowledge_search→search_knowledge 等 6 处）；修正 Context Picker 类别（skills/profiles→schema/services） |
| v1.5 | 2026-02-20 | Phase 2 计划纳入 + 方法论升级 + 整体审视：Phase 2 从 12 交付物重构为 Sprint 2.1/2.2/2.3；新增设计原则 13-14（场景先行验收 + 本地验证优先）；新增 §11 交付方法论（四维记忆系统 + PDCA + 经验编码管道）；度量体系加入 Phase 1.6 实际数据（E2E 92%、Bug 修复率 95%、7 个 Prometheus 指标）；更新进度基线到 Session 18（325+ 文件 / 50K+ 行 / 147 测试 / 34 commits）；Gap 清单细化到 Sprint 级别；**整体审视**：§1.1 新增目标 6（方法论内化）；§1.3 新增核心判断（手动方法论→平台能力种子）；§3.6 ForgeNativeRuntime 增加手动实践映射列；§4.6 进化环增加方法论→平台组件映射表；Phase 3 重写为「交付方法论平台化 + 进化环闭合」（Sprint 3.1/3.2/3.3）；Phase 4 重写为表格格式（含飞轮验证标准）；§11.5 新增方法论平台化路径 |
| v1.6 | 2026-02-21 | **Phase 2 完成 + Phase 3 重构**：Phase 2 全部 4 个 Sprint 标记完成（Sprint 2.1 CI/Playwright ✅、Sprint 2.2 OODA+MCP ✅、Sprint 2.3 多模型 ✅、Sprint 2.4 内部试用 ✅）；Phase 2 验收标准全部勾选；Sprint 2.4 试用反馈（4 条核心反馈）纳入文档；**Phase 3 从「ForgeNativeRuntime + 进化环」重构为「人机协作闭环」**（6 模块 16 步：执行透明度 / HITL 全量暂停点 / 编译测试管道 / 质量度量面板 / 学习循环集成 / 文档验收）；ForgeNativeRuntime 完整体推迟至 Phase 3+；Gap 清单更新（6 个已关闭 + 8 个新增 Phase 3 Gap）；里程碑更新（Phase 2 全部 ✅，Phase 3 拆分为独立里程碑）；新增文档引用：`docs/sprint2.4-trial-discussion-record.md`、`docs/planning/phase3-implementation-plan.md` |

| v1.7 | 2026-02-21 | **Phase 3 实现完成**：6 模块 16 步全部实现（模块 1 执行透明度 ✅、模块 2 HITL 暂停点 ✅、模块 3 编译/测试管道 ✅、模块 4 质量度量面板 ✅、模块 5 学习循环集成 ✅、模块 6 文档验收 ✅）；新建 12 文件（后端 7 + 前端 2 + 配置/文档 3）；修改 11 文件（后端 5 + 前端 3 + 配置/文档 3）；6 个 Phase 3 Gap 全部关闭；24 验收用例；新增文档：`docs/phase3-acceptance-test.md` |
| v1.8 | 2026-02-22 | **Phase 4 完成 + Phase 5 计划**：Phase 4 全部 4 个 Sprint 标记完成（Sprint 4.1 Metadata 架构 ✅、Sprint 4.2 可执行脚本 ✅、Sprint 4.3 管理 API+UI ✅、Sprint 4.4 生态度量 ✅）；Skill 32→28（质量治理）；MCP 工具 12→16；新增 /skills 管理页面 |
| v1.9 | 2026-02-22 | Phase 4 实施计划详细化（对齐 Anthropic Agent Skills 标准） |
| v2.0 | 2026-02-23 | **Phase 4/5/6 全部完成**：Phase 4 Skill 架构改造 ✅、Phase 5 记忆与上下文管理 ✅、Phase 6 产品可用性加固 ✅（4 Sprint: Workspace 持久化+Git+API Key+codebase-profiler+架构重构）；数据校准：17 MCP / 156 测试 / 30 Skill / V1-V6 migration / 设计基线 v10；Phase 7-9 路线图；Gap 清单全量刷新（+7 已关闭）；ForgeNativeRuntime 标记为「已由直接实现替代」|
| v2.1 | 2026-02-23 | **Phase 7 实现（Session 30）**：Git Clone 异步化 + 知识库 Scope 三层分层 + 品牌重定位统一为"AI 驱动的智能交付平台"；数据校准（17 MCP / 156 测试 / 30 Skill / V1-V8 migration） |
| v2.2 | 2026-02-23 | **Phase 7 完成（Session 31-32）**：MiniMax 多模型端到端（3 模型，第 6 Provider）+ Evaluation Profile（第 6 个 Profile）+ 4 新 Skill（bug-fix-workflow / document-generation / knowledge-distillation / progress-evaluation）+ PageCreateTool 本地模式（知识库写入）+ Context Usage 每轮增强 + InteractionEvaluation 全栈 + LearningLoopPipelineService 学习闭环 + SkillFeedbackService 4D 评分 + H2 持久化恢复 + MAX_AGENTIC_TURNS 8→50；数据校准：18 MCP / 156 测试 / 32 Skill / 6 Profile / 6 Provider / V1-V8 migration / 设计基线 v12；Buglist 32（30 fixed / 1 pending / 1 unfixed）；演进路线重编号（Phase 7 实现，Phase 8-10 路线图）；5 个新 Gap 关闭 |

> 基线版本: v2.2 | 基线日期: 2026-02-23 | 下次评审: Phase 8 启动前
