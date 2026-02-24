# Sprint 2.4 内部试用讨论记录

> 日期: 2026-02-21 | Session 21 | 参与人: zhaoqi + Claude

---

## 一、背景

Sprint 2.1（CI/CD）、2.2（OODA 增强 + MCP 真实服务）、2.3（多模型适配器）、2.4（内部试用准备）均已完成。平台具备完整功能：6 容器部署、5 模型提供商（13+ 模型）、5 Profile + 32 Skill、9 MCP 工具、底线自动检查。

Sprint 2.4 产出了完整的试用材料后，zhaoqi 自己作为第一个试用者进行了真实体验测试。

---

## 二、试用活动记录

### 2.1 时间线（12:40 — 12:44，约 4 分钟有效使用）

| 时间 | 事件 | 详情 |
|------|------|------|
| 12:40:05 | 创建 Workspace | **"印章管理系统"** |
| 12:40:59 | 发送第 1 条消息 | 37 字符，路由到 development-profile |
| 12:41:06 | 第 1 次对话 Turn 1 | 调用 3 个工具（含 `adr_search`），耗时 6.8s |
| 12:41:07 | **Rate Limit** | Turn 2 触发限流（30K input tokens/min） |
| 12:41:49 | 重试第 1 条消息 | 同一消息重新发送 |
| 12:41:53 — 12:43:40 | **完整 8 轮 Agentic Loop** | 生成 6 个文件，用满 8 轮上限 |
| 12:44:25 | **底线自动检查** | code-style ✅、security ✅、test-coverage ❌ |
| 12:44:25 | 底线修复重试 | 第 1/2 次修复尝试 |
| 12:44:28 — 12:44:39 | 修复重试 4 轮 | 每轮调用 2 个工具，尝试补测试 |
| 12:44:40 | **Rate Limit** | 修复第 5 轮再次限流，对话中断 |

### 2.2 功能覆盖

| 功能 | 状态 | 说明 |
|------|------|------|
| Workspace 创建 | ✅ 正常 | "印章管理系统" |
| Profile 路由 | ✅ 正常 | 自动路由到 development-profile，加载 20/32 Skill |
| OODA 流程 | ✅ 正常 | 完整流转 |
| MCP 工具 — adr_search | ✅ 正常 | 调用了知识库搜索 |
| MCP 工具 — workspace_write_file | ✅ 正常 | 成功写入 6 个文件 |
| 底线自动检查 | ✅ 触发 | 检测到代码生成后自动运行 3 个 baseline |
| 底线修复重试 | ⚠️ 被限流中断 | 正在修复 test-coverage 时触发 Rate Limit |
| 多模型切换 | ❌ 未体验 | 全程使用 claude-sonnet-4-6 |

### 2.3 AI 生成的文件（印章管理系统）

| 文件 | 大小 | Turn |
|------|------|------|
| `package.json` | 539B | Turn 3 |
| `tsconfig.json` | 369B | Turn 4 |
| `src/models/types.ts` | 2.6KB | Turn 5 |
| `src/data/store.ts` | 2.8KB | Turn 6 |
| `src/services/sealService.ts` | 5.5KB | Turn 7 |
| `src/routes/seals.ts` | 689B | Turn 8 |

AI 用 8 轮生成了一个相对完整的 TypeScript 项目结构。

### 2.4 发现的 Bug

| Bug | 根因 | 修复 |
|-----|------|------|
| 空字符串 model 错误 | Kotlin `?:` 只处理 null 不处理空串 | `options.model?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL`，修复 ClaudeAdapter.kt 3 处 |

### 2.5 试用中发现的问题

1. **Rate Limit 是最大障碍** — System prompt 约 105K chars（20 个 Skill），加上消息内容轻松超过 30K tokens/min 限额。4 分钟内触发 2 次限流
2. **8 轮上限刚好够用** — "印章管理系统" 6 个文件恰好用满 8 轮，更复杂的项目可能不够
3. **底线修复被限流打断** — test-coverage baseline 失败后的自动修复流程因限流未能完成

---

## 三、用户四大核心反馈

### 反馈原文

> 1. 整体逻辑还可以，现在这个平台可以帮我来编译直到部署试用吗？只有打通这个全链路才能真正帮到我们的用户；
> 2. 我们现在的平台自动程度太高，人员的参与程度不够，我们之前的baseline规划里面有设计过；
> 3. 整个操作的过程中对于用户来讲比较黑盒，完全不知道发生了什么，还将发生什么？
> 4. 我们对于工作完成度的表现是什么，如何评估和度量，如何将我们的使用数据用来做未来的改进？

---

### 反馈 1：能否帮我编译直到部署？——全链路打通

**现状**：平台目前是"对话生成代码 → 写入 workspace → 结束"。代码生成后就断了，用户需要手动完成剩下的 build/test/deploy。

**已有设计但未实现的**：
- `deployment-preflight-baseline.sh`（部署预检）
- `runtime-health-baseline.sh`（运行时健康检查）
- Ops Profile 定义了 `hitlCheckpoint: "Release Approval"`

**缺失的关键链条**：

```
当前: 对话 → 生成代码 → 写入文件 → [断裂]
目标: 对话 → 生成代码 → 写入文件 → 构建 → 测试 → 审查 → 部署 → 验证
```

---

### 反馈 2：自动程度太高，人员参与不够

**现状**：AI 收到消息后跑满 8 轮 Agentic Loop，底线检查也是全自动修复重试——用户全程只能看着。

**已设计未落地的 HITL（Human-In-The-Loop）机制**：

baseline-v1.5.md 中设计了明确的审批点：

| Profile | 审批点 | 应暂停等待的动作 |
|---------|--------|----------------|
| Planning | PRD 确认 | 展示完整 PRD，等用户确认再继续 |
| Design | 架构评审 | 展示架构设计 + ADR，等批准 |
| Development | Code Review | 展示代码变更，等审查通过 |
| Testing | 测试报告审批 | 展示测试结果和覆盖率 |
| Operations | 发布审批 | 展示部署计划和风险评估 |

**试用中的问题**：AI 生成"印章管理系统"6 个文件，用户全程没有机会说"先停一下，types.ts 的设计我想调整"。底线检查失败后 AI 自己重试修复——也没问用户意见。

**实现差距**：约 30%。Profile Markdown 里写了 `hitlCheckpoint`，但后端没有**暂停执行等待审批**的机制，前端没有 **Approve / Reject / 修改意见** 的 UI。

---

### 反馈 3：黑盒感——不知道发生了什么、将发生什么

**现状**：OODA 指示器（Observe → Orient → Decide → Act → Done）和 Profile Badge 是唯一的透明度手段。但：

- OODA 只有 5 个高层阶段，**没有子步骤**（不知道"Act 阶段正在写第 3/6 个文件"）
- **没有执行计划预览**（AI 不会先告诉你"我打算创建这 6 个文件"）
- **没有进度百分比或时间估算**
- 工具调用卡片需要手动展开才能看到细节
- 8 轮 Agentic Loop 跑了约 2 分钟，用户只看到消息在"转圈"

**理想体验对比**：
```
AI: 我计划为印章管理系统创建以下文件：
  1. package.json — 项目配置
  2. tsconfig.json — TS 配置
  3. src/models/types.ts — 数据模型
  4. src/data/store.ts — 数据存储层
  5. src/services/sealService.ts — 业务逻辑
  6. src/routes/seals.ts — API 路由

  [确认执行] [修改计划]

（用户确认后）

  ✅ 1/6 package.json (539B)
  ✅ 2/6 tsconfig.json (369B)
  🔄 3/6 src/models/types.ts — 生成中...
```

---

### 反馈 4：完成度表现与度量

**现状**：Prometheus 采集了 7 个 `forge.*` 指标，但：
- **没有可视化**（没有 Grafana 或内置 Dashboard）
- **没有"任务完成度"概念**（一次对话 = 一个任务？不清楚）
- **数据只写不读**（指标采集了但没人看、没人用）

**baseline-v1.5.md 设计的度量体系（4 层指标）落地情况**：

| 指标层 | 设计状态 | 落地状态 |
|--------|---------|---------|
| 活跃度（日活/Skill 触发/工具调用） | ✅ 已设计 | ⚠️ Prometheus 采集但无面板 |
| 效率（PR 周期/审查时间/上手速度） | ✅ 已设计 | ❌ 无基线数据 |
| 质量（Bug 率/规范一致性/测试覆盖） | ✅ 已设计 | ⚠️ 部分有数据（147 测试/5 baseline） |
| Agent 可靠性（OODA 一次通过率） | ✅ 已设计，目标 ≥85% | ❌ 无统计 |

---

## 四、共同根因分析

四个反馈指向同一个核心差距：**平台目前是"AI 独舞"模式，缺少"人机协作"编排**。

```
当前:  用户输入 ──→ AI 自动跑完 ──→ 输出结果
                    （黑盒）        （无法评估）

目标:  用户输入 ──→ AI 提出计划 ──→ 用户审批 ──→ 分步执行（可见进度）
                                       ↕              ↓
                                   可随时干预      完成度度量 + 数据回流
```

这正是 baseline-v1.5.md 中 HITL + execution-logger + HookEngine 组合要解决的问题，目前设计覆盖率很高但实现只到 30-50%。

---

## 五、关键决策

### 用户决策

> "我觉得你的方向完全没问题，就是不需要 sprint2.5 这个 poc 版本了吧，直接做 phase3 的完整版吧"

**决策说明**：
- 初始方案提出了 Sprint 2.5 作为一个精简 POC 版本（模拟编译/测试、单点 HITL、简化面板）
- 用户认为方向正确但不需要 POC 过渡，直接实现 Phase 3 完整版
- 这一决策将 Phase 3 的范围从 10 步精简 POC 升级为 16 步完整实现

### 升级对比

| 维度 | Sprint 2.5 POC | Phase 3 完整版 |
|------|---------------|---------------|
| HITL | 1 个暂停点（development） | **5 Profile 全量 checkpoint** |
| 暂停机制 | 内存 CompletableFuture | **DB 持久化 + 断线重连恢复** |
| 编译/测试 | 模拟（正则解析语法） | **真实执行**（Gradle/tsc/pytest） |
| 度量面板 | 3 卡片简单面板 | **4 区域完整 Dashboard + 7 日趋势 + 执行记录表** |
| 学习循环 | 无 | **ExecutionLogger + SkillFeedbackAnalyzer Spring 集成** |
| 步骤数 | 10 步 | **16 步** |
| 新建文件 | ~3 | **7** |
| 修改文件 | ~8 | **14** |

---

## 六、Phase 3 实施计划方向

基于以上讨论，确定了 Phase 3 的六大模块：

1. **执行透明度**（解决反馈 3）— sub_step 事件、活动日志面板、Baseline 结果展示
2. **HITL 全量暂停点**（解决反馈 2）— 5 Profile checkpoint、持久化审批状态、WebSocket 双向审批、审批交互 UI
3. **编译/测试管道**（解决反馈 1）— workspace_compile/workspace_test 真实工具、完整交付流程
4. **质量度量面板**（解决反馈 4）— 执行记录持久化、Dashboard API、前端质量面板
5. **学习循环集成** — ExecutionLogger + SkillFeedbackAnalyzer Spring 化
6. **文档与验收** — 验收测试 + 文档更新

详细实施计划见：`docs/planning/phase3-implementation-plan.md`

---

## 七、附录：Sprint 2.4 实施记录

### 文件变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 重写 | `.env.example` | 覆盖 5 Provider + 安全配置 |
| 修改 | `infrastructure/docker/docker-compose.trial.yml` | 新增 GEMINI/DASHSCOPE/AWS 等环境变量 |
| 新增 | `docs/trial-feedback-template.md` | 7 维度评分 + 6 开放问题 + 日志模板 |
| 新增 | `docs/sprint2.4-acceptance-test.md` | 4 场景 17 用例 |
| 重写 | `docs/TRIAL-GUIDE.md` | 10 章节完整试用指南（~490 行） |
| 修改 | `docs/user-guide-trial.md` | 添加废弃提示 |
| 修改 | `adapters/model-adapter/.../ClaudeAdapter.kt` | 修复空字符串 model Bug（3 处） |

### Git 提交

| Commit | 说明 |
|--------|------|
| `c83fc54` | Sprint 2.4: 内部试用准备 — 6 文件（+905/-66 行） |
| `d0fc765` | fix: 空字符串 model 参数导致 API 400 错误 |
