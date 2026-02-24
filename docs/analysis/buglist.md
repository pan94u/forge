# Forge Platform Bug 追踪清单

> 持久化记录所有发现的 Bug，包括根因分析和修复方案。
> 格式：Bug ID | 发现时间 | 严重等级 | 状态

---

## Bug 汇总

| ID | 严重等级 | 状态 | 简述 |
|----|---------|------|------|
| BUG-001 | P1 | ✅ 已修复 | FileExplorer 右键菜单不显示（空白区域无 onContextMenu） |
| BUG-002 | P2 | ✅ 已修复 | 右键文件夹时上下文菜单路径被覆盖为空（事件冒泡） |
| BUG-003 | P2 | ✅ 已修复 | 文件/文件夹可重名创建，无唯一性校验 |
| BUG-004 | P2 | ✅ 已修复 | 重名创建时无用户反馈（静默失败） |
| BUG-005 | P1 | ✅ 已修复 | 无法删除文件夹（后端 deleteFile 只删单个路径） |
| BUG-006 | P2 | ✅ 已修复 | 重名校验为全局级别，应改为同级校验 |
| BUG-007 | P2 | ✅ 已修复 | 右键文件时 New File/New Folder 创建到根目录而非父目录 |
| BUG-008 | P0 | ✅ 已修复 | 文件树不显示层级结构（枚举大小写序列化不匹配） |
| BUG-009 | P2 | ✅ 已修复 | rebuildFileTree 只支持 2 层扁平结构，不支持多级嵌套 |
| BUG-010 | P2 | ✅ 已修复 | McpControllerTest mock 签名不匹配（2参数→3参数） |
| BUG-011 | P2 | ✅ 已修复 | handleFileSelect 声明顺序错误导致 TypeScript 编译失败 |
| BUG-012 | P0 | ✅ 已修复 | AI 不写文件到 workspace（WebSocket 未传 workspaceId） |
| BUG-013 | P1 | ✅ 已修复 | 刷新页面后 AI 不记得对话历史（sessionId 未持久化） |
| BUG-014 | P2 | ✅ 已修复 | ContextPicker 抢焦点，按 `@` 后无法在主输入框继续输入 |
| BUG-015 | P2 | ✅ 已修复 | @设计/@测试 等 Profile 标签被 ContextPicker 拦截，无法路由到后端 |
| BUG-016 | P2 | ⏸ 挂起 | Agentic loop 8 轮耗尽后 AI 无文字输出（safety net 未生效） |
| BUG-017 | P1 | ✅ 已修复 | Knowledge Services 页面白屏崩溃（ServiceType/ServiceStatus 枚举大小写） |
| BUG-018 | P2 | ✅ 已修复 | Context Picker Knowledge tab 无内容（空字符串未 fallback 到通配搜索） |
| BUG-019 | P2 | ✅ 已修复 | 代码块 Apply/Copy 按钮不可见（CSS opacity-0 隐藏） |
| BUG-020 | P2 | ✅ 已修复 | Context Picker 搜索过滤无反应（焦点留在主输入框，键入未转发到搜索框） |
| BUG-021 | P1 | ✅ 已修复 | HITL Approve 后无任何反馈（APPROVE 分支为空，无确认消息） |
| BUG-022 | P1 | ✅ 已修复 | HITL Approve 后未继续执行（HITL 在 agentic loop 外，approve 后无后续执行路径） |
| BUG-023 | P2 | ✅ 已修复 | 活动日志在流程结束后消失（finally 块清理所有状态，isStreaming=false 隐藏面板） |
| BUG-024 | P0 | ✅ 已修复 | Development Profile rate limit — system prompt 106K→75K chars，7 skills 替代 20 skills |
| BUG-025 | P2 | ✅ 已修复 | OODA 指示器未显示 — BUG-024 修复后 rate limit 不再触发，OODA 正常显示 |
| BUG-026 | P1 | ✅ 已修复 | Baseline 修复循环触发 rate limit — 主循环 8 轮 + baseline 修复循环无 token 预算感知 |
| BUG-027 | P2 | ✅ 已修复 | test-coverage-baseline 对非 Java/Kotlin workspace 报失败 — 已从所有 profile 移除 |
| BUG-028 | P1 | ✅ 已修复 | WebSocket 消息体过大导致断连 — 多轮对话后 history 超出默认 8KB 缓冲区 |
| BUG-029 | P1 | ✅ 已修复 | streamWithRetry 在 Flow collect 阶段无法捕获 RateLimitException — 聊天 UI 卡住 |
| BUG-030 | P2 | ✅ 已修复 | Knowledge CRUD 400 — DocumentType/KnowledgeScope 枚举反序列化大小写不兼容 |
| BUG-031 | P3 | ✅ 已修复 | KnowledgeIndexService 初始化警告 — private val 在 init{} 中赋值 |
| BUG-032 | P1 | 🔴 未修复 | Chat Message FK 违约 — chatSessionId 在 chat_sessions 表中不存在，聊天历史未持久化 |

---

## 详细记录

### BUG-001: FileExplorer 右键菜单不显示
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: 右键点击文件树空白区域，只弹出浏览器原生菜单，不显示 New File/New Folder
- **根因**: 文件树容器 `<div>` 缺少 `onContextMenu` 事件处理
- **修复**: 在文件树容器 div 添加 `onContextMenu` handler，显示 New File / New Folder 选项
- **文件**: `web-ide/frontend/src/components/editor/FileExplorer.tsx`

### BUG-002: 右键文件夹时上下文菜单路径被覆盖
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: 右键文件夹弹出菜单但 path 为空，无法在该文件夹下创建子项
- **根因**: TreeNode 的 `onContextMenu` 没有调用 `e.stopPropagation()`，事件冒泡到容器后容器的 handler 将 path 覆盖为 `""`
- **修复**: TreeNode 的 `onContextMenu` 添加 `e.stopPropagation()`
- **文件**: `web-ide/frontend/src/components/editor/FileExplorer.tsx`

### BUG-003: 文件/文件夹可重名创建
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: 在同一目录下可以创建同名的文件或文件夹
- **根因**: `handleNewFile` 和 `handleNewFolder` 没有唯一性校验逻辑
- **修复**: 添加 `collectPaths()` 工具函数进行创建前校验（后续升级为 `hasDuplicateSibling` 同级校验）
- **文件**: `web-ide/frontend/src/components/editor/FileExplorer.tsx`

### BUG-004: 重名创建时无用户反馈
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: 创建同名文件时无任何提示，用户以为操作失败
- **根因**: 校验失败后直接 `return`，没有 UI 反馈
- **修复**: 添加 `window.alert()` 提示 "already exists in this directory"
- **文件**: `web-ide/frontend/src/components/editor/FileExplorer.tsx`

### BUG-005: 无法删除文件夹
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: 点击 Delete 删除文件夹时，文件夹不消失
- **根因**: 后端 `WorkspaceService.deleteFile()` 只调用 `fileContents.remove(path)` 删除精确匹配的 key。文件夹 `src` 不是 fileContents 的 key（key 是 `src/index.ts` 等），所以删除无效
- **修复**: `deleteFile` 增加前缀匹配删除：`files.keys.removeIf { it.startsWith("$path/") }`
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/WorkspaceService.kt`

### BUG-006: 重名校验为全局级别
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: `src/index.ts` 存在时，无法在 `lib/` 下创建 `index.ts`（全局路径去重）
- **根因**: `collectPaths()` 收集整棵树所有路径做去重，应只检查同一父目录下的兄弟节点
- **修复**: 替换为 `hasDuplicateSibling()` 函数，按路径层级导航到父目录后只检查同级节点的 name
- **文件**: `web-ide/frontend/src/components/editor/FileExplorer.tsx`

### BUG-007: 右键文件时 New File 创建到根目录
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: 右键 `src/index.ts` → New File，默认路径是根目录而非 `src/`
- **根因**: 上下文菜单 New File/New Folder 按钮只在 `isDirectory` 时传递 parentPath，文件节点直接传 `undefined`
- **修复**: 新增 `getParentPath()` 方法，右键文件时自动推断其父目录（提取 lastIndexOf("/") 前的路径）
- **文件**: `web-ide/frontend/src/components/editor/FileExplorer.tsx`

### BUG-008: 文件树不显示层级结构（枚举序列化）
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: 文件树所有节点（包括文件夹）都显示为文件，没有展开箭头，无法展示嵌套目录
- **根因**: 后端 Kotlin 枚举 `FileType.DIRECTORY` 被 Jackson 序列化为 `"DIRECTORY"`（大写），但前端 TypeScript 类型定义为 `"directory"`（小写）。`node.type === "directory"` 永远为 false，所有节点都被当作文件渲染
- **修复**: 在 `FileType` 和 `WorkspaceStatus` 枚举上添加 `@JsonValue fun toValue() = name.lowercase()`，确保序列化为小写
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/model/Models.kt`

### BUG-009: rebuildFileTree 只支持 2 层扁平结构
- **发现**: Session 15, Phase 1.6 验收测试 场景 D
- **症状**: `src/utils/helper.ts` 在文件树中显示为 `src/` 下的扁平文件 `utils/helper.ts`，不是嵌套目录
- **根因**: 后端 `rebuildFileTree` 只按第一级目录分组，所有子路径拼接为单个文件名
- **修复**: 重写 `rebuildFileTree`，使用递归 MutableNode 树构建算法，支持任意深度的嵌套目录
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/WorkspaceService.kt`

### BUG-010: McpControllerTest mock 签名不匹配
- **发现**: Session 15, 自动化验收测试
- **症状**: `McpControllerTest` 编译失败，`callTool` mock 使用 2 参数但实际方法是 3 参数
- **根因**: Session 14 将 `callTool` 从 2 参数升级为 3 参数（增加 workspaceId），测试未同步更新
- **修复**: mock 表达式添加第三个 `any()` 参数
- **文件**: `web-ide/backend/src/test/kotlin/com/forge/webide/controller/McpControllerTest.kt`

### BUG-011: handleFileSelect 声明顺序错误
- **发现**: Session 15, 前端构建
- **症状**: `npm run build` 报 TypeScript 错误：`handleFileSelect` used before declaration
- **根因**: `useEffect`（第 52 行）引用了在第 70 行才声明的 `handleFileSelect`
- **修复**: 将 `handleFileSelect` 的 `useCallback` 声明移到 `useEffect` 之前
- **文件**: `web-ide/frontend/src/app/workspace/[id]/page.tsx`

### BUG-012: AI 不写文件到 workspace（WebSocket 未传 workspaceId）
- **发现**: Session 15, Phase 1.6 验收测试 场景 2 / 场景 B
- **症状**: AI 能调用 workspace_list_files、search_knowledge 等读取工具，但从不调用 workspace_write_file 写入文件。代码仅在聊天中展示，不写入 workspace
- **根因**: 三层断链：
  1. 前端 `claude-client.ts` 的 `streamMessage()` 不接受也不传递 workspaceId 参数
  2. WebSocket 消息 payload 中不包含 workspaceId
  3. 后端 `ChatWebSocketHandler.kt:113` 硬编码 `workspaceId = ""`，导致 `McpProxyService.callTool()` 中 workspace 工具路由失败（需要非空 workspaceId）
- **修复**:
  1. `claude-client.ts`: `streamMessage()` 新增 `workspaceId` 参数，WebSocket 消息中包含 workspaceId
  2. `AiChatSidebar.tsx`: 调用时传入 `workspaceId`
  3. `ChatWebSocketHandler.kt`: 从 payload 中提取 `workspaceId` 并传给 `claudeAgentService.streamMessage()`
- **文件**: `claude-client.ts`, `AiChatSidebar.tsx`, `ChatWebSocketHandler.kt`

### BUG-013: 刷新页面后 AI 不记得对话历史
- **发现**: Session 16, Phase 1.6 验收测试 TC-2.2
- **症状**: 刷新浏览器页面后，AI 聊天侧边栏显示空白，之前的对话消息全部丢失。用户需要重新开始对话
- **根因**: `AiChatSidebar.tsx` 每次组件挂载时调用 `createSession()` 创建新 sessionId，旧 sessionId 未持久化。刷新后新 session 无历史消息
- **修复**:
  1. `AiChatSidebar.tsx`: sessionId 持久化到 `localStorage`（key: `chat-session-{workspaceId}`）
  2. 组件挂载时先检查 localStorage 是否有已有 sessionId，有则复用
  3. 通过后端 API `/api/chat/sessions/{id}/messages` 恢复历史消息
- **文件**: `web-ide/frontend/src/components/chat/AiChatSidebar.tsx`

### BUG-014: ContextPicker 抢焦点
- **发现**: Session 16, Phase 1.6 验收测试 TC-2.3
- **症状**: 在聊天输入框按下 `@` 后，ContextPicker 弹出并自动获取焦点。后续输入的文字进入 ContextPicker 的搜索框而非主输入框，用户无法继续输入消息并发送
- **根因**: `ContextPicker.tsx` 的 `useEffect` 在组件挂载时自动调用 `searchInputRef.current?.focus()`，将焦点从主输入框抢走
- **修复**:
  1. `ContextPicker.tsx`: 移除自动 focus 的 `useEffect`
  2. `AiChatSidebar.tsx`: `handleContextSelect` 选中后调用 `inputRef.current?.focus()` 将焦点还给主输入框
  3. `AiChatSidebar.tsx`: `handleSubmit` 中增加 `setShowContextPicker(false)` 确保提交时关闭 picker
- **文件**: `ContextPicker.tsx`, `AiChatSidebar.tsx`

### BUG-015: @Profile 标签被 ContextPicker 拦截
- **发现**: Session 16, Phase 1.6 验收测试 TC-2.3 / TC-2.4
- **症状**: 在聊天输入框输入 `@设计` 或 `@测试`，Context Picker 弹出并搜索 "设计"/"测试"，显示 "No results"。Profile 标签无法到达后端 ProfileRouter
- **根因**: `AiChatSidebar.tsx` 的 `handleKeyDown` 在按下 `@` 键时无条件打开 ContextPicker，而 ContextPicker 只有 Files/Knowledge/Schema/Services 四个类别，没有 Profiles 选项
- **修复**:
  1. `ContextPicker.tsx`: 新增 "Profiles" 类别（放在第一个 tab），包含 5 个静态 Profile 选项（规划/设计/开发/测试/运维），不需要 API 调用
  2. `AiChatSidebar.tsx`: `handleContextSelect` 判断 `item.type === "profile"` 时，将 `@标签名 ` 插入到输入框文本前缀，而非添加为 context attachment。这样 `@设计` 等标签保留在消息文本中，后端 ProfileRouter 可以正常检测
- **文件**: `ContextPicker.tsx`, `AiChatSidebar.tsx`

### BUG-016: Agentic loop 耗尽后无文字输出（⏸ 挂起）
- **发现**: Session 16, Phase 1.6 验收测试 TC-2.3
- **症状**: `@设计 帮我看下这个系统的架构`，AI 调了 8 轮工具（workspace_list_files, workspace_read_file, search_knowledge 等），全部 stopReason=TOOL_USE，最终无文字总结输出
- **已尝试修复**:
  1. MAX_AGENTIC_TURNS 5 → 8（仍不够）
  2. Safety net：轮数耗尽后注入 user message + 无工具最终轮 → 产出 0 chars，未生效
- **根因推测**: Claude API 在长工具链后的空工具调用可能不产生 ContentDelta；或 conversation 过长导致模型输出为空
- **状态**: ⏸ 挂起，后续排查
- **文件**: `ClaudeAgentService.kt`

### BUG-017: Knowledge Services 页面白屏崩溃
- **发现**: Session 16, Phase 1.6 验收测试 TC-5.4
- **症状**: 切换到 Knowledge 页面的 Services 标签页时，页面白屏崩溃。浏览器控制台无明显报错，但 React 渲染异常
- **根因**: 后端 `/api/knowledge/services` 返回的 JSON 中 `ServiceType` 为 `"SERVICE"`（大写）、`ServiceStatus` 为 `"HEALTHY"`（大写），前端 TypeScript 类型定义期望小写 `"service"`/`"healthy"`。类型不匹配导致条件渲染逻辑异常（与 BUG-008 为同类问题）
- **修复**: 在 `ServiceType` 和 `ServiceStatus` 枚举上添加 `@JsonValue fun toValue() = name.lowercase()`，确保序列化为小写
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/model/Models.kt`

### BUG-018: Context Picker Knowledge tab 无内容
- **发现**: Session 17, Phase 1.6 验收测试 TC-6.4
- **症状**: 在 Context Picker 中切换到 Knowledge tab，列表为空，显示 "No knowledge found"
- **根因**: 两层问题：
  1. `ContextController.searchKnowledge()` 中 `query ?: "documentation"` 只处理 null 不处理空字符串（前端发 `q=` 空字符串）
  2. `McpProxyService.handleSearchKnowledge()` 对空查询直接返回 error（`'query' parameter is required`），不支持列出全部文档
- **修复**:
  1. `ContextController.kt`: 改为 `query ?: ""`，空查询时传空字符串触发"列出全部"
  2. `McpProxyService.kt`: 移除空查询报错逻辑，空查询时返回全部文档（13 条）
- **文件**: `ContextController.kt`, `McpProxyService.kt`

### BUG-019: 代码块 Apply/Copy 按钮不可见
- **发现**: Session 18, Phase 1.6 验收测试 TC-B.5
- **症状**: AI 回复中的代码块右上角没有 Apply 和 Copy 按钮，用户无法一键将代码写入 workspace
- **根因**: `ChatMessage.tsx` 中 Apply 和 Copy 按钮的 CSS 类包含 `opacity-0 group-hover:opacity-100`，默认隐藏。在聊天侧边栏窄面板中，`group-hover` 可能未正常触发
- **修复**: 移除 `opacity-0` 和 `group-hover:opacity-100`，让按钮始终可见
- **文件**: `web-ide/frontend/src/components/chat/ChatMessage.tsx`

### BUG-020: Context Picker 搜索过滤无反应
- **发现**: Session 18, Phase 1.6 验收测试 TC-C.3
- **症状**: 在 Context Picker 中切换到 Knowledge tab 后输入搜索关键词，列表不过滤，搜索框无反应
- **根因**: BUG-014 修复移除了搜索框自动聚焦后，焦点始终留在主聊天 textarea 上。用户打字时字符进入 textarea 而非 ContextPicker 搜索框
- **修复**:
  1. `ContextPicker.tsx`: 添加全局 keydown 监听，当用户输入可打印字符时自动聚焦搜索框；切换 tab 时 `setTimeout(() => searchRef.focus(), 0)`
  2. `AiChatSidebar.tsx`: 当 `showContextPicker` 为 true 时，`handleKeyDown` 中 `preventDefault()` 阻止可打印字符和 Backspace 进入 textarea
- **文件**: `ContextPicker.tsx`, `AiChatSidebar.tsx`

### BUG-021: HITL Approve 后无任何反馈
- **发现**: Session 23, Phase 3 验收体验
- **症状**: `@规划 写PRD` → HITL 暂停 → 点击「批准」→ 审批面板消失，之后完全没有反应
- **根因**: `ClaudeAgentService.kt` APPROVE 分支为空（`// Continue normally`），没有发送任何内容消息。后端直接跳到 `done` 事件关闭 WebSocket
- **修复**: APPROVE 分支增加确认消息 `✅ 已批准。{checkpoint} 通过审核。`
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/ClaudeAgentService.kt`

### BUG-022: HITL Approve 后未继续执行
- **发现**: Session 23, Phase 3 验收体验（BUG-021 修复后复测）
- **症状**: HITL 审批通过后，AI 只回复"已批准"就结束了，没有继续执行后续工作
- **根因**: HITL checkpoint 位于 agentic loop 结束之后（`streamMessage()` 第 246-291 行）。agentic loop 已跑完所有轮次才触发 HITL，APPROVE 后没有后续执行路径
- **期望**: APPROVE 后 AI 应继续执行（补充输出、进入下一阶段、或输出完整阶段总结报告）
- **修复方向**:
  - 方案 A：HITL checkpoint 移到 agentic loop 内部，在关键节点暂停，approve 后继续循环
  - 方案 B：APPROVE 后重入 agentic loop，以"用户已审批通过，请继续"作为新消息继续执行
  - 方案 C：APPROVE 后发送完整阶段总结报告（文件清单 + 执行统计 + 下一步建议）
- **状态**: ✅ 已修复 — APPROVE 后重入 agentic loop，要求 AI 输出阶段总结并继续工作
- **验证**: 规划阶段 approve 后输出 2269 字总结；设计阶段 approve 后继续写了 6 个文件（ADR + 架构 + API + DB schema）
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/ClaudeAgentService.kt`

### BUG-023: 活动日志在流程结束后消失
- **发现**: Session 23, Phase 3 验收体验
- **症状**: AI 执行过程中活动日志面板实时显示 sub_step 条目，但流程结束后日志消失
- **根因**: `AiChatSidebar.tsx` `finally` 块（第 424-432 行）清理所有状态。活动日志面板的显示条件绑定 `isStreaming`，流结束后 `isStreaming=false` 导致面板隐藏
- **修复方向**: 活动日志显示条件不应绑定 `isStreaming`，而是始终可见（只要有日志条目）。`finally` 块不应清理 `activityLog` 状态
- **状态**: ✅ 已修复 — 活动日志显示条件改为 `activityLog.length > 0`，不再绑定 `isStreaming`
- **验证**: 流程结束后活动日志仍可查看
- **文件**: `web-ide/frontend/src/components/chat/AiChatSidebar.tsx`

### BUG-029: streamWithRetry 在 Flow collect 阶段无法捕获 RateLimitException
- **发现**: Session 30, 用户测试聊天功能
- **严重等级**: P1
- **症状**: 用户在 Docker 环境中使用 AI 聊天，UI 卡住无响应，等待数分钟无结果
- **根因**: `AgenticLoopOrchestrator.streamWithRetry` 只在 Flow 创建阶段（`block()` 调用）用 try-catch 捕获 `RateLimitException`。但 Claude API 的 `RateLimitException` 也可能在 Flow 收集阶段（`.collect{}`）抛出（HTTP SSE 连接建立时），此时异常完全绕过重试逻辑，直接向上传播导致 WebSocket 无响应
- **修复**: 用 `flow {}` builder 重写 `streamWithRetry`，将 `block()` 和 `upstream.collect { emit(event) }` 都包裹在同一个 try-catch 内，确保两个阶段的 RateLimitException 都能被捕获并重试
- **状态**: ✅ 已修复
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/AgenticLoopOrchestrator.kt`

### BUG-030: Knowledge CRUD 400 Bad Request（枚举反序列化）
- **发现**: Session 30, Acceptance Test AT-3
- **严重等级**: P2
- **症状**: `POST /api/knowledge/docs` 创建文档时返回 400，Jackson 报错 `Cannot deserialize value of type KnowledgeScope from String "WORKSPACE": not one of the values accepted for Enum class: [personal, global, workspace]`
- **根因**: `KnowledgeScope` 有 `@JsonValue` 返回小写 → Jackson 反序列化也期望小写输入。`DocumentType` 无 `@JsonValue` → 期望大写输入。前端/curl 发送大写时 KnowledgeScope 拒绝，发送小写时 DocumentType 拒绝。与 BUG-008/017 同类系统性枚举序列化问题
- **修复**: 两个枚举都添加 `@JsonCreator` companion object，`fromValue()` 方法先 `.uppercase()` 再 `valueOf()`，实现大小写无关反序列化
- **状态**: ✅ 已修复
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/model/Models.kt`

### BUG-031: KnowledgeIndexService 初始化警告
- **发现**: Session 30, 编译阶段
- **严重等级**: P3
- **症状**: Kotlin 编译器警告 `Property must be initialized, be final, or be abstract`
- **根因**: `serviceGraph`、`apiCatalog`、`diagrams` 声明为 `private val` 但在 `init {}` 块中赋值，Kotlin 编译器认为可能未初始化
- **修复**: 改为内联初始化（`= buildSampleServiceGraph()` 等），`init {}` 只调用 `initializeSampleData()`
- **状态**: ✅ 已修复
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/KnowledgeIndexService.kt`

### BUG-032: Chat Message FK 违约（聊天历史未持久化）
- **发现**: Session 30, Docker 后端日志分析
- **严重等级**: P1
- **症状**: 后端日志反复报错 `Referential integrity constraint violation: CHAT_MESSAGES FOREIGN KEY(SESSION_ID) REFERENCES CHAT_SESSIONS(ID)`。聊天功能表面正常（消息在内存中），但刷新页面后聊天记录丢失
- **根因**: 前端通过 WebSocket 传入的 `chatSessionId`（如 `f0e866b0-3737-48e4-ac67-bf6121f63f90`）在 `chat_sessions` 表中不存在。`ChatWebSocketHandler` 或 `ClaudeAgentService` 在保存 `chat_messages` 前未创建对应的 `chat_sessions` 记录，导致 FK 约束失败
- **影响**: 所有对话历史未持久化到数据库。当前对话可正常进行（内存中），但刷新浏览器后丢失
- **修复方向**: 排查 `ChatWebSocketHandler` → `ClaudeAgentService` → `ChatSessionRepository` 链路，确保 WebSocket 连接建立时先 upsert `chat_sessions` 记录
- **状态**: 🔴 未修复
- **文件**: 待定（`ChatWebSocketHandler.kt` / `ClaudeAgentService.kt` / `ChatSessionRepository.kt`）

---

## 统计

### BUG-024: Development Profile rate limit
- **发现**: Session 23, `@开发` 实现用户注册
- **症状**: Turn 1 成功但 Turn 2 立即报错 `RateLimitException: 30,000 input tokens per minute`
- **根因**: `foundation-skills-all` 加载全部 20 个 foundation skills，system prompt 膨胀到 106,277 chars（约 35K tokens），超过 API 30K token/min 限额
- **修复**: 双重修复：(1) `development-profile.md` 从 `foundation-skills-all` 改为 7 个精确 skills，(2) `SkillLoader.loadSkillsForProfile()` 增加 60K chars safety net
- **验证**: 7 skills / 74,973 chars prompt → Turn 1-8 全部成功，无 rate limit ✅
- **状态**: ✅ 已修复已验证
- **文件**: `SkillLoader.kt`, `development-profile.md`

### BUG-025: OODA 指示器未显示
- **发现**: Session 23, `@开发` rate limit 快速失败时
- **症状**: 开发模式下 OODA 指示器未出现
- **根因**: Rate limit 在 Turn 2 立即触发，OODA 指示器来不及渲染。依赖 BUG-024 修复
- **验证**: BUG-024 修复后 Turn 1-8 正常执行，OODA 事件正常发送 ✅
- **状态**: ✅ 已修复已验证
- **文件**: `web-ide/frontend/src/components/chat/AiChatSidebar.tsx`

---

## 统计

### BUG-026: Baseline 修复循环触发 rate limit
- **发现**: Session 23, `@开发` 实现用户注册
- **症状**: Turn 8 耗尽 → 强制总结 → baseline 1/3 失败 → 修复循环 Turn 1-4 → rate limit，前端看到一直转圈最后报错
- **根因**: 主循环 8 轮 + 强制总结 1 轮 + baseline 修复循环（最多 8 轮），1 分钟内 13+ 轮 API 调用累积超 30K token/min
- **修复**: baseline 修复循环用 try-catch 捕获异常（RateLimitException 等），遇到限制时跳过修复直接返回当前结果
- **状态**: ✅ 已修复
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/service/ClaudeAgentService.kt`

### BUG-027: test-coverage-baseline 对非 Java/Kotlin workspace 报失败而非跳过
- **发现**: Session 23, `@开发` 第二轮验证
- **症状**: AI 在 workspace 生成 TypeScript 代码，baseline 检查时 `test-coverage-baseline` 报 FAIL：`No recognized build tool found (Gradle or Maven). Cannot run tests.`
- **根因**: `test-coverage-baseline.sh` 硬编码检测 Gradle/Maven，非 Java/Kotlin 项目无构建工具时报 FAIL 而非 SKIP
- **影响**: baseline 修复循环被无意义触发，AI 浪费 8 轮 API 调用尝试"修复"不存在的问题
- **修复**: 从 development-profile 和 testing-profile 的 baselines 列表中移除 test-coverage-baseline，只保留 code-style + security
- **状态**: ✅ 已修复
- **文件**: `development-profile.md`, `testing-profile.md`, `baseline-runner.kt`

### BUG-028: WebSocket 消息体过大导致断连 (code=1009)
- **发现**: Session 24, 全流程验证（规划→设计→开发）
- **症状**: 经过规划+设计多轮对话后，发送新消息时 WebSocket 立即断连，错误码 1009："The decoded text message was too big for the output buffer"
- **根因**: `WebSocketConfig.kt` 未设置 `maxTextMessageBufferSize`，使用 Tomcat 默认值 8KB。多轮对话后消息历史累积超过 8KB
- **修复**: 新增 `ServletServerContainerFactoryBean` Bean，设置 `maxTextMessageBufferSize = 512KB`
- **状态**: ✅ 已修复
- **文件**: `web-ide/backend/src/main/kotlin/com/forge/webide/config/WebSocketConfig.kt`

---

## 统计

- **总计**: 32 个 Bug
- **已修复**: 30 个
- **挂起**: 1 个 (BUG-016)
- **未修复**: 1 个 (BUG-032)
- **P0 (阻塞)**: 2 个 (BUG-008, BUG-012) — 均已修复
- **P1 (严重)**: 10 个 (BUG-001, BUG-005, BUG-013, BUG-017, BUG-021, BUG-022, BUG-026, BUG-028, BUG-029, BUG-032) — 9 已修复 / 1 未修复
- **P2 (一般)**: 17 个
- **P3 (低)**: 1 个 (BUG-031)

## 影响文件

| 文件 | 涉及 Bug |
|------|---------|
| `web-ide/frontend/src/components/editor/FileExplorer.tsx` | BUG-001~004, 006, 007 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/service/WorkspaceService.kt` | BUG-005, 009 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/model/Models.kt` | BUG-008, 017, 030 |
| `web-ide/frontend/src/app/workspace/[id]/page.tsx` | BUG-011 |
| `web-ide/backend/src/test/kotlin/com/forge/webide/controller/McpControllerTest.kt` | BUG-010 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/websocket/ChatWebSocketHandler.kt` | BUG-012 |
| `web-ide/frontend/src/lib/claude-client.ts` | BUG-012 |
| `web-ide/frontend/src/components/chat/AiChatSidebar.tsx` | BUG-012, 013, 014, 015, 020, 023 |
| `web-ide/frontend/src/components/chat/ContextPicker.tsx` | BUG-014, 015, 020 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/service/ClaudeAgentService.kt` | BUG-016, 021, 022, 026 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/controller/ContextController.kt` | BUG-018 |
| `web-ide/frontend/src/components/chat/ChatMessage.tsx` | BUG-019 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/service/skill/SkillLoader.kt` | BUG-024 |
| `plugins/forge-superagent/skill-profiles/development-profile.md` | BUG-024, 027 |
| `plugins/forge-superagent/skill-profiles/testing-profile.md` | BUG-027 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/config/WebSocketConfig.kt` | BUG-028 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/service/AgenticLoopOrchestrator.kt` | BUG-029 |
| `web-ide/backend/src/main/kotlin/com/forge/webide/service/KnowledgeIndexService.kt` | BUG-031 |
