# Forge Platform — AI 驱动的智能交付平台

> **愿景**：重构软件交付流程本身——不是给每个人配一个 AI 助手，而是用 SuperAgent + Skill 体系将 5-7 人团队的交付能力压缩到 1-2 人 + AI 协同完成。
> **定位**：面向全软件交付生命周期的智能交付平台（AI Delivery Platform），涵盖编码、知识管理、质量保障、跨栈迁移、持续进化。

## 交互偏好

- **用中文交流**。所有回复、注释、文档默认使用中文
- **行动优先**：用户说"执行"/"继续"/"全部"时直接做，不要反问
- **简洁回复**：少说多做。避免冗长解释，给结果和关键数据
- **遵循四大开发纪律**（见下方"开发纪律"章节）
- **Git 提交署名**：以 zhao55 和 Claude 的名义提交。commit 末尾必须包含 `Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>`，Git author 使用 zhao55 身份（`zhao55 <18565667766@163.com>`）

## Quick Start

```bash
# 环境要求：JDK 21（必须！JDK 8/17 会编译失败）
java -version  # 确认 21+

# 构建后端 jar（跳过测试加速）
./gradlew :web-ide:backend:clean :web-ide:backend:bootJar -x test

# 构建前端
cd web-ide/frontend && npm install && npm run build && cd ../..

# Docker 一键部署（4 容器：backend + frontend + nginx + keycloak）
cd infrastructure/docker
docker compose -f docker-compose.trial.yml up --build -d

# 访问 http://localhost:9000

# 运行全量单元测试（当前 156 个）
./gradlew :web-ide:backend:test :adapters:model-adapter:test
./gradlew :agent-eval:test
```

## Architecture

Forge is a Gradle monorepo (Kotlin DSL) with the following modules:

- **plugins/**: Claude Code plugin system (Skills, Profiles, Commands, Hooks)
- **mcp-servers/**: MCP Server implementations (Kotlin/Ktor, HTTP transport)
- **web-ide/**: Web IDE (Next.js 15 frontend + Spring Boot 3 backend)
- **cli/**: Forge CLI (Kotlin + GraalVM Native)
- **adapters/**: Adapter layer isolating stable/volatile concerns
- **agent-eval/**: SuperAgent evaluation framework
- **skill-tests/**: Skill validation framework
- **knowledge-base/**: Knowledge repository (13 docs: profiles, conventions, ADR, runbooks)

## Key Design Decisions

1. **SuperAgent over Multi-Agent**: One intelligent agent dynamically switches Skill Profiles
2. **Skill over Prompt**: Professional knowledge encoded as reusable, composable Skills
3. **Baseline guarantees quality floor**: Baseline scripts must pass regardless of model
4. **Dual-loop architecture**: Delivery Loop (what) + Learning Loop (getting better)
5. **Adapter isolation**: Skills/baselines stable; models/runtime swappable via adapters
6. **MCP 工具聚合**: 5 MCP Server × 20 细粒度工具 → McpProxyService 17 聚合工具（5 handler 拆分路由）
7. **本地构建 + Docker 打包**: 不在 Docker 内编译（网络不可靠），本地 bootJar/npm build 后 Docker 只 COPY 产物

## Language & Conventions

- **Backend**: Kotlin 1.9+ on JDK 21, Spring Boot 3.3+
- **MCP Servers**: Kotlin + Ktor
- **Frontend**: TypeScript, React 19, Next.js 15 (App Router)
- **Build**: Gradle Kotlin DSL
- **Testing**: JUnit 5 + MockK + AssertJ (backend), Jest + Playwright (frontend)

## Docker 部署注意事项

- **4 容器**: backend + frontend + nginx + keycloak（`docker-compose.trial.yml`）
- **后端 Dockerfile**: 单阶段 `eclipse-temurin:21-jre-alpine`（Alpine 无 bash，shell 脚本不可用）
- **Health check**: 使用 `/api/knowledge/search`（非 `/actuator/health`）
- **Keycloak**: 端口 8180，realm `forge`，client `forge-web-ide`（OIDC PKCE）
- **环境变量**: `ANTHROPIC_API_KEY`, `FORGE_SECURITY_ENABLED`, `FORGE_PLUGINS_PATH=/plugins`
- **Volume 挂载**: `plugins/` 和 `knowledge-base/` 必须挂载为只读

## MCP 工具清单（17 个）

| 工具 | 来源 | 说明 |
|------|------|------|
| search_knowledge | builtin | 搜索知识库文档 |
| read_file | builtin | 读取知识库文件 |
| get_service_info | builtin | 获取服务信息 |
| run_baseline | builtin | 运行 baseline 脚本（Docker Alpine 内不可用） |
| query_schema | builtin | 查询数据库 schema |
| list_baselines | builtin | 列出可用 baselines |
| read_skill | builtin | 按需读取 SKILL.md 或子文件内容 |
| run_skill_script | builtin | 执行 Skill 脚本，60s 超时 |
| list_skills | builtin | 列出可用 Skill metadata |
| workspace_write_file | workspace | 写文件到 workspace（需 workspaceId） |
| workspace_read_file | workspace | 读取 workspace 文件 |
| workspace_list_files | workspace | 列出 workspace 文件树 |
| workspace_compile | workspace | 编译/构建项目，自动检测项目类型 |
| workspace_test | workspace | 运行测试，自动检测测试框架 |
| update_workspace_memory | memory | Agent 主动更新 workspace 记忆 |
| get_session_history | memory | 读取历史 session 摘要 |
| analyze_codebase | memory | 对 workspace 执行结构分析，返回 JSON |

**注意**: workspace 工具通过 `callTool(name, args, workspaceId)` 三参数版调用，workspaceId 从 arguments 中提取。

## Security Rules

- NEVER hardcode credentials — use environment variables
- Database MCP: SELECT only, no DDL/DML, no production databases
- All MCP servers require OAuth2 Bearer Token authentication
- All requests include audit trail (user, timestamp, tool, parameters)
- Workspace 工具有路径遍历检查（`..` 禁止）

## Module Dependency Rules

- MCP servers depend on `forge-mcp-common` only
- Web IDE backend may depend on `forge-mcp-common` and `adapters`
- No circular dependencies between modules
- Plugins are standalone Markdown/JSON — no Kotlin compilation needed

## 已知陷阱（从 29 个 Session 提炼）

- **JDK 版本**: 必须 21+，系统默认可能是 8，用 `JAVA_HOME` 显式指定
- **Profile 命名**: 带 `-profile` 后缀（如 `development-profile`，非 `development`）
- **Micrometer 指标**: 懒注册，`forge.*` 指标首次使用后才出现在 `/actuator/prometheus`
- **SSE 格式**: Spring 输出 `data:{"type":"..."}` 冒号后无空格，前端需兼容
- **WebSocket CORS**: `allowed-origins` 用逗号分隔字符串（非 YAML list），@Value 无法解析 list
- **npm run build vs dev**: `npm run dev` 不检查类型错误，**必须用 `npm run build` 验证**
- **workspaceId 传递**: REST API（McpController）和 WebSocket（agenticStream）是两条独立路径，都需要传 workspaceId
- **Kotlin 枚举序列化**: `enum class` 默认序列化为大写（`DIRECTORY`），前端期望小写（`directory`）。所有枚举必须加 `@JsonValue` 返回小写。**新增枚举时立即全局排查** `grep -r "enum class" --include="*.kt"`
- **空字符串 vs null**: Kotlin `String?` 从 HTTP query param 接收时，`q=`（空串）和不传 `q`（null）不同。`?: default` 只处理 null，需用 `isNullOrBlank()` 同时处理两者

## 交付方法论（从 29 Session 实践总结）

> 核心理念：**用文档对抗 AI 遗忘，用验收测试对抗质量腐化，用双基线对抗设计偏移。**
> 详细分析：`docs/delivery-methodology-analysis.md`

### Session 微型 PDCA 循环

每个 Session 遵循固定结构：`目标声明 → 实施 → Bug 修复 → 文件变更表 → 经验沉淀 → 统计快照 → Git 提交`。Session 结束时 Claude 应主动提醒用户完成所有环节。

### 经验编码管道

发现可复用的经验时，按此管道固化：`logbook 经验沉淀 → 验证（跨 2+ Session 确认） → 编码到 CLAUDE.md（已知陷阱/纪律/方法论）`。用户明确要求记住的内容可跳过验证直接编码。

### 并行 Agent 策略

以下场景**应该**使用并行 Agent（Task 工具）提高效率：
- 初始化或大型功能集（5+ 文件同时修改）：拆分为前端/后端两个 Agent
- 独立场景的验收测试：不同场景可并行执行
- 文档批量更新：logbook + baseline + acceptance test 可并行修改

### 里程碑命名规范

格式：`Phase X.Y — 语义标题`（如 `Phase 1.6 — AI 交付闭环`）。新增中间阶段用小数点扩展，**禁止重命名已有编号**（Session 13 的 69 处重命名教训）。

---

## 开发纪律（四大支柱）

### 纪律 1：Logbook 维护（对抗遗忘）

**文件**: `docs/planning/dev-logbook.md`

**何时更新**: 每次 session 结束时，用户会要求更新。主动提醒用户如果他忘了。

**每条 Session 必须包含**:
- Session 编号 + 日期 + 一句话目标
- 实施内容（文件变更表：操作 | 文件 | 说明）
- 发现的 bug 及修复（根因 → 修复方案）
- 经验沉淀（可编码为 Skill/Baseline 的教训）
- 关键数据更新（测试数量、工具数量、容器数量等统计快照）
- Git commit hash

**格式约定**: 沿用现有 `### X.Y 小节标题` + 表格 + 代码块风格。统计快照放在 Session 末尾。

**文档债务控制**: 每 5 个 Session 做一次文档全量审查（格式统一、去重、清理版本批注）。Claude 在 Session 编号为 5 的倍数时主动提醒用户。

### 纪律 2：Baseline 交叉校验（防止腐化）

**两份 Baseline 的定位不同**:
- **设计基线** `docs/baselines/design-baseline-v1.md`（当前 v10）：**实现驱动**，每个 Phase 结束后根据实际实现来更新，记录"我们造了什么"
- **规划基线** `docs/baselines/planning-baseline-v1.5.md`（当前 v2.0）：**设计驱动**，由开发者和 Claude 共同讨论设计来更新，记录"我们要造什么"

**更新时机**:
- Phase 结束 → 先更新设计基线（对齐实现）→ 再用设计基线与规划基线交叉校验 → 发现偏差后决定是修正规划还是补齐实现

**交叉校验清单**:
1. 设计基线中的 API 端点、MCP 工具名是否与代码一致（设计基线 vs 代码）
2. 规划基线中的 Phase 状态/指标是否反映实际完成情况（规划基线 vs 代码）
3. 两份基线之间是否有矛盾：规划说要做的功能，设计基线中是否体现（规划 vs 设计）
4. 设计基线中已实现的功能，是否超出或偏离了规划基线的设计意图（设计 vs 规划）
5. 检查是否有残留的版本批注（"v1.3 新增"之类）需要清理

**Session 13 教训**: 多次增量修改后，格式/逻辑债务会累积。每 3-4 个 Session 做一次全量审查。

### 纪律 3：验收测试驱动（连接代码与产品）

**当前验收测试**: `docs/phase1.6-e2e-acceptance-test.md`（21 场景 / 87 用例，80 通过 = 92.0%）

**验收测试生命周期**:

```
场景先行 → 编码实现 → 交叉验证 → 运行时执行 → 数据校准 → 更新文档
  ↑                                                        |
  └────────────── 下一个 Phase 时继承并扩展 ────────────────┘
```

1. **场景先行**: 每个新 Phase **编码前**先写验收场景标题 + 关键预期（防止后补导致的 16 处错误，Session 13 教训）
2. **编码后补充**: 编码完成后填充 TC 操作细节，并对照代码交叉验证
3. **运行时执行**: 用 curl/docker exec 自动执行可自动化的用例，UI 用例标注为手动
4. **数据校准**: 执行后将实际值（profile 名称、测试数量、工具数量等）回填文档
5. **ROI 评估**: 验收测试的通过率直接反映 Phase 交付的有效性

**验收测试格式约定**:
- 场景标题 + 引用块描述
- 每个 TC 有 **操作** 和 **预期** 两部分
- 预期用 `- [ ]` checkbox 格式
- 末尾有汇总表 + 启动命令 + 关键观察点

**核心原则**: 验收测试是写给人看的产品规格，同时也是可执行的运行时校验。156 个单元测试全过 ≠ 产品可用（Session 14 的 workspace 工具 bug 就是证明）。

### 纪律 4：防腐规则（持续改进）

**系统性 Bug 全局排查**: 发现一个 Bug 属于某类系统性问题时（如枚举序列化），**立即**全局排查同类问题并一次性修复，不留到下次踩坑。

**本地验证优先于 Docker**: 代码修改后先跑本地验证（单元测试 + `npm run build` 类型检查），确认无误后再 Docker 重建。避免"改一行 → Docker 重建 3 分钟 → 发现新问题"的低效循环。

```
修改代码 → 本地单元测试（30s）→ npm run build（10s）→ 确认无误 → Docker 重建（仅集成验证时）
```

**Git 提交粒度**: 每个逻辑变更一个 commit（一个 Bug 修复 = 一个 commit，一个功能 = 一个 commit）。避免单个 commit 包含 20+ 文件的大杂烩，便于 bisect 和回滚。

**同一入口多路径测试**: 同一功能的不同入口（REST API / WebSocket / UI）必须各自测试。Session 14 教训：workspace 工具通过 WebSocket 可用但 REST API 未连通。

## Git

- **Remote**: `git@github.com:pan94u/forge.git`（branch: main）
- **文档导航**: `docs/index.md`
- **开发日志**: `docs/planning/dev-logbook.md`
- **设计基线**: `docs/baselines/design-baseline-v1.md`
- **规划基线**: `docs/baselines/planning-baseline-v1.5.md`
- **功能清单**: `docs/product/feature-list.md`
- **验收测试**: `docs/acceptance-tests/`（phase1.6~5, sprint2.1~2.4）
- **分析报告**: `docs/analysis/`（方法论、度量、Bug、状态评估）
- **实施计划**: `docs/planning/implementation-plans/`（phase1~4）
