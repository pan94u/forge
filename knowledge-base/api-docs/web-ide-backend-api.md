# Web IDE Backend API

## Base URL
`http://localhost:8080` (直接) 或 `http://localhost:9000` (通过 Nginx)

## REST Endpoints

### Chat
| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/chat/sessions` | 创建聊天会话 |
| GET | `/api/chat/sessions/{id}/messages` | 获取会话消息列表 |
| POST | `/api/chat/sessions/{id}/messages` | 发送同步消息 |
| POST | `/api/chat/sessions/{id}/stream` | 流式消息 (SSE) |
| GET | `/api/chat/skills` | 获取所有已加载 Skills |
| GET | `/api/chat/profiles` | 获取所有 Profiles |

### Workspace
| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/workspaces` | 创建 Workspace |
| GET | `/api/workspaces` | 列表 |
| GET | `/api/workspaces/{id}` | 详情 |
| DELETE | `/api/workspaces/{id}` | 删除 |
| GET | `/api/workspaces/{id}/files` | 文件树 |
| GET | `/api/workspaces/{id}/files/content` | 读取文件 |
| PUT | `/api/workspaces/{id}/files/content` | 保存文件 |

### MCP Tools
| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/mcp/tools` | 列出所有可用工具 |
| POST | `/api/mcp/tools/call` | 调用工具 |
| POST | `/api/mcp/tools/cache/invalidate` | 清除工具缓存 |

### Knowledge
| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/knowledge/search` | 搜索知识库 |
| GET | `/api/knowledge/docs/{id}` | 获取文档 |
| GET | `/api/knowledge/services` | 服务依赖图 |
| GET | `/api/knowledge/apis` | API 目录 |

## WebSocket
| Path | 说明 |
|------|------|
| `/ws/chat/{sessionId}` | AI 聊天流式通信 |
| `/ws/terminal/{workspaceId}` | 终端模拟 |

## SSE Event Types
| Type | 说明 |
|------|------|
| `ooda_phase` | OODA 阶段切换 (observe/orient/decide/act/complete) |
| `profile_active` | Profile 路由结果 |
| `content` | 文本内容增量 |
| `tool_use_start` | 工具调用开始 |
| `tool_use` | 工具调用完成 |
| `tool_result` | 工具执行结果 |
| `error` | 错误 |
| `done` | 流结束 |
