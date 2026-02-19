# Phase 1.6 端到端验收测试 — 全功能覆盖

> 测试前提：`docker compose -f docker-compose.trial.yml --env-file .env.trial up --build` 已启动（含 4 容器：backend, frontend, nginx, keycloak）
> 访问地址：http://localhost:9000
> Keycloak 管理后台：http://localhost:8180
> 测试人：人工验收
> 覆盖范围：Phase 0~1.5 全部功能 + Phase 1.6 新增功能
>
> 本文档在 Phase 0~1.5 验收用例（59 个）基础上扩展，新增 Phase 1.6 专属功能验收用例。
> Phase 1.6 新增功能：Keycloak SSO、AI → Workspace 交付闭环、Context Picker 实连、代码块 Apply 按钮、FileExplorer CRUD、未保存标记 + 自动保存、System Prompt 交付指导、知识库 5 篇新文档。

---

## 场景 1：新人入职第一天 — 探索平台

> 模拟一个刚入职的开发者，不看任何文档，凭直觉使用平台。

### TC-1.1 首页能否给我方向感

**操作**：打开 http://localhost:9000

**预期**：
- [ ] 页面加载成功（HTTP 200），看到 Dashboard
- [ ] 有 "Quick Actions" 区域，至少包含创建 Workspace、搜索知识库
- [ ] 有 "Recent Projects" 区域（可能为空，但区域存在）
- [ ] 有 Activity Feed 区域
- [ ] 左侧 Sidebar 可见，包含 Dashboard / Workspaces / Knowledge / AI Chat 导航项

### TC-1.2 侧边栏导航是否完整

**操作**：依次点击 Sidebar 中的每个导航项

**预期**：
- [ ] Dashboard → `/` — 回到首页
- [ ] Workspaces → 进入工作区列表页面
- [ ] Knowledge → `/knowledge` — 进入知识库页面
- [ ] Workflows → `/workflows` — 进入工作流页面（Developer 角色可见）
- [ ] AI Chat → 进入 AI 对话页面
- [ ] Integrations → 进入集成页面（Developer 角色可见）
- [ ] Infrastructure → 进入基础设施页面（Developer 角色可见）
- [ ] 每个页面都能正常渲染，无白屏或 JS 报错（打开浏览器 Console 检查）

### TC-1.3 创建 Workspace 进入 IDE

**操作**：
1. 点击 "Create Workspace" 或导航到 Workspace 页面
2. 填写名称（如 "my-first-project"），提交

**预期**：
- [ ] 进入 `/workspace/{id}` 页面
- [ ] 三面板布局可见：左侧文件树、中间编辑器、右侧 AI 聊天
- [ ] 右侧 AI Chat 显示 "Start a conversation" 引导文案
- [ ] 底部输入框可见，placeholder 为 "Ask anything... (@ for context)"

---

## 场景 2：开发者日常 — 用 AI 写代码

> 模拟一个 Kotlin/Spring Boot 开发者的真实工作流程。

### TC-2.1 第一次对话 — 不带标签，自然提问

**操作**：在 AI Chat 输入框输入：
```
帮我写一个 Spring Boot 的 REST Controller，实现用户注册功能，要求有参数校验
```

**预期**：
- [ ] OODA 指示器出现，从 Observe → Orient 依次流转
- [ ] Profile Badge 出现，显示 `development`（因为包含"实现"关键词）
- [ ] Badge 上有黄色或绿色置信度圆点
- [ ] Badge 显示 routing reason（如 `keyword '实现'`）
- [ ] Badge 显示加载的 Skills（如 `kotlin-conventions, spring-boot-patterns +N`）
- [ ] OODA 流转到 Decide，然后 Claude 开始流式输出
- [ ] 回复中包含 Kotlin 代码块（```kotlin）
- [ ] 代码块有语法高亮 + "Copy" 按钮
- [ ] 回复体现 Spring Boot 专业知识（如 @Valid, @RestController, @RequestBody）
- [ ] 回复体现 Kotlin 规范（如 data class, val 而非 var）
- [ ] 流式结束后，OODA 指示器消失
- [ ] 消息气泡有时间戳

### TC-2.2 追问 — 验证会话连续性

**操作**：在同一会话中继续输入：
```
给这个 Controller 加上单元测试
```

**预期**：
- [ ] Claude 知道上一条消息的上下文（指的是刚才的用户注册 Controller）
- [ ] 生成的测试代码引用了上面的 Controller 类名和方法名
- [ ] OODA 指示器再次出现并流转
- [ ] Profile 可能还是 development（因为"测试"关键词可能路由到 testing）
- [ ] 无论路由到哪个 profile，回复都应包含测试代码

### TC-2.3 显式切换 Profile — @设计 标签

**操作**：在同一会话中输入：
```
@设计 这个用户注册服务应该怎么设计架构？需要考虑哪些非功能需求？
```

**预期**：
- [ ] Profile Badge 切换为 `design`（不再是 development）
- [ ] 置信度圆点为绿色（confidence = 1.0，因为是显式标签）
- [ ] routing reason 显示 `'@设计'`
- [ ] 加载的 Skills 变化（design profile 的 skills 和 development 不同）
- [ ] 回复偏向架构层面（如提到模块拆分、高可用、安全性、可扩展性），而非直接写代码

### TC-2.4 显式切换 Profile — @测试 标签

**操作**：
```
@测试 针对用户注册功能，设计完整的测试策略，包括单元测试、集成测试和端到端测试
```

**预期**：
- [ ] Profile Badge 切换为 `testing`
- [ ] 绿色圆点（显式标签）
- [ ] 回复偏向测试策略（测试金字塔、边界条件、mock 策略等）

### TC-2.5 英文关键词路由

**操作**：
```
deploy this service to kubernetes with rolling update strategy
```

**预期**：
- [ ] Profile Badge 显示 `ops`（"deploy" + "kubernetes" 是运维关键词）
- [ ] 回复涉及 K8s deployment yaml、rolling update 配置等

---

## 场景 3：AI 工具调用 — MCP 实连验证

> 验证 Claude 通过 MCP 工具获取真实数据的能力。

### TC-3.1 触发知识搜索工具调用

**操作**：在 AI Chat 中输入：
```
帮我搜索知识库里关于 Spring Boot 的文档
```

**预期**：
- [ ] OODA 流转到 Act 阶段（表示正在执行工具）
- [ ] 出现 Tool Call 卡片，工具名为 `search_knowledge`
- [ ] Tool Call 卡片显示输入参数（如 `{"query": "Spring Boot"}`）
- [ ] Tool Call 完成后卡片状态变为绿色 ✓
- [ ] 展开 Tool Call 详情，能看到返回的知识库文档内容
- [ ] Claude 基于工具返回结果给出回复
- [ ] OODA 完成从 Act 回到 Decide → Complete 的完整流转

### TC-3.2 触发底线检查工具调用

**操作**：
```
帮我运行 code-quality 底线检查
```

**预期**：
- [ ] 出现 Tool Call 卡片，工具名为 `run_baseline` 或 `list_baselines`
- [ ] 工具执行有结果返回（pass/fail 或可用底线列表）
- [ ] Claude 对底线检查结果进行解读

### TC-3.3 多轮工具调用（Agentic Loop）

**操作**：
```
先搜索知识库里关于微服务架构的文档，然后根据文档内容帮我设计一个服务拆分方案
```

**预期**：
- [ ] Claude 先发起 `search_knowledge` 工具调用（Turn 1）
- [ ] 收到工具结果后，Claude 继续思考并给出回复（Turn 2）
- [ ] OODA 指示器流转：Observe → Orient → Decide → Act（工具调用）→ Decide → Complete
- [ ] 最终回复引用了工具返回的知识库内容

### TC-3.4 工具调用失败的优雅降级

**操作**：发送一条可能触发工具调用但工具可能出错的消息（如搜索一个不存在的知识领域）

**预期**：
- [ ] Tool Call 卡片状态显示为红色错误（如果工具报错）
- [ ] Claude 不崩溃，继续给出回复（可能是 fallback 回复或承认工具调用失败）
- [ ] 整个对话流程不中断

---

## 场景 4：代码审查 — 贴代码给 AI 看

> 模拟开发者把代码贴给 AI 做 code review。

### TC-4.1 贴一段有问题的代码

**操作**：
```
帮我 review 下面这段代码，找出潜在问题：

@Service
class UserService(val userRepo: UserRepository) {
    fun register(name: String, email: String): User {
        val user = User(name = name, email = email, password = "123456")
        userRepo.save(user)
        return user
    }

    fun findAll(): List<User> {
        return userRepo.findAll().toList()
    }

    fun delete(id: Long) {
        userRepo.deleteById(id)
    }
}
```

**预期**：
- [ ] Profile 路由到 development（"review" + "代码" 关键词）
- [ ] Claude 能识别出至少以下问题：
  - 明文密码 "123456"
  - 没有参数校验
  - 没有异常处理
  - findAll 可能有性能问题（无分页）
  - delete 没有权限检查
- [ ] 回复格式清晰，有问题分类和改进建议
- [ ] 代码块中给出修复示例

### TC-4.2 长回复的 Markdown 渲染质量

**操作**：观察 TC-4.1 的回复

**预期**：
- [ ] 标题（#, ##, ###）正确渲染为不同大小
- [ ] 加粗（**text**）正确渲染
- [ ] 列表（- item）正确渲染为带圆点的列表
- [ ] 行内代码（`code`）有灰色背景
- [ ] 代码块有语法高亮、语言标签、Copy 按钮
- [ ] 空行正确分隔段落

---

## 场景 5：知识库探索

> 模拟用户在做技术决策前，先查阅知识库。

### TC-5.1 知识库搜索

**操作**：导航到 Knowledge 页面（`/knowledge`）

**预期**：
- [ ] 四个标签页可见：Docs / Architecture / Services / APIs
- [ ] 默认在 Docs 标签页
- [ ] 左侧有搜索框

### TC-5.2 文档搜索与查看

**操作**：在搜索框输入 "Spring" 或任意关键词

**预期**：
- [ ] 搜索结果列表出现（来自 `/api/knowledge/search` 接口）
- [ ] 每个结果有标题、类型标签、摘要
- [ ] 点击一个结果，右侧显示文档详情

### TC-5.3 架构图查看

**操作**：切换到 Architecture 标签页

**预期**：
- [ ] 有架构图列表（可能为 mock 数据）
- [ ] 有缩放控件（放大/缩小/重置）

### TC-5.4 服务依赖图

**操作**：切换到 Services 标签页

**预期**：
- [ ] 有服务节点图（ReactFlow 渲染）
- [ ] 节点有颜色区分（健康/退化/故障）

### TC-5.5 API Explorer

**操作**：切换到 APIs 标签页

**预期**：
- [ ] API 目录列表可见
- [ ] 每个 API 有方法标签（GET/POST 等）颜色区分
- [ ] 点击展开能看到参数和响应示例

---

## 场景 6：AI 对话高级功能

> 验证对话的各种交互细节。

### TC-6.1 新建对话

**操作**：
1. 先发几条消息，建立对话历史
2. 点击右上角 ↻ (RotateCcw) 按钮

**预期**：
- [ ] 所有消息清除
- [ ] 回到 "Start a conversation" 状态
- [ ] 输入新消息后，Claude 不记得之前的对话

### TC-6.2 中断流式响应

**操作**：
1. 发送一条会产生长回复的消息（如 "详细解释微服务架构的12种设计模式"）
2. 在 Claude 还在输出时，点击红色 Stop 按钮

**预期**：
- [ ] 流式输出立即停止
- [ ] 已输出的内容保留在消息气泡中
- [ ] OODA 指示器消失
- [ ] 输入框恢复可用状态
- [ ] Stop 按钮变回 Send 按钮

### TC-6.3 @ 上下文弹出

**操作**：在输入框中输入 `@`

**预期**：
- [ ] Context Picker 弹出
- [ ] 有 4 个类型标签：Files / Knowledge / Schema / Services
- [ ] 有搜索框（自动获取焦点）
- [ ] 按 Escape 关闭 Context Picker

### TC-6.4 附加上下文后发送

**操作**：
1. 输入 `@`，在 Context Picker 中选择一个 Knowledge 项
2. 观察输入框上方出现的 context chip
3. 输入 "解释这个文档的核心要点"
4. 发送

**预期**：
- [ ] 选择后，输入框上方出现 `@{label}` 的蓝色 chip
- [ ] chip 有 x 按钮可移除
- [ ] 发送后，用户消息气泡上方显示 context chip 标签
- [ ] Claude 的回复引用了附加的上下文内容

### TC-6.5 多行输入

**操作**：按 Shift+Enter 换行输入多行内容，然后 Enter 发送

**预期**：
- [ ] Shift+Enter 换行，输入框高度自动增长（最多 5 行）
- [ ] 单独 Enter 触发发送
- [ ] 多行内容正确发送和显示

---

## 场景 7：全部 5 个 Profile 轮转

> 验证所有 Profile 的路由和 Skill 加载。

### TC-7.1 规划 Profile

**操作**：`@规划 写一个用户管理模块的 PRD，包含用户故事和验收标准`

**预期**：
- [ ] Profile: `planning`，绿色圆点
- [ ] 回复包含用户故事格式（As a... I want... So that...）
- [ ] 回复包含验收标准

### TC-7.2 设计 Profile

**操作**：`@设计 设计一个支付系统的数据库 schema，支持多币种和退款`

**预期**：
- [ ] Profile: `design`，绿色圆点
- [ ] Skills 包含 api-design 或 database-patterns
- [ ] 回复包含 ER 图或表结构设计
- [ ] 考虑了多币种和退款的设计

### TC-7.3 开发 Profile

**操作**：`@开发 用 Kotlin + Spring Boot 实现上面的支付服务`

**预期**：
- [ ] Profile: `development`，绿色圆点
- [ ] Skills 最多（17 个），包含 kotlin-conventions, spring-boot-patterns
- [ ] 回复包含完整的 Kotlin 代码

### TC-7.4 测试 Profile

**操作**：`@测试 为支付服务写集成测试，覆盖正常支付和退款失败场景`

**预期**：
- [ ] Profile: `testing`，绿色圆点
- [ ] 回复包含测试代码和测试策略

### TC-7.5 运维 Profile

**操作**：`@运维 支付服务上线前需要做哪些检查？给出部署 checklist`

**预期**：
- [ ] Profile: `ops`，绿色圆点
- [ ] 回复包含部署检查清单（健康检查、监控、回滚策略等）

---

## 场景 8：边界和异常

### TC-8.1 空消息

**操作**：不输入任何内容，直接按 Enter 或点击 Send

**预期**：
- [ ] 不发送请求
- [ ] Send 按钮为 disabled 状态（opacity-50）

### TC-8.2 超长消息

**操作**：粘贴一段超过 1000 字的文本发送

**预期**：
- [ ] 消息正常发送
- [ ] Claude 正常回复
- [ ] 用户气泡正确显示长文本

### TC-8.3 连续快速发送

**操作**：发送一条消息后，在 Claude 还在回复时尝试再次发送

**预期**：
- [ ] 输入框在流式期间为 disabled 状态
- [ ] 无法重复发送

### TC-8.4 特殊字符

**操作**：发送包含特殊字符的消息：`<script>alert('xss')</script> and "quotes" & <tags>`

**预期**：
- [ ] 内容正常显示为文本（不执行脚本）
- [ ] 无 XSS 漏洞

---

## 场景 9：API 健康度 + Skills/Profiles 验证

> 通过浏览器或 curl 直接访问 API 验证后端状态。

### TC-9.1 Skills 加载（32 Skills）

**操作**：浏览器访问 http://localhost:9000/api/chat/skills

**预期**：
- [ ] 返回 JSON 数组
- [ ] 包含 **32** 个 skills
- [ ] 每个 skill 有 name, description 字段
- [ ] 包含 3 个来自 Phase 1.5 的 skill：`deployment-readiness-check`、`design-baseline-guardian`、`environment-parity`

### TC-9.2 Profiles 加载（5 Profiles）

**操作**：浏览器访问 http://localhost:9000/api/chat/profiles

**预期**：
- [ ] 返回 5 个 profiles
- [ ] 包含 planning, design, development, testing, ops
- [ ] development profile 的 skills 列表最长

### TC-9.3 MCP Tools 列表（9 工具）

**操作**：浏览器访问 http://localhost:9000/api/mcp/tools

**预期**：
- [ ] 返回 JSON 数组
- [ ] 包含 **9** 个工具：`search_knowledge`、`read_file`、`query_schema`、`run_baseline`、`list_baselines`、`get_service_info`、`workspace_write_file`、`workspace_read_file`、`workspace_list_files`
- [ ] 每个工具有 name, description, inputSchema
- [ ] 3 个 workspace 工具的 inputSchema 包含 workspaceId 参数

### TC-9.4 Knowledge 搜索

**操作**：浏览器访问 http://localhost:9000/api/knowledge/search

**预期**：
- [ ] 返回 JSON 数组
- [ ] 包含知识库文档

---

## 场景 10：Actuator + 度量指标验证

> 验证 Spring Boot Actuator 和 Forge 自定义 Micrometer 指标。

### TC-10.1 Actuator 健康端点

**操作**：
```bash
curl http://localhost:9000/actuator/health
```

**预期**：
- [ ] 返回 JSON，`status` 为 `UP`
- [ ] 包含 `db` 和 `diskSpace` 组件状态

### TC-10.2 Metrics 列表

**操作**：
```bash
curl http://localhost:9000/actuator/metrics
```

**预期**：
- [ ] 返回 JSON，`names` 数组包含大量指标名
- [ ] 列表中包含 Forge 自定义指标：
  - [ ] `forge.profile.route`
  - [ ] `forge.tool.calls`
  - [ ] `forge.ooda.phases`
  - [ ] `forge.message.duration`
  - [ ] `forge.turn.duration`
  - [ ] `forge.tool.duration`

### TC-10.3 Profile 路由指标验证

**操作**：
1. 先通过 AI Chat 发送几条消息（触发 Profile 路由）
2. 然后访问：
```bash
curl http://localhost:9000/actuator/metrics/forge.profile.route
```

**预期**：
- [ ] 返回指标详情 JSON
- [ ] `measurements` 中 `COUNT` > 0
- [ ] `availableTags` 包含 `profile` 和 `method` 两个 tag

### TC-10.4 OODA 阶段指标验证

**操作**：
```bash
curl http://localhost:9000/actuator/metrics/forge.ooda.phases
```

**预期**：
- [ ] 返回指标详情
- [ ] `availableTags` 包含 `phase` tag
- [ ] phase 值包含 `observe`、`orient`、`decide`、`complete`（如果之前有工具调用还包含 `act`）

### TC-10.5 工具调用指标验证

**操作**（在 TC-3.1 之后执行）：
```bash
curl http://localhost:9000/actuator/metrics/forge.tool.calls
```

**预期**：
- [ ] 返回指标详情
- [ ] `availableTags` 包含 `tool` 和 `status` 两个 tag
- [ ] tool 值包含之前调用过的工具名（如 `search_knowledge`）

### TC-10.6 Prometheus 格式导出

**操作**：
```bash
curl http://localhost:9000/actuator/prometheus | grep forge_
```

**预期**：
- [ ] 输出包含 `forge_profile_route_total` 行
- [ ] 输出包含 `forge_ooda_phases_total` 行
- [ ] 输出包含 `forge_tool_calls_total` 行
- [ ] 输出包含 `forge_message_duration_seconds` 行
- [ ] 每行有正确的 tag（label）格式

### TC-10.7 Turn Duration 和 Tool Duration

**操作**（在 TC-3.1 之后执行）：
```bash
curl http://localhost:9000/actuator/metrics/forge.turn.duration
curl http://localhost:9000/actuator/metrics/forge.tool.duration
```

**预期**：
- [ ] `forge.turn.duration` 有 `turn` tag，COUNT > 0
- [ ] `forge.tool.duration` 有 `tool` tag，COUNT > 0（如果之前有工具调用）

---

## 场景 11：MCP 工具直接调用验证

> 通过 REST API 直接调用 MCP 工具，验证后端 McpProxyService 实连。

### TC-11.1 search_knowledge 直接调用

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"search_knowledge","arguments":{"query":"Spring Boot"}}'
```

**预期**：
- [ ] 返回 JSON 响应
- [ ] `content` 数组非空（包含搜索结果）
- [ ] `isError` 为 false

### TC-11.2 list_baselines 直接调用

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"list_baselines","arguments":{}}'
```

**预期**：
- [ ] 返回 JSON 响应
- [ ] `content` 包含可用的底线脚本列表
- [ ] 列表中有 `code-quality` 等底线名

### TC-11.3 run_baseline 直接调用

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"run_baseline","arguments":{"baseline":"code-quality"}}'
```

**预期**：
- [ ] 返回 JSON 响应
- [ ] `content` 包含底线执行结果（pass/fail + 详情）
- [ ] `isError` 为 false（即使底线检查本身 fail，接口不报错）

### TC-11.4 get_service_info 直接调用

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"get_service_info","arguments":{"service":"order-service"}}'
```

**预期**：
- [ ] 返回 JSON 响应
- [ ] `content` 包含服务信息（可能是 mock 数据或 MCP Server 返回的真实数据）

### TC-11.5 不存在的工具

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"nonexistent_tool","arguments":{}}'
```

**预期**：
- [ ] 返回错误响应（HTTP 400 或 `isError: true`）
- [ ] 不导致服务崩溃

---

## 场景 12：工作流编辑器

### TC-12.1 打开工作流页面

**操作**：导航到 `/workflows`

**预期**：
- [ ] 页面正常加载
- [ ] 左侧有节点面板（Node Palette）
- [ ] 中间有画布区域（带网格背景）
- [ ] 有 "New" / "Save" / "Run" 按钮

### TC-12.2 拖放节点

**操作**：从 Node Palette 拖一个 "Trigger" 节点和一个 "Action" 节点到画布

**预期**：
- [ ] 节点出现在画布上
- [ ] 可以拖动节点重新定位
- [ ] 节点有颜色区分

### TC-12.3 连线

**操作**：从 Trigger 节点的输出端口拖线到 Action 节点的输入端口

**预期**：
- [ ] 连线成功，箭头指向 Action
- [ ] 连线可视化清晰

---

## 场景 13：agent-eval 验证

> 验证评估框架可以在两种模式下运行。在开发机上执行（非 Docker 内）。

### TC-13.1 无 API Key 的结构验证模式

**操作**：
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home
./gradlew :agent-eval:run
```

**预期**：
- [ ] 输出 `WARNING: ANTHROPIC_API_KEY not set, running structure validation only`
- [ ] 扫描 `eval-sets/` 目录下的 YAML 文件
- [ ] 输出 `Evaluation complete: X passed, Y failed out of Z total`
- [ ] 生成报告到 `agent-eval/build/eval-reports/`

### TC-13.2 有 API Key 的真实评估模式

**操作**：
```bash
ANTHROPIC_API_KEY=sk-ant-xxx ./gradlew :agent-eval:run
```

**预期**：
- [ ] 输出 `Using Claude API for real evaluation`
- [ ] 每个场景调用 Claude API（日志中有 model call 记录）
- [ ] 断言基于 Claude 真实输出进行评估（不再是 "structure valid"）
- [ ] 输出包含 pass/fail 和评分

### TC-13.3 agent-eval 单元测试

**操作**：
```bash
./gradlew :agent-eval:test
```

**预期**：
- [ ] 18 tests, 0 failures
- [ ] 所有断言类型测试通过（contains, not_contains, matches_pattern, json_schema, semantic_similarity）

---

## 场景 14：全量单元测试回归

> 确保所有模块的单元测试通过。

### TC-14.1 全量测试

**操作**：
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home
./gradlew :web-ide:backend:test :adapters:model-adapter:test :agent-eval:test
```

**预期**：
- [ ] BUILD SUCCESSFUL
- [ ] web-ide/backend: 101+ tests, 0 failures（含 workspace tool tests + ContextControllerTest）
- [ ] adapters/model-adapter: 11 tests, 0 failures
- [ ] agent-eval: 18 tests, 0 failures
- [ ] 总计 130+ tests, 0 failures

---

## 场景 15：Docker 部署完整性

> 验证 Docker 4 容器部署的整体健康。

### TC-15.1 容器状态

**操作**：
```bash
docker compose -f infrastructure/docker/docker-compose.trial.yml ps
```

**预期**：
- [ ] 4 个容器全部 running：backend, frontend, nginx, keycloak
- [ ] backend 容器 health status 为 `healthy`
- [ ] keycloak 容器 health status 为 `healthy`

### TC-15.2 Docker 日志验证

**操作**：
```bash
docker compose -f infrastructure/docker/docker-compose.trial.yml logs backend | head -50
```

**预期**：
- [ ] 日志中有 `Skill loading complete: 32 skills, 5 profiles`
- [ ] 日志中有 `Registered workspace tools: workspace_write_file, workspace_read_file, workspace_list_files`
- [ ] 日志中有 `Started ForgeWebIdeApplication`
- [ ] 无 ERROR 级别日志

### TC-15.3 Plugins 和 Knowledge-Base 挂载

**操作**：
```bash
docker compose -f infrastructure/docker/docker-compose.trial.yml exec backend ls /plugins
docker compose -f infrastructure/docker/docker-compose.trial.yml exec backend ls /knowledge-base
```

**预期**：
- [ ] `/plugins` 包含 `forge-foundation`, `forge-superagent`, `forge-deployment`, `forge-knowledge`
- [ ] `/knowledge-base` 包含 `adr`, `api-docs`, `conventions`, `runbooks`
- [ ] `/knowledge-base` 总文件数 12+（Phase 2 的 7 + Phase 1.6 新增 5 篇）

---

## 场景 A：Keycloak SSO 认证（Phase 1.6 新增）

> 验证 Keycloak OIDC PKCE 登录、JWT 验证和登出流程。

### TC-A.1 Keycloak 管理后台可访问

**操作**：浏览器访问 http://localhost:8180

**预期**：
- [ ] Keycloak 管理后台登录页面正常显示
- [ ] 使用 admin/admin 可登录管理后台
- [ ] 左侧 realm 列表中包含 `forge` realm
- [ ] 进入 forge realm → Clients，可看到 `forge-web-ide` 客户端
- [ ] 客户端配置：Access Type = public，Valid Redirect URIs 包含 `http://localhost:9000/*`

### TC-A.2 SSO 登录流程（启用安全模式时）

**操作**：
1. 设置环境变量 `FORGE_SECURITY_ENABLED=true` 并重启服务
2. 访问 http://localhost:9000

**预期**：
- [ ] 未登录用户被重定向到 Keycloak 登录页面
- [ ] 登录页面显示 Forge 品牌或 Keycloak 默认主题
- [ ] 输入用户名/密码后（如 demo/demo），点击登录
- [ ] 成功登录后回调到 http://localhost:9000，携带 authorization code
- [ ] 前端通过 PKCE 流程交换 access_token
- [ ] 页面正常加载，Header 显示当前登录用户名

### TC-A.3 JWT Token 验证

**操作**（在 TC-A.2 登录后）：
1. 打开浏览器 DevTools → Application → Local Storage
2. 找到存储的 JWT token
3. 复制 token，用 curl 访问受保护 API：
```bash
curl -H "Authorization: Bearer <token>" http://localhost:9000/api/chat/skills
```

**预期**：
- [ ] Local Storage 中有 `access_token` 或类似键名
- [ ] Token 是标准 JWT 格式（三段 base64，以 `.` 分隔）
- [ ] 携带 Token 的 API 请求返回 200
- [ ] 不携带 Token 的请求返回 401（FORGE_SECURITY_ENABLED=true 时）

### TC-A.4 登出流程

**操作**：点击 Header 右侧的用户头像/名称，选择 "Logout"

**预期**：
- [ ] 跳转到 Keycloak 登出端点
- [ ] Local Storage 中的 token 被清除
- [ ] 回到登录页面或首页
- [ ] 再次访问受保护页面需要重新登录

---

## 场景 B：AI 交付闭环 — 代码写入 Workspace（Phase 1.6 核心）

> 验证 AI 通过 workspace 工具直接将代码写入用户 workspace，实现从"聊天展示"到"文件交付"的闭环。

### TC-B.1 AI 自动写文件到 workspace

**操作**：在 Workspace 的 AI Chat 中输入：
```
帮我创建一个 Hello World 的 Spring Boot Application.kt 文件
```

**预期**：
- [ ] OODA 流转到 Act 阶段
- [ ] 出现 Tool Call 卡片，工具名为 `workspace_write_file`
- [ ] Tool Call 参数包含 `path`（如 `src/main/kotlin/Application.kt`）和 `content`（Kotlin 源码）
- [ ] Tool Call 完成后卡片状态为绿色 ✓
- [ ] Claude 回复确认文件已写入，而非仅在聊天中展示代码
- [ ] 回复中可能仍包含代码块供用户预览，但核心交付是写文件

### TC-B.2 文件树自动刷新 + 编辑器自动打开

**操作**：观察 TC-B.1 完成后的 UI 变化

**预期**：
- [ ] 左侧 FileExplorer 自动刷新，出现新写入的文件（无需手动刷新）
- [ ] 文件树中新文件有高亮或动画提示（file_changed 事件驱动）
- [ ] 编辑器自动打开新写入的文件
- [ ] 编辑器中的内容与 AI 写入的内容一致
- [ ] 文件 tab 出现在编辑器标签栏

### TC-B.3 AI 先读取再修改已有文件

**操作**：在 AI Chat 中输入：
```
帮我修改 Application.kt，添加一个 /health 端点
```

**预期**：
- [ ] Claude 先发起 `workspace_read_file` 工具调用（读取现有内容）
- [ ] 再发起 `workspace_write_file` 工具调用（写入修改后内容）
- [ ] 两次 Tool Call 卡片按顺序出现
- [ ] 最终文件包含原有代码 + 新增的 /health 端点
- [ ] 编辑器中内容自动更新

### TC-B.4 AI 主动了解项目结构（workspace_list_files）

**操作**：在 AI Chat 中输入：
```
帮我看看当前项目里有哪些文件
```

**预期**：
- [ ] 出现 Tool Call 卡片，工具名为 `workspace_list_files`
- [ ] 工具返回当前 workspace 的文件列表
- [ ] Claude 基于文件列表给出项目结构概述
- [ ] 如果 workspace 为空，Claude 说明无文件并建议创建

### TC-B.5 代码块 Apply 按钮

**操作**：
1. 在 AI Chat 中让 AI 生成一段代码（确保回复中有代码块）
2. 观察代码块右上角的按钮

**预期**：
- [ ] 代码块右上角有 "Copy" 按钮和 "Apply" 按钮
- [ ] 点击 "Apply" 按钮
- [ ] 弹出文件路径输入或自动推断文件名
- [ ] 确认后，代码通过 `workspace_write_file` 写入 workspace
- [ ] 文件树刷新，编辑器打开该文件

---

## 场景 C：Context Picker 实连（Phase 1.6 新增）

> 验证 @ Context Picker 通过 /api/context/search 端点实连 4 个类别的真实数据。

### TC-C.1 Files 类别显示 workspace 文件列表

**操作**：
1. 确保 workspace 中有文件（可先通过场景 B 创建）
2. 在输入框输入 `@`，点击 "Files" 标签

**预期**：
- [ ] Files 类别调用 `/api/context/search?category=files&workspaceId={id}`
- [ ] 列表显示当前 workspace 中的文件
- [ ] 每个文件项显示文件名和路径
- [ ] 选择一个文件后，文件内容作为上下文附加到消息

### TC-C.2 Knowledge 类别显示知识库文档

**操作**：在 Context Picker 中点击 "Knowledge" 标签

**预期**：
- [ ] Knowledge 类别调用 `/api/context/search?category=knowledge`
- [ ] 列表显示知识库中的文档
- [ ] 包含 Phase 1.6 新增的文档（如 git-workflow、troubleshooting-guide 等）
- [ ] 选择后作为上下文附加

### TC-C.3 搜索过滤

**操作**：在 Context Picker 搜索框中输入 "git"

**预期**：
- [ ] 搜索请求发送到 `/api/context/search?category=knowledge&query=git`
- [ ] 结果过滤为包含 "git" 关键词的项
- [ ] 搜索有 debounce（不是每次按键都请求）
- [ ] 结果列表实时更新

---

## 场景 D：FileExplorer CRUD（Phase 1.6 新增）

> 验证文件树的右键菜单操作：新建、重命名、删除。

### TC-D.1 新建文件

**操作**：
1. 在 FileExplorer 空白区域或文件夹上右键
2. 选择 "New File"
3. 输入文件名（如 `test.kt`）

**预期**：
- [ ] 右键菜单出现，包含 "New File"、"New Folder"、"Rename"、"Delete" 选项
- [ ] 点击 "New File" 后出现内联输入框
- [ ] 输入文件名回车后，文件创建成功
- [ ] 新文件出现在文件树中
- [ ] 编辑器自动打开新文件（空白）

### TC-D.2 新建文件夹

**操作**：
1. 右键 → "New Folder"
2. 输入文件夹名（如 `src`）

**预期**：
- [ ] 内联输入框出现
- [ ] 输入文件夹名回车后，文件夹创建成功
- [ ] 文件夹出现在文件树中，带文件夹图标
- [ ] 文件夹可展开/折叠

### TC-D.3 重命名文件

**操作**：
1. 右键某个文件 → "Rename"
2. 修改文件名（如 `test.kt` → `Main.kt`）

**预期**：
- [ ] 文件名变为可编辑的输入框，预填当前文件名
- [ ] 修改后回车，文件重命名成功
- [ ] 文件树和编辑器 tab 都更新为新名称
- [ ] 如果文件已打开，编辑器内容不丢失

### TC-D.4 删除文件

**操作**：
1. 右键某个文件 → "Delete"

**预期**：
- [ ] 弹出确认对话框（"确定删除 xxx？"）
- [ ] 确认后，文件从文件树中消失
- [ ] 如果文件在编辑器中打开，对应 tab 关闭
- [ ] 取消则不删除

---

## 场景 E：编辑器增强 — 未保存标记 + 自动保存（Phase 1.6 新增）

> 验证文件编辑的保存状态指示和自动保存功能。

### TC-E.1 编辑后出现未保存标记

**操作**：
1. 打开一个文件
2. 在编辑器中修改内容（输入任意字符）

**预期**：
- [ ] 文件 tab 上出现蓝色圆点（unsaved indicator）
- [ ] 蓝色圆点位于文件名旁
- [ ] 继续编辑，圆点保持显示

### TC-E.2 Cmd+S 手动保存

**操作**：
1. 在有未保存标记的文件上按 Cmd+S（Mac）或 Ctrl+S（Windows/Linux）

**预期**：
- [ ] 文件保存成功（发送 API 请求）
- [ ] 蓝色圆点消失
- [ ] 编辑器内容与保存内容一致

### TC-E.3 5 秒自动保存

**操作**：
1. 打开一个文件并修改内容
2. 不手动保存，等待 5 秒以上

**预期**：
- [ ] 5 秒后蓝色圆点自动消失
- [ ] 文件自动保存成功（可通过 Network tab 观察保存请求）
- [ ] 保存后再刷新页面，内容是最新修改后的

---

## 场景 F：知识库内容升级（Phase 1.6 新增）

> 验证新增的 5 篇知识库文档。

### TC-F.1 知识库文档数量验证

**操作**：
```bash
ls knowledge-base/
find knowledge-base/ -name "*.md" | wc -l
```

**预期**：
- [ ] 知识库总文件数 12+（Phase 2 的 7 + Phase 1.6 新增 5）
- [ ] 新增文档包括：
  - [ ] `git-workflow.md`（Git 工作流规范）
  - [ ] `code-review-checklist.md`（代码审查清单）
  - [ ] `forge-mcp-tools.md`（Forge MCP 工具参考）
  - [ ] `troubleshooting-guide.md`（故障排除指南）
  - [ ] `adr/ADR-004-*.md`（架构决策记录 #4）

### TC-F.2 新增文档可搜索

**操作**：
```bash
curl "http://localhost:9000/api/knowledge/search?query=git-workflow"
curl "http://localhost:9000/api/knowledge/search?query=troubleshooting"
```

**预期**：
- [ ] git-workflow 搜索返回 git-workflow 相关文档
- [ ] troubleshooting 搜索返回 troubleshooting-guide 文档
- [ ] 搜索结果有标题和摘要

---

## 场景 G：API 健康度升级（Phase 1.6 新增）

> 验证 Phase 1.6 新增的 API 端点。

### TC-G.1 Workspace 工具 inputSchema 验证

> 工具总数（9 个）已在 TC-9.3 验证，此处聚焦 Phase 1.6 新增的 3 个 workspace 工具 inputSchema 细节。

**操作**：
```bash
curl http://localhost:9000/api/mcp/tools | python3 -c "
import sys, json
tools = json.load(sys.stdin)
for t in tools:
    if t['name'].startswith('workspace_'):
        print(json.dumps(t, indent=2, ensure_ascii=False))
"
```

**预期**：
- [ ] `workspace_write_file` 的 inputSchema 包含必需参数 `workspaceId`、`path`、`content`
- [ ] `workspace_read_file` 的 inputSchema 包含必需参数 `workspaceId`、`path`
- [ ] `workspace_list_files` 的 inputSchema 包含必需参数 `workspaceId`
- [ ] 三个工具的 description 清晰描述了用途

### TC-G.2 Context Search API

**操作**：
```bash
curl "http://localhost:9000/api/context/search?category=files&workspaceId=1"
curl "http://localhost:9000/api/context/search?category=knowledge"
curl "http://localhost:9000/api/context/search?category=schema"
curl "http://localhost:9000/api/context/search?category=services"
```

**预期**：
- [ ] 4 个类别都返回 200
- [ ] files 类别返回指定 workspace 的文件列表
- [ ] knowledge 类别返回知识库文档列表
- [ ] schema 类别返回 schema 信息
- [ ] services 类别返回服务信息

### TC-G.3 Auth API

**操作**：
```bash
curl http://localhost:9000/api/auth/me
curl http://localhost:9000/api/auth/me/jwt
```

**预期**：
- [ ] `/api/auth/me` 返回 JSON，包含 `authenticated`、`username`、`email`、`roles` 字段
- [ ] 未登录时 `authenticated` 为 `false`，`username` 为 `"anonymous"`
- [ ] `/api/auth/me/jwt` 返回 JSON，未携带 JWT 时 `authenticated` 为 `false`
- [ ] 携带有效 JWT 时返回 `authenticated: true` + `preferred_username` + `email` + `roles`

---

## 场景 H：Header 角色切换 + 导航增强（Phase 1.6 新增）

> 验证 Header 中的角色切换和导航增强功能。

### TC-H.1 角色切换影响侧边栏

**操作**：
1. 在 Header 的角色选择器中切换角色（如从 Developer 切换到 Designer）

**预期**：
- [ ] Header 显示当前角色名称
- [ ] 切换角色后，侧边栏的导航项可能变化（不同角色看到不同菜单）
- [ ] 页面内容区域根据角色适配

### TC-H.2 命令面板打开/关闭

**操作**：按 Cmd+K（Mac）或 Ctrl+K（Windows/Linux）

**预期**：
- [ ] 命令面板（Command Palette）弹出
- [ ] 有搜索输入框
- [ ] 可搜索页面、命令、文件等
- [ ] 按 Escape 关闭

### TC-H.3 侧边栏折叠/展开

**操作**：点击侧边栏折叠按钮（如 hamburger 图标或 ← 箭头）

**预期**：
- [ ] 侧边栏折叠为图标模式（只显示图标，不显示文字）
- [ ] 主内容区域宽度扩大
- [ ] 再次点击恢复展开状态

---

## 场景 I：Docker 4 容器部署完整性（Phase 1.6 新增）

> 专门验证 Phase 1.6 的 4 容器架构完整性。

### TC-I.1 容器间网络连通性

> 4 容器 running 状态已在 TC-15.1 验证，此处聚焦容器间通信。

**操作**：
```bash
# backend → keycloak 连通
docker compose -f infrastructure/docker/docker-compose.trial.yml exec backend curl -s http://keycloak:8080/realms/forge/.well-known/openid-configuration | head -5

# nginx → backend 连通
docker compose -f infrastructure/docker/docker-compose.trial.yml exec nginx curl -s http://backend:8080/actuator/health

# nginx → frontend 连通
docker compose -f infrastructure/docker/docker-compose.trial.yml exec nginx curl -s http://frontend:3000 | head -5
```

**预期**：
- [ ] backend 能访问 keycloak 的 OIDC 发现端点，返回 JSON
- [ ] nginx 能访问 backend 的 health 端点，返回 `{"status":"UP"}`
- [ ] nginx 能访问 frontend，返回 HTML
- [ ] 无 DNS 解析失败或连接拒绝

### TC-I.2 Keycloak realm 导入

**操作**：
```bash
docker compose -f infrastructure/docker/docker-compose.trial.yml logs keycloak | grep -i "realm"
```

**预期**：
- [ ] 日志中有 `forge` realm 创建或导入成功的记录
- [ ] realm 包含预配置的 `forge-web-ide` 客户端
- [ ] realm 包含至少一个测试用户（如 demo/demo）

### TC-I.3 Workspace 工具端到端验证（Docker 内）

> Skills 加载日志已在 TC-15.2 验证，此处验证 workspace 工具在 Docker 环境中的端到端可用性。

**操作**：
```bash
# 通过 nginx 反代调用 workspace_list_files
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"workspace_list_files","arguments":{"workspaceId":"1"}}'

# 通过 nginx 反代调用 workspace_write_file
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"workspace_write_file","arguments":{"workspaceId":"1","path":"test-docker.txt","content":"hello from docker"}}'

# 通过 nginx 反代调用 workspace_read_file
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"workspace_read_file","arguments":{"workspaceId":"1","path":"test-docker.txt"}}'
```

**预期**：
- [ ] `workspace_list_files` 返回文件列表（可能为空或包含之前创建的文件）
- [ ] `workspace_write_file` 返回成功，`isError` 为 false
- [ ] `workspace_read_file` 返回刚写入的内容 `"hello from docker"`
- [ ] 三个工具在 Docker 环境中均正常工作，无路径权限问题

---

## 测试结果汇总模板

| 场景 | 用例数 | 通过 | 失败 | 备注 |
|------|--------|------|------|------|
| 1. 新人入职 | 3 | /3 | /3 | |
| 2. 开发日常 | 5 | /5 | /5 | |
| 3. AI 工具调用 (MCP) | 4 | /4 | /4 | |
| 4. 代码审查 | 2 | /2 | /2 | |
| 5. 知识库探索 | 5 | /5 | /5 | |
| 6. 对话高级功能 | 5 | /5 | /5 | |
| 7. Profile 轮转 | 5 | /5 | /5 | |
| 8. 边界异常 | 4 | /4 | /4 | |
| 9. API 健康度 | 4 | /4 | /4 | MCP Tools 9 个 |
| 10. Actuator 度量 | 7 | /7 | /7 | |
| 11. MCP 工具直接调用 | 5 | /5 | /5 | |
| 12. 工作流编辑器 | 3 | /3 | /3 | |
| 13. agent-eval | 3 | /3 | /3 | |
| 14. 全量单元测试 | 1 | /1 | /1 | 130+ tests |
| 15. Docker 部署完整性 | 3 | /3 | /3 | 4 容器 |
| **Phase 0~1.5 小计** | **59** | **/59** | **/59** | |
| A. Keycloak SSO | 4 | /4 | /4 | **Phase 1.6 新增** |
| B. AI 交付闭环 | 5 | /5 | /5 | **Phase 1.6 核心** |
| C. Context Picker 实连 | 3 | /3 | /3 | **Phase 1.6 新增** |
| D. FileExplorer CRUD | 4 | /4 | /4 | **Phase 1.6 新增** |
| E. 编辑器增强 | 3 | /3 | /3 | **Phase 1.6 新增** |
| F. 知识库内容升级 | 2 | /2 | /2 | **Phase 1.6 新增** |
| G. API 健康度升级 | 3 | /3 | /3 | **Phase 1.6 新增** |
| H. Header + 导航增强 | 3 | /3 | /3 | **Phase 1.6 新增** |
| I. Docker 4 容器 | 3 | /3 | /3 | **Phase 1.6 新增** |
| **Phase 1.6 新增小计** | **30** | **/30** | **/30** | |
| **总计** | **89** | **/89** | **/89** | |

---

## 启动命令

```bash
# 如果需要重新构建
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home
./gradlew :web-ide:backend:bootJar -x test --no-daemon
cd web-ide/frontend && npm install && npm run build && cd ../..

# Docker 启动（4 容器：backend + frontend + nginx + keycloak）
cd infrastructure/docker
docker compose -f docker-compose.trial.yml --env-file .env.trial up --build

# 访问
open http://localhost:9000           # 主应用
open http://localhost:8180           # Keycloak 管理后台（admin/admin）
```

## 关键观察点

1. **OODA 是否流畅**：5 个阶段（Observe → Orient → Decide → Act → Done）是否按顺序快速流转，不卡顿
2. **Profile 切换是否准确**：显式标签 100% 正确，关键词路由合理
3. **Skills 是否体现在回复中**：development 回复应体现 Kotlin/Spring Boot 规范，不是通用建议
4. **流式体验是否顺畅**：文字逐字出现，不是一大段突然出现
5. **整体 UI 是否一致**：暗色主题，间距紧凑，图标统一
6. **MCP 工具调用是否返回真实数据**：search_knowledge 返回知识库文档，不是 mock 数据
7. **工具调用在对话流中是否自然**：Tool Call 卡片展开/折叠、状态变化是否清晰
8. **度量指标是否正确记录**：每次交互后 forge.* 指标递增，Prometheus 端点可导出
9. **32 Skills 是否全部加载**：API 和 Docker 日志都确认 32 个
10. **agent-eval 双模式是否工作**：无 key 降级为结构验证，有 key 调用真实模型
11. **AI 是否写文件而非仅展示代码**：workspace_write_file 工具调用必须出现，代码交付到文件树（Phase 1.6 核心）
12. **文件树是否自动刷新**：file_changed 事件驱动，无需手动刷新（Phase 1.6）
13. **Keycloak SSO 流程是否顺畅**：OIDC PKCE 登录 → 回调 → JWT 存储 → 登出（Phase 1.6）
14. **Context Picker 是否返回真实数据**：4 个类别（files/knowledge/schema/services）都从后端 API 获取，不是 mock 数据（Phase 1.6）
15. **自动保存是否可靠**：5 秒后蓝色圆点消失，内容持久化（Phase 1.6）

---

## 验收标准对照

### Phase 1.6 验收标准

| # | 验收标准 | 对应测试场景 |
|---|---------|-------------|
| 1 | AI → Workspace 交付闭环：代码写入文件树 | TC-B.1~B.5（核心） |
| 2 | Keycloak SSO：OIDC PKCE 登录/登出 | TC-A.1~A.4 |
| 3 | Context Picker 实连 4 类别（files/knowledge/schema/services） | TC-C.1~C.3 |
| 4 | FileExplorer CRUD 完整 | TC-D.1~D.4 |
| 5 | 未保存标记 + 5 秒自动保存 | TC-E.1~E.3 |
| 6 | 知识库 12+ 文档，新增 5 篇可搜索 | TC-F.1~F.2 |
| 7 | MCP 9 工具注册（+3 workspace 工具） | TC-G.1, TC-9.3, TC-I.3 |
| 8 | Docker 4 容器部署健康 | TC-I.1~I.3, TC-15.1~15.3 |

### Phase 0~1.5 回归验收标准

| # | 验收标准 | 对应测试场景 |
|---|---------|-------------|
| 1 | Web IDE 可访问：知识搜索 → AI 对话 → Skill 感知 → 工具调用 | TC-1~3, TC-5, TC-9 |
| 2 | SkillLoader 加载 32 Skills + 5 Profiles | TC-9.1, TC-9.2, TC-15.2 |
| 3 | agent-eval 可运行（结构验证模式） | TC-13.1~13.3 |
| 4 | 130+ 单元/集成测试全部通过 | TC-14.1 |

### Phase 2 前置验收标准（待 Phase 2 开始后验证）

| # | 验收标准 | 说明 |
|---|---------|------|
| 1 | SuperAgent OODA 循环运转，底线一次通过率 ≥ 70% | Phase 2 目标 |
| 2 | 跨栈迁移 PoC：.NET → Java，覆盖率 ≥ 90% | Phase 2 目标 |
| 3 | 底线脚本 CI 集成 | Phase 2 目标 |
