# Web IDE 数据设计分析报告

## 1. 数据模型总览

本系统包含 **13 个核心实体**，涵盖工作空间、AI 对话、知识库、技能分析、记忆存储等核心领域。

---

## 2. 实体关系图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Workspace                                   │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  ┌───────────────┐  │
│  │Workspace    │  │Workspace     │  │StageMemory  │  │SessionSummary │  │
│  │Entity       │──│MemoryEntity  │  │Entity       │  │Entity         │  │
│  │(1:1)        │  │(1:1)         │  │(1:N)        │  │(1:N)          │  │
│  └─────────────┘  └──────────────┘  └─────────────┘  └───────────────┘  │
│        │                                                         │       │
│        │  1:N                                                    │       │
│        ▼                                                         ▼       │
│  ┌─────────────┐                                        ┌─────────────┐  │
│  │ChatSession  │ 1:N                                    │Execution   │  │
│  │Entity       │─────────────────────────────────────────│Record      │  │
│  └─────────────┘                                        │Entity      │  │
│        │                                                 └─────────────┘  │
│        │ 1:N                                                    │         │
│        ▼                                                        │         │
│  ┌─────────────┐  1:N                         ┌─────────────┐   │         │
│  │ChatMessage  │──────────────────────────────│ToolCall     │   │         │
│  │Entity       │                             │Entity       │◄──┘         │
│  └─────────────┘                             └─────────────┘             
│                                                                          
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  ┌───────────────┐  
│  │Knowledge    │  │SkillUsage   │  │HITLCheck-   │  │UserModel      │  
│  │Document     │  │Entity       │  │pointEntity  │  │ConfigEntity   │  
│  └─────────────┘  └──────────────┘  └─────────────┘  └───────────────┘  
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 核心实体详解

### 3.1 WorkspaceEntity — 工作空间

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 主键 |
| name | String | NOT NULL | 工作空间名称 |
| description | String | DEFAULT "" | 描述 |
| status | Enum (ACTIVE/ERROR/PENDING) | NOT NULL | 状态 |
| owner | String | NOT NULL | 所有者 |
| repository | String | NULLABLE | Git 仓库地址 |
| branch | String | NULLABLE | Git 分支 |
| localPath | String | NULLABLE | 本地路径 |
| errorMessage | String (1000) | NULLABLE | 错误信息 |
| createdAt | Instant | NOT NULL | 创建时间 |
| updatedAt | Instant | NOT NULL | 更新时间 |

**设计评价**: ⭐⭐⭐⭐⭐
- 字段完整，状态枚举合理
- 时间戳采用 Instant（UTC）
- 唯一约束清晰

---

### 3.2 ChatSessionEntity — AI 对话会话

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 会话 ID |
| workspaceId | String | FK, NOT NULL | 关联工作空间 |
| userId | String | - | 用户 ID |
| createdAt | Instant | NOT NULL | 创建时间 |
| updatedAt | Instant | NOT NULL | 更新时间 |
| messages | List<ChatMessage> | 1:N (LAZY) | 消息列表 |

**关系**:
```
Workspace (1) ─── (N) ChatSession
ChatSession (1) ─── (N) ChatMessage
```

---

### 3.3 ChatMessageEntity — 聊天消息

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 消息 ID |
| sessionId | String | FK, NOT NULL | 所属会话 |
| role | String (20) | NOT NULL | user/assistant/system |
| content | String (1MB) | NOT NULL | 消息内容 |
| createdAt | Instant | NOT NULL | 创建时间 |
| toolCalls | List<ToolCall> | 1:N (LAZY) | 工具调用列表 |

**设计评价**: ⭐⭐⭐⭐
- content 限制 1MB 合理，防止超大消息
- role 字段可改为枚举约束

---

### 3.4 ToolCallEntity — 工具调用记录

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 调用 ID |
| messageId | String | FK, NOT NULL | 关联消息 |
| toolName | String | NOT NULL | 工具名称 |
| input | String (1MB) | NULLABLE | 输入参数 |
| output | String (1MB) | NULLABLE | 输出结果 |
| status | String (20) | DEFAULT "complete" | 状态 |
| durationMs | Long | NULLABLE | 执行耗时(ms) |

**设计评价**: ⭐⭐⭐⭐⭐
- 完整记录 AI 工具调用全过程
- 包含耗时统计，便于性能分析

---

### 3.5 KnowledgeDocumentEntity — 知识文档

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 文档 ID |
| title | String (500) | NOT NULL | 标题 |
| type | Enum (ADR/RUNBOOK/CONVENTION/API_DOC) | NOT NULL | 文档类型 |
| content | TEXT | NOT NULL | 完整内容 |
| snippet | String (1000) | DEFAULT "" | 摘要 |
| author | String | NOT NULL | 作者 |
| tags | String (2000) | DEFAULT "[]" | 标签 JSON 数组 |
| scope | Enum (GLOBAL/WORKSPACE/PERSONAL) | NOT NULL | 作用域 |
| scopeId | String | NULLABLE | 作用域 ID |
| createdAt | Instant | NOT NULL | 创建时间 |
| updatedAt | Instant | NOT NULL | 更新时间 |

**设计评价**: ⭐⭐⭐⭐
- 作用域设计支持多租户/多工作空间
- tags 存储为 JSON 字符串（可考虑 JSON column）

---

### 3.6 SkillUsageEntity — 技能使用统计

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK (AUTO) | 自增 ID |
| sessionId | String | NOT NULL | 会话 ID |
| skillName | String | NOT NULL | 技能名称 |
| action | String | NOT NULL | read/execute |
| scriptType | String | NULLABLE | 脚本类型 |
| profile | String | NOT NULL | Profile 名称 |
| success | Boolean | NOT NULL | 是否成功 |
| createdAt | Instant | NOT NULL | 创建时间 |

**用途**: 技能使用分析、学习循环数据

---

### 3.7 UserModelConfigEntity — 用户模型配置

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 配置 ID |
| userId | String | NOT NULL | 用户 ID |
| provider | String (50) | NOT NULL | 模型提供商 |
| apiKeyEncrypted | String (1024) | - | 加密后的 API Key |
| baseUrl | String (512) | - | 自定义端点 |
| region | String (50) | - | 区域 |
| enabled | Boolean | DEFAULT true | 是否启用 |
| createdAt | Instant | NOT NULL | 创建时间 |
| updatedAt | Instant | NOT NULL | 更新时间 |

**设计评价**: ⭐⭐⭐⭐⭐
- API Key 加密存储（EncryptionService）
- 支持多模型提供商

---

### 3.8 ExecutionRecordEntity — 执行记录

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 记录 ID |
| sessionId | String | NOT NULL | 会话 ID |
| profile | String | NOT NULL | Profile 名称 |
| skillsLoaded | Int | DEFAULT 0 | 加载的技能数 |
| oodaDurations | TEXT (JSON) | DEFAULT "{}" | OODA 各阶段耗时 |
| toolCalls | TEXT (JSON) | DEFAULT "[]" | 工具调用列表 |
| baselineResults | TEXT (JSON) | NULLABLE | 基线结果 |
| hitlResult | String | NULLABLE | HITL 审批结果 |
| totalDurationMs | Long | DEFAULT 0 | 总耗时(ms) |
| totalTurns | Int | DEFAULT 0 | 总轮次 |
| createdAt | Instant | NOT NULL | 创建时间 |

**设计评价**: ⭐⭐⭐⭐⭐
- 完整记录 OODA 循环执行过程
- 适合事后分析和审计

---

### 3.9 HitlCheckpointEntity — HITL 检查点

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 检查点 ID |
| sessionId | String | NOT NULL | 会话 ID |
| profile | String | NOT NULL | Profile 名称 |
| checkpoint | String | NOT NULL | 检查点类型 |
| deliverables | TEXT | DEFAULT "[]" | 交付物 JSON |
| baselineResults | TEXT | NULLABLE | 基线结果 |
| status | String | DEFAULT "PENDING" | PENDING/APPROVED/REJECTED |
| feedback | TEXT | NULLABLE | 审批反馈 |
| createdAt | Instant | NOT NULL | 创建时间 |
| resolvedAt | Instant | NULLABLE | 解决时间 |

---

### 3.10 SessionSummaryEntity — 会话总结

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 总结 ID |
| sessionId | String | UNIQUE, NOT NULL | 会话 ID |
| workspaceId | String | NOT NULL | 工作空间 ID |
| profile | String | NOT NULL | Profile 名称 |
| summary | TEXT | NOT NULL | 会话总结 |
| completedWork | TEXT | DEFAULT "[]" | 完成的工作 |
| artifacts | TEXT | DEFAULT "[]" | 生成的产物 |
| decisions | TEXT | DEFAULT "[]" | 做出的决策 |
| unresolved | TEXT | DEFAULT "[]" | 未解决的问题 |
| nextSteps | TEXT | DEFAULT "[]" | 下一步计划 |
| turnCount | Int | DEFAULT 0 | 轮次计数 |
| toolCallCount | Int | DEFAULT 0 | 工具调用次数 |
| createdAt | Instant | NOT NULL | 创建时间 |

---

### 3.11 WorkspaceMemoryEntity — 工作空间记忆

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 记忆 ID |
| workspaceId | String | UNIQUE, NOT NULL | 工作空间 ID |
| content | TEXT | NOT NULL | 记忆内容 |
| version | Int | DEFAULT 1 | 版本号 |
| updatedAt | Instant | NOT NULL | 更新时间 |

---

### 3.12 StageMemoryEntity — 阶段记忆

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | String (UUID) | PK | 记忆 ID |
| workspaceId | String | NOT NULL | 工作空间 ID |
| profile | String | NOT NULL | Profile 名称 |
| completedWork | TEXT | DEFAULT "[]" | 完成的工作 |
| keyDecisions | TEXT | DEFAULT "[]" | 关键决策 |
| unresolvedIssues | TEXT | DEFAULT "[]" | 未解决问题 |
| nextSteps | TEXT | DEFAULT "[]" | 下一步计划 |
| sessionCount | Int | DEFAULT 0 | 会话计数 |
| updatedAt | Instant | NOT NULL | 更新时间 |

**复合唯一约束**: (workspace_id, profile)

---

## 4. 发现的问题

### 4.1 JSON 字段存储为 TEXT ⚠️

多个实体将 JSON 存储为 String：
```kotlin
val oodaDurations: String = "{}"      // 应使用 JSON column
val toolCalls: String = "[]"          // 应使用 JSON column
val tags: String = "[]"              // 应使用 JSON column
```

**建议**: 
- PostgreSQL 使用 `jsonb` 类型
- JPA 使用 `@Column(columnDefinition = "jsonb")`

### 4.2 主键策略不统一 ⚠️

| 实体 | 主键类型 | 生成策略 |
|------|----------|----------|
| WorkspaceEntity | String (UUID) | 手动生成 |
| ChatSessionEntity | String (UUID) | 手动生成 |
| SkillUsageEntity | Long | AUTO |
| 大部分实体 | String (UUID) | 手动生成 |

**建议**: 统一使用 UUID 作为所有实体的主键

### 4.3 缺少软删除 ⚠️

所有删除操作都是硬删除：
```kotlin
@Delete  // 物理删除
fun deleteWorkspace(id: String)
```

**建议**: 添加 `deletedAt` 字段支持软删除

### 4.4 缺少审计字段 ⚠️

大部分实体缺少：
- `createdBy` - 创建人
- `updatedBy` - 更新人
- `version` - 乐观锁版本

### 4.5 索引不完整 ⚠️

```kotlin
// 缺失的索引
- chat_messages.session_id + created_at  // 按时间排序查询
- skill_usage.skill_name + created_at    // 使用统计
- hitl_checkpoints.session_id + status    // 待审批查询
```

### 4.6 ChatMessage content 大小 ⚠️

```kotlin
@Column(length = 1_000_000)  // 1MB 单字段
val content: String
```

**问题**: 
- 单条消息 1MB 限制
- 累积数据量可能很大
- 建议归档策略或压缩存储

---

## 5. 数据质量评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 主键设计 | ⭐⭐⭐⭐ | 大部分使用 UUID |
| 关系映射 | ⭐⭐⭐⭐⭐ | JPA 注解正确 |
| 字段类型 | ⭐⭐⭐ | JSON 用 TEXT 可优化 |
| 约束完整性 | ⭐⭐⭐⭐ | NOT NULL 到位 |
| 审计字段 | ⭐⭐ | 缺少 createdBy/updatedBy |
| 索引设计 | ⭐⭐ | 需完善 |
| 软删除 | ⭐ | 缺失 |

**综合评分: 7/10** — 基础良好，生产环境需补全审计和索引

---

## 6. 改进建议

| 优先级 | 改进项 | 工作量 |
|--------|--------|--------|
| **P0** | 添加审计字段 (createdBy, updatedBy, version) | 中 |
| **P0** | 完善索引设计 | 小 |
| **P1** | JSON 字段改用 jsonb 类型 | 中 |
| **P1** | 添加软删除支持 | 中 |
| **P2** | 统一主键策略 | 小 |
| **P2** | 消息归档策略 | 大 |

---

## 7. 数据库表清单

| 表名 | 用途 | 行数预估 |
|------|------|----------|
| workspaces | 工作空间 | 低 |
| chat_sessions | 对话会话 | 中 |
| chat_messages | 消息历史 | 高 |
| tool_calls | 工具调用 | 高 |
| knowledge_documents | 知识文档 | 低 |
| skill_usage | 技能统计 | 中 |
| user_model_configs | 模型配置 | 低 |
| execution_records | 执行记录 | 中 |
| hitl_checkpoints | 检查点 | 低 |
| session_summaries | 会话总结 | 低 |
| workspace_memories | 空间记忆 | 低 |
| stage_memories | 阶段记忆 | 低 |

---

*报告生成时间: 2026-02-23*