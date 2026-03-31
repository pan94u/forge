# Forge Platform — AI-Native IT 交付与治理基础设施

> **使命**：重构企业 IT 组织的人机分工。
>
> AI 已经能够以零摩擦、高速、可靠的方式执行软件交付的大部分作业。在这个前提下，让人类继续做执行者，是一种巨大的浪费。Forge 让 AI 承接全部 SDLC 执行作业，让人类从执行中解放，专注于目标对齐、价值验收和战略决策；Enterprise Console 以 Agentic Loop 覆盖九大 IT 治理域，让 IT 治理也整体 AI-Native 化。
>
> **终极目标：解放人，聚焦价值创造，实现无限可能。**
>
> **定位**：AI-Native IT 基础设施，双层协同——
> - **执行层（Forge）**：SuperAgent + 28 Skill + 24 MCP 工具，三环架构（人类控制环主导 + 智能环执行 + 进化环学习），将 5-7 人团队的交付能力压缩到 1-2 人 + AI；人类角色重构为目标对齐者、价值验证者、结果裁判者
> - **治理层（Enterprise Console）**：Governance AI 以 Agentic Loop 覆盖九大 IT 治理域（架构/预算/团队/安全/数据/流程/合规/容量/供应商），Forge 执行数据通过 Governance Data API 驱动治理洞察与决策
>
> 面向全软件交付生命周期，支持集团-域-产业多层级组织规模化落地、可管理、可运营、可演进。

## 交互偏好

- **用中文交流**。所有回复、注释、文档默认使用中文
- **行动优先**：用户说"执行"/"继续"/"全部"时直接做，不要反问
- **简洁回复**：少说多做。避免冗长解释，给结果和关键数据
- **遵循四大开发纪律**（见下方"开发纪律"章节）
- **Git 提交署名**：commit 末尾必须包含 `Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>`

## Quick Start

```bash
# 环境要求：JDK 21（必须！JDK 8/17 会编译失败）
java -version  # 确认 21+

# 构建后端 jar（跳过测试加速）
./gradlew :web-ide:backend:clean :web-ide:backend:bootJar -x test

# 构建前端
cd web-ide/frontend && npm install && npm run build && cd ../..

# 构建企业控制台
cd enterprise-console && npm install && npm run build && cd ..

# Docker 一键部署（7 容器：backend + frontend + console + nginx + postgres + 2 MCP）
# 前置：先启动 SSO（cd infrastructure/sso && docker compose up -d）
cd infrastructure/docker
docker compose -f docker-compose.trial.yml up --build -d

# 访问 http://forge.local:19000（需 /etc/hosts 配置 127.0.0.1 forge.local sso.forge.local）

# 运行全量单元测试（当前 335 个）
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
6. **MCP 工具聚合**: 5 MCP Server × 20 细粒度工具 → McpProxyService 20 聚合工具（5 handler 拆分路由）
7. **本地构建 + Docker 打包**: 不在 Docker 内编译（网络不可靠），本地 bootJar/npm build 后 Docker 只 COPY 产物

## Language & Conventions

- **Backend**: Kotlin 1.9+ on JDK 21, Spring Boot 3.3+
- **MCP Servers**: Kotlin + Ktor
- **Frontend**: TypeScript, React 19, Next.js 15 (App Router)
- **Build**: Gradle Kotlin DSL
- **Testing**: JUnit 5 + MockK + AssertJ (backend), Jest + Playwright (frontend)

## 认证 (@opc-ai/auth SDK)

- 前端: `@opc-ai/auth/client`, `initAuth({ tokenKey: 'forge_access_token' })` 兼容现有 6 个 API 文件零改动
- 后端: Kotlin `GatewayUserFilter.kt` (与 SDK 的 `synapseAuth()` 模式等价, 语言不同)
- Next.js 必须 `transpilePackages: ['@opc-ai/auth']` (SDK 发布 .ts 源文件)
- trial compose 的 enterprise-console 容器占 :19001, 本地联调 Gateway 用 :19002 避开

## Docker 部署注意事项

- **后端 Dockerfile**: 单阶段 `eclipse-temurin:21-jre-alpine`（Alpine 无 bash）
- **前端 Dockerfile**: COPY 预构建 `.next/standalone`, 需先在宿主机 `bun run build`
- **trial 模式**: `FORGE_SECURITY_ENABLED=false`, 无 Gateway, 无认证
- **production 模式**: Gateway sidecar, OIDC 认证, X-User-* headers
- **Volume 挂载**: `plugins/` 和 `knowledge-base/` 必须挂载为只读

## MCP 工具清单（20 个）

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
| page_create | knowledge | 创建知识页面（local mode 写 Markdown 到 knowledge-base/） |
| workspace_write_file | workspace | 写文件到 workspace（需 workspaceId） |
| workspace_read_file | workspace | 读取 workspace 文件 |
| workspace_list_files | workspace | 列出 workspace 文件树 |
| workspace_compile | workspace | 编译/构建项目，自动检测项目类型 |
| workspace_test | workspace | 运行测试，自动检测测试框架 |
| workspace_start_service | workspace | 在 workspace 内启动服务进程（command + port） |
| workspace_stop_service | workspace | 按端口停止 workspace 内运行的服务 |
| update_workspace_memory | memory | Agent 主动更新 workspace 记忆 |
| get_session_history | memory | 读取历史 session 摘要 |
| analyze_codebase | memory | 对 workspace 执行结构分析，返回 JSON |
| plan_create | planning | 提交任务计划，等待用户确认后开始执行 |
| plan_update_task | planning | 更新单个任务状态（in_progress/done/failed/blocked）|
| plan_ask_user | planning | 向用户提选择题+问答题，阻塞等待回答 |
| plan_complete | planning | 提交执行总结并写入记忆 |

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
- **NEXT_PUBLIC_* 构建时变量**: Next.js `NEXT_PUBLIC_*` 在 `npm run build` 时烘焙进产物，运行时设置无效。Enterprise Console 的 `NEXT_PUBLIC_BASE_PATH=/console` 已固化在项目 `.env` 文件中，构建时自动读取
- **Console fetch() 不自动加 basePath**: `<Link>` 和 `useRouter` 自动加 basePath，但 `fetch()` 不加。`api.ts` 中所有请求必须手动加 `process.env.NEXT_PUBLIC_BASE_PATH` 前缀，否则 nginx 直接路由到 backend（绕过 auth 代理层导致 401）
- **Auth.js RFC 9207 issuer 校验**: Keycloak 24 在授权回调中带 `iss` 参数，Auth.js 会校验。`type: "oauth"` 时设置 `issuer` **不会**触发 OIDC discovery，但**必须**设置以通过 RFC 9207 校验
- **nginx proxy_buffer_size**: NextAuth session cookie 超过 4KB 默认值，所有代理到 Console 的 nginx location 需设 `proxy_buffer_size 16k; proxy_buffers 4 16k;`
- **JPA 非空字段必须配 Flyway 迁移**: 给 Entity 新增 Kotlin 非空字段（`Int`/`Long`/`String`）时，**必须同步写 Flyway 迁移脚本**（ADD COLUMN + UPDATE 回填 + SET NOT NULL）。`ddl-auto=update` 只管加列不回填，老数据 NULL 导致运行时崩溃。Session 30 的 `eval_runs.total_tasks` 踩过此坑

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

**当前验收测试**: `docs/acceptance-tests/current-at.md`（41 用例，39 通过 = 95.1%）

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

- **主仓库**: `git@github.com:pan94u/forge.git`（branch: main）
- **docs submodule**: `git@github.com:pan94u/forge-docs.git`（branch: main）

### docs/ Submodule 操作规范

`docs/` 是独立 Git submodule，指向 `pan94u/forge-docs.git`。**不要在主仓库直接 commit docs/ 下的文件**。

```bash
# 初始化（clone 主仓库后）
git submodule update --init --recursive

# 修改 docs 内容的标准流程
cd docs
git checkout main
# ... 编辑文件 ...
git add <files>
git commit -m "docs: 描述"
git push                    # 推送到 forge-docs 远程

# 回到主仓库，更新 submodule 指针
cd ..
git add docs
git commit -m "docs: update submodule pointer"
git push                    # 推送到 forge 主仓库
```

**注意事项**：
- `git submodule update --init` 后 docs/ 处于 detached HEAD，修改前必须先 `cd docs && git checkout main`
- 主仓库的 `git diff` / `git status` 只显示 submodule 指针变化（`docs (new commits)`），不显示 docs 内文件级 diff
- 并行 Agent 修改 docs 时，需在 submodule 内操作（`cd docs` 后执行 git 命令）

### 文档导航

- **文档导航**: `docs/index.md`
- **开发日志**: `docs/planning/dev-logbook.md`
- **设计基线**: `docs/baselines/design-baseline-v1.md`
- **规划基线**: `docs/baselines/planning-baseline-v1.5.md`
- **功能清单**: `docs/product/feature-list.md`
- **验收测试**: `docs/acceptance-tests/`（phase1.6~5, sprint2.1~2.4）
- **分析报告**: `docs/analysis/`（方法论、度量、Bug、状态评估）
- **实施计划**: `docs/planning/implementation-plans/`（phase1~4）
