# Forge Platform — Changelog

> 8 天 · 34 Sessions · 75 Commits · 50K+ 行代码

---

### v0.8.0 (2026-02-24) — E2E Bugfix
- 修复 9 Bug + 5 UX 问题（AI 回复时间顺序、Intent 追问、角色显示、Skills 开关等）
- Context Window 25K → 180K tokens

### v0.8.0-alpha (2026-02-24) — 进化环闭合
- Skill 执行质量 Hook（三层质量模型）
- 知识管道（资产提取 + 差距检测 + Skill 建议）
- 四维交互评估 + Evaluations Dashboard
- 面板折叠 + Focus Chat 全屏模式
- Workspace 管理页面

### v0.7.0 (2026-02-23) — 产品试用打磨
- Git Clone 异步化 + 进度条
- MiniMax 模型支持（1M context）
- H2 文件持久化 · 知识库本地写入 · Context Usage 实时推送
- Evaluation Profile（只读评估角色）

### v0.6.0 (2026-02-23) — 可用性加固
- Workspace 全生命周期持久化
- 用户 API Key 加密管理
- ClaudeAgentService 拆分 4 服务 · McpProxyService 拆分 5 Handler

### v0.5.0 (2026-02-23) — 记忆系统
- 3 层跨 Session 记忆（Workspace / Stage / Session Summary）
- 3 阶段消息压缩 · 4-Tab 右侧面板

### v0.4.0 (2026-02-22) — Skill 架构改造
- SKILL.md frontmatter 元数据 · 渐进式加载（L1→L2→L3）
- Skill 质量评分 · 管理 API + UI · 度量分析

### v0.3.0 (2026-02-21) — 人机协作闭环
- HITL 审批面板 · 底线自动检查 + 自修复
- Intent Confirmation · Activity Log · Delivery Stage 指示器

### v0.2.0 (2026-02-18~21) — OODA + 多模型 + CI
- OODA 循环可视化 · Profile Badge · Turn 计数器
- 6 Provider 多模型适配（Claude/Gemini/Qwen/Bedrock/OpenAI/MiniMax）
- MCP 实连接 · 6 容器架构 · GitHub Actions CI
- 7 个 Prometheus 自定义指标

### v0.1.6 (2026-02-19) — AI 交付闭环
- Keycloak SSO · AI → Workspace 文件写入 · Context Picker
- FileExplorer CRUD · 代码块 Apply · 自动保存
- 验收: 87 用例, 92% 通过

### v0.1.5 (2026-02-17) — Docker 部署
- Docker Compose 一键部署（4 容器）· Prompt Caching

### v0.1.0 (2026-02-17) — 核心引擎
- 实时流式输出 · Agentic Loop（50 轮）· DB 持久化 · Skill 体系

### v0.0.1 (2026-02-17) — 项目初始化
- Gradle monorepo · Web IDE · Claude API · MCP Server · 13 篇知识文档

---

**累计**: 68 API · 18 MCP 工具 · 32 Skill · 156 单元测试 · 11 DB 迁移 · 6 Provider
