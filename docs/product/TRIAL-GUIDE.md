# Forge Platform — 内部试用指南

> **版本**: Phase 3 | **更新**: 2026-02-21
> **目标读者**: 内部试用人员（3-5 人，≥3 天试用期）
> **平台能力**: 6 容器部署、5 模型提供商（13+ 模型）、5 Profile + 32 Skill、11 MCP 工具、HITL 审批闭环、质量度量面板

---

## 1. 前置条件

| 项目 | 要求 | 验证命令 |
|------|------|---------|
| Docker Engine | 24+ | `docker --version` |
| Docker Compose | v2+ | `docker compose version` |
| 可用内存 | ≥ 8 GB 分配给 Docker | Docker Desktop → Settings → Resources |
| API Key | 至少 1 个 Provider 的 Key | 见下方 Provider 列表 |
| 端口 | 8081、8082、8180、9000 未被占用 | `lsof -i :9000` |

**支持的 API Key 来源**（任选一个即可启动）：

| Provider | 获取地址 | 环境变量 |
|----------|---------|---------|
| Anthropic | https://console.anthropic.com/settings/keys | `ANTHROPIC_API_KEY` |
| Google Gemini | https://aistudio.google.com/apikey | `GEMINI_API_KEY` |
| 阿里云 DashScope | https://dashscope.console.aliyun.com/apiKey | `DASHSCOPE_API_KEY` |
| AWS Bedrock | AWS IAM Console | `AWS_REGION` + `AWS_PROFILE` |
| OpenAI 兼容 | 取决于你的服务 | `LOCAL_MODEL_URL` + `LOCAL_MODEL_NAME` |

---

## 2. 快速启动（5 分钟）

### Step 1: 克隆仓库

```bash
git clone git@github.com:pan94u/forge.git forge-platform
cd forge-platform
```

### Step 2: 配置环境变量

```bash
cp .env.example infrastructure/docker/.env
```

编辑 `infrastructure/docker/.env`，填入你的 API Key。最简配置只需填一行：

```bash
ANTHROPIC_API_KEY=sk-ant-api03-你的密钥
```

> 如果你有多个 Provider 的 Key，都可以填入——平台会自动识别可用模型。

### Step 3: 本地构建

```bash
# 确保 JDK 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21  # macOS，按实际路径调整

# 后端 JAR
./gradlew :web-ide:backend:bootJar -x test --no-daemon

# 前端
cd web-ide/frontend && npm install && npm run build && cd ../..
```

### Step 4: Docker 启动

```bash
cd infrastructure/docker
docker compose -f docker-compose.trial.yml --env-file .env up --build -d
```

### Step 5: 验证启动

```bash
# 检查 6 个容器状态
docker compose -f docker-compose.trial.yml ps

# 预期输出：6 个容器均为 healthy 或 running
# keycloak, knowledge-mcp, database-mcp, backend, frontend, nginx
```

### Step 6: 访问平台

打开浏览器访问 **http://localhost:9000**

验证 API 可用：
```bash
# 检查模型列表
curl -s http://localhost:9000/api/models | python3 -m json.tool

# 检查知识库
curl -s http://localhost:9000/api/knowledge/search?q=API
```

---

## 3. 服务架构一览

### 6 容器概览

| 容器 | 端口 | 技术栈 | 说明 |
|------|------|--------|------|
| **nginx** | 9000 | Nginx Alpine | 反向代理，统一入口 |
| **frontend** | 3000（内部） | Next.js 15 + React 19 | Web IDE 前端 |
| **backend** | 8080（内部） | Spring Boot 3 + Kotlin | 核心业务逻辑 + AI 对话 |
| **keycloak** | 8180 | Keycloak 24 | 身份认证（试用模式可关闭） |
| **knowledge-mcp** | 8081 | Kotlin + Ktor | 知识库 MCP Server |
| **database-mcp** | 8082 | Kotlin + Ktor | 数据库 MCP Server |

### Nginx 路由规则

| 路径 | 转发目标 | 说明 |
|------|---------|------|
| `/api/*` | backend:8080 | REST API（含 SSE 长连接） |
| `/ws/*` | backend:8080 | WebSocket（AI 对话流） |
| `/actuator/*` | backend:8080 | Spring Actuator 监控 |
| `/h2-console/*` | backend:8080 | H2 数据库控制台 |
| `/auth/*` | keycloak:8080 | Keycloak 认证代理 |
| `/*` | frontend:3000 | 前端页面（兜底路由） |

---

## 4. 初次使用走查

### 4.1 创建 Workspace

1. 打开 http://localhost:9000
2. 点击左侧导航栏 **Workspaces**
3. 点击 **Create Workspace** 按钮
4. 输入名称（如 "我的第一个项目"），点击创建
5. 自动跳转到 IDE 页面（三面板布局：文件树 / 编辑器 / AI 对话）

### 4.2 发送第一条消息

1. 在右侧 AI 对话面板的输入框中输入：`帮我写一个 Hello World 的 Kotlin 程序`
2. 点击发送或按 Enter

### 4.3 观察 OODA 思考过程

发送后注意观察对话区域顶部的阶段指示器：

```
👁 Observe → 🧭 Orient → 🧠 Decide → ⚡ Act → ✅ Done
```

| 阶段 | 含义 |
|------|------|
| Observe | AI 理解你的意图和上下文 |
| Orient | 分析上下文，路由到合适的 Profile |
| Decide | 制定回复方案（直接回复 or 调用工具） |
| Act | 执行工具调用（如写文件、搜索知识库） |
| Done | 回复完成 |

### 4.4 查看 Profile Badge

OODA 指示器下方显示当前激活的 Profile：

```
● development | kotlin-conventions, spring-boot-patterns +15 | keyword '实现' (conf=0.6)
```

- **绿色圆点**: 高置信度（≥80%）— AI 很确定你的意图
- **黄色圆点**: 中置信度（50-80%）— 基于关键词推断
- **灰色圆点**: 低置信度（<50%）— 使用默认 Profile

### 4.5 查看文件生成

如果 AI 调用了 `workspace_write_file` 工具：
1. 左侧文件树会自动刷新
2. 点击新生成的文件可在中间编辑器中查看
3. 工具调用过程以折叠卡片展示，点击可展开查看输入/输出

---

## 5. 多模型配置与切换

### 5.1 配置 API Key

**方式一：环境变量**（推荐，启动时生效）

编辑 `infrastructure/docker/.env`，填入对应 Provider 的 Key，重启 Docker。

**方式二：UI 配置**（运行时生效）

1. 点击对话面板中模型名称旁的 ⚙️ 图标
2. 打开 **Model Settings** 对话框
3. 为每个 Provider 填入 API Key，启用/禁用 Provider
4. Key 会加密存储，刷新后保留

### 5.2 切换模型

在对话输入框上方，点击 **模型选择器**（ModelSelector）下拉菜单：
- 模型按 Provider 分组显示
- 每个模型标注费用等级图标：👑 HIGH / 💻 MEDIUM / ⚡ LOW
- 选择后立即生效，下一条消息使用新模型

### 5.3 模型对照表

| 模型 ID | 显示名称 | Provider | 上下文窗口 | 最大输出 | 视觉 | 费用 |
|---------|---------|----------|-----------|---------|------|------|
| claude-opus-4-6 | Claude Opus 4.6 | Anthropic | 200K | 128K | ✅ | HIGH |
| claude-sonnet-4-6 | Claude Sonnet 4.6 | Anthropic | 200K | 64K | ✅ | MEDIUM |
| claude-haiku-4-5-20251001 | Claude Haiku 4.5 | Anthropic | 200K | 64K | ✅ | LOW |
| gemini-2.5-pro | Gemini 2.5 Pro | Google | 1M | 65K | ✅ | HIGH |
| gemini-2.5-flash | Gemini 2.5 Flash | Google | 1M | 65K | ✅ | MEDIUM |
| gemini-2.5-flash-lite | Gemini 2.5 Flash Lite | Google | 1M | 32K | ✅ | LOW |
| qwen3.5-plus | Qwen 3.5 Plus | DashScope | 1M | 16K | ✅ | HIGH |
| qwen-plus | Qwen Plus | DashScope | 131K | 8K | ❌ | MEDIUM |
| qwen-turbo | Qwen Turbo | DashScope | 131K | 8K | ❌ | LOW |
| qwen-long | Qwen Long | DashScope | 1M | 6K | ❌ | LOW |
| anthropic.claude-opus-4-6-v1 | Claude Opus 4.6 (Bedrock) | AWS | 200K | 128K | ✅ | HIGH |
| anthropic.claude-sonnet-4-6 | Claude Sonnet 4.6 (Bedrock) | AWS | 200K | 64K | ✅ | MEDIUM |
| anthropic.claude-haiku-4-5-... | Claude Haiku 4.5 (Bedrock) | AWS | 200K | 64K | ✅ | LOW |

---

## 6. Profile 与 Skill 说明

### 5 个 Profile

Forge AI 不是普通聊天机器人，它是一个 **SuperAgent**——根据你的意图自动切换专家角色。

| Profile | 触发方式 | 加载的核心 Skill | 适用场景 |
|---------|---------|-----------------|---------|
| **planning** | `@规划` 或说 "写 PRD"、"需求分析" | requirement-analysis, prd-writing | 需求分析、用户故事、PRD 撰写 |
| **design** | `@设计` 或说 "架构设计"、"API 设计" | architecture-design, detailed-design | 系统架构、API 设计、数据库建模 |
| **development** | `@开发` 或说 "实现"、"编码"、"写代码" | code-generation + 15 Foundation Skills | Kotlin/Java 编码、Spring Boot 开发 |
| **testing** | `@测试` 或说 "写测试"、"测试用例" | test-case-writing, test-execution | 测试策略、用例设计、测试执行 |
| **ops** | `@运维` 或说 "部署"、"K8s"、"CI/CD" | deployment-ops, kubernetes-patterns, ci-cd-patterns | 部署检查、K8s 配置、CI/CD 管道 |

### 32 个 Skill 分布

| 插件 | Skill 数 | 说明 |
|------|---------|------|
| forge-superagent | 8 | 核心交付技能（需求→设计→开发→测试→运维） |
| forge-foundation | 15 | 基础规范（Java/Kotlin/Spring Boot/Gradle/API/DB/安全等） |
| forge-deployment | 2 | 部署技能（K8s + CI/CD） |
| forge-knowledge | 3 | 知识访问（内部 API / 领域模型 / Runbook） |
| forge-team-templates | 4 | 团队模板（数据平台 / 移动端 / 后端 API + 通用） |
| **合计** | **32** | |

### 使用技巧

- **不加标签也行**：AI 会根据关键词自动识别意图并切换 Profile
- **@ 标签强制切换**：输入 `@开发 实现订单服务` 可强制使用 development Profile
- **观察 Badge**：Profile Badge 显示当前 Profile、加载的 Skill 列表和置信度

---

## 7. 试用场景

以下 5 个场景覆盖平台核心能力，建议每天至少尝试 1-2 个。

### 场景 A：新功能开发

> 体验从需求到代码的完整流程

1. 输入：`@规划 我需要一个用户注册功能，支持邮箱和手机号`
2. 观察 AI 切换到 planning Profile，输出需求分析
3. 继续：`@设计 请设计注册功能的 API 和数据库`
4. 观察切换到 design Profile，输出架构设计
5. 继续：`@开发 实现注册 API 的 Controller 和 Service`
6. 观察切换到 development Profile，生成代码并写入 workspace

### 场景 B：代码审查

> 体验 AI 代码审查能力

1. 在 workspace 中创建一个有问题的代码文件（如缺少错误处理、硬编码配置）
2. 输入：`请审查这个文件的代码质量`
3. 观察 AI 分析代码并给出改进建议
4. 尝试：`帮我修复你发现的问题`

### 场景 C：新人上手

> 体验利用知识库加速新人理解系统

1. 输入：`搜索知识库中关于 Spring Boot 模式的文档`
2. 观察 AI 调用 search_knowledge 工具
3. 输入：`查询数据库中有哪些表`
4. 观察 AI 调用 query_schema 工具
5. 输入：`解释一下这个系统的服务依赖关系`

### 场景 D：测试编写

> 体验 AI 辅助测试

1. 输入：`@测试 为以下 Kotlin 函数编写单元测试`（粘贴一段代码）
2. 观察 AI 切换到 testing Profile
3. 查看生成的测试代码是否覆盖边界条件
4. 继续：`补充异常场景的测试`

### 场景 E：多模型对比

> 体验不同模型的回答差异

1. 选择一个中等复杂度的问题（如 "设计一个消息队列的重试策略"）
2. 用 Claude Sonnet 回答一次，记录结果
3. 切换到 Gemini Flash，用相同问题再问一次
4. 如果有 DashScope Key，再切换到 Qwen Plus 对比
5. 比较三个模型的回答质量、详细程度、代码质量

---

## 8. 知识库与 MCP 工具

### 知识库

平台内置知识库包含 13 篇文档，涵盖：
- Profile 定义文档
- 编码规范（Java/Kotlin/Spring Boot）
- 架构决策记录（ADR）
- 运维 Runbook

**使用方式**：
- 对话中直接说 "搜索知识库中关于 XXX 的内容"
- 或使用 `@` 上下文附加：输入框中按 `@`，选择 **Knowledge** 类别

### 9 个 MCP 工具

| 工具 | 说明 | 示例触发语 |
|------|------|-----------|
| `search_knowledge` | 搜索知识库文档 | "搜索关于 API 设计的文档" |
| `read_file` | 读取知识库文件全文 | "读取 kotlin-conventions 文档" |
| `get_service_info` | 获取服务信息 | "查看服务依赖关系" |
| `query_schema` | 查询数据库 Schema | "查询数据库中的表结构" |
| `list_baselines` | 列出可用底线脚本 | "有哪些代码检查规则" |
| `run_baseline` | 运行底线脚本 | "运行代码风格检查" |
| `workspace_write_file` | 写文件到 Workspace | "帮我创建一个 Controller 文件" |
| `workspace_read_file` | 读取 Workspace 文件 | "读取 pom.xml 的内容" |
| `workspace_list_files` | 列出 Workspace 文件 | "看看项目里有哪些文件" |
| `workspace_compile` | 编译/语法检查 Workspace 代码 | "编译检查当前项目" |
| `workspace_test` | 分析 Workspace 中的测试文件 | "运行测试分析" |

### @ 上下文附加

在输入框中输入 `@` 触发上下文选择器：

| 类别 | 说明 |
|------|------|
| Files | 当前 Workspace 中的文件 |
| Knowledge | 知识库文档 |
| Schema | 数据库 Schema |
| Services | 服务依赖信息 |

---

## 9. 故障排除 FAQ

### Q1: Docker 启动失败，backend 容器反复重启

**排查**：
```bash
docker compose -f docker-compose.trial.yml logs backend
```
**常见原因**：
- JDK 版本不对 → 确认 `java -version` 显示 21+
- bootJar 未构建 → 先执行 `./gradlew :web-ide:backend:bootJar -x test`
- keycloak 未就绪 → backend 依赖 keycloak healthy，等待 30s 后重试

### Q2: 端口 9000 被占用

**排查**：
```bash
lsof -i :9000
kill -9 <PID>
```

### Q3: 前端页面白屏

**排查**：
```bash
docker compose -f docker-compose.trial.yml logs frontend
```
**常见原因**：
- 前端未构建 → 先执行 `cd web-ide/frontend && npm install && npm run build`
- nginx 配置问题 → 检查 `nginx-trial.conf` 是否存在

### Q4: API Key 配置了但模型不可用

**排查**：
```bash
# 检查环境变量是否透传到 backend 容器
docker compose -f docker-compose.trial.yml exec backend env | grep -E "ANTHROPIC|GEMINI|DASHSCOPE|AWS"
```
**常见原因**：
- .env 文件位置不对 → 应放在 `infrastructure/docker/.env`
- Key 有多余空格或引号 → 确认格式 `ANTHROPIC_API_KEY=sk-ant-...`（无引号）

### Q5: 对话发送后无响应 / WebSocket 断连

**排查**：
```bash
docker compose -f docker-compose.trial.yml logs nginx
docker compose -f docker-compose.trial.yml logs backend
```
**常见原因**：
- Nginx 未正确代理 WebSocket → 检查 `/ws/` 路由配置
- backend 内存不足 → Docker 分配 ≥ 8GB 内存
- API Key 无效 → backend 日志会显示 401 错误

### Q6: MCP 工具调用报错

**排查**：
```bash
curl -s http://localhost:8081/health/live   # knowledge-mcp
curl -s http://localhost:8082/health/live   # database-mcp
```
**常见原因**：
- MCP Server 未启动 → 检查容器状态
- knowledge-base 目录未挂载 → 检查 docker-compose 中的 volumes

### Q7: 模型切换后回复质量下降

**说明**：不同模型的能力差异较大。建议：
- 复杂代码生成 → Claude Opus / Sonnet 或 Gemini Pro
- 简单问答 → Claude Haiku / Gemini Flash Lite / Qwen Turbo
- 长文档分析 → Gemini（1M 上下文）或 Qwen Long

### Q8: Rate Limit 限流

**症状**：连续使用后返回 429 错误
**解决**：
- 等待 1-2 分钟后重试
- 切换到其他 Provider 的模型继续使用
- 使用低费用等级模型（Haiku / Flash Lite / Turbo）减少消耗

### Q9: H2 数据库数据丢失

**说明**：试用模式使用 H2 内存数据库，**重启容器后数据会丢失**。这是已知设计，不是 Bug。Workspace 中的文件同样不持久化。

### Q10: 构建时间过长

**建议**：
- 首次构建需要下载依赖，耗时 3-5 分钟属正常
- 后续启动（不 rebuild）只需 < 30 秒
- 避免 `--no-cache` 除非遇到构建缓存问题
- 确保网络可访问 Maven Central 和 npm registry

### Q11: Keycloak 启动慢

**说明**：Keycloak 首次启动需要 30-60 秒导入 realm 配置，此期间 backend 会等待。这是正常行为。

### Q12: Profile 切换不准确

**建议**：
- 使用 `@` 标签强制指定 Profile（如 `@开发`、`@测试`）
- 关键词越明确，自动识别越准确
- 观察 Profile Badge 的置信度，低于 50% 时建议手动指定

---

## 10. HITL 审批流程（Phase 3 新增）

Forge AI 不再是"一键到底"的自动化工具——每个 Profile 在关键节点会**暂停并等待你的审批**。

### 工作原理

| Profile | 暂停点 | 触发条件 |
|---------|--------|---------|
| **planning** | PRD 确认 | AI 输出完整 PRD 后暂停 |
| **design** | 架构评审 | AI 输出架构设计后暂停 |
| **development** | 代码审查 | AI 调用 workspace_write_file 后暂停 |
| **testing** | 报告确认 | AI 输出测试报告后暂停 |
| **ops** | 发布审批 | AI 输出部署计划后暂停 |

### 审批操作

暂停后会出现橙色边框的审批面板，提供 3 种操作：

- **✅ 批准继续** — AI 继续执行后续步骤
- **❌ 拒绝停止** — AI 终止当前任务，输出已完成内容的总结
- **✏️ 修改指令** — 输入补充指示（如"增加参数校验"），AI 从头重新执行

### 超时机制

如果 5 分钟内无操作，AI 会自动继续执行（等效于批准）。审批面板显示倒计时。

### 断线恢复

刷新页面或 WebSocket 断开重连后，如果有未处理的审批请求，面板会自动恢复。

---

## 11. 编译/测试管道（Phase 3 新增）

Development Profile 现在支持完整的交付管道：**编码 → 编译 → 底线检查 → 测试 → HITL 审批 → 总结**。

### 新增工具

| 工具 | 说明 | 触发方式 |
|------|------|---------|
| `workspace_compile` | 对 workspace 中的代码进行语法检查 | AI 写完代码后自动调用，或输入"编译检查" |
| `workspace_test` | 分析 workspace 中的测试文件和覆盖度 | AI 写完测试后自动调用，或输入"运行测试" |

### 标准交付流程

```
1. 编码 → workspace_write_file（写入代码文件）
2. 编译 → workspace_compile（语法检查）
3. 底线 → 自动运行 code-style + security 检查
4. 测试 → workspace_test（测试分析）
5. [HITL] → 暂停，等待用户审查
6. 总结 → 输出执行报告（文件清单 + 编译结果 + 测试结果 + 底线结果）
```

> 如果编译失败，AI 会自动修复并重新编译，循环直到通过或达到最大轮次（8 轮）。

---

## 12. 质量度量面板（Phase 3 新增）

### 访问方式

在 AI Chat 侧边栏顶部，点击「质量面板」Tab。

### 面板内容

| 区域 | 说明 |
|------|------|
| **统计卡片**（3 列） | 总会话数、平均耗时、HITL 审批数 |
| **HITL 审批分布** | 批准/拒绝/修改/超时的分布徽标 |
| **Profile 使用** | 各 Profile 的执行次数和平均耗时 |
| **工具调用排行** | Top 10 工具调用的横向柱状图 |
| **7 日趋势** | 每日会话数柱状图 |
| **执行记录表** | 最近 10 条执行记录（时间、Profile、耗时） |

### Dashboard API

| 端点 | 说明 |
|------|------|
| `GET /api/dashboard/metrics` | 聚合统计（Profile、工具、HITL） |
| `GET /api/dashboard/executions?limit=20` | 最近执行记录 |
| `GET /api/dashboard/trends?days=7` | 7 日趋势数据 |

---

## 13. 活动日志（Phase 3 新增）

### 实时可见的执行过程

发送消息后，OODA 指示器现在增强显示：

```
👁 Observe → 🧭 Orient → 🧠 Decide → ⚡ Act (Turn 3/8 · workspace_write_file) → ✅ Done
```

- **Turn X/8**：当前 agentic loop 轮次（最大 8 轮）
- **工具名**：当前正在执行的工具

### 活动日志面板

OODA 指示器下方有可折叠的「活动日志」：
- 实时显示 AI 执行的每个步骤（解析意图、路由 Profile、工具调用、底线检查等）
- 最近 50 条记录，最新在底部
- 默认折叠，点击展开

---

## 14. 已知限制

| 限制 | 说明 | 影响 |
|------|------|------|
| **数据不持久** | H2 内存数据库 + 临时 Workspace，重启后丢失 | 重要代码请手动保存到本地 |
| **单用户** | 不支持多用户并发访问 | 每人独立部署一套环境 |
| **仅本地** | 绑定 localhost:9000，不支持远程访问 | 需在本机运行 |
| **底线脚本受限** | Docker Alpine 环境无 bash，run_baseline 工具受限 | 底线检查依赖 CI 环境 |
| **认证关闭** | 试用模式 `FORGE_SECURITY_ENABLED=false` | 无登录验证，仅限内网使用 |
| **Workspace 文件无版本** | 文件操作无 undo/版本历史 | AI 覆写文件无法回退 |
| **SSE 格式** | `data:{"type":"..."}` 冒号后无空格 | 部分 HTTP 客户端可能解析异常 |

---

## 停止与清理

```bash
cd infrastructure/docker

# 停止所有容器
docker compose -f docker-compose.trial.yml down

# 停止并删除构建镜像
docker compose -f docker-compose.trial.yml down --rmi local

# 停止并清除所有数据卷
docker compose -f docker-compose.trial.yml down -v --rmi local
```

---

## 反馈

请使用 `docs/trial-feedback-template.md` 模板记录试用反馈，通过以下渠道提交：
- GitHub Issue：forge-platform 仓库
- Slack 频道：#forge-trial-feedback

> 你的每一条反馈都将直接影响平台迭代方向。感谢参与试用！
