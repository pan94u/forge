# Forge Platform — Phase 1.6 度量报告

> 报告日期: 2026-02-20 | 数据来源: Session 1~18 开发日志 + E2E 验收测试 + Prometheus 指标采集
> 对照基准: 规划基线 v1.4 §10 价值度量体系

---

## 一、执行摘要

Phase 0~1.6 历经 **18 个 Session**，产出 **325+ 文件 / 50K+ 行代码 / 34 Git Commits**。E2E 验收通过率 **92.0%**（80/87），累计发现并修复 **20 个 Bug**（修复率 95%）。以下为各维度度量数据。

---

## 二、核心度量指标

### 2.1 Prometheus 运行时指标（forge.* 自定义指标）

> 数据采集时间: Session 18（2026-02-20），经过场景 B/C/H 手动测试后。
> 采集端点: `http://localhost:9000/actuator/prometheus`

| 指标名 | 类型 | 采集值 | 说明 |
|--------|------|--------|------|
| `forge_profile_route_total` | Counter | 9 | Profile 路由触发 |
| `forge_ooda_phases_total` | Counter | 44（5 阶段合计） | OODA 阶段触发计数 |
| `forge_tool_calls_total` | Counter | 10 | MCP 工具调用次数 |
| `forge_message_duration_seconds` | Timer | 7 次 / 150.16s | 消息端到端处理耗时 |
| `forge_turn_duration_seconds` | Timer | 17 轮 / 252.66s | Agentic Loop 每轮耗时 |
| `forge_tool_duration_seconds` | Timer | 10 次 / 0.002s | 单次工具执行耗时 |
| `forge_baseline_results_total` | Counter | 0（未触发） | Docker Alpine 无 bash，底线脚本无法执行 |

#### 2.1.1 forge_profile_route_total（Profile 路由）

| profile | method | count |
|---------|--------|-------|
| development-profile | Default fallback | 9 |

> 测试期间所有对话均路由到 development-profile（默认回退），未触发显式标签或关键词路由。

#### 2.1.2 forge_ooda_phases_total（OODA 阶段触发）

| phase | count |
|-------|-------|
| observe | 9 |
| orient | 9 |
| decide | 9 |
| act | 10 |
| complete | 7 |

> act(10) > complete(7)：有 3 次 act 后进入了额外轮次（agentic loop 多轮工具调用后再次 act），说明 AI 在部分任务中需要多步操作才能完成。

#### 2.1.3 forge_tool_calls_total（工具调用）

| tool | status | count |
|------|--------|-------|
| workspace_list_files | success | 4 |
| workspace_read_file | success | 3 |
| workspace_write_file | success | 3 |

> 共 10 次工具调用，**全部成功**（error=0）。全部为 workspace_* 系列工具，knowledge/schema/service 等工具未在本次测试对话中被 AI 主动调用。

#### 2.1.4 forge_message_duration_seconds（消息端到端耗时）

| 维度 | 值 |
|------|-----|
| count | 7 次消息 |
| sum | 150.16 秒 |
| **平均** | **~21.5 秒/消息** |

> 包含 system prompt 组装 + Claude API 推理 + 工具调用 + 结果处理全链路。

#### 2.1.5 forge_turn_duration_seconds（Agentic Loop 每轮耗时）

| turn | count | sum | 平均耗时 |
|------|-------|-----|---------|
| Turn 1 | 9 | 178.67s | **19.9s** |
| Turn 2 | 6 | 61.84s | **10.3s** |
| Turn 3 | 2 | 12.16s | **6.1s** |

> Turn 1 最慢（含 system prompt 首次传输 + 模型冷启动），后续轮次递减。9 次对话中 6 次触发 Turn 2（tool_use → tool_result → 继续），2 次触发 Turn 3（多步操作）。

#### 2.1.6 forge_tool_duration_seconds（工具执行耗时）

| tool | count | sum | 平均耗时 |
|------|-------|-----|---------|
| workspace_list_files | 4 | ~0ms | <1ms |
| workspace_read_file | 3 | ~0ms | <1ms |
| workspace_write_file | 3 | 2ms | **0.67ms** |

> 工具执行极快（本地内存文件操作），**瓶颈 100% 在模型推理端**而非工具端。Turn 平均 12s vs 工具平均 <1ms，工具耗时占比 < 0.01%。

#### 2.1.7 forge_baseline_results_total — 未触发

> Docker Alpine 环境无 bash，`run_baseline` 工具无法执行 shell 脚本。Phase 2 需解决（改用 JRE slim 或容器内安装 bash）。

**总体观察**：
- 7 个 forge.* 指标全部注册成功，Micrometer 懒注册机制正常（首次 AI 对话后指标才出现）
- 系统瓶颈在 Claude API 推理（平均 21.5s/消息），工具层耗时可忽略
- Agentic Loop 平均 1.9 轮/消息（17 轮 / 9 次 observe），多数任务 1-2 轮完成
- 工具调用成功率 100%，无 error 记录

---

### 2.2 E2E 验收测试

| 维度 | 数据 |
|------|------|
| 总场景数 | 21 个 |
| 总用例数 | 87 个 |
| **通过** | **80（92.0%）** |
| 未通过 | 7 个 |
| 测试方式 | curl 自动 + UI 手动混合 |
| 测试周期 | Session 14~18（5 个 Session） |

**未通过用例分析**：

| 用例 | 原因 | 类型 | 阻塞度 |
|------|------|------|--------|
| TC-2.3/2.4 部分 | BUG-016 agentic loop 耗尽无输出 | 代码缺陷 | ⏸ 挂起 |
| TC-13.1~13.3 | Keycloak SSO 需 FORGE_SECURITY_ENABLED=true | 环境配置 | 可解除 |
| TC-14.2 | 命令面板 Cmd+K 无键盘监听器 | 功能未实现 | ⏸ 挂起 |
| TC-21.2 | agent-eval 真实 API 调用需 API Key | 环境配置 | 可解除 |

**通过率按分区**：

| 分区 | 场景数 | 用例数 | 通过 | 通过率 |
|------|--------|--------|------|--------|
| 用户旅程（场景 1~8） | 8 | 33 | 31 | 93.9% |
| Phase 1.6 核心功能（场景 9~14） | 6 | 22 | 19 | 86.4% |
| 技术验证与回归（场景 15~21） | 7 | 32 | 30 | 93.8% |

---

### 2.3 Bug 追踪

| 维度 | 数据 |
|------|------|
| 总 Bug 数 | 20 |
| 已修复 | 19（95%） |
| 挂起 | 1（BUG-016） |

**严重等级分布**：

| 等级 | 数量 | 已修复 | 说明 |
|------|------|--------|------|
| P0（阻断） | 2 | 2 | BUG-008（文件树不显示）、BUG-012（AI 不写文件） |
| P1（严重） | 5 | 5 | BUG-001/005/013/017 + 1 |
| P2（一般） | 13 | 12 | 1 个挂起（BUG-016） |

**Bug 发现时间分布**：

| Session | Bug 数量 | 说明 |
|---------|---------|------|
| Session 15 | 12 | FileExplorer CRUD + enum 序列化 + workspace 工具（首次系统测试，集中爆发） |
| Session 16 | 5 | ContextPicker + Profile + 页面白屏 |
| Session 17 | 1 | Knowledge tab 空内容 |
| Session 18 | 2 | Apply 按钮不可见 + 搜索过滤 |

**影响组件分布**：

| 组件 | Bug 数量 | 影响文件 |
|------|---------|---------|
| FileExplorer | 9 | FileExplorer.tsx, WorkspaceService.kt |
| AiChatSidebar / ContextPicker | 5 | AiChatSidebar.tsx, ContextPicker.tsx |
| ChatMessage (代码块) | 1 | ChatMessage.tsx |
| 后端 Service | 3 | McpProxyService.kt, ClaudeAgentService.kt |
| 知识库页面 | 1 | Services page (enum) |
| 构建/测试 | 1 | McpControllerTest.kt |

**关键洞察**：Session 15 首次系统测试发现 12 个 Bug（60%），说明 Phase 1.6 代码提交时缺少集成测试覆盖。FileExplorer CRUD 是 Bug 重灾区（9/20 = 45%），主要因为文件操作涉及前后端交互 + 事件冒泡 + 递归数据结构。

---

### 2.4 单元测试

| 维度 | 数据 |
|------|------|
| 总测试数 | **147** |
| 通过率 | **100%**（0 failures） |
| 后端 (web-ide:backend) | 118 tests |
| 适配器 (model-adapter) | 11 tests |
| 评估 (agent-eval) | 18 tests |

**测试增长趋势**：

| Phase | 测试数 | 增量 |
|-------|--------|------|
| Phase 0 | ~20 | 初始框架 |
| Phase 1 | 37 | +17 |
| Phase 2 (Sprint 2A/2B/2C) | 128 | +91 |
| Phase 1.6 | 147 | +19（workspace tools + ContextController） |

---

### 2.5 代码规模

| 维度 | 数据 |
|------|------|
| 总文件数 | 325+ |
| 总代码行数 | ~50,000+ |
| Git Commits | 34 |
| 开发 Sessions | 18 |
| 模块数 | 12+（monorepo） |

**模块分布**：

| 模块 | 文件数 | 完成阶段 |
|------|--------|---------|
| plugins/ | ~70 | Phase 0（Skills/Profiles/Commands/Hooks） |
| mcp-servers/ | ~48 | Phase 0（5 MCP Server 骨架） |
| web-ide/frontend | 30+ | Phase 1.6（UI 完整） |
| web-ide/backend | 35+ | Phase 1.6（API 完整） |
| knowledge-base/ | 13 | Phase 1.6（+5 新文档） |
| infrastructure/ | 8+ | Phase 1.6（Docker 4 容器） |
| docs/ | 19+ | 持续更新 |
| 其他（cli/adapters/agent-eval/skill-tests） | ~36 | Phase 0~1 |

---

## 三、规划基线度量体系对照

> 对照规划基线 v1.4 §10"价值度量体系"的 11 个指标。

| # | 指标 | 目标 | Phase 1.6 实际 | 状态 | 数据来源 |
|---|------|------|---------------|------|---------|
| 1 | OODA 一次通过率 | ≥ 85% | **~75%**（估算：18 Session 中约 13-14 次 AI 对话一次完成任务） | ⚠️ 未达标 | dev-logbook 人工统计 |
| 2 | 安全漏洞逃逸数 | 零逃逸 | **0** | ✅ 达标 | 无安全漏洞报告 |
| 3 | 设计基线回归测试通过率 | 100% | **100%**（手动验证，自动化 CI 未建立） | ⚠️ 手动达标 | design-baseline-v1.md 5 次升级 |
| 4 | 首次部署成功率 | ≥ 90% | Session 5: **7.7%**（1/13）→ Session 14: **100%**（1/1） | 📈 显著改善 | dev-logbook |
| 5 | 构建失败→定位根因时间 | ≤ 5 分钟 | Session 5: ~7 分钟/次 → Session 14+: ~3 分钟/次 | ✅ 达标 | dev-logbook |
| 6 | E2E 验收通过率 | — | **92.0%**（80/87） | ✅ | acceptance-test |
| 7 | 单元测试通过率 | 100% | **100%**（147/147） | ✅ | gradlew test |
| 8 | Skill 触发次数 | 可观测 | **forge_profile_route_total 有数据** | ✅ 可采集 | Prometheus |
| 9 | MCP 调用量 | 可观测 | **forge_tool_calls_total 有数据**（10 次工具调用） | ✅ 可采集 | Prometheus |
| 10 | 知识持久性 | → 0 信息损失 | 13 篇知识库文档 + 2614 行 logbook + design-baseline v5.1 | ✅ | 文档清单 |
| 11 | Runtime 独立性 | Phase 3 达到 100% | **~80%**（Web IDE 通过 ClaudeAdapter 直连 Claude API，不依赖 Claude Code CLI；CLI 仍依赖） | 📈 进行中 | 功能矩阵 |

---

## 四、效率度量

### 4.1 开发效率

| 维度 | 数据 | 计算方式 |
|------|------|---------|
| 总代码行数 | ~50,000 行 | git |
| 总 Session 数 | 18 | logbook |
| 平均每 Session 产出 | ~2,778 行 | 50K / 18 |
| Session 平均时长 | ~2 小时 | logbook 记录 |
| 代码生产率 | **~1,389 行/小时** | 50K / (18×2) |

### 4.2 部署效率改善

| 维度 | Session 5（首次） | Session 14（Phase 1.6） | 改善幅度 |
|------|-------------------|------------------------|---------|
| 构建尝试次数 | 13 次 | 1 次 | **-92%** |
| 部署总时间 | 90 分钟 | ~10 分钟 | **-89%** |
| 首次成功率 | 7.7%（1/13） | 100%（1/1） | **+92.3pp** |
| 计划外问题数 | 10 个 | 0 个 | **-100%** |

**改善原因**：
1. 本地构建 + Docker 只打包策略（Session 5 教训）
2. CLAUDE.md 记录了已知陷阱（JDK 版本、npm build 必须等）
3. 经验沉淀为开发纪律（纪律 1: logbook、纪律 2: baseline、纪律 3: 验收测试）

### 4.3 Bug 修复效率

| 维度 | 数据 |
|------|------|
| 总 Bug 数 | 20 |
| 修复率 | 95%（19/20） |
| 平均修复时间 | ~15 分钟/Bug（Session 内实时修复） |
| 最快修复 | BUG-019：~5 分钟（CSS 类名移除） |
| 最慢修复 | BUG-020：~30 分钟（两次迭代，涉及焦点管理协调） |
| 修复后引入新 Bug | 0（每次修复后回归验证） |

---

## 五、质量度量

### 5.1 代码质量

| 维度 | 数据 |
|------|------|
| 单元测试覆盖 | 147 tests（Service 层覆盖良好，Controller 有集成测试） |
| E2E 验收覆盖 | 87 用例 / 21 场景 |
| 安全漏洞 | 0（路径遍历检查、XSS 防护、SQL 注入防护） |
| 设计基线版本 | v5.1（5 次升级，1007 行） |
| 规划基线版本 | v1.4（4 次升级，817 行） |

### 5.2 文档质量

| 文档类型 | 数量 | 总行数 |
|---------|------|--------|
| 开发日志 (logbook) | 1 | 2,700+ 行 |
| 设计基线 | 1 | 1,007 行 |
| 规划基线 | 1 | 817 行 |
| E2E 验收测试 | 1 | 1,059 行 |
| Bug 追踪清单 | 1 | 400+ 行 |
| 知识库文档 | 13 | ~3,000+ 行 |
| 其他规划/设计文档 | 15+ | ~5,000+ 行 |
| **文档总计** | **33+** | **~14,000+ 行** |

文档占总代码量的 **~28%**（14K / 50K），体现了"交付 + 知识沉淀"双环理念。

---

## 六、架构度量

### 6.1 系统组件完成度

| 组件 | Phase 1.6 状态 | 说明 |
|------|---------------|------|
| Web IDE 前端 | ✅ 完整 | 6 个页面路由，20+ 组件 |
| Web IDE 后端 | ✅ 完整 | 8 个 Controller，10+ Service |
| MCP 聚合层 | ✅ 完整 | 9 个工具，McpProxyService |
| MCP Server（独立） | ⚠️ 骨架 | 5 个 Server，Phase 0 代码 |
| Keycloak SSO | ✅ 完整 | OIDC PKCE，可开关 |
| Docker 部署 | ✅ 完整 | 4 容器，docker-compose |
| CI/CD | ❌ 未建立 | GitHub Actions 待配置 |
| ForgeNativeRuntime | ❌ 未开始 | Phase 3 范畴 |
| 进化环 | ❌ 未开始 | Phase 3 范畴 |

### 6.2 MCP 工具使用热度

| 工具 | 测试中调用频率 | 说明 |
|------|--------------|------|
| workspace_write_file | 高 | AI 交付闭环核心，每次代码生成必调用 |
| workspace_read_file | 中 | AI 读取现有文件时调用 |
| workspace_list_files | 中 | AI 了解项目结构时调用 |
| search_knowledge | 中 | Context Picker + AI 主动搜索 |
| get_service_info | 低 | 仅在特定查询时调用 |
| query_schema | 低 | 数据库相关查询 |
| read_file | 低 | 知识库文件读取 |
| list_baselines | 低 | 底线列表查询 |
| run_baseline | 无法执行 | Docker Alpine 无 bash |

---

## 七、总结与建议

### 7.1 达标情况

| 达标（6/11） | 未达标/进行中（5/11） |
|-------------|---------------------|
| ✅ 安全漏洞零逃逸 | ⚠️ OODA 一次通过率 ~75%（目标 ≥85%） |
| ✅ 构建失败定位 ≤5 分钟 | ⚠️ 设计基线回归测试仅手动 |
| ✅ 单元测试 100% | ⚠️ 首次部署成功率仍需 CI 保障 |
| ✅ Prometheus 指标可采集 | 📈 Runtime 独立性 ~80% |
| ✅ 知识持久性良好 | 📈 底线 CI 未集成 |
| ✅ E2E 验收 92% | |

### 7.2 Phase 2 改善方向

1. **OODA 一次通过率 → ≥85%**：需要 SkillLoader trigger 增强 + AgentLoop 底线自动检查
2. **设计基线回归 → 自动化**：需要 CI 集成 design-regression-baseline.sh
3. **部署效率 → CI 保障**：需要 GitHub Actions Pipeline
4. **底线执行 → 容器内可运行**：需要解决 Docker Alpine 无 bash 问题（改用 JRE slim 或在容器内安装 bash）
5. **Prometheus → Dashboard**：需要 Grafana 或内置 Dashboard 可视化趋势

---

> 报告版本: v1.0 | 日期: 2026-02-20
> 数据截止: Session 18 / Git Commit `60f859c`
> 下一份报告: Phase 2 完成后
