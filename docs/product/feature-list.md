# Forge Platform — 产品功能清单

> 版本: v1.1 | 日期: 2026-02-24 | 基于 Design Baseline v12 + 代码实现交叉校验
>
> 本文档面向目标客户，详细描述 Forge 平台的功能性与非功能性特性。

---

## 一、平台定位

Forge 是一个 **AI 驱动的智能软件交付平台**，将 AI Agent 深度嵌入软件开发全生命周期（规划→设计→开发→测试→运维），通过 **SuperAgent 架构**（单智能体 + 动态 Skill Profile 切换）替代传统的多 Agent 方案，实现：

- **自主执行**：用户声明意图，SuperAgent 自主完成规划→编码→验证→交付的完整闭环（最多 50 轮自主执行）
- **一个 Agent 全流程覆盖**：单一 SuperAgent 通过 6 个 Skill Profile 动态切换专业角色，替代多 Agent 方案
- **人机协同**（Human-In-The-Loop）：关键决策点由人类审批，而非全自动黑盒
- **知识驱动**：32 个 Skill 将专业知识编码为可复用、可执行的标准化资产
- **底线质量保障**（Baseline）：无论模型能力如何变化，质量下限不变
- **记忆增强**：3 层跨 Session 记忆 + 四维评估学习闭环，Agent 越用越懂你的项目

---

## 二、功能性特性

### 2.1 Web IDE — 云端集成开发环境

#### 2.1.1 代码编辑器（Monaco Editor）

| 特性 | 说明 |
|------|------|
| **多语言语法高亮** | 支持 25+ 语言：TypeScript、Python、Kotlin、Java、Go、Rust、YAML、JSON、SQL、Markdown 等 |
| **智能编辑辅助** | 自动缩进、括号匹配、括号对着色、自动闭合引号/括号、粘贴格式化 |
| **多文件 Tab** | 多标签页切换，未保存文件蓝色圆点提示 |
| **Minimap** | 代码缩略图导航 |
| **代码折叠** | 区域折叠/展开 |
| **AI 解释** | 选中代码 → 一键 "AI Explain"，自动发送到 AI 聊天面板解读 |
| **自动保存** | 编辑后 5 秒自动保存（Debounce），Cmd+S / Ctrl+S 手动保存 |

#### 2.1.2 文件管理器（File Explorer）

| 特性 | 说明 |
|------|------|
| **树状文件浏览** | 递归目录展开/折叠，语法感知图标（代码/JSON/Markdown/图片等） |
| **右键上下文菜单** | 新建文件、新建文件夹、重命名、复制路径、AI 解释、删除 |
| **快捷操作栏** | 顶部一键新建文件/文件夹按钮 |
| **同级重名检测** | 创建/重命名时自动检测同级文件名冲突 |
| **拖拽支持** | 文件拖拽操作 |

#### 2.1.3 终端面板（Terminal Panel）

| 特性 | 说明 |
|------|------|
| **WebSocket 实时终端** | 通过 WebSocket 连接后端 Shell |
| **彩色输出** | 输入（绿色）、输出（白色）、错误（红色）、系统（黄色） |
| **命令历史** | 保留最近 500 行历史 |
| **连接状态** | 实时显示连接状态 |
| **可折叠** | 底部面板可收起/展开 |

#### 2.1.4 Workspace 管理

| 特性 | 说明 |
|------|------|
| **多 Workspace** | 每个项目一个独立 Workspace，互不干扰 |
| **生命周期管理** | 创建 → 激活 → 暂停 → 删除 |
| **默认脚手架** | 新建 Workspace 自动生成项目骨架（src/index.ts、package.json 等） |
| **文件 CRUD API** | 完整的文件树获取、内容读写、创建、删除 API |
| **Git 仓库载入** | 通过 Git URL clone 远程仓库到 Workspace（异步执行 + 前端进度条）|
| **数据库持久化** | Workspace 元数据持久化到数据库（JPA Entity），重启不丢失 |

---

### 2.2 SuperAgent 智能体引擎

Forge 的核心不是"聊天助手"，而是一个 **自主执行的 SuperAgent**——接收用户意图后，自动规划、执行、验证、修复，直到交付完成。

#### 2.2.1 Agentic Loop — 自主执行管线

用户发送一条消息后，SuperAgent 自动启动多轮执行管线（最多 50 轮），**无需用户逐步指令**：

```
用户消息
  │
  ▼
[1] Profile 路由 — 分析意图，自动选择专业角色（开发/设计/测试/评估…）
  │
  ▼
[2] Skill 加载 — 按 Profile + 关键词动态加载 3-20 个相关 Skill
  │
  ▼
[3] 记忆注入 — Workspace Memory + Stage Memory + Session History 注入上下文
  │
  ▼
[4] OODA 循环（每轮）
  │  ├─ Observe: 读取知识库、分析代码上下文、查询数据库 Schema
  │  ├─ Orient:  结合 Skill 指引和记忆，评估方案
  │  ├─ Decide:  选择工具、制定执行计划
  │  └─ Act:     调用 MCP 工具（读写文件、编译、测试、搜索知识）
  │
  ▼
[5] Context 管理 — 每轮估算 Token 使用率，超限自动压缩（3 阶段）
  │
  ▼
[6] 底线检查 — 代码生成后自动运行质量底线，失败则自动修复并重试
  │
  ▼
[7] 交付 — 写入 Workspace 文件 + 更新记忆 + 生成 Session Summary
```

**关键能力**：

| 能力 | 说明 |
|------|------|
| **自主多轮执行** | Agent 自行决定调用什么工具、读什么文件、写什么代码，单次对话最多 50 轮 |
| **工具链编排** | 自动串联多种工具：搜索知识→读取文件→生成代码→写入 Workspace→编译→运行测试 |
| **失败自修复** | 底线检查失败→自动分析失败原因→修改代码→重新检查（最多 2 轮） |
| **上下文自管理** | Token 接近上限时自动压缩历史消息，不中断执行 |
| **记忆增强** | 每次执行携带项目记忆（技术栈、关键决策、进行中的工作），无需重复说明 |

#### 2.2.2 流式通信 + 执行透明

| 特性 | 说明 |
|------|------|
| **实时流式输出** | 逐 token 输出，无需等待完整响应 |
| **WebSocket 优先** | WebSocket 全双工通信 → SSE 降级 → HTTP 同步兜底，三级通信保障 |
| **上下文附加** | `@` 提及机制附加文件、知识库文档、数据库 Schema、服务信息到对话 |
| **会话持久化** | 聊天记录持久化到数据库，刷新不丢失 |
| **思考过程可见** | AI 思考过程（Extended Thinking）动画展示 |

#### 2.2.3 OODA 循环可视化

Agent 的每轮执行遵循 OODA 决策循环，前端实时展示当前阶段：

| 阶段 | 图标 | 含义 |
|------|------|------|
| Observe | 👁 | 收集信息（读取知识库、分析上下文） |
| Orient | 🧭 | 判断方向（Profile 路由、Skill 选择） |
| Decide | 🧠 | 制定方案（Claude 开始生成回复） |
| Act | ⚡ | 执行操作（调用工具、生成代码、写入文件） |
| Complete | ✅ | 完成交付（持久化结果、生成摘要） |

- 实时显示当前轮次（Turn X）
- 当前执行的工具名称可见
- Context Usage 指示器始终显示：Token 使用率百分比 + 当前 Turn 编号 + 压缩阶段

#### 2.2.4 Profile 智能路由

Agent 根据用户消息自动切换专业角色（Profile），无需手动指定：

| 路由级别 | 触发方式 | 置信度 | 示例 |
|---------|---------|--------|------|
| L1: 显式标签 | `@规划`、`@设计`、`@开发`、`@测试`、`@运维`、`@评估` | 1.0 | `@设计 画一个架构图` |
| L2: 关键词 | 中英文关键词自动检测 | 0.6-0.8 | `帮我写接口` → development |
| L3: 分支名 | feature/\*、hotfix/\*、release/\* 模式匹配 | 0.5 | `feature/auth` → development |
| L4: 默认 | 无匹配时回退 | 0.3 | → development |

6 个 Profile 对应 6 个专业角色：

| Profile | 职责 | 典型 Skill |
|---------|------|-----------|
| **planning** | 需求分析、PRD 编写 | requirement-engineering、delivery-methodology |
| **design** | 架构设计、ADR 编写 | api-design、database-patterns |
| **development** | 编码实现、代码生成 | kotlin-conventions、spring-boot-patterns |
| **testing** | 测试策略、用例编写 | testing-standards |
| **ops** | 部署运维、问题排查 | logging-observability、deployment-readiness-check |
| **evaluation** | 进度评估、知识萃取、文档生成 | progress-evaluation、knowledge-distillation、document-generation |

> **evaluation** Profile 为只读模式（分析观察，不写代码）。

前端实时显示 **Profile Badge**：当前角色名称、加载的 Skill 列表、置信度圆点（绿/黄/灰）、路由原因。

#### 2.2.5 工具调用透明化

| 特性 | 说明 |
|------|------|
| **18 种 MCP 工具** | 覆盖文件操作、知识搜索、数据库查询、代码编译、测试运行、底线检查等 |
| **工具调用可展开** | 每次工具调用显示：工具名、输入参数、输出结果、执行时长 |
| **子步骤日志** | Activity Log 面板实时展示每个子步骤（加载记忆、执行工具、检查底线…） |
| **文件变更通知** | Agent 写入文件后自动通知前端，文件树刷新 + 自动打开新文件 |

#### 2.2.6 代码块一键 Apply

AI 回复中的代码块可一键写入 Workspace：

1. 代码块右上角显示 Apply 按钮（FileDown 图标）
2. 点击 → 输入文件名（基于语言自动推荐，20+ 语言映射）
3. 确认 → 自动写入 Workspace → 文件树刷新 → 自动打开

---

### 2.3 Human-In-The-Loop（HITL）人机协同

#### 2.3.1 HITL 审批检查点

在关键交付节点，Agent 自动暂停并请求人工审批：

| 触发场景 | 展示内容 |
|---------|---------|
| 规划阶段完成 PRD | 产出物列表 + 底线检查结果 |
| 设计阶段完成架构文档 | 设计文档列表 + 底线通过/失败状态 |

**审批面板**：
- 📄 **交付物清单**：列出本阶段生成的所有文件
- ✅/❌ **底线检查结果**：每个底线脚本的 PASS/FAIL 状态
- ⏱️ **倒计时**：5 分钟审批时限（<30 秒变红色警告）
- 三种操作：
  - **Approve**：确认通过，Agent 继续下一阶段
  - **Reject**：拒绝，Agent 停止
  - **Modify**：附加修改意见，Agent 根据反馈调整

> **注**：当前 HITL checkpoint 已简化为 intent confirmation 模式（AI 确认理解用户意图后继续执行），完整审批暂停点保留但默认禁用。

#### 2.3.2 底线自动检查（Baseline Auto-Check）

Agent 每次生成代码后，自动运行质量底线检查：

| 底线脚本 | 检查内容 |
|---------|---------|
| `code-style` | 代码风格规范 |
| `security` | 安全漏洞扫描 |
| `api-contract` | API 契约合规 |
| `architecture` | 架构约束检查 |

- 检查失败 → Agent 自动修复并重试（最多 2 轮）
- 重试仍失败 → 标记为 `exhausted`，交由用户判断
- 全部通过 → 继续后续流程

---

### 2.4 Skill 知识体系

#### 2.4.1 Skill 架构

Skill 是平台的核心知识单元，将专业知识编码为可复用、可组合、可执行的 Markdown 文件：

| 属性 | 说明 |
|------|------|
| **总数** | 32 个（均为 B 级以上质量） |
| **6 个 Profile** | planning / design / development / testing / ops / evaluation |
| **3 层存储** | PLATFORM（内置只读）/ WORKSPACE（项目级）/ CUSTOM（用户自定义） |
| **渐进式加载** | Level 1 元数据（始终加载）→ Level 2 完整内容（按需读取）→ Level 3 子文件+脚本（运行时） |
| **条件触发** | 根据 Profile 阶段 + 消息关键词动态加载 3-20 个 Skill |

**System Prompt 优化**：从 55K chars 优化到 20-25K chars（-74%），通过元数据注入 + MCP 按需加载实现。

**v1.1 新增 Skill**：bug-fix-workflow、document-generation、knowledge-distillation、progress-evaluation。

#### 2.4.2 Skill 管理

| 操作 | 说明 |
|------|------|
| **浏览** | 4 Tab 分类（全部 / Platform / Workspace / Custom）+ Tag 过滤 + 全文搜索 |
| **详情** | 查看 Skill 描述、版本、作者、标签、子文件列表、脚本列表 |
| **创建** | 创建自定义 Skill（名称、描述、标签、Markdown 内容） |
| **编辑/删除** | CUSTOM Skill 完全 CRUD，PLATFORM Skill 不可删除 |
| **启用/禁用** | 每个 Workspace 可独立控制 Skill 的启用状态 |
| **脚本执行** | 运行 Skill 附带的 Python/Bash/Kotlin 脚本（60 秒超时，沙箱隔离） |

#### 2.4.3 Skill 分析度量

| 度量 | 说明 |
|------|------|
| **使用排行** | 按使用频次排序的 Skill 排行榜（可按时间范围过滤） |
| **进化建议** | 自动识别未使用/低成功率的 Skill，推荐改进方向 |
| **触发分析** | 关键词到 Skill 的映射分析，优化触发规则 |
| **使用追踪** | 每次 Skill 读取/脚本运行记录到数据库 |

#### 2.4.4 Skill 反馈与学习闭环

| 功能 | 说明 |
|------|------|
| **四维评估** | 每次交互自动评估：意图理解（Intent）、完成度（Completion）、质量（Quality）、体验（Experience） |
| **Profile 聚合分析** | 按 Profile 汇总评估分数，识别薄弱环节 |
| **学习闭环管道** | LearningLoopPipelineService 自动：评估→诊断→建议→知识沉淀 |
| **反馈报告** | 按时间范围生成执行报告 + 四维评分 + Profile 对比 |

---

### 2.5 跨 Session 记忆系统

#### 2.5.1 三层记忆架构

解决 AI Agent "每次重新开始"的核心痛点：

| 层级 | 名称 | 范围 | 容量 | 加载方式 |
|------|------|------|------|---------|
| Layer 1 | **Workspace Memory** | 工作区级 | 4,000 字符 | 始终注入 system prompt |
| Layer 2 | **Stage Memory** | Profile × Workspace | 8,000 字符 | 按当前 Profile 注入 |
| Layer 3 | **Session Summary** | 单次会话 | 2,000 字符/条 | 最近 3 条注入 |

**Workspace Memory 内容示例**：
```
## 项目概况
- 项目名: 印章管理系统
- 技术栈: Kotlin + Spring Boot 3 + PostgreSQL

## 关键决策
- 采用状态机管理印章生命周期
- 数据库选择 PostgreSQL

## 当前阶段
- 设计阶段已完成（4 个设计文档）
- 开发阶段进行中
```

**效果**：
- 新 Session 启动时立即拥有项目上下文，无需重复探索
- 节省 30-40% Token 消耗
- 关键决策跨 Session 传递，不再丢失

#### 2.5.2 Session Summary 自动生成

每次会话结束后，异步生成结构化摘要：

| 字段 | 说明 |
|------|------|
| **summary** | 2-3 句话概述 |
| **completedWork** | 已完成的工作项列表 |
| **artifacts** | 产出文件路径列表 |
| **decisions** | 关键决策列表 |
| **unresolved** | 未解决问题列表 |
| **nextSteps** | 建议下一步列表 |

#### 2.5.3 消息压缩

长会话中自动管理 Context Window，防止性能劣化：

| 阶段 | 触发条件 | 操作 |
|------|---------|------|
| Phase 1 | Token > 25K | 工具输出截断到 500 字符 |
| Phase 2 | 仍超限 | 保留最近 3 轮，早期消息替换为摘要 |
| Phase 3 | 仍超限 | Claude 生成全量对话总结替换历史 |

**Context Usage 指示器**：前端始终显示 Token 使用率（如 `65% · T3 · P1`），其中 T=Turn 编号，P=压缩阶段。每轮 Agentic Loop 自动推送 `context_usage` 事件。

#### 2.5.4 记忆管理面板

前端提供可视化记忆管理界面（4-Tab 右侧面板的"记忆" Tab）：

| 子 Tab | 功能 |
|--------|------|
| **工作区记忆** | 查看/编辑/清空 Workspace Memory |
| **阶段记忆** | 查看各 Profile 的 Stage Memory（已完成工作、决策、未解决问题） |
| **会话历史** | 时间线浏览历史 Session 摘要 |

---

### 2.6 知识库（Knowledge Base）

#### 2.6.1 四类知识文档

| 类型 | 说明 | 示例 |
|------|------|------|
| **Wiki** | 项目 Wiki 文档 | 架构概览、技术选型 |
| **ADR** | 架构决策记录 | ADR-001 SuperAgent 架构、ADR-002 Skill 体系 |
| **Runbook** | 运维手册 | 部署指南、故障排查 |
| **API Doc** | API 文档 | Web IDE 后端 API、MCP 工具文档 |

当前内置 **13+ 篇知识文档**，可通过 MCP 工具搜索和引用。

#### 2.6.2 三层知识 Scope

| Scope | 优先级 | 说明 |
|-------|--------|------|
| **Global** | 最低 | 平台级知识，所有 Workspace 共享 |
| **Workspace** | 中 | 项目级知识，绑定到具体 Workspace |
| **Personal** | 最高 | 用户个人知识，仅自己可见 |

搜索时按 Workspace > Personal > Global 的优先级 cascade 返回结果。支持通过 REST API（POST/PUT/DELETE /api/knowledge/docs）进行 CRUD 管理。

#### 2.6.3 知识浏览功能

| Tab | 功能 |
|-----|------|
| **Docs** | 全文搜索 + 按类型过滤 + Markdown 渲染 + 动态目录 |
| **Architecture** | Mermaid 架构图渲染 + 缩放（25%-300%）+ 拖拽平移 + 导出 SVG |
| **Services** | ReactFlow 服务依赖图 + 节点状态颜色（健康/退化/故障）+ 影响分析 |
| **APIs** | API 目录 + 端点详情展开 + 参数文档 + "Try it out" 交互测试 |

#### 2.6.4 知识空白检测

`KnowledgeGapDetectorService` 自动分析用户提问，识别知识库未覆盖的话题：
- 追踪高频未覆盖主题
- 输出 Top 10 知识空白报告
- 为知识库扩充提供数据驱动的优先级

#### 2.6.5 知识写入（Knowledge Write）

Agent 可通过 `page_create` MCP 工具直接将知识写入知识库：

| 模式 | 说明 |
|------|------|
| **Local 模式** | KNOWLEDGE_MODE=local 时，直接写 Markdown 文件到 `knowledge-base/<space>/` 目录，即时重索引 |
| **Wiki 模式** | 调用 Confluence REST API 创建页面 |

支持的知识分类（space）：adr、conventions、api-docs、runbooks、architecture 等。

---

### 2.7 工作流编辑器（Workflow Editor）

| 特性 | 说明 |
|------|------|
| **可视化画布** | 基于 ReactFlow 的拖拽式工作流编辑器 |
| **7 种节点类型** | Trigger、MCP Tool、Action、Condition、Loop、Transform、Output |
| **颜色编码** | 不同节点类型不同颜色，一眼可辨 |
| **连线编辑** | 箭头连线定义执行顺序 |
| **节点属性面板** | 编辑节点标签和 JSON 配置 |
| **执行与历史** | 一键运行 + 查看历史执行结果 |
| **网格对齐** | 自动对齐网格，Minimap 全局预览 |

---

### 2.8 质量面板（Quality Dashboard）

4-Tab 右侧面板的"质量"Tab，提供实时质量监控：

| 指标 | 说明 |
|------|------|
| **总会话数** | 统计期内总 Session 数 |
| **平均耗时** | 每次 Agent 交互的平均响应时间 |
| **Profile 使用分布** | 各角色被激活的频次、平均耗时、平均轮次 |
| **工具调用排行** | Top 10 工具使用频次（水平柱状图） |
| **HITL 统计** | 审批通过/拒绝/修改/超时的分布 |
| **7 天趋势** | 每日 Session 数量趋势图 |
| **执行记录表** | 最近执行记录（时间、Profile、耗时、轮次） |

---

### 2.9 多模型支持

#### 2.9.1 模型注册表

| 提供商 | 支持模型 | Context Window | 说明 |
|--------|---------|---------------|------|
| **Anthropic Claude** | Opus 4.6 / Sonnet 4.5 / Haiku 4.5 | 200K | 默认推荐，Streaming + Tool Calling |
| **Google Gemini** | Gemini Pro | 30K | Google Cloud 集成 |
| **Alibaba Qwen** | Qwen2.5-7B / 72B | 32K | 阿里云集成 |
| **AWS Bedrock** | Claude via AWS | Provider-specific | AWS Marketplace 模型 |
| **OpenAI-compatible** | 兼容 OpenAI API 协议的第三方模型 | Provider-specific | 通用兼容层 |
| **MiniMax** | MiniMax-M2.5 / M2.5-lightning / M2.5-highspeed | 1M | Anthropic 兼容协议，复用 ClaudeAdapter |

#### 2.9.2 用户自定义配置

| 功能 | 说明 |
|------|------|
| **API Key 配置** | 每个用户可配置自己的 Provider API Key |
| **加密存储** | API Key 采用 AES-256-GCM 加密存储，前端显示脱敏（首 4 + 末 4 位） |
| **Base URL 覆盖** | 支持自定义 API 端点（私有部署、代理） |
| **Region 配置** | 指定 AWS 区域等参数 |
| **Model 选择器** | 前端下拉切换当前使用的模型 |
| **参数调节** | Temperature、Max Tokens、System Prompt 自定义 |
| **模型选择端到端生效** | 前端选择的模型通过 WebSocket 传递到后端，动态选择对应 Provider 的 adapter，实现真正的运行时模型切换 |

---

### 2.10 Dashboard 首页

| 区域 | 说明 |
|------|------|
| **Quick Actions** | 创建 Workspace、搜索知识库、启动 AI 对话的快捷入口 |
| **Recent Projects** | 最近 5 个 Workspace，显示状态（Active/Suspended/Error）和更新时间 |
| **Activity Feed** | 最近活动时间线（提交、对话、部署、审查） |

---

## 三、非功能性特性

### 3.1 安全

| 特性 | 实现 |
|------|------|
| **SSO 单点登录** | Keycloak 24.0 + OIDC Authorization Code + PKCE 流程 |
| **JWT 认证** | Bearer Token 认证，每次 API 请求自动附加 |
| **API Key 加密** | AES-256-GCM 加密存储用户 API Key |
| **路径遍历防护** | Workspace 文件操作拒绝包含 `..` 的路径 |
| **CORS 白名单** | 仅允许配置的源站访问（非 `*`） |
| **PLATFORM Skill 保护** | 内置 Skill 不可删除，防止误操作 |
| **数据库安全** | MCP 数据库工具仅支持 SELECT 查询，禁止 DDL/DML |
| **审计追踪** | 每次工具调用记录用户、时间、参数、结果 |
| **安全开关** | `forge.security.enabled` 环境变量控制认证启用/禁用 |

### 3.2 性能

| 特性 | 实现 |
|------|------|
| **Prompt Caching** | System Prompt 缓存 5 分钟，缓存命中节省 90% 成本 |
| **渐进式 Skill 加载** | 仅加载元数据到 System Prompt（-74%），按需读取完整内容 |
| **消息压缩** | 3 阶段自动压缩，Context Window 上限 25K tokens |
| **Rate Limit 重试** | 指数退避重试（1s → 2s → 4s），最多 3 次，上限 30s |
| **异步摘要生成** | Session Summary 异步生成，不阻塞用户交互 |
| **WebSocket 长连接** | 避免 HTTP 反复握手，减少延迟 |
| **文件树缓存** | ConcurrentHashMap 内存缓存，高频文件操作低延迟 |

### 3.3 可观测性

| 特性 | 实现 |
|------|------|
| **Prometheus 指标** | 通过 `/actuator/prometheus` 导出 |
| **7 个自定义指标** | profile 路由次数、工具调用次数/成功率、底线检查结果、OODA 阶段、消息耗时、轮次耗时、工具耗时 |
| **HITL 指标** | 审批结果分布（通过/拒绝/修改/超时） |
| **Context Usage 可视化** | 前端实时显示 Token 预算使用率和压缩阶段 |
| **Activity Log** | Agent 执行子步骤实时日志 |
| **健康检查** | 每个容器独立健康探针 |

**自定义 Prometheus 指标清单**：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `forge.profile.route` | Counter | Profile 路由次数（按 profile、method） |
| `forge.tool.calls` | Counter | 工具调用次数（按 tool、status） |
| `forge.baseline.results` | Counter | 底线检查结果（按 baseline、result） |
| `forge.ooda.phases` | Counter | OODA 阶段触发次数（按 phase） |
| `forge.hitl.results` | Counter | HITL 结果（按 profile、result） |
| `forge.message.duration` | Timer | 消息端到端耗时 |
| `forge.turn.duration` | Timer | 每轮 Agentic Loop 耗时（按 turn） |
| `forge.tool.duration` | Timer | 工具执行耗时（按 tool） |

### 3.4 可靠性

| 特性 | 实现 |
|------|------|
| **三级通信降级** | WebSocket → SSE → HTTP 同步，任一通道故障自动切换 |
| **Rate Limit 容错** | 指数退避 + 3 次重试，避免瞬时限流导致失败 |
| **HITL 超时兜底** | 5 分钟审批超时后自动通过（可配置），防止流程卡死 |
| **断线恢复** | WebSocket 断连后自动重发待审批的 HITL Checkpoint |
| **异步不阻塞** | 摘要生成异步执行，失败不影响主流程 |
| **底线重试** | 底线检查失败自动修复 + 重试（最多 2 轮） |
| **容器健康检查** | 每个 Docker 容器配置健康探针（10-15s 间隔，5-15 次重试） |

### 3.5 可扩展性

| 特性 | 实现 |
|------|------|
| **Adapter 模式** | 模型适配器隔离，新增 Provider 只需实现 `ModelAdapter` 接口 |
| **MCP 工具扩展** | 通过配置添加外部 MCP Server，自动发现工具 |
| **Skill 扩展** | CUSTOM Skill 支持用户自建，Workspace 级别隔离 |
| **底线脚本扩展** | 添加 Shell 脚本到 baselines 目录即可启用新质量检查 |
| **知识库扩展** | 添加 Markdown 文档到 knowledge-base 目录即可被搜索和引用 |
| **多 Workspace 隔离** | 每个 Workspace 独立文件系统、独立 Skill 配置、独立记忆 |

### 3.6 部署

| 特性 | 实现 |
|------|------|
| **Docker Compose 一键部署** | 6 容器编排，单命令启动 |
| **容器组成** | Backend + Frontend + Nginx + Keycloak + Knowledge MCP + Database MCP |
| **Nginx 统一入口** | 单端口 (9000) 反向代理所有服务 |
| **本地构建 + Docker 打包** | 避免 Docker 内网络问题，本地编译后 Docker 仅 COPY 产物 |
| **数据库双模** | 开发/试用: H2 文件持久化（data/forge）；生产: PostgreSQL（已声明依赖） |
| **Flyway 迁移** | 8 个版本化 SQL 迁移（V1-V8），自动升级 Schema |
| **Kubernetes Ready** | Workspace 设计预留 K8s Pod 编排接口 |

---

## 四、技术规格汇总

| 维度 | 数值 |
|------|------|
| **REST API 端点** | 68 个（11 个 Controller） |
| **SSE 事件类型** | 14 种 |
| **MCP 工具** | 18 个内置 + 外部自动发现 |
| **JPA Entity** | 12 个（+InteractionEvaluationEntity） |
| **Flyway 迁移** | 8 个版本（V1-V8） |
| **Skill 总数** | 32 个（6 个 Profile） |
| **单元测试** | 156 个（全部通过） |
| **验收测试** | 38 TC / 11 场景（94.7% 通过） |
| **Docker 容器** | 6 个 |
| **知识库文档** | 13 篇 |
| **代码规模** | ~50K+ 行（Kotlin + TypeScript） |
| **前端页面** | 7 个路由 |
| **后端技术栈** | Kotlin 1.9 + Spring Boot 3.3 + JDK 21 |
| **前端技术栈** | TypeScript + React 19 + Next.js 15 |
| **支持模型** | 6 Provider（Claude / Gemini / Qwen / Bedrock / OpenAI-compatible / MiniMax） |
| **Context Window** | 最大 200K tokens（Claude Opus） |

---

## 五、核心差异化价值

### vs. GitHub Copilot / Cursor
| 维度 | Copilot / Cursor | Forge |
|------|-----------------|-------|
| 定位 | 代码补全 / 编辑器级 AI | **自主执行的全交付 SuperAgent** |
| 执行模式 | 用户逐行指令，AI 补全 | **用户声明意图，Agent 自主规划→执行→验证→交付** |
| 覆盖范围 | 开发阶段 | 规划→设计→开发→测试→运维→评估 |
| 执行深度 | 单次补全 / 单次对话 | **最多 50 轮自主执行（工具链编排 + 失败自修复）** |
| 角色切换 | 无 | 6 Profile 自动路由 |
| 质量保障 | 无 | **底线自动检查 + 自修复重试** |
| 人机协同 | 无 | HITL 审批检查点 |
| 知识体系 | 无 | 32 Skill + 13+ 知识文档 + 知识写入 |
| 记忆 | 单文件上下文 | **3 层跨 Session 记忆（项目记忆 + 阶段记忆 + 会话摘要）** |
| 学习能力 | 无 | **四维评估 + 学习闭环管道** |
| 可观测性 | 无 | Prometheus 指标 + 质量面板 + Context Usage 实时监控 |

### vs. 传统 DevOps 平台（Jenkins / GitLab CI）
| 维度 | 传统 DevOps | Forge |
|------|-----------|-------|
| 自动化对象 | CI/CD 管线 | **软件交付全流程（含规划、设计、编码、评估）** |
| 执行方式 | 预定义脚本，静态流水线 | **AI Agent 动态决策，根据上下文自主选择工具和策略** |
| AI 能力 | 无 / 有限 | **多轮 Agentic Loop + 18 种工具编排 + 知识库** |
| 学习能力 | 静态规则 | **Skill 进化 + 记忆积累 + 四维评估反馈** |
| 开发者体验 | 配置 YAML | 自然语言对话 + 实时执行透明 |

---

> **Forge: 不是 AI 辅助编程工具，而是拥有自主执行能力的 AI 驱动软件交付平台。**
> 用户只需声明"要做什么"，SuperAgent 自主完成"怎么做"——从理解意图、选择角色、加载知识、编排工具、生成代码、验证质量，到写入交付物的完整闭环。
