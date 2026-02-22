# 分析：Forge 平台能否脱离 Claude Code 运行

> 日期：2026-02-17
> 目的：评估 Forge 平台对 Claude Code 的依赖程度，以及脱离的路径

---

## 当前依赖分析

### 组件耦合度

| 组件 | 依赖 Claude Code？ | 耦合方式 |
|------|-------------------|---------|
| **MCP Servers** (5 个) | **不依赖** | 标准 HTTP Server，任何 MCP 客户端都能调用 |
| **Web IDE 前端** | **不依赖** | React/Next.js，通过 WebSocket/REST 连后端 |
| **Web IDE 后端** | **不依赖** | Spring Boot，直接调 Claude API（通过 ClaudeAdapter） |
| **Adapters** | **不依赖** | 直接调 Anthropic API / Bedrock / 本地模型 |
| **Baselines** (.sh) | **不依赖** | 纯 Shell 脚本，任何 CI 都能跑 |
| **Forge CLI** | **不依赖** | 独立 Kotlin 应用 |
| **Foundation Skills** (SKILL.md) | **强依赖** | Claude Code 的 Skill 加载格式 |
| **Slash Commands** (.md) | **强依赖** | Claude Code 的命令格式 |
| **Skill Profiles** (.md) | **强依赖** | Claude Code 插件系统读取 |
| **Hooks** (hooks.json) | **强依赖** | Claude Code 的 PreToolUse/PostToolUse 机制 |
| **CLAUDE.md** (三级) | **强依赖** | Claude Code 的上下文注入机制 |
| **Agents** (.md) | **强依赖** | Claude Code 的 Agent 定义格式 |
| **Plugin 体系** (.claude-plugin/) | **强依赖** | Claude Code 的插件注册机制 |

### 架构示意

```
Forge 平台组件
│
├── 独立层（不依赖 Claude Code）
│   ├── MCP Servers (Kotlin/Ktor HTTP)     ← 标准协议，任何客户端可用
│   ├── Web IDE (React + Spring Boot)      ← 直接调 Claude API
│   ├── Adapters (ModelAdapter 接口)       ← 可接任何模型
│   ├── Baselines (Shell 脚本)             ← 纯 CI 工具
│   ├── Forge CLI (Kotlin)                 ← 独立二进制
│   └── knowledge-base (Git 仓库)          ← 纯数据
│
└── 耦合层（强依赖 Claude Code）
    ├── 15 个 SKILL.md                     ← Claude Code 格式
    ├── 6 个 Slash Commands                ← Claude Code 格式
    ├── 5 个 Skill Profiles                ← Claude Code 插件加载
    ├── 4 个 Agent 定义                    ← Claude Code 格式
    ├── hooks.json                         ← Claude Code Hook 机制
    ├── CLAUDE.md (三级)                   ← Claude Code 上下文
    └── .claude-plugin/plugin.json         ← Claude Code 插件注册
```

**结论：大约 60% 的"代码基础设施"是独立的，但 100% 的"智能层"绑定在 Claude Code 上。**

---

## 两条路径的独立性

### 路径 A：开发者通过 CLI/IDE 使用（强依赖）

```
开发者 → Claude Code CLI → 加载 Plugin → 加载 Skill/Command/Hook → 调用 Claude API
                ↑                                                       ↕
           完全绑定                                                  MCP Servers
```

这条路径无法脱离 Claude Code。Skill 怎么加载、Profile 怎么切换、Hook 怎么触发——全部是 Claude Code 的运行时机制。

### 路径 B：用户通过 Web IDE 使用（基本独立）

```
用户 → Web IDE 前端 → Spring Boot 后端 → ClaudeAdapter → Claude API
                                       → McpProxyService → MCP Servers
```

这条路径已经不依赖 Claude Code。Web IDE 后端直接调 Anthropic API，不经过 Claude Code。但问题是：Web IDE 后端目前没有 Skill 加载、Profile 切换、OODA 循环、底线检查这些能力——它只是一个简单的 chat proxy。

---

## 脱离 Claude Code 需要替换的核心能力

| Claude Code 提供的能力 | 替代方案 | 工作量 |
|----------------------|---------|--------|
| **Skill 加载**：读 SKILL.md → 注入 system prompt | 自建 SkillLoader：读 Markdown → 按 frontmatter trigger 条件 → 拼接到 prompt | M |
| **Profile 切换**：根据上下文选择 Skill 组合 | 自建 ProfileRouter：解析用户意图 → 选择 Profile → 加载对应 Skill 集 | M |
| **OODA 循环**：自主决策 + 多轮 tool calling | 自建 AgentLoop：消息 → 模型 → tool_use → 执行 → 回传 → 循环 | L |
| **Hook 系统**：PreToolUse/PostToolUse | 自建 HookEngine：在 tool 调用前后执行检查脚本 | S |
| **CLAUDE.md 上下文**：三级配置合并注入 | 自建 ContextBuilder：读取三级 .md 文件 → 合并 → 注入 system prompt | S |
| **Tool Calling**：模型 → tool_use → 执行 → tool_result | ClaudeAdapter 已实现（Phase 1 WP1 补全） | 已有 |
| **Streaming**：SSE 流式输出 | ClaudeAdapter 已实现 | 已有 |

---

## 两种脱离策略

### 策略 A：最小替换——在 Web IDE 后端重建智能层

```
Web IDE 后端 (Spring Boot)
  ├── SkillLoader.kt        ← 读取 SKILL.md，解析 frontmatter
  ├── ProfileRouter.kt      ← 根据用户意图选择 Profile
  ├── AgentLoop.kt          ← OODA 循环 + tool calling
  ├── HookEngine.kt         ← 底线检查集成
  ├── ContextBuilder.kt     ← 三级上下文合并
  └── ClaudeAdapter.kt      ← 已有，调用 Claude API
```

工作量估算：**3-5 周**（1-2 人）。本质上是用 Kotlin 重新实现 Claude Code 的 Agent Runtime 子集。

优点：SKILL.md 格式不用改，所有 Skill/Profile/Command 内容资产全部复用。
缺点：需要持续跟进 Claude Code 的新特性（如果有价值的话）。

### 策略 B：适配器抽象——RuntimeAdapter

```
SuperAgent 层
    │
    ↓
RuntimeAdapter 接口（稳态）
    │
    ├── ClaudeCodeRuntime（当前）  ← 走 Claude Code CLI
    ├── ForgeNativeRuntime（未来） ← 自建 Agent Loop
    └── OtherAgentRuntime（未来） ← 接其他框架（LangChain, CrewAI, etc.）
```

适配器层设计中已经预留了 `RuntimeAdapter`，但目前是空壳。如果真正实现它，切换 Runtime 就像切换模型一样——改一个配置。

---

## 演进路线建议

### 短期（Phase 0-2）：不需要脱离

Claude Code 是当前最成熟的 Agent Runtime：
- Skill/Command/Hook/Plugin 系统完善
- 正好满足"开发者通过 CLI 使用"的路径 A
- 自己造轮子没有必要

### 中期（Phase 3-4）：应该具备脱离能力

原因：
1. **供应商风险**：如果 Anthropic 改变 Claude Code 的定价/许可/功能方向
2. **定制需求**：组织可能需要 Claude Code 不支持的运行时行为（如并行 OODA、自定义安全围栏）
3. **私有化部署**：某些环境可能无法安装 Claude Code CLI
4. **Web IDE 完整化**：Web IDE 路径需要完整的智能层，不能依赖用户本地装 Claude Code

### 推荐路径

```
Phase 0-1（现在）：
  路径 A: Claude Code CLI（强依赖，这是对的）
  路径 B: Web IDE（简单 chat proxy）

Phase 2：
  路径 A: Claude Code CLI（继续使用）
  路径 B: Web IDE + 自建 SkillLoader + ProfileRouter
         → Web IDE 开始具备独立的 Skill 加载和 Profile 切换能力

Phase 3：
  路径 A: Claude Code CLI（可选）
  路径 B: Web IDE + ForgeNativeRuntime（完整 OODA + 底线 + HITL）
         → 两条路径功能对等，Claude Code 变成"可选项"而非"必选项"

Phase 4+：
  RuntimeAdapter 完全抽象
  → Claude Code / ForgeNative / 其他框架均可作为运行时
  → 组织自主选择
```

---

## 关键设计原则

> **SKILL.md 的内容格式应该从一开始就设计为"运行时无关"的。**

SKILL.md 不应该包含 Claude Code 特有的指令（如 `use the Read tool`），而应该是通用的知识描述。运行时特定的行为（如何调用工具、如何执行底线）应该在 Runtime 层处理，不应该写进 Skill 内容里。

这一点在当前设计中**没有做到**——很多 Skill 内容假设了 Claude Code 的工具集（Read、Write、Bash、Grep 等）。这是一个需要在 Phase 1-2 逐步修正的问题。
