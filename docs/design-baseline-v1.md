# Forge Web IDE — 设计基线 v2

> 基线日期: 2026-02-18 | Phase 2 E2E 验证通过后更新（v1 → v2）
> 本文档冻结当前已验证的 UI/API/数据模型/架构设计细节，作为未来修改的对照基准。
> 任何对本文档覆盖范围的修改，必须先意识到偏离、再决定是否接受。
>
> **v2 变更摘要**: 新增 Skill-Aware OODA Loop 架构（SkillLoader + ProfileRouter + SystemPromptAssembler）、Profile Badge UI、Prompt Caching、`/api/chat/skills` 和 `/api/chat/profiles` 端点、`profile_active` StreamEvent 类型。

---

## 一、UI/UX 设计基线

### 1.1 页面路由结构

| 路由 | 页面 | 源文件 | 说明 |
|------|------|--------|------|
| `/` | Dashboard | `src/app/page.tsx` | 欢迎页 + 快捷操作 + 最近项目 + 活动动态 |
| `/workspace/[id]` | IDE Workspace | `src/app/workspace/[id]/page.tsx` | 三面板 IDE：文件树 + 编辑器 + AI 聊天 |
| `/knowledge` | Knowledge Base | `src/app/knowledge/page.tsx` | 四标签页：Docs / Architecture / Services / APIs |
| `/workflows` | Workflow Editor | `src/app/workflows/page.tsx` | ReactFlow 可视化工作流编辑器 |

**根布局** (`src/app/layout.tsx`):
- `QueryClientProvider` (React Query 服务端状态)
- 全局 Header + 可折叠 Sidebar
- 默认暗色主题 (`<html className="dark">`)

### 1.2 关键组件布局

#### IDE Workspace（`/workspace/[id]`）— 三面板布局

```
┌─────────────────────────────────────────────────────────────────────┐
│ Header (全局)                                                       │
├──────────┬──────────────────────────────────┬───────────────────────┤
│ File     │ Monaco Editor                    │ AI Chat Sidebar       │
│ Explorer │   - 多标签页文件编辑               │   - 消息列表 + 自动滚动 │
│          │   - 25+ 语言语法高亮              │   - @ 提及附加上下文    │
│ (可折叠)  │   - Minimap + 括号匹配           │   - 流式响应展示       │
│          │   - "AI Explain" 按钮             │   - Tool Call 展开    │
│          ├──────────────────────────────────┤   - Profile Badge     │
│          │ Terminal Panel (可折叠底部)        │   - 会话管理          │
│          │   - WebSocket 终端连接            │                       │
│          │   - 彩色输出                      │ (可折叠)               │
└──────────┴──────────────────────────────────┴───────────────────────┘
```

#### AI Chat Sidebar Profile Badge（Phase 2 新增）

流式响应期间，在消息列表底部显示当前 active profile 信息：

```
┌──────────────────────────────────────────┐
│ development | kotlin-conventions, +3     │  ← Profile Badge
└──────────────────────────────────────────┘
```

**Profile Badge 设计规范**：
- 位置：消息列表底部，`thinkingText` 指示器上方
- 仅在 `isStreaming && activeProfile` 时显示
- 样式：`border border-border rounded-md px-2 py-1 bg-muted/50`
- 布局：`flex items-center gap-1.5 text-xs text-muted-foreground`
- Profile 名称：`font-medium text-primary`，去除 `-profile` 后缀
- Skills 列表：最多显示 3 个，超出部分显示 `+N`
- Profile 名和 Skills 之间用 `|` 分隔

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
| 图标库 | lucide-react 0.460+ | 全局统一图标（Send, Paperclip, RotateCcw, StopCircle, User, Bot, Wrench, CheckCircle, Loader2, AlertCircle, Copy, Check 等） |
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
| AI Chat | `/ws/chat/{sessionId}` | Client→Server | `{ type: "message", content: string, contexts: ContextReference[] }` |
| | | Server→Client | `StreamEvent` JSON (每行一个事件) |
| Terminal | `/ws/terminal/{workspaceId}` | 双向 | 终端输入/输出文本 |
| Workflow | `/ws/workflow/{id}` | Server→Client | 工作流执行步骤事件 |

### 2.3 SSE 事件格式

SSE 端点: `POST /api/chat/sessions/{sessionId}/stream`

**StreamEvent 类型**:

```typescript
type StreamEvent =
  | { type: "profile_active",                       // Profile 路由结果（Phase 2 新增，Agentic Loop 开始前发送）
      activeProfile?: string,                       //   路由到的 profile 名称
      loadedSkills?: string[],                      //   加载的 skill 列表
      routingReason?: string,                       //   路由原因（如 "keyword '接口' (score=1, conf=0.6)"）
      confidence?: number }                         //   置信度 0.0-1.0
  | { type: "thinking", content?: string }          // AI 思考过程
  | { type: "content", content?: string }           // 文本输出增量
  | { type: "tool_use_start", toolCallId?: string, toolName?: string }  // Tool 调用开始
  | { type: "tool_use", toolCallId?: string, toolName?: string, toolInput?: object }  // Tool 调用完整
  | { type: "tool_result", toolCallId?: string, content?: string, durationMs?: number }  // Tool 执行结果
  | { type: "error", content?: string }             // 错误
  | { type: "done" }                                // 流结束
```

**事件发送顺序**:
```
profile_active → [thinking →] content* → [tool_use → tool_result →]* → content* → done
```

**传输方式**:
- SSE: `data: {JSON}\n\n` 格式，`data: [DONE]` 结束
- WebSocket: 每行一个 JSON 对象

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
```

### 3.3 Flyway 迁移

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__create_chat_tables.sql` | 创建 `chat_sessions` + `chat_messages` + `tool_calls` 三张表 + 索引 |

**索引**:
- `idx_sessions_workspace` → `chat_sessions(workspace_id)`
- `idx_messages_session` → `chat_messages(session_id)`
- `idx_tool_calls_message` → `tool_calls(message_id)`

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
│  /*          → frontend:3000   (Next.js, catch-all)  │
└─────────┬──────────────────────┬────────────────────┘
          │                      │
┌─────────▼──────────┐ ┌────────▼──────────┐
│ Backend (:8080)    │ │ Frontend (:3000)  │
│ Spring Boot 3      │ │ Next.js 15        │
│ Kotlin + JDK 21    │ │ standalone mode   │
│ H2 / PostgreSQL    │ │ React 19          │
│ WebSocket + SSE    │ │ Monaco Editor     │
└────────────────────┘ └───────────────────┘
```

### 4.2 Nginx 路由规则（5 条）

源文件: `infrastructure/docker/nginx-trial.conf`

| # | 路径匹配 | 目标 | 特殊配置 |
|---|---------|------|---------|
| 1 | `/api/` | `backend:8080` | `proxy_buffering off` (SSE), `proxy_read_timeout 3600s` |
| 2 | `/ws/` | `backend:8080` | `proxy_http_version 1.1`, `Upgrade` + `Connection` headers |
| 3 | `/actuator/` | `backend:8080` | 标准代理 |
| 4 | `/h2-console/` | `backend:8080` | 标准代理 |
| 5 | `/` (catch-all) | `frontend:3000` | 标准代理 |

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

**三容器**:
- `backend`: Spring Boot JAR on JRE Alpine
- `frontend`: Next.js standalone on Node 20
- `nginx`: Alpine, 挂载 nginx-trial.conf

**健康检查**: `wget --spider http://localhost:8080/api/knowledge/search` (15s 间隔, 5 次重试, 30s 启动等待)

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
| `SkillLoader.kt` | 266 行 | 扫描 `plugins/` 目录，解析 YAML frontmatter (Jackson YAML)，`ConcurrentHashMap` 缓存，`@PostConstruct` 初始化 |
| `ProfileRouter.kt` | 197 行 | 4 级优先路由：显式标签 → 中英文关键词 → 分支名模式 → 默认 development |
| `SystemPromptAssembler.kt` | 238 行 | 6 段式动态 system prompt 组装 |

**完整 Agentic Loop 流程**:

```
用户消息
    │
    ▼
ProfileRouter.route(message) → ProfileRoutingResult {profile, confidence, reason}
    │   ├─ L1: 显式标签 @规划/@设计/@开发/@测试/@运维 (confidence=1.0)
    │   ├─ L2: 中英文关键词匹配 (confidence=0.6-0.8)
    │   ├─ L3: 分支名模式 feature/*/hotfix/*/release/* (confidence=0.5)
    │   └─ L4: 默认 development-profile (confidence=0.3)
    │
    ▼
SkillLoader.loadSkillsForProfile(profile) → List<SkillDefinition>
    │   (展开 "foundation-skills-all" → 所有 foundation skills)
    │   (跳过 "domain-skills-contextual" → 运行时依赖)
    │
    ▼
SystemPromptAssembler.assemble(profile, skills) → String
    │   [1] SuperAgent 角色定义 (CLAUDE.md, 排除 routing/loading 段)
    │   [2] Active Profile OODA 指导
    │   [3] 每个 Skill 内容作为独立 section
    │   [4] Baseline 执行规则
    │   [5] HITL 检查点
    │   [6] Available MCP 工具
    │
    ▼
emit profile_active 事件 → 前端显示 Profile Badge
    │
    ▼
agenticStream() — 最多 MAX_AGENTIC_TURNS(5) 轮
    │
    ├─ 每轮: ClaudeAdapter.streamWithTools(systemPrompt=动态prompt)
    │   └─ Prompt Caching: system prompt 以 content block + cache_control 发送
    │
    ├─ 如果 stop_reason == TOOL_USE:
    │    ├─ 执行工具 (McpProxyService.callTool)
    │    ├─ 发送 tool_result 事件
    │    └─ 继续下一轮
    │
    └─ 如果 stop_reason == END_TURN:
         ├─ 持久化消息和 tool calls
         ├─ 知识空白检测 (KnowledgeGapDetectorService)
         └─ 发送 done 事件
```

**Prompt Caching 实现**:

| 状态 | system prompt 发送格式 | 费用（以 development 24K tokens 为例） |
|------|----------------------|--------------------------------------|
| 无缓存 | `"system": "..."` | $0.072/次 |
| 缓存写入 | `"system": [{"type":"text","text":"...","cache_control":{"type":"ephemeral"}}]` + `anthropic-beta: prompt-caching-2024-07-31` header | $0.090/次 (+25%) |
| 缓存命中 | 同上（5 分钟窗口内自动命中） | **$0.0072/次 (-90%)** |

**各 Profile System Prompt 规模**:

| Profile | 字符数 | ~Input Tokens | 加载 Skills 数 |
|---------|--------|---------------|---------------|
| development | 96,165 | ~24,000 | 17 |
| design | 43,248 | ~10,800 | 3 |
| testing | 38,504 | ~9,600 | 3 |
| ops | 32,234 | ~8,000 | 3 |
| planning | 29,595 | ~7,400 | 2 |

### 4.6 Spring Security 配置

**当前状态**: 试用阶段安全功能已禁用

- `forge.security.enabled: false` (环境变量 `FORGE_SECURITY_ENABLED`)
- 所有端点无需认证即可访问
- CORS 允许来源: `http://localhost:3000,http://localhost:9000` (可配置)
- OAuth2 Resource Server 依赖已引入，待激活

### 4.7 后端技术栈

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
| YAML 解析 | Jackson Dataformat YAML | Skill/Profile frontmatter 解析 |
| 序列化 | Jackson + Kotlin Module | (Spring Boot managed) |
| 测试 | JUnit 5 + MockK 1.13 + AssertJ | 92 tests passing |

---

## 五、验证状态

> Phase 2 E2E 验证结果 (2026-02-18) — 22/24 测试路径通过

| # | 验证项 | 状态 | 说明 |
|---|--------|------|------|
| 1 | Docker 镜像构建 | ✅ | 3 个镜像全部构建成功 |
| 2 | 容器启动 | ✅ | 3 容器 running, backend healthy |
| 3 | Nginx 路由 | ✅ | 前端 200, API 正常返回 |
| 4 | 前端页面加载 | ✅ | `http://localhost:9000` 返回 200 |
| 5 | 后端 API | ✅ | `/api/knowledge/search` + `/api/chat/skills`(29) + `/api/chat/profiles`(5) |
| 6 | AI Chat 流式响应 | ✅ | 真实 Claude API Key 验证通过 |
| 7 | Profile 路由（显式标签） | ✅ | 5/5 — @规划/@设计/@开发/@测试/@运维 全部正确，confidence=1.0 |
| 8 | Profile 路由（关键词） | ✅ | 5/5 — 中英文关键词检测正确 |
| 9 | Profile 路由（默认回退） | ✅ | 1/1 — 无关消息回退到 development |
| 10 | Profile 路由（标签覆盖） | ✅ | 1/1 — 标签优先级高于关键词 |
| 11 | Prompt Caching | ✅ | 缓存命中后 system prompt 费用降 90% |
| 12 | 降级与容错 | ⏳ | 2/2 未测试 |

**单元测试**: 92 tests, 0 failures（`./gradlew :web-ide:backend:build`）

| 测试文件 | 测试数 | 覆盖范围 |
|---------|--------|---------|
| `SkillLoaderTest.kt` | 11 | frontmatter 解析、缓存、降级、reload |
| `ProfileRouterTest.kt` | 14 | 5 标签 + 中英文关键词 + 分支名 + 优先级链 |
| `SystemPromptAssemblerTest.kt` | 13 | 6 段组装 + MCP 降级 + 空集 + prompt 大小 |
| `SkillLoaderIntegrationTest.kt` | 7 | 真实 plugins/ 目录集成验证 |
| `ClaudeAdapterToolCallingTest.kt` | 9 | SSE 解析 + HTTP 错误 + tool_use 序列化 |
| `ClaudeAgentServiceTest.kt` | 7 | 同步/流式 + Agentic Loop + 降级 |
| `McpProxyServiceTest.kt` | 10 | Tool handlers + Cache + formatResult |
| `McpControllerTest.kt` | 3 | REST 端点 |
| `ChatRepositoryTest.kt` | 8 | JPA CRUD + 排序 + cascade |

**Skill 加载验证**: 29 skills, 5 profiles（Docker 日志确认）

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
    case "profile_active":  // 更新 Profile Badge state
    case "thinking":        // 更新 thinkingText state
    case "content":         // 增量追加到 fullContent，更新消息列表
    case "tool_use":        // push 到 toolCalls 数组，更新消息
    case "tool_result":     // 更新对应 toolCall 的 output + status
    case "error":           // 追加错误信息到消息内容
    case "done":            // 流结束，清理状态
  }
}
```

---

## 七、变更规则

1. **修改前**：查阅本文档对应基线节，确认当前设计
2. **评估影响**：判断变更是否为"非预期退化"还是"有意演进"
3. **有意演进**：更新本文档对应节 + 更新 `design-regression-baseline.sh` 快照
4. **非预期退化**：回退变更，或经 Review 后接受并更新基线
5. **新增设计**：在对应维度添加新节，标注引入日期和原因
6. **前端新增组件**：必须遵循第六节设计规范，保持视觉和交互一致性

---

> 基线版本: v2
> 初始冻结日期: 2026-02-18 (v1, Phase 1.5)
> 本次更新日期: 2026-02-18 (v2, Phase 2 E2E 验证后)
> 下次评审: Phase 2 Sprint 2A 完成后
