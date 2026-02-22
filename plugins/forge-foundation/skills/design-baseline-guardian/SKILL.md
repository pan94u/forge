---
name: design-baseline-guardian
version: "2.0"
scope: platform
category: foundation
triggers:
  - "refactor"
  - "redesign"
  - "modify"
  - "change"
  - "update"
  - "重构"
  - "修改"
  - "变更"
tags: [design, baseline, guardian, quality-gate, foundation]
baselines:
  - api-contract-baseline
  - architecture-baseline
---

# Design Baseline Guardian — 设计基线守护

## 目标

确保已通过 E2E 验证的设计决策不会在后续修改中被无意退化。本 Skill 实现设计原则 11：「已验证设计不可隐式退化」。

## 守护范围

### 1. UI/UX 设计基线
保护对象：页面路由结构、组件布局、交互流程、样式系统。

**检查项**：
- 页面路由未减少（当前 4 条：`/`, `/workspace/[id]`, `/knowledge`, `/workflows`）
- 三面板 IDE 布局保持（File Explorer + Editor + AI Chat）
- OODA 阶段指示器 5 icon 流转逻辑不变
- Profile Badge 显示 confidence 圆点 + 路由原因 + Skills 列表
- 暗色主题为默认（`<html className="dark">`）

### 2. API 契约基线
保护对象：REST 端点路径、请求/响应 DTO、WebSocket 消息格式、SSE 事件类型。

**检查项**：
- REST 端点路径不变（`/api/chat/*`, `/api/workspaces/*`, `/api/mcp/*`, `/api/knowledge/*`）
- SSE 事件类型集合只增不减（content, tool_use, tool_result, ooda_phase, profile_active, error, done）
- WebSocket 路径不变（`/ws/chat/{sessionId}`, `/ws/terminal/{workspaceId}`）
- ChatStreamMessage DTO 字段不减少
- SSE 格式兼容 `data:` 和 `data: `（Sprint 2A 修复）

### 3. 数据模型基线
保护对象：JPA 实体结构、Flyway 迁移脚本。

**检查项**：
- 现有数据库表不可删除（chat_sessions, chat_messages, tool_calls）
- 现有列不可删除或改变类型
- 新迁移脚本版本号必须连续递增
- 实体字段与迁移脚本保持一致

### 4. 架构决策基线
保护对象：Nginx 路由规则、Docker 部署结构、通信模式。

**检查项**：
- Nginx 统一入口 :9000 路由规则不变
- WebSocket 优先 + SSE 降级 + HTTP 兜底的三级降级策略
- SuperAgent 单 Agent 模式不变（不拆分为多 Agent）
- Skill Profile 路由优先级链不变（4 级：显式标签 > 关键词 > 上下文 > 默认）

## 使用方式

### 在 OODA Orient 阶段

当任务涉及修改已有代码时，自动检查修改是否触及基线保护范围：

1. **识别变更范围**：分析要修改的文件列表
2. **匹配基线条目**：将变更文件映射到上述 4 类基线
3. **评估影响**：判断变更是否会破坏基线约定
4. **报告偏离**：如果会，明确告知用户并要求确认

### 报告模板

```
⚠️ 设计基线影响评估

本次修改触及以下设计基线：

[API 契约] /api/chat/sessions/{id}/stream
  - 当前：POST, 返回 SSE 流
  - 变更：拟修改为 GET
  - 影响：前端 claude-client.ts 需同步修改
  - 建议：保持 POST 不变，或同时更新前端

[UI/UX] AiChatSidebar OODA 指示器
  - 当前：5 icon (Eye/Compass/Brain/Zap/CheckCircle2)
  - 变更：拟移除 Zap (Act) icon
  - 影响：用户无法看到工具执行阶段
  - 建议：保留，这是 Sprint 2A 验证过的设计

请确认是否接受这些基线偏离？
```

## 豁免条件

以下情况可以接受基线偏离：
1. 用户明确要求变更设计
2. 新增能力不影响现有行为（加法变更）
3. 修复已知 bug（修正性变更，需记录在 logbook）
4. 性能优化且不改变外部行为

偏离后必须更新 `docs/design-baseline-v1.md` 文档。
