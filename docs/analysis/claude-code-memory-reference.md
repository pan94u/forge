# Claude Code 记忆与上下文管理设计深度分析

## 概述

Claude Code 的上下文管理解决的核心矛盾是：**LLM 是无状态的，但开发者需要跨会话的连续性**。它的方案是通过一个多层级的记忆架构 + 动态上下文管理来实现。

---

## 一、4 层记忆架构（优先级从高到低）

```
┌─────────────────────────────────────────┐
│ 1. Enterprise Policy (最高优先级)       │ ← 组织 IT 全局规则
├─────────────────────────────────────────┤
│ 2. Project Memory (CLAUDE.md)           │ ← 项目级指令
├─────────────────────────────────────────┤
│ 3. Project Rules (.claude/rules/)       │ ← 模块化规则（支持 glob 匹配）
├─────────────────────────────────────────┤
│ 4. User Memory (~/.claude/CLAUDE.md)    │ ← 个人偏好
└─────────────────────────────────────────┘
```

### 1.1 Enterprise Policy（企业策略层）

- 组织管理员定义的全局规则，通过 MDM、Group Policy、Ansible 等配置管理系统分发
- 对所有用户生效，**优先级最高，不可被下层覆盖**

### 1.2 Project Memory（`CLAUDE.md`）

项目根目录的 Markdown 文件，定义项目级上下文：

```markdown
# CLAUDE.md 示例

## Tech Stack
- Next.js 14 with App Router
- Strict TypeScript
- Prisma + PostgreSQL
- Tailwind CSS

## Conventions
- Use Server Components by default
- Name files in kebab-case
- Tests with Vitest

## Strict Rules
- NEVER expose API keys client-side
- ALWAYS validate inputs with Zod
```

- 每次会话启动时自动加载
- 支持 `@path/to/import` 语法引入其他文件（模块化管理）
- 上层目录的 CLAUDE.md 在启动时完整加载；子目录的 CLAUDE.md **按需加载**（当 Claude 读取该目录文件时才加载）

### 1.3 Project Rules（`.claude/rules/`）

模块化规则文件，支持 `globs` 匹配实现按需加载：

```
.claude/rules/
├── typescript.md          # TypeScript 规则
├── testing.md             # 测试规则
├── api/
│   └── security.md        # API 安全规则（仅匹配 src/api/**/*.ts）
└── frontend/
    └── components.md      # 组件规则
```

示例规则文件（带 glob 匹配）：

```markdown
---
globs: ["src/api/**/*.ts", "src/routes/**/*.ts"]
---

# API Security Rules
- Always validate JWT tokens
- Rate limiting on all endpoints
- Log suspicious access
```

**精髓**：规则只在 Claude 操作匹配路径的文件时才被加载，节省 token。

### 1.4 User Memory（`~/.claude/CLAUDE.md`）

用户个人偏好，跨所有项目生效。存放个人编码风格、常用工具链等通用偏好。

---

## 二、Auto Memory（自动记忆系统）

### 2.1 工作原理

Claude Code 自动将有价值的上下文（项目模式、关键命令、用户偏好）保存到项目记忆目录：

```
~/.claude/projects/<project>/memory/
├── MEMORY.md              # 精简索引，每次会话加载（限前 200 行）
├── debugging.md           # 调试模式笔记
├── api-conventions.md     # API 设计决策
└── ...                    # 其他 Claude 创建的主题文件
```

### 2.2 设计要点

- **MEMORY.md 是索引**：仅加载前 200 行，保持精简
- **详细内容分散在主题文件中**：按需查阅，不会一次性占满上下文
- **项目隔离**：每个 git 仓库有独立的记忆目录，同一仓库的子目录共享
- **Git worktree 独立**：不同 worktree 有各自的记忆目录

### 2.3 使用方式

- **自动保存**：Claude 在对话中自动识别并保存有价值的模式
- **主动要求**：`"remember that we use pnpm, not npm"` 或 `"save to memory that the API tests require a local Redis instance"`
- **快速记忆**：用 `#` 前缀添加 → `# build fails if NODE_ENV isn't set explicitly`
- **编辑管理**：`/memory` 命令打开文件选择器

### 2.4 环境变量控制

```bash
export CLAUDE_CODE_DISABLE_AUTO_MEMORY=1  # 强制关闭
export CLAUDE_CODE_DISABLE_AUTO_MEMORY=0  # 强制开启
```

---

## 三、上下文窗口的动态管理

### 3.1 200K Token 窗口分配

```
┌──────────────────────────────────────────────────────┐
│ System Prompt                          ~5-15K tokens │
├──────────────────────────────────────────────────────┤
│ CLAUDE.md 文件 (3 个位置)              ~1-10K tokens │
├──────────────────────────────────────────────────────┤
│ Rules (.claude/rules/*.md)                  ~变量     │
├──────────────────────────────────────────────────────┤
│ MCP 工具 Schema              ~500-2000/server tokens │
├──────────────────────────────────────────────────────┤
│ 对话历史 + 工具调用结果                              │
│ (用户提示 + Claude 响应 + 工具结果)                  │
│                                    ~140-150K tokens  │
├──────────────────────────────────────────────────────┤
│ 响应生成缓冲区                       ~40-45K tokens  │
│ (思考 + 代码输出)                                    │
└──────────────────────────────────────────────────────┘
```

### 3.2 上下文使用率 4 区间模型

| 区间 | 使用率 | 状态 | 建议操作 |
|------|--------|------|----------|
| 🟢 绿色 | 0-50% | 安全 | 正常工作 |
| 🟡 黄色 | 50-70% | 关注 | 开始注意 token 消耗 |
| 🟠 橙色 | 70-90% | 需干预 | 执行 `/compact` 或整理上下文 |
| 🔴 红色 | 90%+ | 危险 | 必须立即清理或重新开始 |

### 3.3 核心管理机制

#### `/compact`（压缩）

- **触发时机**：自动触发约在 75-92% 使用率（具体阈值由内部启发式决定）
- **工作流程**：
  1. 系统注入摘要 prompt 作为 user turn
  2. Claude 生成结构化摘要（`<summary></summary>` 标签包裹）
  3. SDK 提取摘要，替换整个消息历史
- **清理优先级**：先清除旧的工具输出，再摘要对话内容
- **保留内容**：用户请求和关键代码片段
- **丢失风险**：早期对话中的详细指令可能丢失
- **聚焦压缩**：`/compact focus on the API changes` 指定关注重点
- **Compact Instructions**：在 CLAUDE.md 中添加专门的"Compact Instructions"章节，控制压缩时保留什么

#### `/clear`（清空）

- 完全清空当前对话历史
- CLAUDE.md 和项目文件依然可用（会重新读取）
- **适用场景**：
  - 完成一个功能后切换到下一个任务
  - Claude 陷入循环或困惑时重置
  - 管理成本和速度

#### Context Editing（上下文编辑 - API 层能力）

- **Tool Result Clearing**：自动清除旧的工具调用结果（文件读取、搜索结果等）
- **Thinking Block Clearing**：管理扩展思考块，可选择保留最近的思考以维持连续性
- **工作方式**：按时间顺序清除最旧的工具结果，保留对话流程
- **与 Prompt Caching 的交互**：清除内容会使缓存前缀失效，需通过 `clear_at_least` 参数确保清除足够多 token 以值得缓存失效的代价

#### Subagent 隔离

- 子代理拥有**完全独立的上下文窗口**
- 主会话派出子代理执行调研或任务
- 子代理完成后只返回摘要
- **关键优势**：避免主上下文被大量中间结果污染

### 3.4 "完成缓冲区"设计

最近的优化把自动 compact 触发点从 ~90% 提前到了 ~75%：

```
旧策略：                          新策略：
[████████████████████░░] 90%     [██████████████░░░░░░░░] 75%
                    ↑                           ↑
              触发 compact                 触发 compact
              (剩余空间不足)             (保留 25% 工作空间)
```

- 看似"浪费"了 25% 的窗口（~50K tokens）
- 实际给了模型充足的工作空间完成当前任务
- 避免在操作中途（如重构一半）被打断
- 研究表明：上下文填充到接近极限时，LLM 性能会**显著下降 20-30%**

---

## 四、最佳实践模式

### 4.1 Bootstrap Pattern（引导模式）

```bash
# 新项目初始化
claude /init
# Claude 分析代码库并生成全面的 CLAUDE.md
```

### 4.2 Quick Memory Pattern（快速记忆）

```
# build fails if NODE_ENV isn't set explicitly
```

用 `#` 前缀即时添加到记忆。

### 4.3 Checkpoint Pattern（检查点模式）

```
# 大型重构前
git add -A && git commit -m "checkpoint before refactoring"

# 告诉 Claude 更新记忆文件
"Update memory files with current architectural decisions"
```

### 4.4 Fresh Context Pattern（全新上下文模式）

```
Session 1: 探索和规划
    └─ /compact 收尾

Session 2: 实现功能 A
    └─ /compact 收尾

Session 3: 实现功能 B
    └─ /compact 收尾
```

- 每个任务用全新上下文启动
- 状态通过文件和 git 持久化
- 避免累积退化

### 4.5 日常工作流

```
1. 启动会话
2. 如需要，回顾上次进度
   > "Summarize what we did yesterday on the auth feature"
3. 定义今日任务
   > "Today, we continue with tests"
4. 工作中（每 30-45 分钟）
   /cost    # 检查用量
   # 如果 > 50k tokens → /compact
5. 切换主要任务前
   /clear
6. 会话结束
   # 更新 CLAUDE.md
```

---

## 五、Token 消耗参考

| 操作类型 | 大致 Token 消耗 |
|----------|----------------|
| 系统 Prompt | ~5-15K |
| CLAUDE.md（3 个位置合计）| ~1-10K |
| 单个 MCP Server Schema | ~500-2000 |
| 每条 Rules 文件 | ~变量 |
| 响应生成缓冲区 | ~40-45K |
| 可用对话空间 | ~140-150K |

---

## 六、与其他方案的对比

### Claude Code（上下文注入） vs 传统 RAG

| 维度 | Claude Code（上下文注入） | 传统 RAG |
|------|--------------------------|----------|
| 存储 | Markdown 文件 | 向量数据库 |
| 检索 | 直接加载到 prompt | 语义搜索 |
| 透明度 | 完全可读可编辑 | 黑盒 |
| 扩展性 | 受限于上下文窗口 | 理论上无限 |
| 复杂度 | 极低 | 较高 |
| 版本控制 | 天然支持 git | 需要额外方案 |
| 基础设施依赖 | 无 | 需要向量 DB |
| 企业审计 | 简单直观 | 复杂 |

### 核心取舍

> 用扩展性换来了极高的**透明度**和**可控性**，同时配合 compact、子代理等机制来缓解上下文窗口的硬限制。

---

## 七、设计哲学总结

1. **文件即记忆，透明可控**：不引入向量数据库等外部依赖，Markdown 文件可随时查看、编辑、git 版本控制
2. **按需加载，token 节约**：子目录 CLAUDE.md 按需加载、rules 支持 glob 匹配、MCP 工具 schema 懒加载（减少 ~85% 初始开销）
3. **层级覆盖，职责分离**：企业策略 > 项目规范 > 模块规则 > 个人偏好，类似 CSS cascading 逻辑
4. **渐进式降级**：先清理工具输出 → 再压缩对话 → 最后才是清空重来
5. **留白哲学**：保留 25% 上下文窗口作为工作空间，宁可早压缩也不要让模型在拥挤的上下文中挣扎
6. **状态外置**：核心状态通过文件和 git 持久化，而非依赖内存中的对话历史

---

*文档整理时间：2026年2月22日*
*信息来源：Anthropic 官方文档、Claude Code Docs、社区实践总结*
