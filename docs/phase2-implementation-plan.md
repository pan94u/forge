# Phase 2 实施计划 — 混合路线：质量基础设施 + OODA 增强 + 内部试用

> 规划日期: 2026-02-20 | 基于规划基线 v1.4 §7 Phase 2 + 实际验收数据
> 前置条件: Phase 0~1.6 全部完成（92% 验收通过率，20 Bug / 19 已修复）

---

## 一、背景与决策

### 1.1 当前状态（Phase 1.6 完成后）

| 维度 | 数据 |
|------|------|
| 代码规模 | 325+ 文件 / 50K+ 行 |
| 单元测试 | 147 tests, 0 failures |
| E2E 验收 | 87 用例，80 通过（92.0%） |
| Bug 追踪 | 20 个（19 已修复，1 挂起 BUG-016） |
| Docker 部署 | 4 容器（backend + frontend + nginx + keycloak） |
| MCP 工具 | 9 个聚合工具（McpProxyService） |
| Skills | 32 个 / 5 Profiles |
| Prometheus 指标 | 7 个 forge.* 自定义指标已采集 |
| 设计基线 | v5.1（1007 行） |
| Git Commits | 34 |
| Sessions | 18 |

### 1.2 Phase 2 关键决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 整体方向 | **混合路线**（Sprint 拆分） | 质量、智能、用户验证三管齐下 |
| ForgeNativeRuntime | **推迟到 Phase 3** | 当前 ClaudeAdapter + Spring Boot 已可独立运行，无需自建 Agent Loop |
| MCP Server | **做 1-2 个真实服务** | knowledge-mcp + database-mcp 优先，其余保持骨架 |
| 内部试用 | **可以组织** | 3-5 人，≥ 3 天 |

### 1.3 规划基线 Phase 2 交付物对照

| # | 原始交付物 | 当前状态 | Phase 2 行动 |
|---|-----------|---------|-------------|
| 1 | SuperAgent OODA 引擎 | ⚠️ 基础版 | Sprint 2.2 增强（底线自动检查） |
| 2 | SkillLoader.kt 增强 | ⚠️ 基础版 | Sprint 2.2 增强（frontmatter trigger） |
| 3 | ProfileRouter.kt 后端 | ⚠️ 基础版 | 已实现关键词+标签路由，Sprint 2.2 可选增强 |
| 4 | MCP Server 完善 | ⚠️ 骨架代码 | Sprint 2.2 做 knowledge + database |
| 5 | convention-miner 跨语言增强 | 📄 SKILL.md | 不在 Phase 2 范围 |
| 6 | 全部 13 Foundation Skill | ✅ 32 Skills 已加载 | 已超额完成 |
| 7 | 端到端跨栈迁移 PoC | ✅ 100% 覆盖 | 已完成 |
| 8 | agent-eval 评估体系 | ⚠️ 框架已搭建 | Sprint 2.1 填充评估集 |
| 9 | 度量基线采集 | ⚠️ MetricsService 已有 | 前置交付 metrics 报告 + Sprint 2.2 Dashboard |
| 10 | 底线脚本 CI 集成 | ❌ 未开始 | Sprint 2.1 核心交付 |
| 11 | Playwright E2E | ❌ 未开始 | Sprint 2.1 核心交付 |
| 12 | 内部用户试用 | ❌ 未开始 | Sprint 2.3 核心交付 |

---

## 二、前置交付：Phase 1.6 Metrics 报告

> 在启动 Sprint 2.1 之前，基于 Session 14~18 测试期间采集的数据，生成度量报告。

**数据来源**：

| 度量维度 | 数据 | 来源 |
|---------|------|------|
| Prometheus 指标 | forge.profile.route / ooda.phases / tool.calls / message.duration / turn.duration (COUNT=17) / tool.duration (COUNT=10) | `/actuator/prometheus` |
| 验收测试 | 87 用例，80 通过（92.0%） | `phase1.6-e2e-acceptance-test.md` |
| Bug 追踪 | 20 个 Bug，19 修复，1 挂起 | `buglist.md` |
| 单元测试 | 147 tests, 0 failures | `./gradlew test` |
| 代码规模 | 325+ 文件，50K+ 行，34 commits | `git` |
| 部署效率 | Session 14 Docker 重建记录 | `dev-logbook.md` |
| 设计保真度 | design-baseline v1→v5.1（5 次升级） | `design-baseline-v1.md` |
| Agent 可靠性 | 18 Session logbook | `dev-logbook.md` |

**产出**：`docs/metrics-report-phase1.6.md`

**对照规划基线 §10 度量体系**：

| 指标 | 目标 | Phase 1.6 实际 | 状态 |
|------|------|---------------|------|
| OODA 一次通过率 | ≥ 85% | 待统计（logbook 数据） | ⏳ |
| 安全漏洞逃逸数 | 零逃逸 | 0（无安全漏洞逃逸） | ✅ |
| 设计基线回归测试通过率 | 100% | 手动验证 100%，自动化待 CI | ⚠️ |
| 首次部署成功率 | ≥ 90% | Session 14: 1 次 bootJar + 1 次 docker up = 成功 | ✅ |
| E2E 验收通过率 | — | 92.0%（80/87） | ✅ |
| 单元测试通过率 | 100% | 100%（147/147） | ✅ |

---

## 三、Sprint 2.1：质量基础设施 — "让系统可信赖"

### 3.1 目标

建立自动化质量保障体系，修复遗留问题，为后续迭代提供安全网。

### 3.2 交付物

| # | 交付物 | 说明 | 优先级 |
|---|--------|------|--------|
| 1 | GitHub Actions CI Pipeline | 构建 + 测试 + lint 自动运行 | P0 |
| 2 | 底线脚本 CI 集成 | 8 个底线脚本接入 GitHub Actions（至少 code-style + security + test-coverage 3 个先行） | P0 |
| 3 | Playwright E2E 核心场景 | 自动化 20-30 个最重要的验收场景（登录→创建 workspace→AI 对话→文件写入→文件操作） | P1 |
| 4 | BUG-016 修复 | Agentic loop 8 轮耗尽后无文字输出（挂起的唯一 Bug） | P1 |
| 5 | 命令面板 Cmd+K | Header.tsx 缺少键盘监听器（TC-14.2 挂起项） | P2 |
| 6 | agent-eval 评估集填充 | 为 5 个 Profile 各填充 2-3 个真实评估场景 | P2 |

### 3.3 关键文件

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `.github/workflows/ci.yml` | CI Pipeline 定义 |
| 新建 | `web-ide/frontend/e2e/` | Playwright 测试目录 |
| 新建 | `web-ide/frontend/playwright.config.ts` | Playwright 配置 |
| 修改 | `web-ide/backend/.../service/ClaudeAgentService.kt` | BUG-016 修复 |
| 修改 | `web-ide/frontend/src/components/common/Header.tsx` | Cmd+K 键盘监听 |
| 新建 | `agent-eval/src/main/resources/eval-sets/*.json` | 评估场景数据 |

### 3.4 验收标准

- [ ] PR 提交 → GitHub Actions 自动运行 build + test + 底线检查
- [ ] Playwright 核心场景自动通过（≥ 20 个 E2E 测试）
- [ ] BUG-016 修复，agentic loop 耗尽后有兜底文字输出
- [ ] Cmd+K 打开命令面板可用
- [ ] agent-eval 评估集包含 ≥ 10 个真实场景

---

## 四、Sprint 2.2：OODA 引擎增强 + MCP 真实服务 — "让智能更真实"

### 4.1 目标

增强 OODA 引擎的智能性（Skill 条件触发、底线自动检查），让 knowledge-mcp 和 database-mcp 成为真实可用的独立服务。

### 4.2 交付物

| # | 交付物 | 说明 | 优先级 |
|---|--------|------|--------|
| 1 | SkillLoader frontmatter trigger 增强 | 根据 SKILL.md 的 `trigger` 字段（keywords/file-patterns/profile）动态过滤加载的 Skill | P0 |
| 2 | AgentLoop 底线自动检查 | OODA Act 阶段后自动运行相关底线脚本，失败 → 自动回到 Observe 修复 | P1 |
| 3 | knowledge-mcp 真实服务 | 从骨架代码升级为可独立运行的 Ktor HTTP 服务，对接真实知识库目录 | P0 |
| 4 | database-mcp 真实服务 | 从骨架代码升级为可独立运行的 Ktor HTTP 服务，对接 H2/PostgreSQL | P1 |
| 5 | Docker 扩容（4→6 容器） | docker-compose.trial.yml 新增 knowledge-mcp + database-mcp 容器 | P1 |
| 6 | McpProxyService 真实 HTTP 调用 | 将 knowledge/database 从内置实现改为 HTTP 调用真实 MCP Server | P0 |
| 7 | Metrics Dashboard | 内置简易 Dashboard 或 Grafana 集成，展示 forge.* 指标趋势 | P2 |

### 4.3 关键文件

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `web-ide/backend/.../service/SystemPromptAssembler.kt` | Skill trigger 条件过滤 |
| 修改 | `web-ide/backend/.../service/ClaudeAgentService.kt` | 底线自动检查逻辑 |
| 重写 | `mcp-servers/forge-knowledge-mcp/` | 升级为真实 Ktor HTTP 服务 |
| 重写 | `mcp-servers/forge-database-mcp/` | 升级为真实 Ktor HTTP 服务 |
| 修改 | `infrastructure/docker/docker-compose.trial.yml` | 新增 2 个 MCP 容器 |
| 修改 | `web-ide/backend/.../service/McpProxyService.kt` | 真实 HTTP 调用 |

### 4.4 技术方案要点

**SkillLoader frontmatter trigger**：

```yaml
# SKILL.md frontmatter 示例
---
name: kotlin-conventions
trigger:
  profiles: [development, testing]
  keywords: [kotlin, kt, 代码规范]
  file_patterns: ["*.kt", "*.kts"]
---
```

SystemPromptAssembler 在组装 system prompt 时，根据当前 active profile + 用户消息关键词 + workspace 文件类型，动态过滤 Skill 子集注入。

**AgentLoop 底线自动检查**：

```
用户消息 → Observe → Orient → Decide → Act（生成代码）
                                           │
                                           ▼
                                    底线脚本自动运行
                                           │
                                    ┌──────┴──────┐
                                    ▼              ▼
                                  通过          失败
                                    │              │
                                    ▼              ▼
                                  Done    回到 Observe（附带失败原因）
                                                   │
                                                   ▼
                                            自动修复（最多 2 轮）
```

**MCP Server 架构**：

```
                     ┌─────────────────────┐
                     │   McpProxyService    │
                     │  （聚合 + 路由层）    │
                     └───────┬─────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
    knowledge-mcp:8081  database-mcp:8082  内置工具
    （独立 Ktor 容器）  （独立 Ktor 容器）  （workspace_*）
              │              │
              ▼              ▼
       knowledge-base/    H2/PostgreSQL
```

### 4.5 验收标准

- [ ] Skill 按 frontmatter trigger 条件动态加载（不同 Profile 加载不同 Skill 子集）
- [ ] AgentLoop 在 Act 后自动运行底线脚本，失败时自动重试（最多 2 轮）
- [ ] knowledge-mcp 作为独立容器运行，通过 HTTP 提供知识搜索
- [ ] database-mcp 作为独立容器运行，通过 HTTP 提供 schema 查询
- [ ] McpProxyService 通过 HTTP 调用真实 MCP Server（非内置实现）
- [ ] Docker 6 容器全部 healthy

---

## 五、Sprint 2.3：内部试用 + 反馈闭环 — "让真人用起来"

### 5.1 目标

组织 3-5 人内部试用 ≥ 3 天，收集结构化反馈，修复 Top 问题。

### 5.2 交付物

| # | 交付物 | 说明 | 优先级 |
|---|--------|------|--------|
| 1 | 试用准备文档 | 用户手册 + 环境搭建指南 + 常见问题 FAQ | P0 |
| 2 | 试用环境部署 | 稳定的共享试用环境（或每人独立 Docker 环境） | P0 |
| 3 | 反馈收集机制 | 结构化反馈表（功能可用性 / 体验流畅度 / 改进建议） | P1 |
| 4 | 试用执行 | 3-5 人实际使用 ≥ 3 天 | P0 |
| 5 | Top 问题修复 | 根据反馈修复最高优先级的 3-5 个问题 | P0 |
| 6 | 验收测试更新 | 基于试用发现的新场景更新验收测试文档 | P1 |

### 5.3 试用场景设计

| 场景 | 参与者 | 预期使用功能 |
|------|--------|-------------|
| 新功能开发 | 后端开发 | AI 对话 + @开发 Profile + 代码写入 workspace |
| 代码审查辅助 | 资深开发 | AI 对话 + @设计 Profile + 知识库搜索 |
| 新人上手 | 新入职 | Dashboard + 知识浏览 + AI 问答 |
| 测试用例编写 | 测试工程师 | AI 对话 + @测试 Profile + 代码生成 |

### 5.4 反馈收集维度

| 维度 | 评分 (1-5) | 开放问题 |
|------|-----------|---------|
| 整体满意度 | □□□□□ | 最喜欢的功能？ |
| AI 对话质量 | □□□□□ | AI 回复是否有帮助？ |
| 代码生成准确性 | □□□□□ | 生成的代码可直接使用吗？ |
| 操作流畅度 | □□□□□ | 有卡顿或不便吗？ |
| 知识库有用性 | □□□□□ | 搜索到的内容是否相关？ |
| 愿意继续使用 | □□□□□ | 改进建议？ |

### 5.5 验收标准

- [ ] ≥ 3 人完成 ≥ 3 天试用
- [ ] 收到结构化反馈报告
- [ ] Top 3 问题已修复
- [ ] 用户整体满意度 ≥ 3.5/5

---

## 六、Phase 2 整体验收标准

| # | 标准 | 来源 | 状态 |
|---|------|------|------|
| 1 | GitHub Actions CI 自动运行 build + test + 底线 | Sprint 2.1 | ⬜ |
| 2 | Playwright E2E ≥ 20 个场景自动通过 | Sprint 2.1 | ⬜ |
| 3 | BUG-016 + Cmd+K 修复 | Sprint 2.1 | ⬜ |
| 4 | Skill frontmatter trigger 动态加载 | Sprint 2.2 | ⬜ |
| 5 | knowledge-mcp + database-mcp 独立服务可用 | Sprint 2.2 | ⬜ |
| 6 | McpProxyService 真实 HTTP 调用 MCP Server | Sprint 2.2 | ⬜ |
| 7 | ≥ 3 人内部试用完成 + 反馈收集 | Sprint 2.3 | ⬜ |
| 8 | 所有 Bug ≤ 1 个挂起 | 全局 | ⬜ |

---

## 七、不在 Phase 2 范围

| 推迟项 | 目标阶段 | 理由 |
|--------|---------|------|
| ForgeNativeRuntime（AgentLoop.kt / HookEngine.kt / ContextBuilder.kt） | Phase 3 | 当前 ClaudeAdapter 已可独立运行 |
| 进化环全部组件（execution-logger / asset-extractor / skill-feedback-analyzer） | Phase 3 | 需要先有足够的执行数据 |
| RuntimeAdapter 配置切换 | Phase 3 | 依赖 ForgeNativeRuntime 完成 |
| 3-5 个 MCP Server 全部真实化 | 仅做 2 个 | artifact-mcp / observability-mcp / service-graph-mcp 保持骨架 |
| Domain Skills 扩展 | Phase 3 | 需要真实业务场景驱动 |
| convention-miner 跨语言增强 | Phase 3 | 优先级低于核心引擎 |

---

## 八、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| MCP Server 真实化工作量超预期 | Sprint 2.2 延期 | 先做 knowledge-mcp（逻辑简单），database-mcp 视情况简化 |
| Playwright 环境配置复杂 | Sprint 2.1 延期 | 仅覆盖核心场景（20 个），非全量 87 个 |
| 内部试用用户时间不足 | Sprint 2.3 无法达标 | 降低门槛：最少 2 人 × 2 天 |
| BUG-016 根因复杂 | 修复耗时 | 如无法根治，实现兜底方案（safety net 输出） |
| Docker 6 容器资源消耗 | 开发机器吃力 | 提供"精简模式"docker-compose（仅 4 核心容器） |

---

> Phase 2 计划版本: v1.0 | 日期: 2026-02-20
> 依据: 规划基线 v1.4 §7 + Phase 1.6 验收测试数据 + 18 Session 开发经验
> 下一步: 生成 Phase 1.6 Metrics 报告 → 启动 Sprint 2.1
