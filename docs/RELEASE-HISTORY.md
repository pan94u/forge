# Forge Platform — Release History

> 项目周期: 2026-02-17 ~ 2026-02-24 (8 天 / 34 Sessions)
>
> 技术栈: Kotlin + Spring Boot 3 + Next.js 15 + Docker

---

## v0.8.0 — Phase 8 Bugfix (2026-02-24)

> E2E 手动测试发现的 9 Bug + 5 UX 问题全面修复

**Bug 修复**
- Sidebar 添加 Evaluations 导航入口
- 文件树隐藏 `.gitkeep` 文件
- Focus Mode 退出时恢复之前面板状态
- 聊天历史角色显示修复（role 大小写不敏感比较）
- Skills Tab 添加启用/禁用 toggle 开关
- Evaluations 页面交互增强（可展开行、Profile 筛选）

**UX 改进**
- AI 回复按时间顺序交错展示文本和工具调用（ContentSegment 架构）
- Intent Confirmation 选 Profile 后追问用户具体意图
- Context Window 容量 25K → 180K tokens
- 记忆/管道 Tab 添加引导说明

`3a4c07a` | 14 files changed, 446 insertions(+)

---

## v0.8.0-alpha — Phase 8: 进化环全面打通 (2026-02-24)

> 知识链路闭合 — 让每次交付自动沉淀知识，反哺下次交付

**新增功能**
- **面板可折叠 + Focus Chat 模式**: 左/中面板支持折叠，AI Chat 可全屏
- **Workspace 管理页面** (`/workspaces`): 完整列表 + 搜索 + 状态筛选 + 操作
- **Skill 执行质量 Hook**: 三层质量模型（平台规则 / Skill 自定义 / 自学习）
- **知识管道闭合**: 执行资产提取 + 知识差距持久化 + Skill 更新建议
- **Pipeline 面板**: 知识管道可视化（差距统计 / Skill 质量 / 自学习模式 / 改进建议）
- **四维交互评估**: Intent × Completion × Quality × Experience 自动评分
- **Evaluations Dashboard** (`/evaluations`): 雷达图 + Profile 分析 + 能力分类

**数据库**
- 新增 `skill_quality_records` + `skill_quality_learned_patterns` 表 (V9)
- 新增 `knowledge_gaps` 表 (V10)
- 新增 `interaction_evaluations` 表 (V11)

`338e0cf` | 28 files changed, 2252 insertions(+)

---

## v0.7.0 — Phase 7: 产品试用级打磨 (2026-02-23 ~ 2026-02-24)

> 从"能跑"到"能用" — 端到端体验打通

**新增功能**
- **Git Clone 异步化**: 后台 clone + 前端进度条，支持大型仓库载入
- **知识库 Scope 分层**: Global / Workspace / Personal 三层优先级
- **H2 文件持久化**: Docker 重启数据不丢失 (`data/forge` volume)
- **MiniMax 模型支持**: M2.5 / M2.5-lightning / M2.5-highspeed (1M context)
- **模型选择端到端**: 前端选择 → WebSocket 传递 → 后端动态适配
- **Evaluation Profile**: 只读评估角色 + 知识萃取 + 文档生成 Skill
- **知识库本地写入**: Agent 可直接写 Markdown 到 knowledge-base 目录
- **Context Usage 实时推送**: 每轮 Agentic Loop 推送 token 使用率

**重构**
- 禁用 HITL checkpoint，简化为 intent confirmation 模式
- streamWithRetry 重试机制修复

`f0f20ed` ~ `cda0e21` | 7 commits

---

## v0.6.0 — Phase 6: 产品可用性加固 (2026-02-23)

> Workspace 全生命周期 + 架构重构

**新增功能**
- **Workspace 持久化**: JPA Entity + 生命周期管理（创建/激活/暂停/删除）
- **Git 仓库载入**: 通过 Git URL clone 远程仓库到 Workspace
- **用户 API Key 管理**: AES-256-GCM 加密存储 + 前端配置弹窗
- **代码结构分析**: `analyze_codebase` MCP 工具
- **PostgreSQL 远程切换**: 开发环境切换到远程 PG，凭据通过 .env 注入

**架构重构**
- ClaudeAgentService 拆分为 4 个独立服务
- McpProxyService 拆分为 5 个 Handler

`d60b8d1` ~ `6a78c3b` | 6 commits

---

## v0.5.0 — Phase 5: 记忆与上下文管理 (2026-02-23)

> 3 层跨 Session 记忆 + 消息压缩 + 4-Tab UI

**新增功能**
- **Workspace Memory**: 工作区级持久记忆（4K chars），始终注入 system prompt
- **Stage Memory**: Profile × Workspace 级记忆（8K chars），按角色注入
- **Session Summary**: 会话结束自动生成结构化摘要
- **消息压缩**: 3 阶段自动压缩（工具截断 → 历史摘要 → 全量总结）
- **4-Tab 右侧面板**: 对话 / 质量 / Skills / 记忆

`d90cdcd` | 1 commit, 大型功能集

---

## v0.4.0 — Phase 4: Skill 架构改造 (2026-02-22)

> 对齐 Anthropic Agent Skills 标准 — 渐进式加载 + 质量治理

**新增功能**
- **Skill Metadata 架构**: SKILL.md frontmatter 解析（YAML header）
- **渐进式加载**: L1 元数据 → L2 完整内容 → L3 脚本/子文件
- **Skill 质量治理**: 质量评分（A/B/C/D）+ 进化建议
- **Skill 管理 API + UI**: CRUD + 搜索 + 分类浏览 + Tag 过滤
- **Skill 度量**: 使用排行 + 触发分析 + 进化追踪

**验收**: 32 TC, 29 通过 (90.6%)

`72cde02` ~ `955b508` | 2 commits

---

## v0.3.0 — Phase 3: 人机协作闭环 (2026-02-21 ~ 2026-02-22)

> HITL 审批 + 底线检查 + Intent Confirmation

**新增功能**
- **HITL 审批面板**: 交付物清单 + 底线检查结果 + 倒计时 + 三种操作
- **底线自动检查**: 代码生成后自动运行质量底线，失败自修复（最多 2 轮）
- **Intent Confirmation**: 低置信度路由时弹出意图确认卡片
- **Activity Log**: Agent 执行子步骤实时日志面板
- **Delivery Stage 指示器**: Planning → Design → Development → Testing → Ops → Evaluation

**验收**: 24 TC, 20 通过 (83.3%) → 修复 BUG-021~028 后全部通过

`ee56428` ~ `a8892ef` | 6 commits

---

## v0.2.0 — Phase 2: Skill-Aware OODA + 多模型 + CI (2026-02-18 ~ 2026-02-21)

> 四大 Sprint: OODA 可视化 / MCP 连接 / 评估框架 / 多模型适配

### Sprint 2.1+2.2 — CI + MCP + 6 容器
- **GitHub Actions CI**: Kotlin build + frontend lint + Playwright E2E
- **MCP 实连接**: Knowledge MCP + Database MCP 容器化
- **6 容器架构**: Backend + Frontend + Nginx + Keycloak + Knowledge MCP + Database MCP
- **BaselineService**: 底线脚本发现 + 执行 + 结果收集
- **3 个新 Skill**: testing-standards, deployment-readiness-check, cross-stack-migration

### Sprint 2A — OODA 可视化
- **OODA 阶段指示器**: Observe → Orient → Decide → Act → Complete 实时展示
- **Profile Badge**: 当前角色 + 置信度 + Skill 列表 + 路由原因
- **Turn 计数器**: 当前轮次 / 最大轮次

### Sprint 2C — 评估框架
- **agent-eval 模块**: 真实模型调用评估框架
- **MetricsService**: 7 个 Prometheus 自定义指标
- **跨栈迁移 PoC**: cross-stack-migration Skill 概念验证

### Sprint 2.3 — 多模型适配
- **Adapter 层**: Qwen / Gemini / Bedrock / OpenAI-compatible 适配器
- **ModelRegistry**: 运行时模型发现 + 动态切换
- **前端模型选择器**: 下拉切换 + 参数调节弹窗
- **API Key 加密**: AES-256-GCM 用户级 Key 管理

`5737423` ~ `79e9ae6` | 20+ commits

---

## v0.1.6 — Phase 1.6: AI 交付闭环 (2026-02-19 ~ 2026-02-20)

> 从 Agent 到 IDE 的完整交付通路

**新增功能**
- **Keycloak SSO**: OIDC Authorization Code + PKCE
- **AI → Workspace 交付**: Agent 生成代码 → 自动写入 Workspace → 文件树刷新
- **Context Picker**: `@` 提及机制附加文件/知识库/数据库 Schema
- **FileExplorer CRUD**: 完整文件树操作（创建/重命名/删除/右键菜单）
- **代码块 Apply**: AI 回复中代码块一键写入 Workspace
- **自动保存**: 编辑后 5 秒 debounce 自动保存

**Bug 修复**: BUG-012 ~ BUG-020（11 个文件操作 + 枚举序列化 + Context Picker 修复）

**验收**: 21 场景 / 87 用例, 80 通过 (92.0%)

`4759ee0` ~ `60f859c` | 12 commits

---

## v0.1.5 — Phase 1.5: Docker 一键部署 (2026-02-17 ~ 2026-02-18)

> 内部试用 MVP

**新增功能**
- **Docker Compose 部署**: 4 容器（Backend + Frontend + Nginx + Keycloak）
- **Nginx 统一入口**: 单端口 9000 反向代理
- **Prompt Caching**: System Prompt 缓存 5 分钟，节省 90% 成本

**Bug 修复**: Docker E2E 验证发现的 10 个问题

`97fd1a3` ~ `de93147` | 3 commits

---

## v0.1.0 — Phase 1: 实时流式 + Agentic Loop (2026-02-17)

> 核心引擎上线

**新增功能**
- **实时流式输出**: SSE 逐 token 输出
- **Agentic Loop**: 多轮自主执行管线（最多 50 轮）
- **DB 持久化**: 会话 + 消息 + 工具调用记录
- **Skill 体系**: 初始 Skill 加载 + Profile 路由
- **单元测试**: 初始测试覆盖

`0381e91` | 1 commit, 大型功能集

---

## v0.0.1 — Phase 0: 平台初始化 (2026-02-17)

> 项目骨架搭建

**交付内容**
- Gradle monorepo 骨架（7 个子模块）
- Web IDE 基础 UI（Monaco Editor + 文件树 + 终端）
- Claude API 集成 + 基础聊天
- MCP Server 骨架（Knowledge + Database）
- Skill/Profile/Command 插件目录结构
- Knowledge Base 初始文档（13 篇）

`02e003c` ~ `93b6ef7` | 2 commits

---

## 累计统计

| 维度 | 数值 |
|------|------|
| **开发周期** | 8 天 (2026-02-17 ~ 2026-02-24) |
| **开发 Session** | 34 个 |
| **Git Commits** | 75 个 |
| **Phase 数量** | 9 个 (Phase 0 ~ Phase 8) |
| **REST API** | 68 个端点 |
| **MCP 工具** | 18 个 |
| **Skill** | 32 个 (6 Profile) |
| **单元测试** | 156 个 |
| **验收测试** | 50 TC / 11 Journey |
| **已修复 Bug** | 32 个 (BUG-001 ~ BUG-032) |
| **Docker 容器** | 5 个 |
| **代码规模** | ~50K+ 行 |
| **数据库迁移** | 11 个 (V1 ~ V11) |
| **支持模型** | 6 Provider |

---

> Built with Kotlin + Spring Boot + Next.js + Claude, by zhao55 & Claude Opus 4.6
