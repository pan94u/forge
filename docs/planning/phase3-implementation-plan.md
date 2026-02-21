# Phase 3 实施计划 — 人机协作闭环（HITL + 透明度 + 管道 + 度量 + 学习循环）

> 版本: v1.0 | 日期: 2026-02-21 | 来源: Sprint 2.4 内部试用反馈讨论
> 讨论记录: `docs/sprint2.4-trial-discussion-record.md`

---

## 一、上下文

Sprint 2.4 内部试用后收到 4 条核心反馈，指向同一根因：**缺少"计划预览 → 人工审批 → 分步执行 → 结果度量"闭环**。

| # | 反馈 | 核心问题 |
|---|------|---------|
| 1 | 无完整管道 | 代码生成后断裂，不能编译/测试/部署 |
| 2 | 过度自动化 | AI 跑满 8 轮无人介入，HITL 设计了但未强制执行 |
| 3 | 黑盒感 | 不知道 AI 在做什么、将做什么 |
| 4 | 无完成度度量 | 数据采集了但无可视化 |

**Phase 3 目标**：实现完整的人机协作闭环，包括 5 Profile 全量 HITL 暂停点、持久化审批状态、真实编译/测试管道、质量度量面板、学习循环集成。

---

## 二、现状分析

### 已有基础

- `ProfileDefinition.hitlCheckpoint` 字段已在 `SkillModels.kt` 中定义
- 5 个 Profile 的 `hitl-checkpoint` YAML 值已写好（规划→PRD确认、设计→架构评审、开发→代码审查、测试→报告确认、运维→发布审批）
- `SystemPromptAssembler` 已将 HITL 文本注入 system prompt（但无运行时强制）
- `MetricsService` 已有 7 个 Micrometer 指标（但 `forge.baseline.results` 未接入）
- `ExecutionLogger`/`AssetExtractor`/`SkillFeedbackAnalyzer` 三个 Learning Loop 组件已有独立 Kotlin 代码（但未 Spring 化）
- 前端 `baseline_check` 事件后端已发送但前端未处理

### 关键 Gap

| Gap | 说明 |
|-----|------|
| HITL 无运行时暂停 | 仅靠 system prompt 提示，AI 自行决定是否暂停 |
| 前端缺 4 种事件处理 | `baseline_check`、`tool_use_start`、`hitl_checkpoint`（新）、`sub_step`（新） |
| 无审批回传通道 | WebSocket 仅支持 `message` 和 `ping` 入站 |
| 编译/测试工具不存在 | McpProxyService 无 `workspace_compile`/`workspace_test` |
| 度量无可视化 | Micrometer 数据仅在 JMX/Actuator，无前端面板 |
| Learning Loop 未集成 | 3 个组件是独立 `.kt` 文件，不是 Spring Service |

---

## 三、六大模块，16 个实施步骤

### 模块 1：执行透明度（解决反馈 3）

#### Step 1：后端 — 细粒度 sub_step 事件

- **文件**：`web-ide/backend/.../service/ClaudeAgentService.kt`
- **改动**：
  - 新增 `emitSubStep(session, message)` 辅助方法，发送 `{type: "sub_step", message, timestamp}`
  - `streamMessage()` 关键节点插入：
    - Observe：`"解析用户意图（${content.length} 字符）"`
    - Orient：`"路由到 ${profile.name}，加载 ${skills.size} 个 Skills"`
    - SystemPrompt 组装后：`"组装 system prompt: ${prompt.length} 字符"`
    - 每轮 Tool 执行前：`"调用 ${toolName}: ${toolInput简要}"`
    - 每轮 Tool 执行后：`"${toolName} 完成 (${durationMs}ms)"`
    - Baseline 执行：`"运行底线检查: ${baselineNames}"`
    - Baseline 结果：`"底线结果: ${passed}✅ ${failed}❌"`
  - `ooda_phase` 事件新增 `detail` 和 `turn`/`maxTurns` 字段
- **改动量**：~60 行

#### Step 2：前端 — 活动日志面板

- **文件**：`web-ide/frontend/src/lib/claude-client.ts`
  - `StreamEvent.type` 新增 `"sub_step" | "baseline_check" | "tool_use_start" | "hitl_checkpoint"`
  - 新增对应字段定义
- **文件**：`web-ide/frontend/src/components/chat/AiChatSidebar.tsx`
  - 新增 `activityLog: SubStep[]` 状态（最近 50 条）
  - 处理 `sub_step` 事件：追加到 activityLog
  - 处理 `baseline_check` 事件：渲染通过/失败状态徽标
  - OODA 指示器增强：显示 `Turn X/8`、当前工具名
  - OODA 下方新增可折叠「活动日志」面板（默认折叠，点击展开）
- **改动量**：~120 行

#### Step 3：前端 — Baseline 结果展示

- **文件**：`web-ide/frontend/src/components/chat/ChatMessage.tsx`
  - 新增 `BaselineResultCard` 组件：显示底线名称、状态（✅/❌/⏳）、详情折叠
  - AI 消息尾部渲染底线检查结果卡片
- **改动量**：~80 行

---

### 模块 2：HITL 全量暂停点（解决反馈 2）

#### Step 4：后端 — HITL 状态机 + 持久化

- **文件**：`web-ide/backend/.../service/ClaudeAgentService.kt`
- **改动**：
  - 新增 `HitlCheckpointManager` 内部类或独立 Service：
    ```kotlin
    data class HitlState(
      val sessionId: String,
      val profile: String,
      val checkpoint: String,
      val deliverables: List<String>,
      val baselineResults: List<BaselineResult>?,
      val createdAt: Instant,
      val status: HitlStatus  // PENDING, APPROVED, REJECTED, TIMEOUT
    )
    enum class HitlStatus { PENDING, APPROVED, REJECTED, TIMEOUT }
    ```
  - 使用 `ConcurrentHashMap<String, CompletableFuture<HitlDecision>>` 管理暂停
  - 每个 Profile 的 checkpoint 触发条件：
    - **planning**：当 AI 输出包含完整 PRD 结构（检测 `## 用户故事` 等标记）
    - **design**：当 AI 输出包含架构设计（检测 `## 架构` 或调用知识库写入）
    - **development**：当 `workspace_write_file` 被调用后（现有触发点）
    - **testing**：当 AI 输出包含测试报告结构（检测 `## 测试结果`）
    - **ops**：当 AI 输出包含部署计划（检测 `## 部署`）
  - **实际触发策略**：`development` Profile 使用工具调用检测（最可靠），其余 4 个 Profile 在 agentic loop 最后一轮（stopReason=END_TURN）时检查 `hitlCheckpoint` 是否非空触发暂停
  - 暂停时发送 `{type: "hitl_checkpoint", status: "awaiting_approval", profile, checkpoint, deliverables, baselineResults, timeoutSeconds: 300}`
  - 超时 5 分钟：自动标记为 TIMEOUT，发送 `{type: "hitl_checkpoint", status: "timeout"}` 并继续执行
  - HITL 状态持久化到 H2 数据库（新增 `hitl_checkpoints` 表），支持断线重连后恢复
- **文件**：`web-ide/backend/.../service/skill/SkillModels.kt`
  - 新增 `data class HitlDecision(val action: HitlAction, val feedback: String?, val modifiedPrompt: String?)`
  - 新增 `enum class HitlAction { APPROVE, REJECT, MODIFY }`
- **文件**：新建 `web-ide/backend/.../entity/HitlCheckpointEntity.kt`
  - JPA Entity 映射到 `hitl_checkpoints` 表
  - 字段：id, sessionId, profile, checkpoint, deliverables(JSON), status, feedback, createdAt, resolvedAt
- **文件**：新建 `web-ide/backend/.../repository/HitlCheckpointRepository.kt`
  - Spring Data JPA Repository
- **改动量**：~250 行（含新建文件）

#### Step 5：后端 — WebSocket 双向审批

- **文件**：`web-ide/backend/.../websocket/ChatWebSocketHandler.kt`
- **改动**：
  - `handleTextMessage()` 新增 `hitl_response` 消息类型：
    ```json
    {"type": "hitl_response", "action": "approve|reject|modify", "feedback": "...", "modifiedPrompt": "..."}
    ```
  - 调用 `claudeAgentService.resolveCheckpoint(sessionId, decision)`
  - `afterConnectionEstablished()` 中检查是否有 PENDING 状态的 checkpoint，如有则重新发送 `hitl_checkpoint` 事件（断线重连恢复）
- **改动量**：~50 行

#### Step 6：后端 — Reject/Modify 重入循环

- **文件**：`web-ide/backend/.../service/ClaudeAgentService.kt`
- **改动**：
  - Reject 处理：记录 rejection feedback，发送 `{type: "hitl_checkpoint", status: "rejected"}`，终止当前 agentic loop，向用户发送总结消息
  - Modify 处理：将 modifiedPrompt 注入到消息历史中作为新的用户消息，重新进入 `streamMessage()` 从 Orient 阶段开始（重新路由 Profile），发送 `{type: "hitl_checkpoint", status: "modified"}`
  - 两种处理都记录到 HITL 持久化表和 MetricsService
- **改动量**：~80 行

#### Step 7：前端 — 审批交互 UI

- **文件**：`web-ide/frontend/src/components/chat/AiChatSidebar.tsx`
  - 新增状态：`hitlPending: boolean`、`hitlData: HitlCheckpointData | null`
  - 收到 `hitl_checkpoint` (status=awaiting_approval) 时渲染 `HitlApprovalPanel`
  - 收到 `hitl_checkpoint` (status=timeout/rejected/modified) 时更新状态
- **文件**：新建 `web-ide/frontend/src/components/chat/HitlApprovalPanel.tsx`
  - 橙色边框审批卡片，显示：
    - Profile 名称 + Checkpoint 描述
    - 已生成文件列表
    - Baseline 结果摘要
    - 倒计时显示（5 分钟超时）
    - 3 个按钮：`✅ 批准继续` / `❌ 拒绝停止` / `✏️ 修改指令`
    - "修改指令"展开 textarea 输入框
  - 通过 WebSocket 发送 `{type: "hitl_response", action, feedback, modifiedPrompt}`
- **文件**：`web-ide/frontend/src/lib/claude-client.ts`
  - 新增 `sendHitlResponse(action, feedback?, modifiedPrompt?)` 方法
- **改动量**：~200 行（含新建 HitlApprovalPanel）

---

### 模块 3：编译/测试管道（解决反馈 1）

#### Step 8：后端 — workspace_compile 工具（真实编译）

- **文件**：`web-ide/backend/.../service/McpProxyService.kt`
- **改动**：
  - `builtInTools` 新增 `workspace_compile`
  - 实现逻辑：
    1. 读取 workspace 文件列表
    2. 根据文件类型检测项目类型（Kotlin/Java → Gradle, TypeScript → tsc, Python → py_compile）
    3. **真实编译**：在 workspace 目录执行编译命令（ProcessBuilder + 超时 60s）
    4. 如果 workspace 无构建配置，降级为**语法检查**
    5. 返回结构化结果：`{success, fileCount, errors[], warnings[], durationMs}`
  - 工具 schema：`{workspaceId: string, projectType?: string}`
- **改动量**：~120 行

#### Step 9：后端 — workspace_test 工具（真实测试）

- **文件**：`web-ide/backend/.../service/McpProxyService.kt`
- **改动**：
  - `builtInTools` 新增 `workspace_test`
  - 实现逻辑：
    1. 检测测试框架（JUnit, Jest, pytest）
    2. **真实执行**：运行测试命令（ProcessBuilder + 超时 120s）
    3. 如果无测试框架，降级为**文件分析**
    4. 返回结构化结果：`{passed, failed, skipped, coverage?, errors[], durationMs}`
  - 工具 schema：`{workspaceId: string, testPattern?: string}`
- **改动量**：~120 行

#### Step 10：Profile 更新 — 完整交付流程

- **文件**：`plugins/forge-superagent/skill-profiles/development-profile.md`
- **改动**：OODA Decide 阶段指引更新，加入完整交付流程：
  ```
  ### 标准交付流程
  1. 编码 → workspace_write_file（写入代码文件）
  2. 编译 → workspace_compile（验证编译通过）
  3. 底线 → 自动运行 code-style + security 检查
  4. 测试 → workspace_test（运行单元测试）
  5. [HITL] → 暂停，等待用户审查代码和测试结果
  6. 总结 → 输出执行报告

  如果编译失败，修复后重新编译。如果测试失败，修复后重新测试。
  循环直到全部通过或达到最大轮次。
  ```
- **改动量**：~30 行

---

### 模块 4：质量度量面板（解决反馈 4）

#### Step 11：后端 — 执行记录持久化

- **文件**：新建 `web-ide/backend/.../entity/ExecutionRecordEntity.kt`
  - JPA Entity：id, sessionId, profile, skillsLoaded, oodaDurations(JSON), toolCalls(JSON), baselineResults(JSON), hitlResult, totalDurationMs, createdAt
- **文件**：新建 `web-ide/backend/.../repository/ExecutionRecordRepository.kt`
- **文件**：`web-ide/backend/.../service/ClaudeAgentService.kt`
  - `streamMessage()` 完成时构建并保存 `ExecutionRecordEntity`
- **改动量**：~100 行

#### Step 12：后端 — Dashboard API

- **文件**：新建 `web-ide/backend/.../controller/DashboardController.kt`
- **端点**：
  - `GET /api/dashboard/metrics` — 聚合统计（baselineStats, toolCallStats, profileStats, oodaStats, hitlStats）
  - `GET /api/dashboard/executions?limit=20` — 最近执行记录列表
  - `GET /api/dashboard/trends?days=7` — 7 日趋势
- **文件**：`web-ide/backend/.../service/MetricsService.kt`
  - 新增 `getAggregatedStats()` 方法
  - 修复：`forge.baseline.results` 接入 `runBaselineAutoCheck()`
  - 新增 HITL 指标：`forge.hitl.checkpoints`、`forge.hitl.approval.duration`、`forge.hitl.approval.result`
- **改动量**：~200 行（含新建文件）

#### Step 13：前端 — 质量面板

- **文件**：新建 `web-ide/frontend/src/components/dashboard/QualityPanel.tsx`
  - **卡片区**（3 列）：底线通过率、HITL 审批统计、平均响应耗时
  - **工具调用排行**（横向柱状图，Top 10）
  - **趋势图**（7 日折线：会话数 + 底线通过率）
  - **执行记录表**（可排序表格）
- **文件**：`web-ide/frontend/src/components/chat/AiChatSidebar.tsx`
  - 顶部新增 Tab 切换：`对话` | `质量面板`
- **改动量**：~300 行（含新建 QualityPanel）

---

### 模块 5：学习循环集成

#### Step 14：后端 — ExecutionLogger Spring 化

- **源文件**：`plugins/forge-superagent/learning-loop/execution-logger.kt`（参考实现）
- **目标文件**：新建 `web-ide/backend/.../service/learning/ExecutionLoggerService.kt`
- **改动**：
  - 将独立 `ExecutionLogger` 改造为 Spring `@Service`
  - 依赖注入 `ExecutionRecordRepository`
  - `logExecution()` — 序列化为 JSON，写入文件系统 + 保存到 DB
  - 由 `ClaudeAgentService.streamMessage()` 在流程结束时调用
- **改动量**：~150 行

#### Step 15：后端 — SkillFeedbackAnalyzer 集成

- **源文件**：`plugins/forge-superagent/learning-loop/skill-feedback-analyzer.kt`（参考实现）
- **目标文件**：新建 `web-ide/backend/.../service/learning/SkillFeedbackService.kt`
- **改动**：
  - Spring `@Service` + `@Scheduled(cron = "0 0 2 * * *")` 每日凌晨 2 点执行
  - 读取最近 7 天执行记录，分析 Skill 使用效果
  - 生成报告保存到 `logs/feedback/feedback-{date}.md`
  - 新增 Dashboard API 端点：`GET /api/dashboard/skill-feedback`
- **改动量**：~120 行

---

### 模块 6：文档与验收

#### Step 16：文档更新 + 验收测试

- **新建**：`docs/phase3-acceptance-test.md` — 6 场景 ~24 TC：
  - 场景 1：执行透明度（4 TC）
  - 场景 2：HITL 全量暂停点（6 TC）
  - 场景 3：编译测试管道（4 TC）
  - 场景 4：质量面板（4 TC）
  - 场景 5：学习循环（3 TC）
  - 场景 6：端到端闭环（3 TC）
- **更新**：`docs/TRIAL-GUIDE.md` — 新增「HITL 审批流程」「编译测试管道」「质量面板」章节
- **更新**：`docs/planning/baseline-v1.5.md` — Phase 3 标记为已实现
- **改动量**：~400 行

---

## 四、执行顺序与依赖

```
模块 1（Step 1-3，透明度）     ──┐
模块 2（Step 4-7，HITL）       ──┼── 三路并行
模块 3（Step 8-10，编译测试）   ──┘
          │
模块 4（Step 11-13，度量面板）  ── 依赖模块 2（HITL 数据结构）
          │
模块 5（Step 14-15，学习循环）  ── 依赖模块 4（ExecutionRecordEntity）
          │
模块 6（Step 16，文档验收）     ── 依赖全部
```

建议拆为 3 个 Session：
- **Session A**：模块 1 + 模块 2（核心体验：透明度 + HITL）— 7 步
- **Session B**：模块 3 + 模块 4（管道 + 度量）— 6 步
- **Session C**：模块 5 + 模块 6（学习循环 + 文档）— 3 步

---

## 五、关键文件清单

### 后端修改（12 文件，7 新建）

| 文件 | 改动 |
|------|------|
| `web-ide/backend/.../service/ClaudeAgentService.kt` | 大（sub_step + HITL 暂停 + 执行记录） |
| `web-ide/backend/.../websocket/ChatWebSocketHandler.kt` | 中（hitl_response + 断线恢复） |
| `web-ide/backend/.../service/skill/SkillModels.kt` | 小（HitlDecision, HitlAction） |
| `web-ide/backend/.../service/McpProxyService.kt` | 大（compile + test 工具） |
| `web-ide/backend/.../service/MetricsService.kt` | 中（聚合统计 + HITL 指标 + baseline 接入） |
| `web-ide/backend/.../entity/HitlCheckpointEntity.kt` | **新建** |
| `web-ide/backend/.../repository/HitlCheckpointRepository.kt` | **新建** |
| `web-ide/backend/.../entity/ExecutionRecordEntity.kt` | **新建** |
| `web-ide/backend/.../repository/ExecutionRecordRepository.kt` | **新建** |
| `web-ide/backend/.../controller/DashboardController.kt` | **新建** |
| `web-ide/backend/.../service/learning/ExecutionLoggerService.kt` | **新建** |
| `web-ide/backend/.../service/learning/SkillFeedbackService.kt` | **新建** |

### 前端修改（5 文件，2 新建）

| 文件 | 改动 |
|------|------|
| `web-ide/frontend/src/components/chat/AiChatSidebar.tsx` | 大（活动日志 + HITL 状态 + Tab） |
| `web-ide/frontend/src/lib/claude-client.ts` | 中（新事件类型 + hitl_response 发送） |
| `web-ide/frontend/src/components/chat/ChatMessage.tsx` | 中（BaselineResultCard） |
| `web-ide/frontend/src/components/chat/HitlApprovalPanel.tsx` | **新建** |
| `web-ide/frontend/src/components/dashboard/QualityPanel.tsx` | **新建** |

### 配置与文档（4 文件）

| 文件 | 操作 |
|------|------|
| `plugins/forge-superagent/skill-profiles/development-profile.md` | 更新 |
| `docs/phase3-acceptance-test.md` | **新建** |
| `docs/TRIAL-GUIDE.md` | 更新 |
| `docs/planning/baseline-v1.5.md` | 更新 |

---

## 六、验证方式

1. **透明度验证**：发送消息 → 活动日志实时显示 10+ 条 sub_step → OODA 指示器显示 `Turn 3/8` + 工具名
2. **HITL 验证（5 Profile）**：
   - `@开发 实现订单服务` → AI 写入文件 → 暂停 → 审批面板出现 → 点击批准 → AI 继续
   - `@规划 写 PRD` → AI 输出 PRD → 暂停 → 点击拒绝 → AI 停止并输出总结
   - `@设计 支付系统架构` → AI 输出架构 → 暂停 → 修改指令 → AI 从 Orient 重新开始
   - 断线重连测试：暂停后刷新页面 → 重新连接 → 审批面板恢复
3. **管道验证**：`@开发 实现完整功能` → AI 依次 write_file → compile → test → baseline → HITL → 执行报告
4. **度量验证**：
   - `curl /api/dashboard/metrics` 返回包含 baseline/tool/profile/ooda/hitl 统计的 JSON
   - `curl /api/dashboard/trends?days=7` 返回 7 日趋势
   - 前端质量面板 Tab 显示 4 区域
5. **学习循环验证**：执行记录保存到 DB + 文件系统，Skill 分析定时任务可手动触发
6. **Docker 验证**：6 容器 healthy，全流程走通

---

## 七、预估工作量

| 模块 | 步骤数 | 新增代码行 | 修改代码行 | 新建文件 |
|------|--------|-----------|-----------|---------|
| 模块 1：透明度 | 3 | ~120 | ~140 | 0 |
| 模块 2：HITL | 4 | ~350 | ~230 | 3 |
| 模块 3：编译测试 | 3 | ~240 | ~30 | 0 |
| 模块 4：度量面板 | 3 | ~400 | ~200 | 4 |
| 模块 5：学习循环 | 2 | ~270 | 0 | 2 |
| 模块 6：文档 | 1 | ~400 | — | 1 |
| **合计** | **16** | **~1780** | **~600** | **10** |
