# Phase 1 实施计划 — Web IDE 实连 + 跨栈基础能力

## Context

Phase 0 已完成（244 文件，38,600+ 行）。Phase 1 目标：Web IDE 与 Claude API 真实连通；codebase-profiler 支持 .NET 源码理解；4 个 Foundation Skill 深化。

**Phase 0 遗留的 5 个核心问题**：
1. `ClaudeAgentService.streamMessage()` 是**假流式**：先同步拿完整响应，再 `chunked(50) + Thread.sleep(20)` 模拟
2. `sendMessage()` 只做**单轮** tool calling：提取 tool_use → 执行 → 结束，不回传 tool_result 给 Claude
3. **MCP 端点不匹配**：前端调 `/api/mcp/tools`（无 controller），后端 proxy 调 `/tools/list`（MCP 服务端实际是 `/mcp/tools/list`）
4. **无数据库持久化**：所有数据存 `ConcurrentHashMap`，重启即丢失
5. `adapters/model-adapter/ClaudeAdapter` 有真 SSE 流式，但 Web IDE 后端**没有使用它**

**架构决策**：集成现有 `adapters:model-adapter` 模块的 ClaudeAdapter，扩展其 tool calling 能力。

---

## 实施节奏：5 周端到端切片

每周交付一个可验证的增量。

---

## Week 1：真流式聊天 ✅ COMPLETE

> 验证点：Web IDE 发消息 → 看到 token 逐个流入（无人工延迟）

### 1.1 扩展 ModelAdapter 支持 Tool Calling 类型

**修改** `adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/Models.kt`：
- 新增 `ToolDefinition(name: String, description: String, inputSchema: Map<String, Any?>)`
- 新增 `ToolUse(id: String, name: String, input: Map<String, Any?>)`
- 新增 `ToolResult(toolUseId: String, content: String, isError: Boolean = false)`

**新建** `adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/StreamEvent.kt`：
```kotlin
sealed class StreamEvent {
    data class MessageStart(val messageId: String, val model: String) : StreamEvent()
    data class ContentDelta(val text: String) : StreamEvent()
    data class ToolUseStart(val index: Int, val id: String, val name: String) : StreamEvent()
    data class ToolInputDelta(val partialJson: String) : StreamEvent()
    data class ToolUseEnd(val index: Int) : StreamEvent()
    data class MessageDelta(val stopReason: StopReason?) : StreamEvent()
    data object MessageStop : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}
```

**修改** `adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/ModelAdapter.kt`：
- 新增方法 `suspend fun streamWithTools(...): Flow<StreamEvent>`
- 保持原有 `complete()` / `streamComplete()` 不变（向后兼容）

### 1.2 扩展 ClaudeAdapter 实现 Tool Calling 流式

**修改** `adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/ClaudeAdapter.kt`：
- `buildRequestBody()` 增加 `tools` 参数 → 构建 Claude API 的 tools JSON
- SSE 事件解析：`message_start` → `content_block_start` → `content_block_delta` → `content_block_stop` → `message_delta` → `message_stop`
- `content_block_start` type=tool_use → emit `ToolUseStart`
- `content_block_delta` type=input_json_delta → emit `ToolInputDelta`
- `content_block_delta` type=text_delta → emit `ContentDelta`
- 保持 Messages 支持 tool_result role

### 1.3 Web IDE Backend 集成 ClaudeAdapter

**修改** `web-ide/backend/build.gradle.kts`：添加 `implementation(project(":adapters:model-adapter"))`
**新建** `web-ide/backend/.../config/ClaudeConfig.kt`：Spring Bean 配置
**修改** `web-ide/backend/.../service/ClaudeAgentService.kt`：注入 ClaudeAdapter，删除假流式逻辑

---

## Week 2：Tool Calling Agentic Loop ✅ COMPLETE

> 验证点：问 Claude "搜索知识库中关于 X 的文档" → 看到 tool_use → tool 执行 → 结果返回 → Claude 回答

### 2.1 实现 Agentic Loop

**修改** `ClaudeAgentService.kt`：
- `agenticStream()` 核心方法，最多 5 轮
- 每轮：`streamWithTools()` → 收集 text + tool_use blocks → 若 stop_reason=tool_use → 执行 tools → 构建 tool_result → 继续

**修改** `ChatWebSocketHandler.kt`：适配多轮事件流

### 2.2 修复 MCP 端点

**修改** `McpProxyService.kt`：`/tools/list` → `/mcp/tools/list`
**新建** `McpController.kt`：`/api/mcp/tools` REST 端点

### 2.3 扩展 Message 支持 tool 角色

**修改** `Models.kt`：Message.Role 增加 TOOL；Message 增加 toolUses/toolResults 字段
**修改** `ClaudeAdapter.kt`：messages 序列化支持 tool_use 和 tool_result 内容块

---

## Week 3：聊天数据库持久化 ✅ COMPLETE

> 验证点：聊天 → 重启服务器 → 历史消息完整保留

### 3.1 数据库依赖
`build.gradle.kts`：+JPA, Flyway, H2, PostgreSQL

### 3.2 JPA 实体
- `ChatSessionEntity`：id, workspaceId, userId, timestamps
- `ChatMessageEntity`：id, sessionId, role, content, toolCalls
- `ToolCallEntity`：id, messageId, toolName, input, output, status, durationMs

### 3.3 Repository
- `ChatSessionRepository`：findByWorkspaceId, findByUserId
- `ChatMessageRepository`：findBySessionIdOrderByCreatedAt

### 3.4 Flyway 迁移
`V1__create_chat_tables.sql`：3 张表 + 索引

### 3.5 配置
`application.yml`：H2 file 数据源 + Flyway enabled + JPA validate

### 3.6 服务层改造
`ClaudeAgentService.kt`：注入 Repository，历史从数据库读取
`AiChatController.kt`：ConcurrentHashMap → JPA

---

## Week 4：Skills 内容 ✅ COMPLETE

> 验证点：codebase-profiler 支持 .NET；business-rule-extraction Skill 存在

### 4.1 codebase-profiler 多语言扩展 ✅
- .NET：.sln/.csproj 解析, ASP.NET Controller, EF DbContext/Migrations, Domain Models
- Python：pyproject.toml, Django, FastAPI
- Go：go.mod, package structure

### 4.2 Foundation Skills 深化 ✅
- **spring-boot-patterns**：+Async (@Async + CompletableFuture), +Caching (@Cacheable + Caffeine), +Event Publishing (ApplicationEvent + @TransactionalEventListener), +Advanced @ConfigurationProperties
- **api-design**：+API 契约对比 (OpenAPI diff, breaking vs non-breaking), +跨栈迁移 API 映射 (ASP.NET ↔ Spring Boot ↔ FastAPI ↔ Go gin)
- **database-patterns**：+多数据源配置 (Primary + Legacy), +Entity Framework → JPA 完整映射 (DbContext, DbSet, Fluent API, Migrations)
- **error-handling**：+Saga Pattern (编排式补偿事务), +Outbox Pattern (可靠事件发布), +DLQ 处理, +.NET Exception → Java Exception 完整映射

### 4.3 business-rule-extraction Skill ✅
- 新建 Skill：从任意语言源码中提取业务规则、边界条件、异常处理策略
- 输出格式：per-domain files (business-rules.md, boundary-conditions.md, error-strategies.md)
- HITL 审批检查清单 + 质量标准

---

## Week 5：测试 + 集成验证 🔲 IN PROGRESS

> 验证点：全部测试绿灯，Phase 1 验收标准逐条通过

### 5.1 Adapter 层测试

**新建** `ClaudeAdapterToolCallingTest.kt`：
- SSE 解析：mock HTTP response → 验证 StreamEvent 序列
- tool_use content block 解析
- partial JSON 累积
- 错误处理（401, 429, 500）

### 5.2 Web IDE 后端测试

**新建** `ClaudeAgentServiceTest.kt`：agentic loop mock + 最大 5 轮 + 降级
**新建** `McpProxyServiceTest.kt`：端点路径 + 缓存 + fallback
**新建** `McpControllerTest.kt`：@WebMvcTest REST 端点
**新建** `ChatRepositoryTest.kt`：@DataJpaTest CRUD + Flyway

### 5.3 验收标准

- [ ] Web IDE → Claude API 真实 streaming（无 20ms 延迟）
- [ ] Tool calling agentic loop 工作（最多 5 轮）
- [ ] codebase-profiler .NET 项目画像
- [ ] 聊天历史数据库持久化（重启不丢失）
- [ ] MCP 工具端到端（前端 → 后端 → MCP Server）
- [ ] business-rule-extraction Skill 格式校验
- [ ] 全部单元/集成测试绿灯

---

## 关键文件清单

### 修改（14 个文件）
| 文件 | Week | 变更摘要 |
|------|------|---------|
| `adapters/model-adapter/.../Models.kt` | 1+2 | +ToolDefinition, ToolUse, ToolResult; Message 支持 tool 角色 |
| `adapters/model-adapter/.../ModelAdapter.kt` | 1 | +streamWithTools() 接口方法 |
| `adapters/model-adapter/.../ClaudeAdapter.kt` | 1+2 | +tool calling 流式, SSE 事件解析完整化 |
| `web-ide/backend/build.gradle.kts` | 1+3 | +model-adapter 依赖, +JPA/Flyway/H2/PostgreSQL |
| `web-ide/backend/.../service/ClaudeAgentService.kt` | 1+2+3 | 核心重写：真流式 + agentic loop + DB 持久化 |
| `web-ide/backend/.../service/McpProxyService.kt` | 2 | URI 修复: /tools/list → /mcp/tools/list |
| `web-ide/backend/.../websocket/ChatWebSocketHandler.kt` | 2 | 多轮 agentic 事件流 |
| `web-ide/backend/.../controller/AiChatController.kt` | 3 | ConcurrentHashMap → JPA |
| `web-ide/backend/.../resources/application.yml` | 3 | H2 数据源 + Flyway |
| `plugins/.../codebase-profiler/SKILL.md` | 4 | +.NET/Python/Go |
| `plugins/.../spring-boot-patterns/SKILL.md` | 4 | +Async/Cache/Event/ConfigProps |
| `plugins/.../api-design/SKILL.md` | 4 | +契约对比 + 跨栈映射 |
| `plugins/.../database-patterns/SKILL.md` | 4 | +多数据源 + EF→JPA |
| `plugins/.../error-handling/SKILL.md` | 4 | +Saga/Outbox/DLQ + .NET→Java异常 |

### 新建（12 个文件）
| 文件 | Week | 用途 |
|------|------|------|
| `adapters/model-adapter/.../StreamEvent.kt` | 1 | 流式事件类型 |
| `web-ide/backend/.../config/ClaudeConfig.kt` | 1 | ClaudeAdapter Spring Bean |
| `web-ide/backend/.../controller/McpController.kt` | 2 | MCP REST 端点 |
| `web-ide/backend/.../entity/ChatSessionEntity.kt` | 3 | JPA 实体 |
| `web-ide/backend/.../entity/ChatMessageEntity.kt` | 3 | JPA 实体 |
| `web-ide/backend/.../entity/ToolCallEntity.kt` | 3 | JPA 实体 |
| `web-ide/backend/.../repository/ChatSessionRepository.kt` | 3 | Spring Data JPA |
| `web-ide/backend/.../repository/ChatMessageRepository.kt` | 3 | Spring Data JPA |
| `web-ide/backend/.../resources/db/migration/V1__create_chat_tables.sql` | 3 | Flyway 迁移 |
| `plugins/.../business-rule-extraction/SKILL.md` | 4 | 新 Skill |
| `adapters/.../test/.../ClaudeAdapterToolCallingTest.kt` | 5 | Adapter 测试 |
| `web-ide/backend/src/test/...` (4 个测试文件) | 5 | 后端测试 |

---

## 依赖与风险

| 风险 | 缓解措施 |
|------|---------|
| Claude API SSE 格式变更 | ClaudeAdapter 内部封装，变更只需改一处 |
| MCP Server 未部署 | McpProxyService 有 fallback default tools |
| H2 → PostgreSQL 兼容性 | Flyway SQL 使用标准 SQL；JPA 自动适配方言 |
| Agentic loop 死循环 | 硬限制 5 轮；每轮 30s 超时 |

## 验证命令

```bash
# Week 1-3: 编译通过
./gradlew :adapters:model-adapter:build :web-ide:backend:build

# Week 2: 启动后端，WebSocket 连接测试
./gradlew :web-ide:backend:bootRun
# http://localhost:3000 → 发消息 → 观察流式 + tool calling

# Week 3: 数据库迁移 + 持久化
# 发消息 → 重启 → GET /api/chat/sessions/{id}/messages → 历史保留

# Week 4: Skill 格式校验
./gradlew :skill-tests:test

# Week 5: 全部测试
./gradlew :adapters:model-adapter:test :web-ide:backend:test :skill-tests:test
```
