# Sprint 2.4 验收测试 — 内部试用准备

> **测试环境**：Docker 6 容器 + 本地开发环境
> **Sprint 目标**：准备内部试用材料，让 3-5 人能在 ≥3 天内独立使用平台并收集结构化反馈
> **场景先行**：本文档在编码前创建，编码后补充操作细节和实际值

---

## 场景 1：试用环境就绪（4 TC）

> 验证 Docker 试用环境可从零启动，所有服务健康，环境变量正确透传。

### TC-1.1 Docker 6 容器启动

**操作**：
```bash
cd infrastructure/docker
cp ../../.env.example .env
# 编辑 .env，填入至少一个 Provider 的 API Key
docker compose -f docker-compose.trial.yml --env-file .env up --build -d
```

**预期**：
- [ ] 6 个容器全部启动：keycloak、knowledge-mcp、database-mcp、backend、frontend、nginx
- [ ] `docker compose -f docker-compose.trial.yml ps` 显示所有容器 `healthy` 或 `running`
- [ ] 启动过程无报错（`docker compose logs` 无 ERROR 级别日志）
- [ ] 首次构建 < 5 分钟（后续启动 < 30 秒）

### TC-1.2 .env.example 覆盖 5 个 Provider

**操作**：检查 `.env.example` 文件内容

**预期**：
- [ ] 包含 Anthropic 配置段（`ANTHROPIC_API_KEY`）
- [ ] 包含 Google Gemini 配置段（`GEMINI_API_KEY`）
- [ ] 包含 DashScope 配置段（`DASHSCOPE_API_KEY`）
- [ ] 包含 AWS Bedrock 配置段（`AWS_REGION`、`AWS_PROFILE`）
- [ ] 包含 OpenAI 兼容配置段（`LOCAL_MODEL_URL`、`LOCAL_MODEL_NAME`、`LOCAL_MODEL_API_KEY`）
- [ ] 包含安全配置段（`FORGE_ENCRYPTION_KEY`、`FORGE_SECURITY_ENABLED`）
- [ ] 每个 Provider 标注获取方式和支持的模型列表

### TC-1.3 模型 API 返回 13+ 模型

**操作**：
```bash
curl -s http://localhost:9000/api/models | python3 -m json.tool
```

**预期**：
- [ ] 返回 JSON 数组，包含 ≥ 13 个模型
- [ ] 每个模型包含 `id`、`displayName`、`provider`、`contextWindow`、`costTier` 字段
- [ ] Anthropic 模型 3 个：claude-opus-4-6、claude-sonnet-4-6、claude-haiku-4-5
- [ ] Gemini 模型 3 个：gemini-2.5-pro、gemini-2.5-flash、gemini-2.5-flash-lite
- [ ] Qwen 模型 4 个：qwen3.5-plus、qwen-plus、qwen-turbo、qwen-long
- [ ] Bedrock 模型 3 个

### TC-1.4 MCP 服务健康

**操作**：
```bash
curl -s http://localhost:8081/health/live   # knowledge-mcp
curl -s http://localhost:8082/health/live   # database-mcp
```

**预期**：
- [ ] knowledge-mcp 返回 200 OK
- [ ] database-mcp 返回 200 OK
- [ ] backend 容器内 `FORGE_MCP_SERVERS` 环境变量包含两个 MCP 地址

---

## 场景 2：用户上手体验（5 TC）

> 验证新用户按试用指南操作，能在 5 分钟内完成首次使用。

### TC-2.1 首次使用全流程

**操作**：按 TRIAL-GUIDE.md「快速启动 5 分钟」章节操作

**预期**：
- [ ] clone → .env → docker up → 验证 → 打开 9000，全程 ≤ 5 分钟
- [ ] 每一步都有明确的命令和预期输出
- [ ] 无遗漏步骤（不需要额外搜索才能完成）

### TC-2.2 试用指南可操作性

**操作**：通读 TRIAL-GUIDE.md 全文

**预期**：
- [ ] 包含前置条件（Docker、内存 8GB、至少 1 个 API Key）
- [ ] 包含服务架构一览（6 容器表格 + Nginx 路由）
- [ ] 包含初次使用走查（创建 workspace → 发消息 → 看 OODA → 看文件生成）
- [ ] 包含多模型配置与切换说明
- [ ] 包含 5 个 Profile 与 Skill 说明
- [ ] 包含 5 个试用场景
- [ ] 包含知识库与 MCP 工具说明
- [ ] 包含故障排除 FAQ（≥ 10 个常见问题）
- [ ] 包含已知限制
- [ ] 全文中文，约 300-400 行

### TC-2.3 模型配置指南

**操作**：按 TRIAL-GUIDE.md「多模型配置与切换」章节，配置一个非 Anthropic 模型

**预期**：
- [ ] 说明在哪里填 API Key（.env 文件 或 UI ModelSettingsDialog）
- [ ] 说明如何切换模型（ModelSelector 下拉菜单）
- [ ] 提供 13 个模型的对照表（名称、Provider、上下文窗口、费用等级）

### TC-2.4 Profile 切换指南

**操作**：按 TRIAL-GUIDE.md Profile 章节，触发 Profile 切换

**预期**：
- [ ] 说明 5 个 Profile（规划/设计/开发/测试/运维）的用途
- [ ] 说明如何触发切换（@ 指令 或 自动关键词识别）
- [ ] 说明 Profile Badge 的含义（置信度颜色、加载的 Skill 列表）

### TC-2.5 FAQ 覆盖常见问题

**操作**：检查 TRIAL-GUIDE.md FAQ 章节

**预期**：
- [ ] 涵盖启动失败（端口冲突、内存不足、构建失败）
- [ ] 涵盖模型相关（API Key 无效、Rate Limit、模型不可用）
- [ ] 涵盖功能相关（WebSocket 断连、MCP 工具报错、文件操作失败）
- [ ] 每个问题有明确的排查步骤和解决方案

---

## 场景 3：功能覆盖（5 TC）

> 验证试用指南中描述的核心功能在 Docker 环境中均可正常工作。

### TC-3.1 代码生成端到端

**操作**：在 workspace 中发送 "帮我用 Kotlin 实现一个简单的 REST API controller"

**预期**：
- [ ] AI 自动切换到 development Profile
- [ ] OODA 指示器正常流转
- [ ] 输出包含 Kotlin 代码块
- [ ] 调用 workspace_write_file 将代码写入 workspace

### TC-3.2 知识库搜索

**操作**：在对话中输入 "搜索知识库中关于 API 设计的文档"

**预期**：
- [ ] AI 调用 search_knowledge MCP 工具
- [ ] 返回知识库中的匹配文档
- [ ] 工具调用卡片可展开查看输入/输出

### TC-3.3 多模型对比

**操作**：同一问题分别用 Claude 和 Gemini（或其他可用模型）回答

**预期**：
- [ ] ModelSelector 可切换模型
- [ ] 不同模型能正常回复
- [ ] OODA 流程在不同模型下均正常

### TC-3.4 底线检查

**操作**：在对话中请求生成包含硬编码密码的代码

**预期**：
- [ ] AI 在输出中提示安全风险
- [ ] 或底线检查拦截不安全代码

### TC-3.5 Schema 查询

**操作**：在对话中输入 "查询数据库 schema"

**预期**：
- [ ] AI 调用 query_schema MCP 工具
- [ ] 返回数据库表结构信息

---

## 场景 4：反馈收集就绪（3 TC）

> 验证反馈模板完整、可操作，试用者能独立填写。

### TC-4.1 反馈模板完整性

**操作**：检查 `docs/trial-feedback-template.md`

**预期**：
- [ ] 包含 7 维度评分表（安装部署、首次上手、AI 回复质量、Profile 切换、工具调用、多模型切换、整体满意度）
- [ ] 每个维度有 1-5 分评分标准说明
- [ ] 包含 6 个开放问题

### TC-4.2 每日使用日志可操作

**操作**：模拟填写第 1 天的使用日志

**预期**：
- [ ] 包含时长、功能、模型、Profile、任务、问题、模型对比 7 个记录项
- [ ] 提供 ≥ 3 天的日志模板
- [ ] 格式清晰，填写无歧义

### TC-4.3 Bug 报告模板

**操作**：检查 Bug 报告模板

**预期**：
- [ ] 包含标题、严重程度、复现步骤、预期/实际行为
- [ ] 包含模型、Profile、浏览器/OS 等环境信息
- [ ] 提供多个 Bug 模板槽位

---

## 五、汇总

| 场景 | 用例数 | 类型 | 优先级 |
|------|--------|------|--------|
| 场景 1：试用环境就绪 | 4 | Docker/API | P0 |
| 场景 2：用户上手体验 | 5 | 文档审查 | P0 |
| 场景 3：功能覆盖 | 5 | 手动/Docker | P1 |
| 场景 4：反馈收集就绪 | 3 | 文档审查 | P1 |
| **总计** | **17** | — | — |

---

## 六、Sprint 2.4 整体验收标准

- [ ] `.env.example` 覆盖 5 个 Provider + 安全配置
- [ ] `docker-compose.trial.yml` 透传所有新增环境变量
- [ ] TRIAL-GUIDE.md 重写完成（中文，~350 行，覆盖 10 大章节）
- [ ] 反馈模板包含 7 维度评分 + 6 个开放问题 + 每日日志 + Bug 模板
- [ ] 旧 `user-guide-trial.md` 标注废弃并指向新指南
- [ ] Docker 6 容器启动 healthy，模型 API 返回 13+ 模型
- [ ] 一个新用户按指南操作能在 5 分钟内完成首次使用

---

> Sprint 2.4 验收测试 v1.0 | 创建日期: 2026-02-21
> 场景先行（编码前创建），编码后对照代码交叉验证
