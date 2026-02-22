# Phase 2 端到端验收测试 — 全功能覆盖

> 测试前提：`docker compose -f docker-compose.trial.yml --env-file .env.trial up --build` 已启动
> 访问地址：http://localhost:9000
> 测试人：人工验收
> 覆盖范围：Phase 2 全部功能（Sprint 2A + 2B + 2C + 2.3 多模型适配）
>
> 本文档在 `sprint2a-acceptance-test.md` 基础上扩展，新增 MCP 实连、底线集成、度量采集、agent-eval 等 Sprint 2B/2C 功能的验收用例。

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
- [ ] Knowledge → `/knowledge` — 进入知识库页面
- [ ] Workflows → `/workflows` — 进入工作流页面
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

> 验证 Claude 通过 MCP 工具获取真实数据的能力。Sprint 2B 核心功能。

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
- [ ] 包含 **32** 个 skills（Sprint 2B 从 29 增加到 32）
- [ ] 每个 skill 有 name, description 字段
- [ ] 包含新增的 3 个 skill：`deployment-readiness-check`、`design-baseline-guardian`、`environment-parity`

### TC-9.2 Profiles 加载（5 Profiles）

**操作**：浏览器访问 http://localhost:9000/api/chat/profiles

**预期**：
- [ ] 返回 5 个 profiles
- [ ] 包含 planning, design, development, testing, ops
- [ ] development profile 的 skills 列表最长

### TC-9.3 MCP Tools 列表（6 工具）

**操作**：浏览器访问 http://localhost:9000/api/mcp/tools

**预期**：
- [ ] 返回 JSON 数组
- [ ] 包含 **6** 个工具：`search_knowledge`、`read_file`、`query_schema`、`run_baseline`、`list_baselines`、`get_service_info`
- [ ] 每个工具有 name, description, inputSchema

### TC-9.4 Knowledge 搜索

**操作**：浏览器访问 http://localhost:9000/api/knowledge/search

**预期**：
- [ ] 返回 JSON 数组
- [ ] 包含知识库文档

---

## 场景 10：Actuator + 度量指标验证

> 验证 Spring Boot Actuator 和 Forge 自定义 Micrometer 指标。Sprint 2C 核心功能。

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
- [ ] web-ide/backend: 99+ tests, 0 failures
- [ ] adapters/model-adapter: 11 tests, 0 failures
- [ ] agent-eval: 18 tests, 0 failures
- [ ] 总计 128+ tests, 0 failures

---

## 场景 15：Docker 部署完整性

> 验证 Docker 3 容器部署的整体健康。

### TC-15.1 容器状态

**操作**：
```bash
docker compose -f infrastructure/docker/docker-compose.trial.yml ps
```

**预期**：
- [ ] 3 个容器全部 running：backend, frontend, nginx
- [ ] backend 容器 health status 为 `healthy`

### TC-15.2 Docker 日志验证

**操作**：
```bash
docker compose -f infrastructure/docker/docker-compose.trial.yml logs backend | head -50
```

**预期**：
- [ ] 日志中有 `Skill loading complete: 32 skills, 5 profiles`
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

---

## 场景 16：多模型适配 — 模型列表与切换

> Sprint 2.3 核心功能。验证多模型注册、前端选择器、动态切换。

### TC-16.1 模型列表 API

**操作**：
```bash
curl http://localhost:9000/api/models
```

**预期**：
- [ ] 返回 JSON 数组，包含所有已启用的模型
- [ ] 每个模型有 id, provider, displayName, contextWindow, maxOutputTokens, toolCallSupport, available 字段
- [ ] provider 包含 ANTHROPIC（默认启用）
- [ ] 如果配置了 BEDROCK_ENABLED=true，包含 BEDROCK provider 的模型
- [ ] 如果配置了 GEMINI_ENABLED=true，包含 GEMINI provider 的模型
- [ ] 如果配置了 QWEN_ENABLED=true，包含 QWEN provider 的模型

### TC-16.2 前端模型选择器可见

**操作**：进入 Workspace IDE 页面，观察 AI Chat Sidebar 或 Header 区域

**预期**：
- [ ] 模型选择器下拉菜单可见
- [ ] 显示当前活跃模型名称（默认 Claude Sonnet 4）
- [ ] 下拉列表显示所有可用模型，按 provider 分组
- [ ] 每个模型旁显示 provider 标签（如 Anthropic / Bedrock / Gemini / Qwen）

### TC-16.3 切换模型

**操作**：在模型选择器中切换到另一个模型（如从 Claude Sonnet 切到 Gemini Flash）

**预期**：
- [ ] 选择器显示更新为新模型名称
- [ ] 不影响当前对话历史（消息保留）
- [ ] 下一条消息使用新模型处理

### TC-16.4 模型健康检查

**操作**：
```bash
curl http://localhost:9000/api/models/health
```

**预期**：
- [ ] 返回各模型的 available 状态
- [ ] API Key 未配置的模型返回 available=false
- [ ] API Key 正确的模型返回 available=true

### TC-16.5 无效模型切换

**操作**：尝试切换到一个 available=false 的模型后发送消息

**预期**：
- [ ] 给出明确的错误提示（如"该模型当前不可用，请检查 API Key 配置"）
- [ ] 不导致页面崩溃或白屏
- [ ] 自动回退到默认模型或提示用户切换

---

## 场景 17：多模型适配 — 各模型对话验证

> 验证不同模型的流式对话和回复质量。需要对应的 API Key 已配置。

### TC-17.1 Anthropic Claude 直连对话

**操作**：选择 Claude Sonnet 4（Anthropic 直连），发送：`用 Kotlin 写一个快速排序`

**预期**：
- [ ] 流式输出正常（逐字出现）
- [ ] 回复包含 Kotlin 代码块
- [ ] OODA 指示器正常流转
- [ ] Profile Badge 正常显示

### TC-17.2 AWS Bedrock Claude 对话

**操作**：选择 Claude Sonnet 4（Bedrock），发送相同问题

**预期**：
- [ ] 流式输出正常
- [ ] 回复质量与 Anthropic 直连一致（同一模型，不同通道）
- [ ] 无 AWS 认证报错

### TC-17.3 Google Gemini 对话

**操作**：选择 Gemini 2.0 Flash，发送：`用 Kotlin 写一个快速排序`

**预期**：
- [ ] 流式输出正常
- [ ] 回复包含 Kotlin 代码块（Gemini 也理解 Kotlin）
- [ ] OODA 指示器正常流转
- [ ] Profile Badge 正常显示

### TC-17.4 阿里 Qwen 对话

**操作**：选择 Qwen Max，发送：`用 Kotlin 写一个快速排序`

**预期**：
- [ ] 流式输出正常
- [ ] 回复包含 Kotlin 代码块
- [ ] 中文交互质量良好（Qwen 中文优势）

### TC-17.5 切换模型后会话连续性

**操作**：
1. 用 Claude 发送：`帮我设计一个用户管理模块`
2. 切换到 Gemini
3. 发送：`给上面的设计加上权限控制`

**预期**：
- [ ] Gemini 能读到之前 Claude 的对话历史
- [ ] 回复基于之前的设计上下文（非重新开始）

### TC-17.6 模型回复中显示模型标识

**操作**：观察不同模型的回复气泡

**预期**：
- [ ] 每条 AI 回复标注当前使用的模型名称（如"Claude Sonnet 4"或"Gemini 2.0 Flash"）
- [ ] 切换模型后的回复标注新的模型名称

---

## 场景 18：多模型适配 — 工具调用兼容

> 验证不同模型的工具调用（Function Calling）能力差异处理。

### TC-18.1 Claude 工具调用（基准）

**操作**：选择 Claude 模型，发送：`帮我搜索知识库里关于 Spring Boot 的文档`

**预期**：
- [ ] 触发 search_knowledge 工具调用
- [ ] Tool Call 卡片正常显示
- [ ] 回复基于工具返回结果

### TC-18.2 Gemini Function Calling

**操作**：选择 Gemini 模型，发送相同问题

**预期**：
- [ ] Gemini 通过 Function Calling 触发 search_knowledge 工具
- [ ] Tool Call 卡片正常显示（与 Claude 格式一致）
- [ ] 回复基于工具返回结果

### TC-18.3 Qwen 工具调用

**操作**：选择 Qwen 模型，发送相同问题

**预期**：
- [ ] Qwen 通过 tools 参数触发工具调用
- [ ] Tool Call 卡片正常显示
- [ ] 如果 Qwen 工具调用能力有限，优雅降级（直接回复，不触发工具但不报错）

### TC-18.4 工具调用能力弱的模型降级

**操作**：选择一个 toolCallSupport=PARTIAL 或 NONE 的模型，发送需要工具调用的问题

**预期**：
- [ ] 不触发工具调用（或触发后正确处理）
- [ ] 模型直接基于已有知识回复
- [ ] 无异常或崩溃
- [ ] 可选：UI 提示"当前模型不支持工具调用，回复基于模型内置知识"

---

## 测试结果汇总模板

| 场景 | 用例数 | 通过 | 失败 | 备注 |
|------|--------|------|------|------|
| 1. 新人入职 | 3 | /3 | /3 | |
| 2. 开发日常 | 5 | /5 | /5 | |
| 3. AI 工具调用 (MCP) | 4 | /4 | /4 | Sprint 2B 新增 |
| 4. 代码审查 | 2 | /2 | /2 | |
| 5. 知识库探索 | 5 | /5 | /5 | |
| 6. 对话高级功能 | 5 | /5 | /5 | |
| 7. Profile 轮转 | 5 | /5 | /5 | |
| 8. 边界异常 | 4 | /4 | /4 | |
| 9. API 健康度 | 4 | /4 | /4 | 更新数据 |
| 10. Actuator 度量 | 7 | /7 | /7 | Sprint 2C 新增 |
| 11. MCP 工具直接调用 | 5 | /5 | /5 | Sprint 2B 新增 |
| 12. 工作流编辑器 | 3 | /3 | /3 | |
| 13. agent-eval | 3 | /3 | /3 | Sprint 2C 新增 |
| 14. 全量单元测试 | 1 | /1 | /1 | |
| 15. Docker 部署完整性 | 3 | /3 | /3 | Sprint 2B 新增 |
| 16. 多模型适配 — 模型列表与切换 | 5 | /5 | /5 | Sprint 2.3 新增 |
| 17. 多模型适配 — 各模型对话验证 | 6 | /6 | /6 | Sprint 2.3 新增 |
| 18. 多模型适配 — 工具调用兼容 | 4 | /4 | /4 | Sprint 2.3 新增 |
| **合计** | **74** | **/74** | **/74** | |

---

## 启动命令

```bash
# 如果需要重新构建
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home
./gradlew :web-ide:backend:bootJar -x test --no-daemon
cd web-ide/frontend && npm install && npm run build && cd ../..

# Docker 启动
cd infrastructure/docker
docker compose -f docker-compose.trial.yml --env-file .env.trial up --build

# 访问
open http://localhost:9000
```

## 关键观察点

1. **OODA 是否流畅**：5 个阶段（Observe → Orient → Decide → Act → Done）是否按顺序快速流转，不卡顿
2. **Profile 切换是否准确**：显式标签 100% 正确，关键词路由合理
3. **Skills 是否体现在回复中**：development 回复应体现 Kotlin/Spring Boot 规范，不是通用建议
4. **流式体验是否顺畅**：文字逐字出现，不是一大段突然出现
5. **整体 UI 是否一致**：暗色主题，间距紧凑，图标统一
6. **MCP 工具调用是否返回真实数据**：search_knowledge 返回知识库文档，不是 mock 数据（Sprint 2B）
7. **工具调用在对话流中是否自然**：Tool Call 卡片展开/折叠、状态变化是否清晰（Sprint 2B）
8. **度量指标是否正确记录**：每次交互后 forge.* 指标递增，Prometheus 端点可导出（Sprint 2C）
9. **32 Skills 是否全部加载**：API 和 Docker 日志都确认 32 个（Sprint 2B 新增 3 个）
10. **agent-eval 双模式是否工作**：无 key 降级为结构验证，有 key 调用真实模型（Sprint 2C）
11. **多模型切换是否顺畅**：选择器切换后下一条消息使用新模型，无延迟或报错（Sprint 2.3）
12. **不同模型的工具调用是否兼容**：Claude/Gemini/Qwen 的 Tool Call 卡片格式一致（Sprint 2.3）
13. **工具调用能力弱的模型是否优雅降级**：不崩溃，给出合理回复或提示（Sprint 2.3）

---

## Phase 2 验收标准对照

| # | 验收标准 | 对应测试场景 |
|---|---------|-------------|
| 1 | SkillLoader 独立加载 Skill | TC-9.1, TC-9.2, TC-15.2 |
| 2 | SuperAgent OODA 循环运转，底线一次通过率 ≥ 70% | TC-2.1, TC-3.1~3.3, TC-11.2~11.3 |
| 3 | 跨栈迁移 PoC：.NET → Java，覆盖率 ≥ 90% | docs/cross-stack-poc-report.md（文档审阅） |
| 4 | Web IDE 可访问：知识搜索 → AI 对话 → Skill 感知 → 工具调用 | TC-1~3, TC-5, TC-9 |
| 5 | agent-eval 可运行真实评估场景 | TC-13.1~13.3 |
| 6 | Bedrock + Gemini + Qwen 三大模型适配器可用 | TC-16.1, TC-17.1~17.4 |
| 7 | 前端可切换模型，工具调用兼容 | TC-16.2~16.3, TC-18.1~18.4 |
