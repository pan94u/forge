# Sprint 2A 验收测试 — 真实用户场景

> 测试前提：`docker compose -f docker-compose.trial.yml --env-file .env.trial up --build` 已启动
> 访问地址：http://localhost:9000
> 测试人：人工验收

---

## 场景 1：新人入职第一天 — 探索平台

> 模拟一个刚入职的开发者，不看任何文档，凭直觉使用平台。

### TC-1.1 首页能否给我方向感

**操作**：打开 http://localhost:9000

**预期**：
- [ok ] 页面加载成功（HTTP 200），看到 Dashboard
- [ ok] 有 "Quick Actions" 区域，至少包含创建 Workspace、搜索知识库
- [ ok] 有 "Recent Projects" 区域（可能为空，但区域存在）
- [ok ] 有 Activity Feed 区域
- [ ok] 左侧 Sidebar 可见，包含 Dashboard / Workspaces / Knowledge / AI Chat 导航项

### TC-1.2 侧边栏导航是否完整

**操作**：依次点击 Sidebar 中的每个导航项

**预期**：
- [ok ] Dashboard → `/` — 回到首页
- [ ok] Knowledge → `/knowledge` — 进入知识库页面
- [ok ] Workflows → `/workflows` — 进入工作流页面
- [ok ] 每个页面都能正常渲染，无白屏或 JS 报错（打开浏览器 Console 检查）

### TC-1.3 创建 Workspace 进入 IDE

**操作**：
1. 点击 "Create Workspace" 或导航到 Workspace 页面
2. 填写名称（如 "my-first-project"），提交

**预期**：
- [ ok] 进入 `/workspace/{id}` 页面
- [ok ] 三面板布局可见：左侧文件树、中间编辑器、右侧 AI 聊天
- [ok ] 右侧 AI Chat 显示 "Start a conversation" 引导文案
- [ ok] 底部输入框可见，placeholder 为 "Ask anything... (@ for context)"

---

## 场景 2：开发者日常 — 用 AI 写代码

> 模拟一个 Kotlin/Spring Boot 开发者的真实工作流程。

### TC-2.1 第一次对话 — 不带标签，自然提问

**操作**：在 AI Chat 输入框输入：
```
帮我写一个 Spring Boot 的 REST Controller，实现用户注册功能，要求有参数校验
```

**预期**：
- [ ok] OODA 指示器出现，从 Observe → Orient 依次流转
- [ ok] Profile Badge 出现，显示 `development`（因为包含"实现"关键词）
- [ ok] Badge 上有黄色或绿色置信度圆点
- [ok ] Badge 显示 routing reason（如 `keyword '实现'`）
- [ ok] Badge 显示加载的 Skills（如 `kotlin-conventions, spring-boot-patterns +N`）
- [ ok] OODA 流转到 Decide，然后 Claude 开始流式输出
- [ ok] 回复中包含 Kotlin 代码块（```kotlin）
- [ok ] 代码块有语法高亮 + "Copy" 按钮
- [ ok] 回复体现 Spring Boot 专业知识（如 @Valid, @RestController, @RequestBody）
- [ ok] 回复体现 Kotlin 规范（如 data class, val 而非 var）
- [ ok] 流式结束后，OODA 指示器消失
- [ ok] 消息气泡有时间戳

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

## 场景 3：代码审查 — 贴代码给 AI 看

> 模拟开发者把代码贴给 AI 做 code review。

### TC-3.1 贴一段有问题的代码

**操作**：
```
帮我 review 下面这段代码，找出潜在问题：

```kotlin
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
```（注意：实际输入时去掉反引号转义）

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

### TC-3.2 长回复的 Markdown 渲染质量

**操作**：观察 TC-3.1 的回复

**预期**：
- [ ] 标题（#, ##, ###）正确渲染为不同大小
- [ ] 加粗（**text**）正确渲染
- [ ] 列表（- item）正确渲染为带圆点的列表
- [ ] 行内代码（`code`）有灰色背景
- [ ] 代码块有语法高亮、语言标签、Copy 按钮
- [ ] 空行正确分隔段落

---

## 场景 4：知识库探索

> 模拟用户在做技术决策前，先查阅知识库。

### TC-4.1 知识库搜索

**操作**：导航到 Knowledge 页面（`/knowledge`）

**预期**：
- [ ] 四个标签页可见：Docs / Architecture / Services / APIs
- [ ] 默认在 Docs 标签页
- [ ] 左侧有搜索框

### TC-4.2 文档搜索与查看

**操作**：在搜索框输入 "Spring" 或任意关键词

**预期**：
- [ ] 搜索结果列表出现（来自 `/api/knowledge/search` 接口）
- [ ] 每个结果有标题、类型标签、摘要
- [ ] 点击一个结果，右侧显示文档详情

### TC-4.3 架构图查看

**操作**：切换到 Architecture 标签页

**预期**：
- [ ] 有架构图列表（可能为 mock 数据）
- [ ] 有缩放控件（放大/缩小/重置）

### TC-4.4 服务依赖图

**操作**：切换到 Services 标签页

**预期**：
- [ ] 有服务节点图（ReactFlow 渲染）
- [ ] 节点有颜色区分（健康/退化/故障）

### TC-4.5 API Explorer

**操作**：切换到 APIs 标签页

**预期**：
- [ ] API 目录列表可见
- [ ] 每个 API 有方法标签（GET/POST 等）颜色区分
- [ ] 点击展开能看到参数和响应示例

---

## 场景 5：AI 对话高级功能

> 验证对话的各种交互细节。

### TC-5.1 新建对话

**操作**：
1. 先发几条消息，建立对话历史
2. 点击右上角 ↻ (RotateCcw) 按钮

**预期**：
- [ ] 所有消息清除
- [ ] 回到 "Start a conversation" 状态
- [ ] 输入新消息后，Claude 不记得之前的对话

### TC-5.2 中断流式响应

**操作**：
1. 发送一条会产生长回复的消息（如 "详细解释微服务架构的12种设计模式"）
2. 在 Claude 还在输出时，点击红色 Stop 按钮

**预期**：
- [ ] 流式输出立即停止
- [ ] 已输出的内容保留在消息气泡中
- [ ] OODA 指示器消失
- [ ] 输入框恢复可用状态
- [ ] Stop 按钮变回 Send 按钮

### TC-5.3 @ 上下文弹出

**操作**：在输入框中输入 `@`

**预期**：
- [ ] Context Picker 弹出
- [ ] 有 4 个类型标签：Files / Knowledge / Schema / Services
- [ ] 有搜索框（自动获取焦点）
- [ ] 按 Escape 关闭 Context Picker

### TC-5.4 附加上下文后发送

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

### TC-5.5 多行输入

**操作**：按 Shift+Enter 换行输入多行内容，然后 Enter 发送

**预期**：
- [ ] Shift+Enter 换行，输入框高度自动增长（最多 5 行）
- [ ] 单独 Enter 触发发送
- [ ] 多行内容正确发送和显示

---

## 场景 6：全部 5 个 Profile 轮转

> 验证所有 Profile 的路由和 Skill 加载。

### TC-6.1 规划 Profile

**操作**：`@规划 写一个用户管理模块的 PRD，包含用户故事和验收标准`

**预期**：
- [ ] Profile: `planning`，绿色圆点
- [ ] 回复包含用户故事格式（As a... I want... So that...）
- [ ] 回复包含验收标准

### TC-6.2 设计 Profile

**操作**：`@设计 设计一个支付系统的数据库 schema，支持多币种和退款`

**预期**：
- [ ] Profile: `design`，绿色圆点
- [ ] Skills 包含 api-design 或 database-patterns
- [ ] 回复包含 ER 图或表结构设计
- [ ] 考虑了多币种和退款的设计

### TC-6.3 开发 Profile

**操作**：`@开发 用 Kotlin + Spring Boot 实现上面的支付服务`

**预期**：
- [ ] Profile: `development`，绿色圆点
- [ ] Skills 最多（17 个），包含 kotlin-conventions, spring-boot-patterns
- [ ] 回复包含完整的 Kotlin 代码

### TC-6.4 测试 Profile

**操作**：`@测试 为支付服务写集成测试，覆盖正常支付和退款失败场景`

**预期**：
- [ ] Profile: `testing`，绿色圆点
- [ ] 回复包含测试代码和测试策略

### TC-6.5 运维 Profile

**操作**：`@运维 支付服务上线前需要做哪些检查？给出部署 checklist`

**预期**：
- [ ] Profile: `ops`，绿色圆点
- [ ] 回复包含部署检查清单（健康检查、监控、回滚策略等）

---

## 场景 7：边界和异常

### TC-7.1 空消息

**操作**：不输入任何内容，直接按 Enter 或点击 Send

**预期**：
- [ ] 不发送请求
- [ ] Send 按钮为 disabled 状态（opacity-50）

### TC-7.2 超长消息

**操作**：粘贴一段超过 1000 字的文本发送

**预期**：
- [ ] 消息正常发送
- [ ] Claude 正常回复
- [ ] 用户气泡正确显示长文本

### TC-7.3 连续快速发送

**操作**：发送一条消息后，在 Claude 还在回复时尝试再次发送

**预期**：
- [ ] 输入框在流式期间为 disabled 状态
- [ ] 无法重复发送

### TC-7.4 特殊字符

**操作**：发送包含特殊字符的消息：`<script>alert('xss')</script> and "quotes" & <tags>`

**预期**：
- [ ] 内容正常显示为文本（不执行脚本）
- [ ] 无 XSS 漏洞

---

## 场景 8：API 健康度检查

> 通过浏览器直接访问 API 验证后端状态。

### TC-8.1 Skills 加载

**操作**：浏览器访问 http://localhost:9000/api/chat/skills

**预期**：
- [ ] 返回 JSON 数组
- [ ] 包含 29 个 skills
- [ ] 每个 skill 有 name, description 字段

### TC-8.2 Profiles 加载

**操作**：浏览器访问 http://localhost:9000/api/chat/profiles

**预期**：
- [ ] 返回 5 个 profiles
- [ ] 包含 planning, design, development, testing, ops
- [ ] development profile 的 skills 列表最长

### TC-8.3 MCP Tools 列表

**操作**：浏览器访问 http://localhost:9000/api/mcp/tools

**预期**：
- [ ] 返回 JSON 数组（即使是 stub 模式也有默认工具）
- [ ] 每个工具有 name, description, inputSchema

### TC-8.4 Knowledge 搜索

**操作**：浏览器访问 http://localhost:9000/api/knowledge/search

**预期**：
- [ ] 返回 JSON 数组
- [ ] 包含 mock 数据文档

---

## 场景 9：工作流编辑器

### TC-9.1 打开工作流页面

**操作**：导航到 `/workflows`

**预期**：
- [ ] 页面正常加载
- [ ] 左侧有节点面板（Node Palette）
- [ ] 中间有画布区域（带网格背景）
- [ ] 有 "New" / "Save" / "Run" 按钮

### TC-9.2 拖放节点

**操作**：从 Node Palette 拖一个 "Trigger" 节点和一个 "Action" 节点到画布

**预期**：
- [ ] 节点出现在画布上
- [ ] 可以拖动节点重新定位
- [ ] 节点有颜色区分

### TC-9.3 连线

**操作**：从 Trigger 节点的输出端口拖线到 Action 节点的输入端口

**预期**：
- [ ] 连线成功，箭头指向 Action
- [ ] 连线可视化清晰

---

## 测试结果汇总模板

| 场景 | 用例数 | 通过 | 失败 | 备注 |
|------|--------|------|------|------|
| 1. 新人入职 | 3 | /3 | /3 | |
| 2. 开发日常 | 5 | /5 | /5 | |
| 3. 代码审查 | 2 | /2 | /2 | |
| 4. 知识库探索 | 5 | /5 | /5 | |
| 5. 对话高级功能 | 5 | /5 | /5 | |
| 6. Profile 轮转 | 5 | /5 | /5 | |
| 7. 边界异常 | 4 | /4 | /4 | |
| 8. API 健康度 | 4 | /4 | /4 | |
| 9. 工作流编辑器 | 3 | /3 | /3 | |
| **合计** | **36** | **/36** | **/36** | |

---

## 启动命令

```bash
# 如果需要重新构建
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
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
