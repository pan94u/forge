# Web IDE 项目 API 设计分析报告

## 1. API 概览

项目是一个 **Spring Boot** 后端服务，提供 Web IDE 核心功能。当前 API 结构如下：

| 模块 | 端点数量 | 主要功能 |
|------|----------|----------|
| Chat | 6 | AI 对话、会话管理 |
| Workspace | 14 | 工作空间、文件操作、Git |
| MCP | 3 | 工具调用代理 |
| Knowledge | 10 | 知识库搜索、文档管理 |
| 其他 | ~5 | 认证、仪表盘等 |

---

## 2. API 设计亮点

### 2.1 URL 命名规范

- 使用 **RESTful** 风格：`/api/chat/sessions`, `/api/workspaces/{id}/files`
- 资源使用 **复数名词**：`/messages`, `/workspaces`, `/tools`
- 层级结构清晰：`/workspaces/{id}/files/content`

### 2.2 HTTP 方法使用正确

| 场景 | 方法 | 示例 |
|------|------|------|
| 获取资源 | GET | `GET /api/workspaces/{id}` |
| 创建资源 | POST | `POST /api/chat/sessions` |
| 更新资源 | PUT | `PUT /api/workspaces/{id}/files/content` |
| 删除资源 | DELETE | `DELETE /api/workspaces/{id}` |

### 2.3 响应状态码规范

- `201 Created` - 创建资源成功
- `200 OK` - 查询/更新成功
- `204 No Content` - 删除成功
- `404 Not Found` - 资源不存在

---

## 3. 发现的问题

### 3.1 缺少分页支持

```
GET /api/chat/sessions/{id}/messages  # 返回所有消息，无分页
GET /api/knowledge/search            # 有限制参数但无游标分页
```

**建议**: 添加 cursor-based 分页：

```json
GET /api/chat/sessions/{id}/messages?cursor=xxx&limit=20

{
  "data": [...],
  "pagination": { "nextCursor": "...", "hasMore": true }
}
```

### 3.2 错误响应格式不统一

当前代码中混合使用不同格式：

```kotlin
// 方式1: 直接返回
return ResponseEntity.notFound().build()

// 方式2: Map
return ResponseEntity.ok(mapOf("error" to true, "message" to ...))
```

**建议**: 统一采用 RFC 7807 格式：

```json
{
  "type": "https://forge.example.com/errors/not-found",
  "title": "RESOURCE_NOT_FOUND",
  "status": 404,
  "detail": "Workspace abc-123 not found"
}
```

### 3.3 缺少 API 版本控制

当前所有端点都在 `/api/` 下，无版本标识：

```
/api/chat/sessions     # 无版本
/api/workspaces        # 无版本
```

**建议**: 添加版本前缀：

```
/api/v1/chat/sessions
/api/v2/workspaces
```

### 3.4 部分端点缺少认证注解

```kotlin
@GetMapping("/{id}/files")  // 未标注 @PreAuthorize
fun getFileTree(@PathVariable id: String)
```

**建议**: 添加安全注解或全局安全检查

### 3.5 字段命名不一致

```kotlin
// ChatMessage
val contexts: List<ContextReference>? = null  // camelCase

// McpToolCallResponse  
val isError: Boolean = false                  // camelCase

// 但某些地方
val errorMessage: String? = null               // camelCase + "error" 前缀
```

### 3.6 缺少 OpenAPI/Swagger 文档

项目中没有看到 `@Operation`、`@ApiResponse` 等注解，无法自动生成 API 文档。

---

## 4. API 质量评分

| 维度 | 评分 | 说明 |
|------|------|------|
| URL 命名 | ⭐⭐⭐⭐ | 基本符合 REST 规范 |
| HTTP 方法 | ⭐⭐⭐⭐⭐ | 使用正确 |
| 状态码 | ⭐⭐⭐⭐ | 基本规范，部分不统一 |
| 错误处理 | ⭐⭐ | 缺少统一 RFC 7807 格式 |
| 版本控制 | ⭐ | 完全缺失 |
| 分页 | ⭐⭐ | 仅部分支持 limit，无游标 |
| 文档 | ⭐ | 无 OpenAPI 注解 |
| 认证 | ⭐⭐⭐ | 部分端点缺失注解 |

**综合评分: 6.5/10** — 基础良好，但需规范化

---

## 5. 改进建议优先级

| 优先级 | 改进项 | 影响 |
|--------|--------|------|
| **P0** | 统一错误响应格式 | API 一致性 |
| **P0** | 添加 API 版本控制 | 长期可维护性 |
| **P1** | 添加分页支持 | 性能、可扩展性 |
| **P1** | 添加 OpenAPI 文档 | 可发现性、可测试性 |
| **P2** | 字段命名规范化 | 代码一致性 |
| **P2** | 补全安全注解 | 安全性 |

---

## 6. 后续跟踪

- [ ] 创建 ADR 记录 API 版本控制决策
- [ ] 定义统一的错误响应 DTO
- [ ] 添加 OpenAPI 注解到 Controller
