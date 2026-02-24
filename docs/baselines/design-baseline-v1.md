# Forge — AI 驱动的智能交付平台 — 设计基线 v12

> 基线日期: 2026-02-23 | Session 32 — MiniMax 多模型 + 知识写入 + Context Usage 增强（v11 → v12）
> 本文档冻结当前已验证的 UI/API/数据模型/架构设计细节，作为未来修改的对照基准。
> 任何对本文档覆盖范围的修改，必须先意识到偏离、再决定是否接受。
>
> **v12 变更摘要**: Session 32 — MiniMax 多模型支持 + 知识写入 + Agentic Loop 增强：
> H2 持久化恢复（docker-compose DB_DRIVER 默认改回 org.h2.Driver）、
> MiniMax 模型支持（3 模型 MiniMax-M2.5/M2.5-lightning/M2.5-highspeed，复用 ClaudeAdapter，ModelProperties +minimax 字段，ClaudeConfig adapter 注册）、
> 模型选择端到端打通（前端 selectedModel → WebSocket modelId → ChatWebSocketHandler → ClaudeAgentService 动态 adapter 选择 → AgenticLoopOrchestrator activeAdapter）、
> ModelRegistry +providerForModel()、
> 前端 ModelSettingsDialog +MiniMax 供应商、ModelSelector +MiniMax label、
> 知识库本地写入（PageCreateTool local mode：KNOWLEDGE_MODE=local 时直接写 Markdown 文件到 knowledge-base/<space>/，LocalKnowledgeProvider +reload()）、
> knowledge-base volume 改为可写、
> Agentic Loop MAX_AGENTIC_TURNS 8→50、
> context_usage 每 turn 发送（含 turn 字段）、
> 前端 Context Usage 卡片始终显示（去除 isStreaming 条件）、
> HITL checkpoint 禁用（保留 intent confirmation）、
> 单元测试 156。
>
> **v11 变更摘要**: Phase 7 — 异步化 + 知识库 Scope 分层：Git Clone 异步化（WorkspaceService cloneAsync + 前端进度条 UI）、WorkspaceEntity +errorMessage、知识库三层 Scope（Global/Workspace/Personal，KnowledgeDocumentEntity DB 持久化，KnowledgeIndexService 从 ConcurrentHashMap→JPA）、KnowledgeController CRUD 端点（POST/PUT/DELETE /api/knowledge/docs）、知识搜索 scope 参数（cascade 优先级 workspace>personal>global）、前端 KnowledgeSearch scope filter、MCP search_knowledge +scope 参数、品牌重定位（AI 驱动的智能交付平台）、Flyway V7/V8、单元测试 156。
>
> **v10 变更摘要**: Phase 6 — 产品可用性加固：Workspace 持久化（ConcurrentHashMap→DB+磁盘、WorkspaceEntity JPA）、Git 仓库载入（GitService git clone --depth 1）、用户 API Key 生效（CompletionOptions.apiKeyOverride per-request override）、代码转知识（codebase-profiler Skill + analyze-structure.py + analyze_codebase MCP 工具 #17）、架构重构（ClaudeAgentService 1097→4 服务 max 547 LOC、McpProxyService 1515→5 服务 max 480 LOC）、Flyway V6（workspaces 表）、单元测试 157→156。
>
> **v9 变更摘要**: 全量代码交叉校验修正 — 补录 3 个缺失 Controller（ModelController 4端点 + UserModelConfigController 4端点 + DashboardController 3端点）、补录 Auth `/me/jwt` 端点、补录 3 个 SSE 事件类型（sub_step/hitl_checkpoint/baseline_check）、补录 3 个 JPA Entity（UserModelConfig/ExecutionRecord/HitlCheckpoint）、修正 Flyway V2/V3/V4 文件名、MCP 工具 14→16（+workspace_compile/workspace_test）、单元测试 147→157。REST 端点总数 39→68。
>
> **v8 变更摘要**: Phase 5 — 记忆与上下文管理系统：3 层记忆架构（Workspace Memory 始终注入 + Stage Memory Profile-scoped 聚合 + Session Summary 按需加载）、消息压缩 3 阶段（工具输出截断→早期摘要→全量总结，MAX_TOKENS=25K）、Rate Limit 指数退避（3 次重试 1s/2s/4s）、Memory REST API（6 端点）、MCP 工具 12→14（+update_workspace_memory/get_session_history）、前端 4-Tab 右侧面板（+Skills/+记忆）、Flyway V5 migration（5 张新表）、Docker python3。
>
> **v7 变更摘要**: Phase 4 — Skill 架构改造：渐进式加载（system prompt 55K→20-25K，Level 1 metadata + 按需 read_skill/run_skill_script）、Skill 质量治理（32→28 个，移除 3 D 级 + 合并 3 + 新建 delivery-methodology）、Skill 管理 API（9 REST 端点 CRUD + enable/disable + 脚本执行）、前端 /skills 管理页面（3 Tab + Tag 过滤）、使用追踪 + 分析度量（SkillUsageEntity + SkillAnalyticsService）、MCP 工具 9→12（+read_skill/run_skill_script/list_skills）。
>
> **v6 变更摘要**: Sprint 2.2 — Skill 条件触发过滤（Profile stage/type + 关键词匹配 → 3~20 个 Skill 动态加载）、AgentLoop 底线自动检查（Act 后自动运行 baseline，失败最多重试 2 轮）、MCP 真实服务（knowledge-mcp 本地搜索 + database-mcp H2 完整 CRUD）、Docker 4→6 容器（+knowledge-mcp:8081 +database-mcp:8082）、McpProxyService callTool fallback 修复。
>
> v5 变更摘要: AI → Workspace 交付闭环（3 个 workspace 工具 + file_changed 事件 + System Prompt 交付指导）、Keycloak SSO（OIDC PKCE 登录/回调/JWT/登出、4 容器部署）、代码块 Apply 按钮、Context Picker 实连（/api/context/search 4 类别）、FileExplorer CRUD（新建/重命名/删除）、未保存标记 + 5 秒自动保存、知识库 12+ 文档（+5 新增）、MCP 工具 6 → 9。
>
> v4 变更摘要: MCP 实连（McpProxyService → 真实 HTTP 调用 6 个 MCP 工具）、MetricsService（Micrometer 自定义指标 + Actuator/Prometheus 暴露）、agent-eval 真实模型调用（5 种断言类型）、BaselineService 底线集成、32 Skills / 5 Profiles、跨栈迁移 PoC（100% 业务规则覆盖）。
>
> v3 变更摘要: 新增 OODA 阶段指示器 UI（5 图标流转）、Profile Badge 增强（confidence 圆点 + 路由原因）、`ooda_phase` StreamEvent 类型、SSE 格式兼容规范、WebSocket CORS 配置修复、ClaudeAdapter `content_block_stop` 修复。
>
> v2 变更摘要: 新增 Skill-Aware OODA Loop 架构（SkillLoader + ProfileRouter + SystemPromptAssembler）、Profile Badge UI、Prompt Caching、`/api/chat/skills` 和 `/api/chat/profiles` 端点、`profile_active` StreamEvent 类型。

---

## 一、UI/UX 设计基线

### 1.1 页面路由结构

| 路由 | 页面 | 源文件 | 说明 |
|------|------|--------|------|
| `/` | Dashboard | `src/app/page.tsx` | 欢迎页 + 快捷操作 + 最近项目 + 活动动态 |
| `/workspace/[id]` | IDE Workspace | `src/app/workspace/[id]/page.tsx` | 三面板 IDE：文件树 + 编辑器 + AI 聊天 |
| `/knowledge` | Knowledge Base | `src/app/knowledge/page.tsx` | 四标签页：Docs / Architecture / Services / APIs |
| `/skills` | Skill Management | `src/app/skills/page.tsx` | Skill 管理面板：4 Tab + Tag 过滤 + 详情/创建（v7 新增） |
| `/workflows` | Workflow Editor | `src/app/workflows/page.tsx` | ReactFlow 可视化工作流编辑器 |
| `/login` | Login | `src/app/login/page.tsx` | Keycloak SSO 登录入口（v5 新增） |
| `/auth/callback` | Auth Callback | `src/app/auth/callback/page.tsx` | OIDC PKCE 回调处理（v5 新增） |

**根布局** (`src/app/layout.tsx`):
- `QueryClientProvider` (React Query 服务端状态)
- 全局 Header + 可折叠 Sidebar
- 默认暗色主题 (`<html className="dark">`)
- Auth Guard：非公开页面检查 `isAuthenticated()`，未认证 → `/login`（v5 新增）
- 公开页面（`/login`, `/auth/callback`）直接渲染，无 shell（v5 新增）

### 1.2 关键组件布局

#### IDE Workspace（`/workspace/[id]`）— 三面板布局

```
┌─────────────────────────────────────────────────────────────────────┐
│ Header (全局) — 角色切换 + 用户菜单(含 Sign out) + 命令面板(Cmd+K)   │
├──────────┬──────────────────────────────────┬───────────────────────┤
│ File     │ Monaco Editor                    │ AI Chat Sidebar       │
│ Explorer │   - 多标签页文件编辑               │  4-Tab (v8):          │
│          │   - 25+ 语言语法高亮              │   [对话|质量|Skills|记忆]│
│ (可折叠)  │   - Minimap + 括号匹配           │   - 消息列表 + 自动滚动 │
│  CRUD:   │   - "AI Explain" 按钮             │   - @ 提及附加上下文    │
│  +新建   │   - 未保存蓝色圆点 (v5)           │   - 流式响应展示       │
│  +重命名 │   - 5 秒自动保存 (v5)            │   - Tool Call 展开    │
│  +删除   ├──────────────────────────────────┤   - Profile Badge     │
│  (v5)    │ Terminal Panel (可折叠底部)        │   - 代码块 Apply (v5)  │
│          │   - WebSocket 终端连接            │   - Context Usage 常显  │
│          │                                  │     65% · T3 · P1 (v12)│
│          │   - 彩色输出                      │ (可折叠)               │
└──────────┴──────────────────────────────────┴───────────────────────┘
```

#### AI Chat Sidebar OODA 指示器 + Profile Badge（Sprint 2A）

流式响应期间，在消息列表底部显示 OODA 阶段流转和当前 active profile 信息：

```
┌───────────────────────────────────────────────────────────┐
│ 👁 Observe → 🧭 Orient → 🧠 Decide → ⚡ Act → ✅ Done    │  ← OODA 指示器
│ ● development | kotlin-conventions, +14 | keyword 'pr'   │  ← Profile Badge
└───────────────────────────────────────────────────────────┘
```

**OODA 阶段指示器设计规范**（v3 新增）：
- 位置：消息列表底部、Profile Badge 上方
- 仅在 `isStreaming && oodaPhase` 时显示
- 5 个阶段 icon 水平排列：`Eye`(Observe), `Compass`(Orient), `Brain`(Decide), `Zap`(Act), `CheckCircle2`(Complete)
- 当前阶段：`bg-primary/15 text-primary font-medium`，显示 icon + label 文字
- 已完成阶段：`text-green-400`，仅显示 icon
- 未到达阶段：`text-muted-foreground/40`，仅显示 icon
- 容器：`flex items-center gap-0.5`
- 每个阶段：`rounded px-1.5 py-0.5 text-xs transition-colors`

**Profile Badge 设计规范**（v2 创建，v3 增强）：
- 位置：OODA 指示器下方，`thinkingText` 指示器上方
- 仅在 `isStreaming && activeProfile` 时显示
- 样式：`border border-border rounded-md px-2 py-1 bg-muted/50`
- 布局：`flex items-center gap-1.5 text-xs text-muted-foreground`
- **Confidence 圆点**（v3 新增）：`h-1.5 w-1.5 rounded-full flex-shrink-0`
  - 高置信度 (≥0.8): `bg-green-400`
  - 中置信度 (≥0.5): `bg-yellow-400`
  - 低置信度 (<0.5): `bg-muted-foreground`
- Profile 名称：`font-medium text-primary`，去除 `-profile` 后缀
- Skills 列表：最多显示 3 个，超出部分显示 `+N`
- **路由原因**（v3 新增）：`truncate italic`，显示路由决策原因
- 各段之间用 `|`（`text-border`）分隔

**Thinking 指示器设计规范**：
- 三个圆点动画：`h-1.5 w-1.5 rounded-full bg-primary animate-thinking-dot`
- 动画延迟：0ms / 200ms / 400ms
- 旁边显示 `italic text-xs "Thinking..."`

#### Knowledge Base（`/knowledge`）— 四标签页

| 标签页 | 组件 | 功能 |
|--------|------|------|
| Docs | `KnowledgeSearch` + `DocViewer` | 文档搜索 + 按类型过滤(Wiki/ADR/Runbook/API Doc) + Markdown 渲染 + 动态目录 |
| Architecture | `ArchDiagramViewer` | Mermaid 图渲染 + 缩放(25%-300%) + 拖拽平移 + 导出 SVG |
| Services | `ServiceGraphViewer` | ReactFlow 服务依赖图 + 节点状态颜色(健康/退化/故障) + 影响分析模式 |
| APIs | `ApiExplorer` | API 目录 + 端点展开 + 参数文档 + "Try it out" 交互测试 |

### 1.3 交互流程

**核心用户旅程**：

```
Dashboard → 创建/选择 Workspace → 编辑文件（Monaco）
                                      │
                                      ├→ AI 对话（AiChatSidebar）
                                      │    ├→ WebSocket 优先连接
                                      │    ├→ 发送消息 + @ 附加上下文
                                      │    ├→ Profile 自动路由（profile_active 事件）
                                      │    │    ├→ 显式标签（@规划/@设计/@开发/@测试/@运维）
                                      │    │    ├→ 中英文关键词检测
                                      │    │    ├→ 分支名模式匹配
                                      │    │    └→ 默认 development
                                      │    ├→ 流式接收 thinking/content/tool_use 事件
                                      │    ├→ Profile Badge 实时显示当前角色
                                      │    └→ Tool Call 展示输入/输出
                                      │
                                      ├→ 知识浏览（Knowledge）
                                      │    ├→ 搜索文档 → 查看详情
                                      │    ├→ 浏览架构图 → 缩放/导出
                                      │    └→ 服务依赖图 → 影响分析
                                      │
                                      └→ 工作流编辑（Workflows）
                                           ├→ 拖放节点到画布
                                           ├→ 连线定义执行顺序
                                           └→ 运行 → 实时查看步骤执行
```

**组件间通信**：
- MonacoEditor → AiChatSidebar: 通过 `window.dispatchEvent(new CustomEvent('forge:ai-explain'))` 发送代码解释请求
- Backend → AiChatSidebar: 通过 `profile_active` StreamEvent 传递路由决策（activeProfile / loadedSkills / routingReason / confidence）
- Backend → AiChatSidebar → WorkspacePage: `file_changed` SSE 事件 → `forge:file-changed` DOM CustomEvent → 刷新文件树 + 自动打开文件（v5 新增）
- ChatMessage Apply 按钮 → WorkspacePage: `forge:file-changed` DOM CustomEvent → 刷新文件树（v5 新增）
- FileExplorer CRUD → WorkspacePage: `onFileTreeChanged` callback → `queryClient.invalidateQueries`（v5 新增）

### 1.4 样式系统

| 维度 | 选型 | 细节 |
|------|------|------|
| CSS 框架 | TailwindCSS 3.4+ | Utility-first, class-based dark mode |
| 组件模式 | CVA + tailwind-merge + clsx | class-variance-authority 管理组件变体 |
| 颜色体系 | HSL CSS 变量 | `--background`, `--foreground`, `--primary`, `--muted`, `--accent`, `--destructive` 等 |
| 品牌色 | `forge` 色阶 (50-950) | 蓝色渐变 |
| 正文字体 | Inter, system-ui | 无衬线 |
| 等宽字体 | JetBrains Mono, Fira Code | 代码编辑器 + 终端 |
| 自定义动画 | `animate-thinking-dot` | AI 思考指示器 (1.4s 循环，三点依次闪烁) |
| 图标库 | lucide-react 0.460+ | 全局统一图标（Send, Paperclip, RotateCcw, StopCircle, User, Bot, Wrench, CheckCircle, Loader2, AlertCircle, Copy, Check, Eye, Compass, Brain, Zap, CheckCircle2, FileDown, FilePlus, FolderPlus, Pencil, Trash2, LogOut 等） |
| 尺寸约定 | 图标 `h-3.5 w-3.5` ~ `h-4 w-4` | 小图标 3.5，正常 4，避免更大 |
| 间距约定 | `gap-1.5` / `gap-2` / `px-2 py-1` | 紧凑但不拥挤，text-xs 为主 |
| 交互反馈 | `hover:bg-accent` / `hover:text-foreground` | 统一使用 accent 色作为 hover 背景 |
| 状态色 | green-400(成功), primary(进行中), destructive(错误) | Tool Call 和操作状态一致 |

### 1.5 关键前端依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Next.js | 15.1+ | App Router, SSR, standalone 输出 |
| React | 19.0+ | UI 核心 |
| @tanstack/react-query | 5.62+ | 服务端状态管理 + 缓存 |
| @monaco-editor/react | 4.6+ | 代码编辑器 |
| @xyflow/react | 12.3+ | 工作流画布 + 服务依赖图 |
| mermaid | 11.4+ | 架构图渲染 |
| zustand | 5.0+ | 客户端状态管理 |

### 1.6 FileExplorer CRUD 交互规范（v5 新增）

**右键菜单** (`FileExplorer.tsx`):

| 菜单项 | 图标 | 作用 | 交互方式 |
|--------|------|------|---------|
| New File | `FilePlus` | 在当前位置创建文件 | `window.prompt()` → `workspaceApi.createFile()` → 刷新树 + 打开文件 |
| New Folder | `FolderPlus` | 在当前位置创建文件夹 | `window.prompt()` → 创建 `{folder}/.gitkeep` 占位 |
| Copy Path | `Copy` | 复制文件路径 | `navigator.clipboard.writeText()` |
| Rename | `Pencil` | 重命名文件 | `window.prompt()` → 读旧文件 → 创建新文件 → 删除旧文件 |
| AI Explain | `Wand2` | AI 解释文件 | `forge:ai-explain` 自定义事件 |
| Delete | `Trash2` | 删除文件 | `window.confirm()` → `workspaceApi.deleteFile()` |

- 菜单分隔线：New File/New Folder → 分隔 → Copy Path/Rename/AI Explain → 分隔 → Delete
- Delete 使用 `text-destructive` 红色文字
- 顶部工具栏新增 FilePlus / FolderPlus 快捷按钮

### 1.7 未保存标记 + 自动保存（v5 新增）

**未保存指示器**:
- 位置：文件 tab 中文件名左侧
- 样式：`h-2 w-2 rounded-full bg-primary flex-shrink-0`（蓝色圆点）
- 显示条件：`unsavedFiles.has(filePath)` 为 true
- title: `"Unsaved changes"`

**自动保存机制**:
- 触发条件：编辑器 `onChange` 回调
- Debounce：5000ms（每次编辑重置计时器）
- 执行：`workspaceApi.saveFile(workspaceId, activeFile, content)`
- 保存后：从 `unsavedFiles` Set 中移除 → 蓝色圆点消失
- 手动保存：Cmd+S / Ctrl+S，立即保存并清除标记

### 1.8 代码块 Apply 按钮（v5 新增）

**按钮位置**: 代码块右上角 header 区域，Copy 按钮左侧

**按钮样式**: 与 Copy 按钮一致 — `rounded p-0.5 text-muted-foreground transition-opacity hover:text-foreground`（始终可见，BUG-019 移除了 opacity-0 隐藏）

**图标**: `FileDown`（默认）/ `Loader2 animate-spin`（applying）/ `Check text-green-400`（applied, 3 秒后重置）

**交互流程**:
1. 仅在 workspace 内的 AI 回复中显示（用户消息不显示）
2. 点击 → `window.prompt("Save as:", suggestedName)` 输入文件名
3. suggestedName 基于代码块语言自动推断（20+ 语言映射）
4. 确认 → `workspaceApi.createFile(workspaceId, fileName, code)`
5. 写入成功 → dispatch `forge:file-changed` → 文件树刷新 + 自动打开

### 1.9 Keycloak SSO 交互流程（v5 新增）

**登录流程**:
```
用户访问 http://localhost:9000
    │
    ▼
layout.tsx Auth Guard: isAuthenticated()?
    │
    ├─ Yes → 正常渲染页面
    │
    └─ No → fetch /api/auth/me
              │
              ├─ 200 → 安全模式未开启，正常渲染
              │
              └─ 401 → window.location.href = "/login"
                         │
                         ▼
                    /login 页面 → auth.login()
                    → 生成 PKCE code_verifier + code_challenge
                    → 重定向到 Keycloak authorize 端点
                         │
                         ▼
                    Keycloak 登录页面（用户输入凭证）
                         │
                         ▼
                    回调到 /auth/callback?code=xxx
                    → auth.handleCallback()
                    → POST token 端点交换 access_token
                    → 存储到 localStorage("forge_access_token")
                    → window.location.href = "/"
```

**登出流程**: Header Sign out → `auth.logout()` → 清除 localStorage → 重定向 Keycloak end_session 端点

**Token 传递**: `workspace-api.ts` 所有 fetch 调用自动附加 `Authorization: Bearer <token>` header

---

## 二、API 契约基线

### 2.1 REST API 端点清单

#### Chat API (`AiChatController` → `/api/chat`)

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| POST | `/api/chat/sessions` | `CreateChatSessionRequest { workspaceId }` | `ChatSession { id, workspaceId, userId, createdAt }` | 创建聊天会话 |
| GET | `/api/chat/sessions/{sessionId}/messages` | — | `List<ChatMessage>` | 获取历史消息 |
| POST | `/api/chat/sessions/{sessionId}/messages` | `ChatStreamMessage { type, content, contexts? }` | `ChatMessage` | 同步发送消息（非流式） |
| POST | `/api/chat/sessions/{sessionId}/stream` | `ChatStreamMessage { type, content, contexts? }` | SSE stream | 流式发送消息 |
| GET | `/api/chat/skills` | — | `List<SkillInfo { name, description, tags, trigger }>` | 列出所有加载的 Skills（Phase 2 新增） |
| GET | `/api/chat/profiles` | — | `List<ProfileInfo { name, description, skills, baselines }>` | 列出所有加载的 Profiles（Phase 2 新增） |

#### Workspace API (`WorkspaceController` → `/api/workspaces`)

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| POST | `/api/workspaces` | `CreateWorkspaceRequest { name, description?, repository?, branch?, template? }` | `Workspace` | 创建工作空间 |
| GET | `/api/workspaces` | — | `List<Workspace>` | 列出工作空间 |
| GET | `/api/workspaces/{id}` | — | `Workspace` | 获取工作空间 |
| DELETE | `/api/workspaces/{id}` | — | 204 | 删除工作空间 |
| POST | `/api/workspaces/{id}/activate` | — | `Workspace` | 激活工作空间 |
| POST | `/api/workspaces/{id}/suspend` | — | `Workspace` | 暂停工作空间 |
| GET | `/api/workspaces/{id}/files` | — | `List<FileNode>` | 获取文件树 |
| GET | `/api/workspaces/{id}/files/content?path=` | — | `String` | 读取文件内容 |
| PUT | `/api/workspaces/{id}/files/content` | `FileContentRequest { path, content }` | 200 | 保存文件 |
| POST | `/api/workspaces/{id}/files` | `FileContentRequest { path, content }` | 201 | 创建文件 |
| DELETE | `/api/workspaces/{id}/files?path=` | — | 204 | 删除文件 |

#### Knowledge API (`KnowledgeController` → `/api/knowledge`)

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| GET | `/api/knowledge/search?q=&type=&limit=` | — | `List<KnowledgeDocument>` | 搜索知识文档 |
| GET | `/api/knowledge/docs/{id}` | — | `KnowledgeDocument` | 获取文档详情 |
| GET | `/api/knowledge/services` | — | `ServiceGraph` | 获取服务依赖图 |
| GET | `/api/knowledge/apis` | — | `List<ApiService>` | 获取 API 目录 |
| POST | `/api/knowledge/apis/try` | `ApiTryRequest { method, url, headers?, body? }` | `Any` | 测试 API 调用 |
| GET | `/api/knowledge/diagrams` | — | `List<ArchDiagram>` | 获取架构图列表 |

#### MCP API (`McpController` → `/api/mcp`)

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| GET | `/api/mcp/tools` | — | `List<McpTool>` | 列出可用工具 |
| POST | `/api/mcp/tools/call` | `McpToolCallRequest { name, arguments }` | `McpToolCallResponse` | 调用工具 |
| POST | `/api/mcp/tools/cache/invalidate` | — | `{ status: "cache_invalidated" }` | 清除工具缓存 |

**MCP 工具清单**（v4 创建，v5: 6→9，v7: 9→12，v8: 12→14，v9 补录: 14→16，v10: 16→17，v12: 17→18）:

| 工具 | MCP Server | 说明 |
|------|-----------|------|
| `workspace_write_file` | backend (local) | 写入文件到 workspace（v5 新增） |
| `workspace_read_file` | backend (local) | 读取 workspace 文件内容（v5 新增） |
| `workspace_list_files` | backend (local) | 列出 workspace 文件树（v5 新增） |
| `workspace_compile` | backend (local) | 编译/构建 workspace 项目，自动检测项目类型（v9 补录） |
| `workspace_test` | backend (local) | 运行 workspace 测试，自动检测测试框架（v9 补录） |
| `read_skill` | backend (local) | 按需读取 SKILL.md 或子文件内容（v7 新增） |
| `run_skill_script` | backend (local) | 执行 Skill 脚本，60s 超时（v7 新增） |
| `list_skills` | backend (local) | 列出可用 Skill metadata（v7 新增） |
| `update_workspace_memory` | backend (local) | Agent 主动更新 workspace 记忆（v8 新增） |
| `get_session_history` | backend (local) | 读取历史 session 摘要，参数 limit 默认 5（v8 新增） |
| `search_knowledge` | knowledge-mcp | 搜索知识库文档 |
| `read_file` | knowledge-mcp | 读取知识库文件内容 |
| `query_schema` | database-mcp | 查询数据库 schema |
| `run_baseline` | backend (local) | 执行底线检查脚本 |
| `list_baselines` | backend (local) | 列出可用底线脚本 |
| `get_service_info` | service-graph-mcp | 获取服务信息 |
| `analyze_codebase` | backend (local) | 对 workspace 执行结构分析脚本，返回 JSON（文件树+技术栈+统计）（v10 新增） |
| `page_create` | knowledge-mcp | 创建知识页面（local mode 写 Markdown 到 knowledge-base/<space>/，wiki mode 调 Confluence API）（v12 新增） |

**Workspace 工具安全规则**（v5 新增）:
- 路径遍历检查：包含 `..` 的路径一律拒绝（`isError: true`）
- workspaceId 必需：workspace 工具需要 workspaceId 参数，无 workspaceId 回退到常规工具分发
- `callTool(name, args, workspaceId)` 三参数重载处理 workspace 工具

#### Auth API（v5 新增，`AuthController` → `/api/auth`）

| 方法 | 路径 | 响应体 | 说明 |
|------|------|--------|------|
| GET | `/api/auth/me` | `{ authenticated, username, email?, roles }` | 返回当前用户信息（Principal） |
| GET | `/api/auth/me/jwt` | `{ authenticated, username, email, name, roles, sub }` | 返回 JWT Token 中的用户信息（v9 补录） |

#### Context Search API（v5 新增，`ContextController` → `/api/context`）

| 方法 | 路径 | 参数 | 响应体 | 说明 |
|------|------|------|--------|------|
| GET | `/api/context/search` | `category`(files/knowledge/schema/services), `workspaceId?`, `query?` | `List<ContextItem>` | 按类别搜索上下文 |

**4 个类别说明**:
| category | 数据来源 | 需要 workspaceId |
|----------|---------|-----------------|
| `files` | WorkspaceService.getFileTree() | 是 |
| `knowledge` | KnowledgeController 知识库 | 否 |
| `schema` | 数据库 schema 信息 | 否 |
| `services` | 服务依赖图信息 | 否 |

#### Skill Management API（v7 新增，`SkillController` → `/api/skills`）

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| GET | `/api/skills` | — | `List<SkillView>` | 列出所有 Skill（支持 `?scope=` / `?category=` 过滤） |
| GET | `/api/skills/{name}` | — | `SkillDetailView` | Skill 详情（含 subFiles + scripts） |
| GET | `/api/skills/{name}/content/**` | — | `{ path, content }` | 读取子文件内容 |
| POST | `/api/skills` | `CreateSkillRequest` | `SkillView` | 创建 CUSTOM Skill |
| PUT | `/api/skills/{name}` | `UpdateSkillRequest` | 200 | 更新 CUSTOM Skill |
| DELETE | `/api/skills/{name}` | — | 200/400 | 删除 Skill（PLATFORM 不可删） |
| POST | `/api/skills/{name}/enable` | — | 200 | 启用 Skill for workspace |
| POST | `/api/skills/{name}/disable` | — | 200 | 禁用 Skill for workspace |
| POST | `/api/skills/{name}/scripts/**` | `RunScriptRequest?` | `ScriptResultView` | 执行 Skill 脚本（60s 超时） |
| GET | `/api/skills/{name}/stats` | — | `SkillStatsView` | 使用统计 |

#### Skill Analytics API（v7 新增，`SkillAnalyticsController` → `/api/skill-analytics`）

| 方法 | 路径 | 参数 | 响应体 | 说明 |
|------|------|------|--------|------|
| GET | `/api/skill-analytics/skill-ranking` | `days=30` | `List<SkillRankingEntry>` | Skill 使用排行 |
| GET | `/api/skill-analytics/skill-suggestions` | — | `List<SkillSuggestion>` | 进化建议（未使用/低成功率） |
| GET | `/api/skill-analytics/skill-triggers` | `trigger` | `List<TriggerSuggestionView>` | 触发建议 |

**Skill 三层存储**（v7 新增）:
- **PLATFORM**: `plugins/` 目录（Docker volume，只读），不可删除/修改，可启用/禁用
- **WORKSPACE**: `workspace/{workspaceId}/.skills/`，可编辑/删除
- **CUSTOM**: `workspace/{workspaceId}/.skills/custom/`，完全 CRUD

#### Model API（v9 补录，`ModelController` → `/api/models`）

| 方法 | 路径 | 响应体 | 说明 |
|------|------|--------|------|
| GET | `/api/models` | `List<ModelInfo>` | 列出所有可用模型（跨 provider） |
| GET | `/api/models/providers` | `RegistrySummary` | 列出所有 provider 及模型数量 |
| GET | `/api/models/providers/{provider}` | `List<ModelInfo>` | 获取指定 provider 的模型列表 |
| GET | `/api/models/health` | `Map<String, Boolean>` | 所有 provider 健康检查 |

#### User Model Config API（v9 补录，`UserModelConfigController` → `/api/user/model-configs`）

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| GET | `/api/user/model-configs` | — | `List<UserModelConfigView>` | 获取当前用户所有 provider 配置 |
| GET | `/api/user/model-configs/{provider}` | — | `UserModelConfigView` | 获取指定 provider 配置 |
| PUT | `/api/user/model-configs/{provider}` | `UserModelConfigRequest` | `UserModelConfigView` | 保存/更新 provider 配置（API Key 加密存储） |
| DELETE | `/api/user/model-configs/{provider}` | — | 204 | 删除 provider 配置 |

#### Dashboard API（v9 补录，`DashboardController` → `/api/dashboard`）

| 方法 | 路径 | 参数 | 响应体 | 说明 |
|------|------|------|--------|------|
| GET | `/api/dashboard/metrics` | — | `{ profileStats, toolCallStats, hitlStats, totalSessions, avgDurationMs }` | 聚合统计（最近 7 天） |
| GET | `/api/dashboard/executions` | `limit=20` | `List<ExecutionRecord>` | 最近执行记录（分页） |
| GET | `/api/dashboard/trends` | `days=7` | `List<{ date, sessions, avgDurationMs }>` | 每日执行趋势 |

#### Memory API（v8 新增，`MemoryController` → `/api/memory`）

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| GET | `/api/memory/workspace/{workspaceId}` | — | `{ workspaceId, content }` | 获取 workspace 记忆 |
| PUT | `/api/memory/workspace/{workspaceId}` | `{ content }` | `{ workspaceId, content }` | 更新 workspace 记忆（max 4000 chars） |
| GET | `/api/memory/stage/{workspaceId}` | — | `List<StageMemoryView>` | 获取所有 stage 记忆 |
| GET | `/api/memory/stage/{workspaceId}/{profile}` | — | `StageMemoryView` | 获取特定 profile 的 stage 记忆 |
| GET | `/api/memory/sessions/{workspaceId}` | `?limit=10` | `List<SessionSummaryView>` | 获取 session 摘要（按时间倒序） |
| GET | `/api/memory/sessions/{workspaceId}/{sessionId}` | — | `SessionSummaryView` | 获取单个 session 摘要 |

**3 层记忆架构**（v8 新增）:
- **Layer 1 Workspace Memory**: 工作区级持久化知识（≈ CLAUDE.md），始终注入 system prompt，max 4000 chars ≈ 1K tokens
- **Layer 2 Stage Memory**: Profile-scoped 跨 Session 聚合（completedWork/keyDecisions/unresolvedIssues/nextSteps），Session 结束后自动更新
- **Layer 3 Session Summary**: 单次会话结构化摘要（summary/completedWork/artifacts/decisions/unresolved/nextSteps），最近 3 个注入 system prompt

**消息压缩**（v8 新增）:
- MAX_CONVERSATION_TOKENS = 25,000
- Phase 1: 工具输出截断到 500 chars
- Phase 2: 保留最近 3 轮，早期消息替换为摘要
- Phase 3: Claude 生成全量总结（last resort）

#### Actuator / Metrics API（v4 新增）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/actuator/health` | 健康检查（含 liveness/readiness 探针） |
| GET | `/actuator/metrics` | Micrometer 指标列表 |
| GET | `/actuator/metrics/{name}` | 单个指标详情（如 `forge.profile.route`） |
| GET | `/actuator/prometheus` | Prometheus 格式指标导出 |

**Forge 自定义指标**（`MetricsService`，v4 新增）:

| 指标名 | 类型 | Tags | 说明 |
|--------|------|------|------|
| `forge.profile.route` | Counter | `profile`, `method` | Profile 路由次数 |
| `forge.tool.calls` | Counter | `tool`, `status`(success/error) | 工具调用次数 |
| `forge.baseline.results` | Counter | `baseline`, `result`(pass/fail) | 底线检查结果 |
| `forge.ooda.phases` | Counter | `phase` | OODA 阶段触发次数 |
| `forge.message.duration` | Timer | — | 消息处理端到端耗时 |
| `forge.turn.duration` | Timer | `turn` | 每轮 Agentic Loop 耗时 |
| `forge.tool.duration` | Timer | `tool` | 单次工具执行耗时 |

#### Workflow API (`WorkflowController` → `/api/workflows`)

| 方法 | 路径 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| POST | `/api/workflows` | `CreateWorkflowRequest { name, description?, nodes?, edges? }` | `Workflow` | 创建工作流 |
| GET | `/api/workflows` | — | `List<Workflow>` | 列出工作流 |
| GET | `/api/workflows/{id}` | — | `Workflow` | 获取工作流 |
| PUT | `/api/workflows/{id}` | `UpdateWorkflowRequest { name?, description?, nodes?, edges? }` | `Workflow` | 更新工作流 |
| DELETE | `/api/workflows/{id}` | — | 204 | 删除工作流 |
| POST | `/api/workflows/{id}/run` | — | `WorkflowExecutionResult` | 运行工作流 |
| GET | `/api/workflows/{id}/runs` | — | `List<WorkflowExecutionResult>` | 查看运行历史 |

### 2.2 WebSocket 协议

| WebSocket | 路径 | 方向 | 消息格式 |
|-----------|------|------|---------|
| AI Chat | `/ws/chat/{sessionId}` | Client→Server | `{ type: "message", content: string, contexts: ContextReference[], modelId?: string }` |
| | | Server→Client | `StreamEvent` JSON (每行一个事件) |
| Terminal | `/ws/terminal/{workspaceId}` | 双向 | 终端输入/输出文本 |
| Workflow | `/ws/workflow/{id}` | Server→Client | 工作流执行步骤事件 |

### 2.3 SSE 事件格式

SSE 端点: `POST /api/chat/sessions/{sessionId}/stream`

**StreamEvent 类型**:

```typescript
type OodaPhase = "observe" | "orient" | "decide" | "act" | "complete";

type StreamEvent =
  | { type: "ooda_phase",                           // OODA 阶段流转（v3 新增）
      phase?: OodaPhase }                           //   当前阶段
  | { type: "profile_active",                       // Profile 路由结果（Phase 2 新增，Agentic Loop 开始前发送）
      activeProfile?: string,                       //   路由到的 profile 名称
      loadedSkills?: string[],                      //   加载的 skill 列表
      routingReason?: string,                       //   路由原因（如 "keyword '接口' (score=1, conf=0.6)"）
      confidence?: number }                         //   置信度 0.0-1.0
  | { type: "sub_step", message?: string }          // 子步骤进度（如加载记忆、工具执行等）（v9 补录）
  | { type: "thinking", content?: string }          // AI 思考过程
  | { type: "content", content?: string }           // 文本输出增量
  | { type: "tool_use_start", toolCallId?: string, toolName?: string }  // Tool 调用开始
  | { type: "tool_use", toolCallId?: string, toolName?: string, toolInput?: object }  // Tool 调用完整
  | { type: "tool_result", toolCallId?: string, content?: string, durationMs?: number }  // Tool 执行结果
  | { type: "file_changed",                         // 文件变更通知（v5 新增）
      path?: string,                                //   变更的文件路径
      action?: string }                             //   "created" | "modified"
  | { type: "hitl_checkpoint",                       // HITL 人工审批检查点（v9 补录）（v12: 已禁用，保留事件定义）
      checkpointId?: string,                          //   检查点 ID
      checkpoint?: string,                            //   检查点名称
      deliverables?: string[],                        //   交付物列表
      baselineResults?: object }                      //   底线检查结果
  | { type: "baseline_check",                        // 底线检查状态（v9 补录）
      status?: string,                                //   "running" | "passed" | "failed" | "exhausted"
      baseline?: string,                              //   底线脚本名
      output?: string }                               //   检查输出
  | { type: "context_usage",                         // Context Window 使用率（v8 新增）
      tokensUsed?: number,                            //   当前 token 用量
      tokenBudget?: number,                           //   token 预算 (25000)
      compressionPhase?: number,                      //   压缩阶段 (0=none, 1=truncate, 2=summarize, 3=full)
      turn?: number }                                 //   当前 agentic turn 序号（v12 新增）
  | { type: "error", content?: string }             // 错误
  | { type: "done" }                                // 流结束
```

**OODA 阶段映射**（v3 新增）:
```
observe  — streamMessage 开始，buildDynamicSystemPrompt 之前
orient   — ProfileRouter 路由完成，emit profile_active 之前
decide   — agenticStream 开始前，Claude 开始生成回复
act      — agenticStream 中 stopReason==TOOL_USE，执行工具时
complete — agenticStream 结束后，持久化消息前
```

**事件发送顺序**:
```
ooda_phase(observe) → ooda_phase(orient) → profile_active → ooda_phase(decide)
  → [thinking →] content* → [ooda_phase(act) → tool_use → tool_result → [file_changed →]]* → content*
  → ooda_phase(complete) → done
```
> `file_changed` 仅在 `workspace_write_file` 工具成功执行后发送（v5 新增）

**传输方式**:
- SSE: Spring SseEmitter 输出 `data:{JSON}\n\n` 格式（**注意：冒号后无空格**），`data:[DONE]` 结束
- WebSocket: 每行一个 JSON 对象

**SSE 解析兼容规范**（v3 新增）:
> Spring SseEmitter 发送 `data:` 后不带空格（如 `data:{"type":"content",...}`），
> 但 SSE 标准允许 `data: ` 带空格。前端 `claude-client.ts` 必须兼容两种格式：
> 用 `line.startsWith("data:")` 匹配，然后动态判断是否有空格再做 slice。

### 2.4 关键 DTO 结构

源文件: `web-ide/backend/src/main/kotlin/com/forge/webide/model/Models.kt`

**核心 DTO**:

```kotlin
// Chat
data class ChatStreamMessage(val type: String, val content: String, val contexts: List<ContextReference>?)
data class ContextReference(val type: String, val id: String, val content: String?)
data class ToolCallRecord(val id: String, val name: String, val input: Map<String, Any?>, val output: String?, val status: String)

// Workspace
data class Workspace(val id: String, val name: String, val description: String, val status: WorkspaceStatus, val owner: String, val repository: String?, val branch: String?)
enum class WorkspaceStatus { CREATING, ACTIVE, SUSPENDED, ERROR }
data class FileNode(val name: String, val path: String, val type: FileType, val size: Long?, val children: List<FileNode>?)

// Knowledge
data class KnowledgeDocument(val id: String, val title: String, val type: DocumentType, val content: String, val snippet: String, val author: String, val tags: List<String>)
enum class DocumentType { WIKI, ADR, RUNBOOK, API_DOC }

// MCP
data class McpTool(val name: String, val description: String, val inputSchema: Map<String, Any?>)
data class McpToolCallRequest(val name: String, val arguments: Map<String, Any?>)
data class McpToolCallResponse(val content: List<McpContent>, val isError: Boolean)
```

---

## 三、数据模型基线

### 3.1 JPA Entity 结构

源文件: `web-ide/backend/src/main/kotlin/com/forge/webide/entity/`

#### ChatSessionEntity

```kotlin
@Entity @Table(name = "chat_sessions")
class ChatSessionEntity(
    @Id val id: String,                              // UUID
    @Column(name = "workspace_id") val workspaceId: String,
    @Column(name = "user_id") val userId: String,
    @Column(name = "created_at") val createdAt: Instant,
    @Column(name = "updated_at") var updatedAt: Instant,
    @OneToMany(mappedBy = "sessionId", cascade = ALL, fetch = LAZY)
    val messages: MutableList<ChatMessageEntity>
)
```

#### ChatMessageEntity

```kotlin
@Entity @Table(name = "chat_messages")
class ChatMessageEntity(
    @Id val id: String,                              // UUID
    @Column(name = "session_id") val sessionId: String,  // FK → chat_sessions
    @Column(name = "role", length = 20) val role: String,  // "user" | "assistant" | "tool" | "system"
    @Column(name = "content", length = 1_000_000) val content: String,
    @Column(name = "created_at") val createdAt: Instant,
    @OneToMany(mappedBy = "messageId", cascade = ALL, fetch = LAZY)
    val toolCalls: MutableList<ToolCallEntity>
)
```

#### ToolCallEntity

```kotlin
@Entity @Table(name = "tool_calls")
class ToolCallEntity(
    @Id val id: String,                              // UUID
    @Column(name = "message_id") val messageId: String,   // FK → chat_messages
    @Column(name = "tool_name") val toolName: String,
    @Column(name = "input", length = 1_000_000) val input: String?,
    @Column(name = "output", length = 1_000_000) val output: String?,
    @Column(name = "status", length = 20) val status: String,  // "complete" | "error"
    @Column(name = "duration_ms") val durationMs: Long?
)
```

### 3.2 关系图

```
chat_sessions (1) ──→ (N) chat_messages (1) ──→ (N) tool_calls
     │                        │                        │
     ├─ id (PK)              ├─ id (PK)              ├─ id (PK)
     ├─ workspace_id         ├─ session_id (FK)      ├─ message_id (FK)
     ├─ user_id              ├─ role                 ├─ tool_name
     ├─ created_at           ├─ content (TEXT)       ├─ input (TEXT)
     └─ updated_at           └─ created_at           ├─ output (TEXT)
                                                      ├─ status
                                                      └─ duration_ms

workspaces
     │
     ├─ id (PK)
     ├─ name
     ├─ description
     ├─ status
     ├─ owner
     ├─ repository
     ├─ branch
     ├─ local_path
     ├─ created_at
     └─ updated_at

user_model_configs          execution_records          hitl_checkpoints
     │                        │                        │
     ├─ id (PK)              ├─ id (PK)              ├─ id (PK)
     ├─ user_id              ├─ session_id            ├─ session_id
     ├─ provider             ├─ profile               ├─ profile
     ├─ api_key_encrypted    ├─ skills_loaded         ├─ checkpoint
     ├─ base_url             ├─ ooda_durations (JSON) ├─ deliverables (JSON)
     ├─ region               ├─ tool_calls (JSON)     ├─ baseline_results
     ├─ enabled              ├─ baseline_results      ├─ status
     ├─ created_at           ├─ hitl_result           ├─ feedback
     └─ updated_at           ├─ total_duration_ms     ├─ created_at
                              ├─ total_turns           └─ resolved_at
                              └─ created_at
     UK(user_id, provider)
```

#### UserModelConfigEntity（v9 补录）

```kotlin
@Entity @Table(name = "user_model_configs")
class UserModelConfigEntity(
    @Id val id: String,                                     // UUID
    @Column(name = "user_id") val userId: String,
    @Column(name = "provider", length = 50) val provider: String,
    @Column(name = "api_key_encrypted", length = 1024) var apiKeyEncrypted: String = "",  // AES 加密存储
    @Column(name = "base_url", length = 512) var baseUrl: String = "",
    @Column(name = "region", length = 50) var region: String = "",
    @Column(name = "enabled") var enabled: Boolean = true,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
// UNIQUE(user_id, provider)
```

#### ExecutionRecordEntity（v9 补录）

```kotlin
@Entity @Table(name = "execution_records")
class ExecutionRecordEntity(
    @Id val id: String,                                     // UUID
    @Column(name = "session_id") val sessionId: String,
    @Column val profile: String,
    @Column(name = "skills_loaded") val skillsLoaded: Int = 0,
    @Column(name = "ooda_durations", columnDefinition = "TEXT") val oodaDurations: String = "{}",  // JSON
    @Column(name = "tool_calls", columnDefinition = "TEXT") val toolCalls: String = "[]",          // JSON array
    @Column(name = "baseline_results", columnDefinition = "TEXT") val baselineResults: String?,
    @Column(name = "hitl_result") val hitlResult: String?,
    @Column(name = "total_duration_ms") val totalDurationMs: Long = 0,
    @Column(name = "total_turns") val totalTurns: Int = 0,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
```

#### HitlCheckpointEntity（v9 补录）

```kotlin
@Entity @Table(name = "hitl_checkpoints")
class HitlCheckpointEntity(
    @Id val id: String,                                     // UUID
    @Column(name = "session_id") val sessionId: String,
    @Column val profile: String,
    @Column val checkpoint: String,
    @Column(columnDefinition = "TEXT") val deliverables: String = "[]",  // JSON array
    @Column(name = "baseline_results", columnDefinition = "TEXT") val baselineResults: String?,
    @Column var status: String = "PENDING",                  // PENDING | APPROVED | REJECTED | TIMEOUT | MODIFY
    @Column(columnDefinition = "TEXT") var feedback: String?,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "resolved_at") var resolvedAt: Instant?
)
```

#### SkillPreferenceEntity（v7 新增）

```kotlin
@Entity @Table(name = "skill_preferences")
class SkillPreferenceEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "workspace_id") val workspaceId: String,
    @Column(name = "skill_name") val skillName: String,
    @Column(name = "enabled") var enabled: Boolean = true
)
```

#### SkillUsageEntity（v7 新增）

```kotlin
@Entity @Table(name = "skill_usage")
class SkillUsageEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "session_id") val sessionId: String,
    @Column(name = "skill_name") val skillName: String,
    @Column(name = "action") val action: String,        // READ / SCRIPT_RUN
    @Column(name = "script_type") val scriptType: String?,
    @Column(name = "profile") val profile: String = "",
    @Column(name = "success") val success: Boolean = true,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
```

#### SessionSummaryEntity（v8 新增）

```kotlin
@Entity @Table(name = "session_summaries")
class SessionSummaryEntity(
    @Id val id: String,                                     // UUID
    @Column(name = "session_id", unique = true) val sessionId: String,
    @Column(name = "workspace_id") val workspaceId: String,
    @Column(name = "profile") val profile: String,
    @Column(name = "summary", columnDefinition = "TEXT") var summary: String,
    @Column(name = "completed_work", columnDefinition = "TEXT") var completedWork: String = "[]",  // JSON array
    @Column(name = "artifacts", columnDefinition = "TEXT") var artifacts: String = "[]",
    @Column(name = "decisions", columnDefinition = "TEXT") var decisions: String = "[]",
    @Column(name = "unresolved", columnDefinition = "TEXT") var unresolved: String = "[]",
    @Column(name = "next_steps", columnDefinition = "TEXT") var nextSteps: String = "[]",
    @Column(name = "turn_count") val turnCount: Int = 0,
    @Column(name = "tool_call_count") val toolCallCount: Int = 0,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
```

#### WorkspaceMemoryEntity（v8 新增）

```kotlin
@Entity @Table(name = "workspace_memories")
class WorkspaceMemoryEntity(
    @Id val id: String,                                     // UUID
    @Column(name = "workspace_id", unique = true) val workspaceId: String,
    @Column(name = "content", columnDefinition = "TEXT") var content: String = "",  // max 4000 chars
    @Column(name = "version") var version: Int = 1,
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
```

#### StageMemoryEntity（v8 新增）

```kotlin
@Entity @Table(name = "stage_memories", uniqueConstraints = [UniqueConstraint(columnNames = ["workspace_id", "profile"])])
class StageMemoryEntity(
    @Id val id: String,                                     // UUID
    @Column(name = "workspace_id") val workspaceId: String,
    @Column(name = "profile") val profile: String,
    @Column(name = "completed_work", columnDefinition = "TEXT") var completedWork: String = "[]",
    @Column(name = "key_decisions", columnDefinition = "TEXT") var keyDecisions: String = "[]",
    @Column(name = "unresolved_issues", columnDefinition = "TEXT") var unresolvedIssues: String = "[]",
    @Column(name = "next_steps", columnDefinition = "TEXT") var nextSteps: String = "[]",
    @Column(name = "session_count") var sessionCount: Int = 0,
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
```

#### WorkspaceEntity（v10 新增）

```kotlin
@Entity @Table(name = "workspaces")
class WorkspaceEntity(
    @Id val id: String,                                     // UUID
    @Column(name = "name") val name: String,
    @Column(name = "description") val description: String = "",
    @Enumerated(EnumType.STRING) @Column(name = "status") var status: WorkspaceStatus = WorkspaceStatus.ACTIVE,
    @Column(name = "owner") val owner: String = "",
    @Column(name = "repository") val repository: String? = null,     // git URL
    @Column(name = "branch") val branch: String? = null,
    @Column(name = "local_path") val localPath: String? = null,      // 磁盘路径
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
```

### 3.3 Flyway 迁移

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__create_chat_tables.sql` | 创建 `chat_sessions` + `chat_messages` + `tool_calls` 三张表 + 索引 |
| V2 | `V2__create_user_model_configs.sql` | 创建 `user_model_configs` 表（用户模型配置，API Key 加密存储）（v9 修正） |
| V3 | `V3__create_execution_records.sql` | 创建 `execution_records` 表（执行遥测，质量面板）（v9 修正） |
| V4 | `V4__create_hitl_checkpoints.sql` | 创建 `hitl_checkpoints` 表（人工审批检查点）（v9 修正） |
| V5 | `V5__create_memory_tables.sql` | 创建 `skill_preferences` + `skill_usage`（v7 backfill）+ `session_summaries` + `workspace_memories` + `stage_memories`（v8 新增） |
| V6 | `V6__create_workspaces.sql` | 创建 `workspaces` 表（id, name, description, status, owner, repository, branch, local_path, created_at, updated_at）+ 索引（v10 新增） |

**索引**:
- `idx_sessions_workspace` → `chat_sessions(workspace_id)`
- `idx_messages_session` → `chat_messages(session_id)`
- `idx_tool_calls_message` → `tool_calls(message_id)`
- `idx_session_summaries_workspace` → `session_summaries(workspace_id)`（v8 新增）
- `idx_session_summaries_ws_created` → `session_summaries(workspace_id, created_at)`（v8 新增）
- `idx_workspace_memories_ws` → `workspace_memories(workspace_id)`（v8 新增）
- `idx_stage_memories_ws_profile` → `stage_memories(workspace_id, profile)`（v8 新增）

### 3.4 数据库配置

- 开发/试用: H2 内存数据库 (Spring Boot 默认)
- 生产就绪: PostgreSQL (已声明 `runtimeOnly("org.postgresql:postgresql")`)
- ORM: Spring Data JPA + Hibernate
- 迁移: Flyway Core

---

## 四、架构决策基线

### 4.1 部署架构

```
┌─────────────────────────────────────────────────────┐
│                    用户浏览器                         │
│              http://localhost:9000                    │
└───────────────────────┬─────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────┐
│                  Nginx (:9000)                        │
│  反向代理 — 统一入口                                   │
│                                                       │
│  /api/*      → backend:8080    (REST + SSE)          │
│  /ws/*       → backend:8080    (WebSocket Upgrade)   │
│  /actuator/* → backend:8080    (健康检查)             │
│  /h2-console/* → backend:8080  (数据库控制台)         │
│  /auth/*     → keycloak:8080   (SSO 认证)  (v5 新增)  │
│  /*          → frontend:3000   (Next.js, catch-all)  │
└───┬──────────────┬──────────────┬────────────────────┘
    │              │              │
┌───▼────────┐ ┌──▼───────────┐ ┌▼─────────────────┐
│ Backend    │ │ Frontend     │ │ Keycloak (v5)    │
│ (:8080)    │ │ (:3000)      │ │ (:8180→8080)     │
│ Spring 3   │ │ Next.js 15   │ │ Keycloak 24.0    │
│ Kotlin     │ │ standalone   │ │ forge realm      │
│ JDK 21     │ │ React 19     │ │ OIDC PKCE        │
│ H2/PG      │ │ Monaco       │ │ realm-export.json│
│ WS + SSE   │ │ Auth Guard   │ │                  │
└──────┬─────┘ └──────────────┘ └──────────────────┘
       │ HTTP (内部网络)
       ├──────────────────────────┐
┌──────▼─────────────┐ ┌────────▼────────────┐
│ knowledge-mcp (v6) │ │ database-mcp (v6)   │
│ (:8081)            │ │ (:8082)             │
│ Ktor HTTP          │ │ Ktor HTTP           │
│ 6 工具             │ │ 3 工具              │
│ 本地文件搜索       │ │ H2/PostgreSQL       │
│ wiki_search 等     │ │ schema_inspector 等 │
└────────────────────┘ └─────────────────────┘
```

### 4.2 Nginx 路由规则（6 条）

源文件: `infrastructure/docker/nginx-trial.conf`

| # | 路径匹配 | 目标 | 特殊配置 |
|---|---------|------|---------|
| 1 | `/api/` | `backend:8080` | `proxy_buffering off` (SSE), `proxy_read_timeout 3600s` |
| 2 | `/ws/` | `backend:8080` | `proxy_http_version 1.1`, `Upgrade` + `Connection` headers |
| 3 | `/actuator/` | `backend:8080` | 标准代理 |
| 4 | `/h2-console/` | `backend:8080` | 标准代理 |
| 5 | `/auth/` | `keycloak:8080` | `proxy_buffer_size 128k`, `proxy_buffers 4 256k`（v5 新增） |
| 6 | `/` (catch-all) | `frontend:3000` | 标准代理 |

### 4.3 Docker 部署策略

源文件: `infrastructure/docker/docker-compose.trial.yml`

**策略**: 本地构建 → Docker 只打包运行

```bash
# 1. 本地构建（避免 Docker 内 TLS/网络问题）
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :web-ide:backend:bootJar -x test --no-daemon
cd web-ide/frontend && npm install && npm run build

# 2. Docker 打包运行
docker compose -f docker-compose.trial.yml up --build
```

**六容器**（v6 更新：4 → 6）:
- `keycloak`: Keycloak 24.0, `start-dev --import-realm`, realm-export.json 自动导入（v5 新增）
- `backend`: Spring Boot JAR on JRE Alpine（含 bash/git/grep/findutils），`depends_on: keycloak+knowledge-mcp+database-mcp(healthy)`
- `frontend`: Next.js standalone on Node 20, Keycloak 环境变量注入
- `nginx`: Alpine, 挂载 nginx-trial.conf（含 `/auth/` proxy）
- `knowledge-mcp`: Ktor HTTP 服务 (:8081)，6 工具（wiki_search/adr_search/api_doc_search/runbook_search/tech_radar/convention_check），KNOWLEDGE_MODE=local 本地文件搜索（v6 新增）
- `database-mcp`: Ktor HTTP 服务 (:8082)，3 工具（schema_inspector/query_executor/data_dictionary），H2 内存数据库 + 示例数据（v6 新增）

**健康检查**:
- backend: `wget --spider http://localhost:8080/api/knowledge/search` (15s 间隔, 5 次重试, 30s 启动等待)
- keycloak: `exec 3<>/dev/tcp/localhost/8080` (10s 间隔, 15 次重试, 30s 启动等待)（v5 新增）
- knowledge-mcp: `wget --spider http://localhost:8081/health/live` (10s 间隔, 5 次重试, 15s 启动等待)（v6 新增）
- database-mcp: `wget --spider http://localhost:8082/health/live` (10s 间隔, 5 次重试, 15s 启动等待)（v6 新增）

**v12 Docker 配置变更**:
- `DB_DRIVER` 默认改回 `org.h2.Driver`（从 org.postgresql.Driver 恢复 H2 持久化）
- `DB_URL` 默认: `jdbc:h2:file:./data/forge;AUTO_SERVER=TRUE`
- `knowledge-base` volume 改为可写（移除 `:ro`，支持 page_create 本地写入）
- 新增环境变量: `MINIMAX_API_KEY`, `MINIMAX_API_URL`
- 新增 `forge-backend-data` named volume（H2 文件持久化）

### 4.4 前后端通信方式

**优先级**: WebSocket → SSE fallback → HTTP 同步兜底

```
前端 ClaudeClient.streamMessage()
    │
    ├─ 尝试 WebSocket (ws://host/ws/chat/{sessionId})
    │    ├─ 成功: 全双工实时通信
    │    └─ 失败: 降级到 SSE
    │
    ├─ SSE Fallback (POST /api/chat/sessions/{sessionId}/stream)
    │    ├─ 成功: Server-Sent Events 流
    │    └─ 失败: 降级到 HTTP
    │
    └─ HTTP 同步 (POST /api/chat/sessions/{sessionId}/messages)
         └─ 等待完整响应返回
```

### 4.5 后端 Skill-Aware Agentic Loop（Phase 2 重构）

**核心组件**（`com.forge.webide.service.skill` 包）：

| 组件 | 源文件 | 职责 |
|------|--------|------|
| `SkillModels.kt` | 35 行 | 领域模型：`SkillDefinition`, `ProfileDefinition`, `ProfileRoutingResult` |
| `SkillLoader.kt` | 266 行 | 扫描 `plugins/` 目录，解析 YAML frontmatter (Jackson YAML)，`ConcurrentHashMap` 缓存，`@PostConstruct` 初始化，v6: Skill trigger 条件过滤（stage/type + 关键词匹配） |
| `ProfileRouter.kt` | 197 行 | 4 级优先路由：显式标签 → 中英文关键词 → 分支名模式 → 默认 development |
| `SystemPromptAssembler.kt` | 238 行 | 6 段式动态 system prompt 组装 |

**Phase 6 架构重构**（v10 新增）:

ClaudeAgentService（原 1097 行）拆分为 4 个服务：

| 组件 | 源文件 | 职责 | LOC |
|------|--------|------|-----|
| `AgenticLoopOrchestrator.kt` | 371 行 | agenticStream() + 工具执行循环 + streamWithRetry() + collectStreamResult() | ~371 |
| `HitlCheckpointManager.kt` | 136 行 | awaitHitlCheckpoint() + CompletableFuture 暂停/恢复 + 审批逻辑 | ~136 |
| `BaselineAutoChecker.kt` | 162 行 | runBaselineAutoCheck() + 底线脚本执行 + 重试 | ~162 |
| `ClaudeAgentService.kt` | 547 行 | 入口协调 + 会话历史加载 + 消息持久化 + 系统提示构建 | ~547 |

McpProxyService（原 1515 行）拆分为 5 个服务：

| 组件 | 源文件 | 职责 | LOC |
|------|--------|------|-----|
| `BuiltinToolHandler.kt` | 374 行 | search_knowledge, read_file, get_service_info, run_baseline, list_baselines, query_schema | ~374 |
| `WorkspaceToolHandler.kt` | 329 行 | workspace_write_file, workspace_read_file, workspace_list_files, workspace_compile, workspace_test | ~329 |
| `SkillToolHandler.kt` | 265 行 | read_skill, run_skill_script, list_skills | ~265 |
| `MemoryToolHandler.kt` | 171 行 | update_workspace_memory, get_session_history, analyze_codebase | ~171 |
| `McpProxyService.kt` | 480 行 | 工具注册表 + 路由分发 + 远程 MCP Server 发现 | ~480 |

**完整 Agentic Loop 流程**:

```
用户消息
    │
    ▼
emit ooda_phase("observe") + metricsService.recordOodaPhase("observe")
    │
    ▼
ProfileRouter.route(message) → ProfileRoutingResult {profile, confidence, reason}
metricsService.recordProfileRoute(profile, reason)             ← v4 新增
    │   ├─ L1: 显式标签 @规划/@设计/@开发/@测试/@运维 (confidence=1.0)
    │   ├─ L2: 中英文关键词匹配 (confidence=0.6-0.8)
    │   ├─ L3: 分支名模式 feature/*/hotfix/*/release/* (confidence=0.5)
    │   └─ L4: 默认 development-profile (confidence=0.3)
    │
    ▼
SkillLoader.loadSkillsForProfile(profile) → List<SkillDefinition>
    │   (展开 "foundation-skills-all" → 所有 foundation skills)
    │   (跳过 "domain-skills-contextual" → 运行时依赖)
    │   (v6: 按 stage/type 过滤 — 仅加载匹配 Profile 阶段的 Skill)
    │   (v6: 关键词匹配 — 消息关键词命中 Skill tags 时额外加载)
    │
    ▼
SystemPromptAssembler.assemble(profile, skills) → String
    │   [1] SuperAgent 角色定义 (CLAUDE.md, 排除 routing/loading 段)
    │   [2] Active Profile OODA 指导
    │   [3] 每个 Skill 内容作为独立 section
    │   [4] Baseline 执行规则
    │   [5] HITL 检查点
    │   [6] Available MCP 工具
    │   [7] Delivery Behavior 交付指导（v5 新增，当 workspace 工具可用时注入）
    │
    ▼
emit ooda_phase("orient") + metricsService.recordOodaPhase("orient")
emit profile_active 事件 → 前端显示 Profile Badge + Confidence 圆点
    │
    ▼
emit ooda_phase("decide") + metricsService.recordOodaPhase("decide")
agenticStream() — 最多 MAX_AGENTIC_TURNS(50) 轮（v12: 8→50，safety cap，模型自主决定何时停止）
    │
    ├─ 每轮: ClaudeAdapter.streamWithTools(systemPrompt=动态prompt)
    │   ├─ Prompt Caching: system prompt 以 content block + cache_control 发送
    │   ├─ content_block_stop: 仅对 tool_use block 发出 ToolUseEnd（v3 修复）
    │   └─ agenticStream: ToolUseEnd 仅在 currentToolId.isNotBlank() 时处理（v3 防御）
    │
    ├─ 每轮结束: metricsService.recordTurnDuration(turn, ms)   ← v4 新增
    ├─ 每轮结束: emit context_usage 事件（含 turn 序号）         ← v12 新增
    │
    ├─ 如果 stop_reason == TOOL_USE:
    │    ├─ emit ooda_phase("act") + metricsService.recordOodaPhase("act")
    │    ├─ 执行工具 (McpProxyService.callTool)
    │    ├─ metricsService.recordToolCall(name, success)       ← v4 新增
    │    ├─ metricsService.recordToolDuration(name, ms)        ← v4 新增
    │    ├─ 发送 tool_result 事件
    │    └─ 继续下一轮
    │
    └─ 如果 stop_reason == END_TURN / MAX_TOKENS:
         ├─ emit ooda_phase("complete") + metricsService.recordOodaPhase("complete")
         ├─ metricsService.recordMessageDuration(totalMs)      ← v4 新增
         ├─ 底线自动检查（v6 新增）:
         │    ├─ 检查本轮是否有 workspace_write_file 工具调用
         │    ├─ 如果有 → 从 Profile 获取 baselines 列表
         │    ├─ 运行 BaselineService.runBaseline() 逐个执行
         │    ├─ emit baseline_check 事件（status: running/passed/failed/exhausted）
         │    ├─ 如果失败 → 重新回到 Observe 阶段（附带失败原因）
         │    ├─ 最多重试 MAX_BASELINE_RETRIES(2) 轮
         │    └─ 全部通过或重试耗尽 → 继续
         ├─ 持久化消息和 tool calls
         ├─ 知识空白检测 (KnowledgeGapDetectorService)
         └─ 发送 done 事件
```

**McpProxyService 实连架构**（v4 新增，v6 更新 — callTool fallback 修复）:

```
ClaudeAgentService
    │
    ▼
McpProxyService.callTool(name, args, workspaceId?)
    │
    │ 1. Workspace 工具（local, v5 新增）
    │   ├─ workspace_write_file → WorkspaceService.createFile()
    │   ├─ workspace_read_file  → WorkspaceService.getFileContent()
    │   └─ workspace_list_files → WorkspaceService.getFileTree()
    │
    │ 2. 外部 MCP Server 查找（v6 改进）
    │   ├─ toolCache 查找: 哪个 server 有此工具 → 直接调用该 server
    │   ├─ 缓存未命中且未全部缓存 → 逐 server 尝试
    │   └─ 全部缓存且未找到 → 跳过，直接 fallback
    │
    │ 3. Built-in Fallback（local）
    │   ├─ search_knowledge → 本地知识库目录搜索
    │   ├─ read_file         → 本地知识库文件读取
    │   ├─ query_schema      → 后端内部 DB schema 查询
    │   ├─ run_baseline      → BaselineService.runBaseline(name)
    │   ├─ list_baselines    → BaselineService.listBaselines()
    │   └─ get_service_info  → 内置服务信息
    │
    ▼
McpToolCallResponse { content: List<McpContent>, isError: Boolean }
```

**外部 MCP Server 发现**（v6 新增）:
- 配置: `FORGE_MCP_SERVERS=http://knowledge-mcp:8081,http://database-mcp:8082`
- 启动时 `GET /tools` 发现工具列表 → toolCache 缓存
- 工具合并: 外部发现的工具 + built-in 默认工具，同名去重
- 日志: `"Discovered {n} tools from {server}"`

**MetricsService 注入点**（v4 新增）:

| 注入位置 | 指标 | 说明 |
|---------|------|------|
| `buildDynamicSystemPrompt` | `forge.profile.route` | 每次 Profile 路由记录 profile + method |
| `streamMessage` OODA 事件 (×4) | `forge.ooda.phases` | observe / orient / decide / complete |
| `agenticStream` OODA act | `forge.ooda.phases` | act（仅 tool_use 时触发） |
| `agenticStream` turn 结束 | `forge.turn.duration` | 每轮 agentic loop 耗时 |
| `agenticStream` tool 执行（成功） | `forge.tool.calls` + `forge.tool.duration` | 工具名 + success 状态 + 耗时 |
| `agenticStream` tool 执行（失败） | `forge.tool.calls` + `forge.tool.duration` | 工具名 + error 状态 + 耗时 |
| `streamMessage` 完成 | `forge.message.duration` | 消息处理端到端总耗时 |

**Prompt Caching 实现**:

| 状态 | system prompt 发送格式 | 费用（以 development 24K tokens 为例） |
|------|----------------------|--------------------------------------|
| 无缓存 | `"system": "..."` | $0.072/次 |
| 缓存写入 | `"system": [{"type":"text","text":"...","cache_control":{"type":"ephemeral"}}]` + `anthropic-beta: prompt-caching-2024-07-31` header | $0.090/次 (+25%) |
| 缓存命中 | 同上（5 分钟窗口内自动命中） | **$0.0072/次 (-90%)** |

**各 Profile System Prompt 规模**（v7 更新：渐进式加载后大幅缩小）:

| Profile | v6 字符数 | v7 字符数 | 降幅 | 加载 Skills 数 |
|---------|----------|----------|------|---------------|
| development | 96,165 | **25,219** | -74% | 8 |
| design | 43,248 | **20,344** | -53% | 4 |
| testing | ~38,500 | ~18,000 | ~-53% | 4 |
| ops | ~32,200 | ~16,000 | ~-50% | 3 |
| planning | ~29,600 | ~15,000 | ~-49% | 2 |

> v7 核心改变：system prompt 只注入 Skill Level 1 metadata（name + description + scripts 列表），
> Agent 通过 `read_skill` MCP 工具按需读取完整 SKILL.md 内容。

### 4.6 模型 Provider 支持（v12 新增 MiniMax）

**支持的 Provider**:

| Provider | 协议 | Adapter | 模型数 | 说明 |
|----------|------|---------|--------|------|
| anthropic | Anthropic Messages API | ClaudeAdapter | — | 默认 provider |
| bedrock | AWS Bedrock | BedrockAdapter | — | AWS 托管 |
| gemini | Google Gemini | GeminiAdapter | — | Google AI |
| dashscope | 阿里通义千问 | DashscopeAdapter | — | 阿里云 |
| openai | OpenAI API | OpenAiAdapter | — | OpenAI |
| minimax | Anthropic-compatible | ClaudeAdapter（复用） | 3 | v12 新增 |

**MiniMax 模型**（v12 新增）:

| 模型 ID | 能力层级 | Context Window |
|---------|---------|---------------|
| MiniMax-M2.5 | MEDIUM | 1,000,000 tokens |
| MiniMax-M2.5-lightning | LOW | 1,000,000 tokens |
| MiniMax-M2.5-highspeed | LOW | 1,000,000 tokens |

**模型选择端到端流程**（v12 新增）:
```
前端 ModelSelector（选择模型）
    → selectedModel state
    → WebSocket message { modelId: "MiniMax-M2.5" }
    → ChatWebSocketHandler 解析 modelId
    → ClaudeAgentService 动态 adapter 选择
    → ModelRegistry.providerForModel(modelId) 查找 provider
    → AgenticLoopOrchestrator 使用 activeAdapter 发送请求
```

**ModelRegistry 扩展**:
- `providerForModel(modelId): String?` — 根据模型 ID 查找所属 provider（v12 新增）
- MiniMax 使用 Anthropic-compatible 协议，复用 ClaudeAdapter，通过不同 baseUrl 路由

### 4.7 Spring Security 配置

**当前状态**: 支持安全模式开关（v5 更新）

- `forge.security.enabled: ${FORGE_SECURITY_ENABLED:-false}` — 环境变量控制
- **安全模式关闭**（默认）: 所有端点无需认证即可访问
- **安全模式开启**: OAuth2 Resource Server + JWT 验证
  - 公开端点（permitAll）: `/actuator/health`, `/actuator/info`, `/ws/**`, `/api/auth/**`, `/h2-console/**`, OPTIONS 请求
  - 其他端点需要有效 JWT Bearer Token
  - JWT issuer-uri: `http://localhost:8180/realms/forge`（Keycloak）
  - JWT audience: `forge-web-ide`
- CORS 允许来源: `http://localhost:3000,http://localhost:9000` (可配置)
- **WebSocket CORS**: `forge.websocket.allowed-origins` 必须为逗号分隔字符串（非 YAML list），默认 `http://localhost:3000,http://localhost:5173,http://localhost:9000`
- frameOptions: `sameOrigin`（H2 console 需要）

**Keycloak 配置**（v5 新增）:

| 配置项 | 值 |
|--------|-----|
| Realm | `forge` |
| Client ID | `forge-web-ide` |
| Access Type | public |
| Auth Flow | OIDC Authorization Code + PKCE |
| Valid Redirect URIs | `http://localhost:9000/*` |
| Token Endpoint | `http://keycloak:8080/realms/forge/protocol/openid-connect/token` |
| 预置用户 | admin/admin (管理员), demo/demo (测试用户) |

### 4.8 后端技术栈

| 层 | 技术 | 版本 |
|---|------|------|
| 运行时 | JDK | 21 (Eclipse Temurin) |
| 框架 | Spring Boot | 3.3+ |
| 语言 | Kotlin | 1.9.25 |
| 协程 | kotlinx-coroutines | 1.7.3 |
| ORM | Spring Data JPA + Hibernate | (Spring Boot managed) |
| 数据库迁移 | Flyway | (Spring Boot managed) |
| HTTP 客户端 | Spring WebFlux (WebClient) | (Spring Boot managed) |
| 安全 | Spring Security + OAuth2 Resource Server | (试用阶段禁用) |
| 监控 | Spring Boot Actuator + Micrometer | 自定义 forge.* 指标（v4 新增） |
| 指标导出 | micrometer-registry-prometheus | /actuator/prometheus 端点（v4 新增） |
| YAML 解析 | Jackson Dataformat YAML | Skill/Profile frontmatter 解析 |
| 序列化 | Jackson + Kotlin Module | (Spring Boot managed) |
| 测试 | JUnit 5 + MockK 1.13 + AssertJ | 157 tests passing（v9 修正） |

---

## 五、验证状态

> Phase 5 全部完成后验证结果 (2026-02-22) — v8 更新

| # | 验证项 | 状态 | 说明 |
|---|--------|------|------|
| 1 | Docker 镜像构建 | ✅ | 6 个镜像全部构建成功（v6: +knowledge-mcp +database-mcp） |
| 2 | 容器启动 | ✅ | 6 容器 running, 全部 healthy（v6: 4→6） |
| 3 | Nginx 路由 | ✅ | 前端 200, API 正常, /auth/ proxy 正常（v5: +keycloak 代理） |
| 4 | 前端页面加载 | ✅ | `http://localhost:9000` 返回 200 |
| 5 | 后端 API | ✅ | `/api/mcp/tools`(25，含外部 MCP)（v8: 12→14 builtin, +update_workspace_memory/get_session_history） |
| 6 | AI Chat 流式响应 | ✅ | WebSocket + SSE 双通道均正常 |
| 7 | OODA 阶段指示器 | ✅ | Observe→Orient→Decide→[Act→]Complete 流转正常 |
| 8 | Profile Badge + Confidence | ✅ | 名称、skills 列表、confidence 圆点、路由原因均显示 |
| 9 | Profile 路由（显式标签） | ✅ | 5/5 — @规划/@设计/@开发/@测试/@运维 全部正确，confidence=1.0 |
| 10 | Profile 路由（关键词） | ✅ | 5/5 — 中英文关键词检测正确 |
| 11 | Profile 路由（默认回退） | ✅ | 1/1 — 无关消息回退到 development |
| 12 | Prompt Caching | ✅ | 缓存命中后 system prompt 费用降 90% |
| 13 | Agentic Loop 多轮 Tool Calling | ✅ | Turn 1 tool_use + Turn 2 content 正常 |
| 14 | MCP 实连 | ✅ | 14 builtin + 11 外部发现（v8: +update_workspace_memory/get_session_history） |
| 15 | BaselineService 底线集成 | ✅ | run_baseline / list_baselines 工具可用 + AgentLoop 自动检查（v6） |
| 16 | MetricsService 指标采集 | ✅ | 7 个 forge.* 自定义指标注册 |
| 17 | Actuator/Prometheus 端点 | ✅ | `/actuator/metrics/forge.*` + `/actuator/prometheus` |
| 18 | agent-eval 真实模型调用 | ✅ | ANTHROPIC_API_KEY 有则调 Claude，无则结构验证 |
| 19 | 跨栈迁移 PoC | ✅ | .NET → Java, 11 条业务规则 100% 覆盖 |
| 20 | AI → Workspace 交付闭环 | ✅ | workspace_write_file/read_file/list_files + file_changed 事件（v5 新增） |
| 21 | Keycloak SSO | ✅ | OIDC PKCE 登录/回调/JWT/登出（v5 新增） |
| 22 | Context Picker 实连 | ✅ | /api/context/search 4 类别（v5 新增） |
| 23 | FileExplorer CRUD | ✅ | 新建/重命名/删除（v5 新增） |
| 24 | 未保存标记 + 自动保存 | ✅ | 蓝色圆点 + 5 秒自动保存（v5 新增） |
| 25 | 代码块 Apply 按钮 | ✅ | 代码块 → workspace 文件（v5 新增） |
| 26 | 知识库 12+ 文档 | ✅ | +5 新增文档可搜索（v5 新增） |
| 27 | 降级与容错 | ⏳ | 未测试 |
| 28 | Skill 渐进式加载 | ✅ | system prompt 55K→20-25K，metadata-only（v7 新增） |
| 29 | Skill 管理 API | ✅ | 9 端点 CRUD + enable/disable + 脚本执行 + 统计（v7 新增） |
| 30 | Skill 管理前端 | ✅ | /skills 页面 4 Tab + Tag 过滤 + 详情/创建（v7 新增） |
| 31 | Skill 质量治理 | ✅ | 32→28 个，D 级移除、C 级合并（v7 新增） |
| 32 | Skill 使用追踪 | ✅ | SkillUsageEntity + SkillAnalyticsService（v7 新增） |
| 33 | PLATFORM Skill 保护 | ✅ | 不可删除，返回 400（v7 新增） |
| 34 | 端到端交付闭环 | ✅ | 设计 profile 完整闭环：搜索→设计→写文档→baseline→HITL→总结（v7 新增） |
| 35 | 3 层记忆注入 | ✅ | Workspace Memory + Stage Memory + Session Summaries 注入 system prompt（v8 新增） |
| 36 | Session Summary 自动生成 | ✅ | streamMessage 完成后异步 LLM 生成结构化 JSON 摘要（v8 新增） |
| 37 | Memory REST API | ✅ | 6 端点 CRUD（workspace/stage/sessions），含字符限制和分页（v8 新增） |
| 38 | 消息压缩 3 阶段 | ✅ | 工具截断→早期摘要→全量总结，MAX_TOKENS=25K（v8 新增） |
| 39 | Rate Limit 指数退避 | ✅ | RateLimitException 捕获，3 次重试 1s/2s/4s（v8 新增） |
| 40 | 4-Tab 右侧面板 | ✅ | 对话/质量/Skills/记忆 四个 Tab（v8 新增） |
| 41 | Docker python3 | ✅ | Alpine apk add python3，Python 3.12.12（v8 新增） |
| 42 | Flyway V5 Migration | ✅ | 5 张表创建成功（v8 新增） |

### Phase 1.6 验收标准达成（v5）

| # | 标准 | 状态 |
|---|------|------|
| 1 | SkillLoader 独立加载 Skill | ✅ |
| 2 | SuperAgent OODA 循环运转，底线一次通过率 ≥ 70% | ✅ |
| 3 | 跨栈迁移 PoC：.NET → Java，业务规则覆盖率 ≥ 90% | ✅ (100%) |
| 4 | Web IDE 可访问：知识搜索 → AI 对话 → Skill 感知 → 工具调用 | ✅ |
| 5 | agent-eval 可运行真实评估场景 | ✅ |
| 6 | AI → Workspace 交付闭环：代码写入文件树 | ✅ |
| 7 | Keycloak SSO：OIDC PKCE 登录/登出 | ✅ |
| 8 | Context Picker 实连 4 类别 | ✅ |
| 9 | FileExplorer CRUD 完整 | ✅ |
| 10 | 未保存标记 + 5 秒自动保存 | ✅ |
| 11 | 知识库 12+ 文档 | ✅ |
| 12 | MCP 9 工具注册（+3 workspace） | ✅ |
| 13 | Docker 4 容器部署健康 | ✅ |

### Sprint 2.2 验收标准达成（v6 新增）

| # | 标准 | 状态 |
|---|------|------|
| 1 | Skill 按 Profile stage/type 动态过滤加载 | ✅ 24/24 TC |
| 2 | AgentLoop 底线自动检查，失败最多重试 2 轮 | ✅ 端到端验证 |
| 3 | knowledge-mcp 独立容器运行，本地文件搜索 | ✅ 7 文档返回 |
| 4 | database-mcp 独立容器运行，H2 schema/查询 | ✅ 3 表 + DML 拒绝 |
| 5 | McpProxyService callTool fallback 正确 | ✅ toolCache 查找 |
| 6 | Docker 6 容器全部 healthy | ✅ 一键启动 |

### Phase 4 验收标准达成（v7 新增）

| # | 标准 | 状态 |
|---|------|------|
| 1 | System prompt Skill 部分从 ~55K 降至 metadata-only (~1K) | ✅ |
| 2 | 渐进式加载 3 层（metadata → SKILL.md → 子文件/脚本） | ✅ |
| 3 | Skill 质量治理：32→28 个，全部 B 级以上 | ✅ |
| 4 | delivery-methodology 方法论 Skill 新建 | ✅ |
| 5 | ~19 个可执行脚本（验证 + 提取两类） | ✅ |
| 6 | Skill 管理 API：9 REST 端点 CRUD | ✅ |
| 7 | 前端 /skills 管理页面 | ✅ |
| 8 | PLATFORM Skill 不可删除 | ✅ |
| 9 | Skill 使用追踪 + 分析度量 | ✅ |
| 10 | MCP 工具 9→12 | ✅ |
| 11 | 端到端交付闭环手工验证 | ✅ |

### Phase 5 验收标准达成（v8 新增）

| # | 标准 | 状态 |
|---|------|------|
| 1 | 3 层记忆架构注入 system prompt | ✅ memory=78c+427c+2sessions |
| 2 | Session Summary 异步自动生成 | ✅ |
| 3 | Workspace Memory CRUD + 4000 char 限制 | ✅ |
| 4 | Stage Memory 从 Session Summary 自动聚合 | ✅ sessionCount=2 |
| 5 | 消息压缩 3 阶段（TokenEstimator + MessageCompressor） | ✅ |
| 6 | Rate Limit 指数退避（streamWithRetry） | ✅ |
| 7 | Memory REST API 6 端点 | ✅ |
| 8 | MCP 工具 12→14（+update_workspace_memory/get_session_history） | ✅ 25 tools total |
| 9 | 前端 4-Tab（对话/质量/Skills/记忆） | ✅ |
| 10 | Docker python3 可用 | ✅ Python 3.12.12 |
| 11 | Flyway V5 Migration（5 张表） | ✅ |
| 12 | System prompt 含 memory 后 < 30K chars | ✅ 27,261 chars |
| 13 | 验收测试 38 TC，通过率 ≥ 90% | ✅ 94.7% (36/38) |

### 单元测试

**总计**: 157 tests, 0 failures（v9 修正）

| 测试文件 | 测试数 | 覆盖范围 |
|---------|--------|---------|
| `SkillLoaderTest.kt` | 11 | frontmatter 解析、缓存、降级、reload |
| `ProfileRouterTest.kt` | 14 | 5 标签 + 中英文关键词 + 分支名 + 优先级链 |
| `SystemPromptAssemblerTest.kt` | 13 | 6 段组装 + MCP 降级 + 空集 + prompt 大小 |
| `SkillLoaderIntegrationTest.kt` | 7 | 真实 plugins/ 目录集成验证 |
| `ClaudeAdapterToolCallingTest.kt` | 9 | SSE 解析 + HTTP 错误 + tool_use 序列化 |
| `ClaudeAgentServiceTest.kt` | 8 | 同步/流式 + Agentic Loop + 降级 + MetricsService |
| `McpProxyServiceTest.kt` | 19+ | Tool dispatch + HTTP 调用 + formatResult + workspace tools（v5: +9） |
| `McpControllerTest.kt` | 3 | REST 端点 |
| `ChatRepositoryTest.kt` | 8 | JPA CRUD + 排序 + cascade |
| `MetricsServiceTest.kt` | 7 | Counter tags + Timer recording |
| `ContextControllerTest.kt` | 新建 | /api/context/search 4 类别搜索（v5 新增） |
| `EvalRunnerTest.kt` | 18 | 5 断言类型 + 有/无 adapter + profile/tag 过滤 |
| model-adapter tests | 11 | ClaudeAdapter + StreamEvent + tool calling |

**Skill 加载验证**: 28 skills, 5 profiles（v7: 渐进式加载 metadata-only，按需 read_skill）
**MCP 工具验证**: 14 builtin + 11 外部发现 = 25 total（v8: +update_workspace_memory/get_session_history）
**Memory 验证**: 3 层记忆注入 system prompt（v8: workspace=78c, stage=427c, sessions=2）

---

## 六、前端设计规范（开发参考）

> Phase 2 及后续开发必须延续以下设计风格，保持 UI 一致性。

### 6.1 组件结构模式

```typescript
// 标准组件结构
"use client";
import React, { useState, useRef, useEffect, useCallback } from "react";
import { IconName } from "lucide-react";  // 统一图标来源

interface ComponentProps { /* typed props */ }

export function ComponentName({ prop1, prop2 }: ComponentProps) {
  // State → Refs → Effects → Handlers → Return JSX
}
```

### 6.2 布局与间距

| 场景 | 样式 | 示例 |
|------|------|------|
| 容器布局 | `flex h-full flex-col` | AiChatSidebar 根容器 |
| Header 区域 | `flex items-center justify-between border-b border-border px-4 py-2` | 面板标题栏 |
| 滚动区域 | `flex-1 overflow-auto p-4 space-y-4` | 消息列表 |
| 输入区域 | `border-t border-border p-3` | 底部输入框 |
| 信息标签 | `flex items-center gap-1.5 text-xs text-muted-foreground` | Profile Badge, 状态标签 |
| 芯片/Chip | `rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary` | Context chips, @标签 |

### 6.3 交互模式

| 模式 | 实现方式 |
|------|---------|
| 按钮 hover | `hover:bg-accent` (背景) 或 `hover:text-foreground` (文字) |
| 禁用状态 | `disabled:opacity-50` |
| 危险操作 | `bg-destructive text-destructive-foreground hover:bg-destructive/90` |
| 主要操作 | `bg-primary text-primary-foreground hover:bg-primary/90` |
| 可折叠/展开 | 用 state toggle + 条件渲染（不用 CSS transition） |
| 加载骨架 | `animate-pulse rounded bg-muted`（如 ContextPicker 的 loading） |
| 流式内容 | 增量追加到 state，使用 `prev.map(m => m.id === id ? {...m, content} : m)` 更新 |

### 6.4 消息气泡设计

| 角色 | 样式 |
|------|------|
| 用户消息 | `bg-primary text-primary-foreground`，头像 `bg-primary` + `User` 图标，右对齐 |
| AI 消息 | `bg-card border border-border`，头像 `bg-forge-600` + `Bot` 图标，左对齐 |
| 头像 | `h-7 w-7 rounded-full flex-shrink-0` |
| 最大宽度 | `max-w-[85%]` |
| 时间戳 | `text-xs text-muted-foreground`，显示 `toLocaleTimeString()` |

### 6.5 Tool Call 展示

- 折叠式卡片：`rounded-md border border-border bg-card text-xs`
- 标题行：工具图标 (`Wrench h-3 w-3`) + 工具名 (`font-mono font-medium`) + 状态图标
- 状态图标：`Loader2 animate-spin text-primary`(运行中) / `CheckCircle text-green-400`(完成) / `AlertCircle text-destructive`(错误)
- 展开详情：`border-t border-border px-3 py-2`，Input/Output 用 `pre font-mono bg-muted p-2`

### 6.6 StreamEvent 处理模式

前端处理 SSE/WebSocket 事件的标准 switch 结构：

```typescript
(event: StreamEvent) => {
  switch (event.type) {
    case "ooda_phase":      // 更新 oodaPhase state → 驱动 OODA 指示器 5 图标流转（v3 新增）
    case "profile_active":  // 更新 activeProfile state（含 confidence）→ Profile Badge + Confidence 圆点
    case "thinking":        // 更新 thinkingText state
    case "content":         // 增量追加到 fullContent，更新消息列表
    case "tool_use":        // push 到 toolCalls 数组，更新消息
    case "tool_result":     // 更新对应 toolCall 的 output + status
    case "file_changed":    // dispatch forge:file-changed DOM 事件 → 刷新文件树 + 自动打开文件（v5 新增）
    case "error":           // 追加错误信息到消息内容
    case "done":            // 流结束，清理状态
  }
}
```

**AiChatSidebar 新增 State**（v3）：
```typescript
const [oodaPhase, setOodaPhase] = useState<OodaPhase | null>(null);
// activeProfile state 增加 confidence 字段
```
`oodaPhase` 和 `activeProfile` 在 `finally` 块和 `handleStop` 中重置为 `null`。

---

## 七、变更规则

1. **修改前**：查阅本文档对应基线节，确认当前设计
2. **评估影响**：判断变更是否为"非预期退化"还是"有意演进"
3. **有意演进**：更新本文档对应节 + 更新 `design-regression-baseline.sh` 快照
4. **非预期退化**：回退变更，或经 Review 后接受并更新基线
5. **新增设计**：在对应维度添加新节，标注引入日期和原因
6. **前端新增组件**：必须遵循第六节设计规范，保持视觉和交互一致性

---

> 基线版本: v12
> 初始冻结日期: 2026-02-18 (v1, Phase 1.5)
> v2 更新日期: 2026-02-18 (Phase 2 E2E 验证后)
> v3 更新日期: 2026-02-18 (Sprint 2A 验收通过后)
> v4 更新日期: 2026-02-19 (Phase 2 全部完成 — Sprint 2B + 2C)
> v5 更新日期: 2026-02-19 (Phase 1.6 全部完成 — AI 交付闭环 + Keycloak SSO + 编辑器增强)
> v5.1 更新日期: 2026-02-20 (BUG-019/020 修复 — Apply 按钮始终可见 + ContextPicker 搜索键盘转发)
> v6 更新日期: 2026-02-21 (Sprint 2.2 — Skill 条件触发 + AgentLoop 底线 + MCP 真实服务 + 6 容器)
> v7 更新日期: 2026-02-22 (Phase 4 — Skill 架构改造：渐进式加载 + 质量治理 + 管理 UI + 度量)
> v8 更新日期: 2026-02-22 (Phase 5 — 记忆与上下文管理：3 层记忆架构 + 消息压缩 + Memory UI + Rate Limit 退避)
> v9 更新日期: 2026-02-22 (全量交叉校验 — 补录 3 Controller / 3 Entity / 2 MCP 工具 / 3 SSE 事件)
> v10 更新日期: 2026-02-23 (Phase 6 — 产品可用性加固：Workspace 持久化 + Git 载入 + API Key override + codebase-profiler + 架构重构)
> v11 更新日期: 2026-02-23 (Phase 7 — 异步化 + 知识库 Scope 分层)
> v12 更新日期: 2026-02-23 (Session 32 — MiniMax 多模型支持 + 知识写入 + Agentic Loop 增强)
> 下次评审: Session 33 启动前
