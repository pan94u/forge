# Phase 8 Bugfix — E2E 测试问题修复计划

> 来源: E2E 手动测试（40/50 TC 已完成）发现 9 个 Bug + 5 个 UX 问题 + 1 个优化项
> 日期: 2026-02-24

---

## 问题总览

| # | 严重度 | 分类 | 问题 | 复杂度 |
|---|--------|------|------|--------|
| 3 | High | Bug | 离开 workspace 再返回，用户消息显示为 AI 消息 | 中 |
| 1 | Medium | Bug | Sidebar 缺少 Evaluations 导航 | 低 |
| 4 | Medium | UX | AI 回复内容未按时间顺序展示（文本/工具/日志分块） | 高 |
| 5 | Medium | UX | Intent Confirmation 选 Profile 后直接执行，不追问意图 | 中 |
| 7 | Medium | Bug | Focus Mode 切换时页面显示异常 | 低 |
| 8 | Medium | Bug | Skills Tab 无启用/禁用开关 | 中 |
| 9 | Medium | Bug | Evaluations 页面数据不可交互 | 中 |
| 2 | Low | Bug | 创建文件夹后 .gitkeep 显示在文件树中 | 低 |
| 6 | Low | UX | workspace_list_files 只返回文件夹+文件个数 | 低 |
| - | - | 优化 | MAX_CONVERSATION_TOKENS 25K → 180K | 低 |
| - | - | UX | 记忆/管道 Tab 缺引导说明 | 低 |
| - | - | UX | 左侧面板拖拽手感不佳 | 低 |

---

## Sprint BF-1: 快速修复（30 min，5 个低复杂度问题）

### BF-1.1 Sidebar 添加 Evaluations 导航（BUG #1）

**文件**: `web-ide/frontend/src/components/common/Sidebar.tsx`
**修复**: 在 navItems 数组中添加 Evaluations 条目（在 Skills 之后）

```typescript
{
  href: "/evaluations",
  icon: BarChart3,  // 从 lucide-react 导入
  label: "Evaluations",
  roles: ["developer"],
},
```

### BF-1.2 隐藏 .gitkeep 文件（BUG #2）

**文件**: `web-ide/frontend/src/components/editor/FileExplorer.tsx`
**修复**: 在文件树渲染时过滤掉 `.gitkeep` 文件

### BF-1.3 Focus Mode 状态恢复（BUG #7）

**文件**: `web-ide/frontend/src/app/workspace/[id]/page.tsx`（约 338-350 行）
**修复**: 退出 Focus Mode 时恢复之前的面板状态，而非强制 `rightPanelOpen(true)`

```typescript
// 进入 focus 前保存状态
const prevLeftOpen = useRef(leftPanelOpen);
const prevRightOpen = useRef(rightPanelOpen);
// 退出时恢复
setLeftPanelOpen(prevLeftOpen.current);
setRightPanelOpen(prevRightOpen.current);
```

### BF-1.4 MAX_CONVERSATION_TOKENS 25K → 180K（优化）

**文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/memory/MessageCompressor.kt`（第 24 行）
**修复**: `const val MAX_CONVERSATION_TOKENS = 180_000`

### BF-1.5 记忆/管道 Tab 增加引导说明（UX）

**文件**:
- `web-ide/frontend/src/components/chat/MemoryPanel.tsx` — 顶部加简短说明
- `web-ide/frontend/src/components/chat/PipelinePanel.tsx` — 顶部加简短说明

---

## Sprint BF-2: 核心 Bug 修复（60 min，3 个中复杂度问题）

### BF-2.1 聊天历史角色显示错误（BUG #3 — High）

**文件**: `web-ide/frontend/src/components/chat/AiChatSidebar.tsx`（119-152 行）
**根因**: 从后端恢复聊天历史时，`m.role === "user"` 的比较可能因大小写不匹配失败（后端存 "USER"，前端期望 "user"）
**修复**: 使用大小写不敏感比较

```typescript
role: m.role.toLowerCase() === "user" ? ("user" as const) : ("assistant" as const),
```

### BF-2.2 Skills Tab 添加启用/禁用开关（BUG #8）

**文件**:
- `web-ide/frontend/src/components/skills/WorkspaceSkillPanel.tsx`（155-192 行）— 添加 toggle 开关 UI
- `web-ide/backend/` — 确认 Skill enable/disable API 是否存在，不存在则添加

### BF-2.3 Evaluations 页面交互增强（BUG #9）

**文件**: `web-ide/frontend/src/app/evaluations/page.tsx`（146-328 行）
**修复**:
- 时间选择器绑定实际筛选逻辑（调用 API 传 timeRange 参数）
- 表格行添加 onClick 展开详情
- Profile 分布图添加点击筛选

---

## Sprint BF-3: 复杂 UX 改进（90 min，2 个高/中复杂度问题）

### BF-3.1 AI 回复按时间顺序交错展示（BUG #4 — 最复杂）

**文件**: `web-ide/frontend/src/components/chat/AiChatSidebar.tsx`（272-436 行）
**现状**: SSE 事件按类型分组存储（`fullContent` 字符串 + `toolCalls` 数组），渲染时文本在上、工具在下
**修复方案**: 改为按时间顺序存储事件序列

```typescript
// 改为有序事件数组
type ChatEvent =
  | { type: "text"; content: string; timestamp: number }
  | { type: "tool_call"; toolCall: ToolCall; timestamp: number }
  | { type: "tool_result"; toolCallId: string; result: string; timestamp: number }
  | { type: "log"; content: string; timestamp: number };

// 渲染时按数组顺序展示，文本和工具调用交错
```

### BF-3.2 Intent Confirmation 追问意图（BUG #5）

**文件**:
- `web-ide/backend/src/main/kotlin/com/forge/webide/service/ClaudeAgentService.kt`（262-319 行）
**修复**: 用户选择 Profile 后，不直接执行，而是插入一轮追问：

```
当前流程: 模糊消息 → 选 Profile → 直接执行
修复流程: 模糊消息 → 选 Profile → AI 追问"你想让我做什么？" → 用户明确 → 执行
```

具体实现：选择 Profile 后，在 system prompt 中注入指令让 AI 先追问用户具体意图。

---

## Sprint 依赖与执行顺序

```
BF-1（快速修复，5个）──→ BF-2（核心修复，3个）──→ BF-3（UX改进，2个）
     30 min                   60 min                   90 min
```

全部串行，预计总耗时 **3 小时**。

## 不修复项（记录但低优先）

| 问题 | 原因 |
|------|------|
| BUG #6 workspace_list_files 格式 | 工具本身返回了文件树，只是 AI 选择了摘要方式呈现，非代码问题 |
| 左侧面板拖拽手感 | 需要引入 resize 库（如 react-resizable-panels），改动较大，非阻塞 |

## 验证方式

每个 Sprint 完成后：
1. `npm run build` 通过（前端类型检查）
2. `./gradlew :web-ide:backend:test` 通过（后端单元测试）
3. 手动验证对应 TC
4. Docker 重建验证
