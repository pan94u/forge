# Phase 1.6 端到端验收测试

> **测试环境**：`docker compose -f docker-compose.trial.yml --env-file .env.trial up --build`（4 容器：backend + frontend + nginx + keycloak）
> **访问地址**：http://localhost:9000 | Keycloak：http://localhost:8180
> **测试结果**：87 用例，80 通过（92.0%），7 未通过（2 部分阻塞 + 3 需安全模式 + 1 挂起 + 1 需 API Key）
> **Bug 累计**：20 个（19 已修复，1 挂起 BUG-016）

---

## 一、用户旅程（场景 1~8，33 用例）

### 场景 1：新人入职 — 探索平台

> 模拟一个刚入职的开发者，不看任何文档，凭直觉使用平台。

#### TC-1.1 首页能否给我方向感

**操作**：打开 http://localhost:9000

**预期**：
- [x] 页面加载成功（HTTP 200），看到 Dashboard
- [x] 有 "Quick Actions" 区域，至少包含创建 Workspace、搜索知识库
- [x] 有 "Recent Projects" 区域（可能为空，但区域存在）
- [x] 有 Activity Feed 区域
- [x] 左侧 Sidebar 可见，包含 Dashboard / Workspaces / Knowledge / AI Chat 导航项

#### TC-1.2 侧边栏导航是否完整

**操作**：依次点击 Sidebar 中的每个导航项

**预期**：
- [x] Dashboard → `/` — 回到首页
- [x] Workspaces → 进入工作区列表页面
- [x] Knowledge → `/knowledge` — 进入知识库页面
- [x] Workflows → `/workflows` — 进入工作流页面（Developer 角色可见）
- [x] AI Chat → 进入 AI 对话页面
- [x] Integrations → 进入集成页面（Developer 角色可见）
- [x] Infrastructure → 进入基础设施页面（Developer 角色可见）
- [x] 每个页面都能正常渲染，无白屏或 JS 报错

#### TC-1.3 创建 Workspace 进入 IDE

**操作**：点击 "Create Workspace" 或导航到 Workspace 页面，填写名称提交

**预期**：
- [x] 进入 `/workspace/{id}` 页面
- [x] 三面板布局可见：左侧文件树、中间编辑器、右侧 AI 聊天
- [x] 右侧 AI Chat 显示 "Start a conversation" 引导文案
- [x] 底部输入框可见，placeholder 为 "Ask anything... (@ for context)"

---

### 场景 2：开发者日常 — 用 AI 写代码

> 模拟一个 Kotlin/Spring Boot 开发者的真实工作流程。

#### TC-2.1 第一次对话 — 自然提问

**操作**：在 AI Chat 输入 `帮我写一个 Spring Boot 的 REST Controller，实现用户注册功能，要求有参数校验`

**预期**：
- [x] OODA 指示器出现，从 Observe → Orient 依次流转
- [x] Profile Badge 出现，显示 `development`（包含"实现"关键词）
- [x] Badge 上有黄色或绿色置信度圆点，显示 routing reason 和 Skills
- [x] 回复中包含 Kotlin 代码块（```kotlin），有语法高亮 + Copy 按钮
- [x] 回复体现 Spring Boot 专业知识（@Valid, @RestController）和 Kotlin 规范（data class, val）
- [x] 流式结束后 OODA 指示器消失，消息气泡有时间戳

#### TC-2.2 追问 — 验证会话连续性

**操作**：在同一会话中输入 `给这个 Controller 加上单元测试`

**预期**：
- [x] Claude 知道上一条消息的上下文（指的是刚才的用户注册 Controller）
- [x] 生成的测试代码引用了上面的 Controller 类名和方法名
- [x] OODA 指示器再次出现并流转
- [x] 无论路由到哪个 profile，回复都包含测试代码

#### TC-2.3 显式切换 Profile — @设计

**操作**：输入 `@设计 这个用户注册服务应该怎么设计架构？需要考虑哪些非功能需求？`

**预期**：
- [x] Profile Badge 切换为 `design`，置信度圆点为绿色（confidence = 1.0）
- [ ] routing reason 显示 `'@设计'`（BUG-016 阻塞）
- [ ] 加载的 Skills 变化（design profile 的 skills 和 development 不同）（BUG-016 阻塞）
- [ ] 回复偏向架构层面（模块拆分、高可用、安全性），而非直接写代码（BUG-016 阻塞）

#### TC-2.4 显式切换 Profile — @测试

**操作**：输入 `@测试 针对用户注册功能，设计完整的测试策略`

**预期**：
- [x] Profile Badge 切换为 `testing`
- [ ] 绿色圆点（显式标签）（BUG-016 阻塞）
- [ ] 回复偏向测试策略（测试金字塔、边界条件、mock 策略等）（BUG-016 阻塞）

#### TC-2.5 英文关键词路由

**操作**：输入 `deploy this service to kubernetes with rolling update strategy`

**预期**：
- [x] Profile Badge 显示 `ops`（"deploy" + "kubernetes" 是运维关键词）
- [x] 回复涉及 K8s deployment yaml、rolling update 配置等

---

### 场景 3：AI 工具调用 — MCP 实连

> 验证 Claude 通过 MCP 工具获取真实数据的能力。

#### TC-3.1 触发知识搜索工具调用

**操作**：输入 `帮我搜索知识库里关于 Spring Boot 的文档`

**预期**：
- [x] OODA 流转到 Act 阶段，出现 Tool Call 卡片（`search_knowledge`）
- [x] 卡片显示输入参数，完成后状态变为绿色 ✓
- [x] 展开详情能看到返回的知识库文档内容
- [x] Claude 基于工具返回结果给出回复，OODA 完成完整流转

#### TC-3.2 触发底线检查工具调用

**操作**：输入 `帮我运行 code-quality 底线检查`

**预期**：
- [x] 出现 Tool Call 卡片（`run_baseline` 或 `list_baselines`）
- [x] 工具执行有结果返回，Claude 对结果进行解读

#### TC-3.3 多轮工具调用（Agentic Loop）

**操作**：输入 `先搜索知识库里关于微服务架构的文档，然后根据文档内容帮我设计一个服务拆分方案`

**预期**：
- [x] Claude 先发起 `search_knowledge` 工具调用（Turn 1）
- [x] 收到工具结果后继续思考并给出回复（Turn 2）
- [x] OODA 完成 Observe → Orient → Decide → Act → Decide → Complete 流转
- [x] 最终回复引用了工具返回的知识库内容

#### TC-3.4 工具调用失败的优雅降级

**操作**：发送一条可能触发工具调用但工具可能出错的消息

**预期**：
- [x] Tool Call 卡片状态显示为红色错误（如果工具报错）
- [x] Claude 不崩溃，继续给出 fallback 回复
- [x] 整个对话流程不中断

---

### 场景 4：代码审查

> 模拟开发者把代码贴给 AI 做 code review。

#### TC-4.1 贴一段有问题的代码

**操作**：贴入一段包含明文密码、无校验、无异常处理的 Kotlin Service 代码请求 review

**预期**：
- [x] Profile 路由到 development
- [x] Claude 能识别出：明文密码、缺少参数校验、缺少异常处理、findAll 无分页、delete 无权限检查
- [x] 回复格式清晰，有问题分类和改进建议，代码块中给出修复示例

#### TC-4.2 长回复的 Markdown 渲染质量

**操作**：观察 TC-4.1 的回复

**预期**：
- [x] 标题（#, ##）、加粗（**text**）、列表、行内代码正确渲染
- [x] 代码块有语法高亮、语言标签、Copy 按钮
- [x] 空行正确分隔段落

---

### 场景 5：知识库探索

> 模拟用户在做技术决策前查阅知识库。

#### TC-5.1 知识库页面

**操作**：导航到 `/knowledge`

**预期**：
- [x] 四个标签页可见：Docs / Architecture / Services / APIs
- [x] 默认在 Docs 标签页，左侧有搜索框

#### TC-5.2 文档搜索与查看

**操作**：在搜索框输入 "Spring"

**预期**：
- [x] 搜索结果列表出现（来自 `/api/knowledge/search`）
- [x] 每个结果有标题、类型标签、摘要
- [x] 点击结果，右侧显示文档详情

#### TC-5.3 架构图查看

**操作**：切换到 Architecture 标签页

**预期**：
- [x] 有架构图列表，有缩放控件（放大/缩小/重置）

#### TC-5.4 服务依赖图

**操作**：切换到 Services 标签页

**预期**：
- [x] 有服务节点图（ReactFlow 渲染），节点有颜色区分（健康/退化/故障）

#### TC-5.5 API Explorer

**操作**：切换到 APIs 标签页

**预期**：
- [x] API 目录列表可见，每个 API 有方法标签颜色区分
- [x] 点击展开能看到参数和响应示例

---

### 场景 6：对话高级功能

#### TC-6.1 新建对话

**操作**：先发几条消息，然后点击 ↻ 按钮

**预期**：
- [x] 所有消息清除，回到 "Start a conversation" 状态
- [x] 输入新消息后 Claude 不记得之前的对话

#### TC-6.2 中断流式响应

**操作**：发送长回复消息，在输出时点击 Stop 按钮

**预期**：
- [x] 流式输出立即停止，已输出内容保留
- [x] OODA 指示器消失，输入框恢复可用，Stop 变回 Send

#### TC-6.3 @ 上下文弹出

**操作**：在输入框中输入 `@`

**预期**：
- [x] Context Picker 弹出，有 5 个标签：Profiles / Files / Knowledge / Schema / Services
- [x] 有搜索框（自动获取焦点已移除，BUG-014 修复）
- [x] 按 Escape 关闭

#### TC-6.4 附加上下文后发送

**操作**：输入 `@`，选择一个 Knowledge 项，输入问题并发送

**预期**：
- [x] 选择后输入框上方出现蓝色 chip（有 x 按钮可移除）
- [x] 发送后用户消息气泡上方显示 context chip 标签
- [x] Claude 的回复引用了附加的上下文内容

#### TC-6.5 多行输入

**操作**：按 Shift+Enter 换行输入多行内容，然后 Enter 发送

**预期**：
- [x] Shift+Enter 换行，输入框高度自动增长（最多 5 行）
- [x] 单独 Enter 触发发送，多行内容正确显示

---

### 场景 7：全部 5 个 Profile 轮转

#### TC-7.1 规划 Profile

**操作**：`@规划 写一个用户管理模块的 PRD，包含用户故事和验收标准`

**预期**：
- [x] Profile: `planning`，绿色圆点
- [x] 回复包含用户故事格式和验收标准

#### TC-7.2 设计 Profile

**操作**：`@设计 设计一个支付系统的数据库 schema，支持多币种和退款`

**预期**：
- [x] Profile: `design`，绿色圆点，Skills 包含 api-design 或 database-patterns
- [x] 回复包含表结构设计，考虑了多币种和退款

#### TC-7.3 开发 Profile

**操作**：`@开发 用 Kotlin + Spring Boot 实现上面的支付服务`

**预期**：
- [x] Profile: `development`，绿色圆点，Skills 最多（17 个）
- [x] 回复包含完整的 Kotlin 代码

#### TC-7.4 测试 Profile

**操作**：`@测试 为支付服务写集成测试，覆盖正常支付和退款失败场景`

**预期**：
- [x] Profile: `testing`，绿色圆点
- [x] 回复包含测试代码和测试策略

#### TC-7.5 运维 Profile

**操作**：`@运维 支付服务上线前需要做哪些检查？给出部署 checklist`

**预期**：
- [x] Profile: `ops`，绿色圆点
- [x] 回复包含部署检查清单（健康检查、监控、回滚策略等）

---

### 场景 8：边界和异常

#### TC-8.1 空消息

**操作**：不输入内容直接按 Enter

**预期**：
- [x] 不发送请求，Send 按钮为 disabled 状态

#### TC-8.2 超长消息

**操作**：粘贴 1000+ 字文本发送

**预期**：
- [x] 消息正常发送，Claude 正常回复，长文本正确显示

#### TC-8.3 连续快速发送

**操作**：发送一条消息后在 Claude 回复时尝试再次发送

**预期**：
- [x] 输入框在流式期间为 disabled，无法重复发送

#### TC-8.4 特殊字符

**操作**：发送 `<script>alert('xss')</script> and "quotes" & <tags>`

**预期**：
- [x] 内容正常显示为文本，无 XSS 漏洞

---

## 二、Phase 1.6 核心功能（场景 9~14，22 用例）

### 场景 9：AI 交付闭环 — 代码写入 Workspace ★核心

> 验证 AI 通过 workspace 工具直接将代码写入用户 workspace，实现从"聊天展示"到"文件交付"的闭环。

#### TC-9.1 AI 自动写文件到 workspace

**操作**：输入 `帮我创建一个 Hello World 的 Spring Boot Application.kt 文件`

**预期**：
- [x] OODA 流转到 Act 阶段，出现 `workspace_write_file` Tool Call 卡片
- [x] 参数包含 `path` 和 `content`，完成后卡片状态为绿色 ✓
- [x] Claude 回复确认文件已写入，核心交付是写文件而非仅展示代码

#### TC-9.2 文件树自动刷新 + 编辑器自动打开

**操作**：观察 TC-9.1 完成后的 UI 变化

**预期**：
- [x] 左侧 FileExplorer 自动刷新，出现新写入的文件（无需手动刷新）
- [x] 编辑器自动打开新写入的文件，内容一致
- [x] 文件 tab 出现在编辑器标签栏

#### TC-9.3 AI 先读取再修改已有文件

**操作**：输入 `帮我修改 Application.kt，添加一个 /health 端点`

**预期**：
- [x] Claude 先发起 `workspace_read_file`，再发起 `workspace_write_file`
- [x] 两次 Tool Call 卡片按顺序出现
- [x] 最终文件包含原有代码 + 新增端点，编辑器中内容自动更新

#### TC-9.4 AI 主动了解项目结构

**操作**：输入 `帮我看看当前项目里有哪些文件`

**预期**：
- [x] 出现 `workspace_list_files` Tool Call 卡片
- [x] 工具返回文件列表，Claude 基于列表给出项目结构概述

#### TC-9.5 代码块 Apply 按钮

**操作**：让 AI 生成代码，观察代码块右上角按钮

**预期**：
- [x] 代码块右上角有 Copy 和 Apply 按钮（BUG-019 修复后可见）
- [x] 点击 Apply → 弹出文件路径输入 → 确认后写入 workspace
- [x] 文件树刷新，编辑器打开该文件

---

### 场景 10：Context Picker 实连

> 验证 @ Context Picker 通过 /api/context/search 实连 4 个类别的真实数据。

#### TC-10.1 Files 类别

**操作**：输入 `@`，点击 Files 标签

**预期**：
- [x] 列表显示当前 workspace 中的文件（文件名 + 路径）
- [x] 选择文件后，文件内容作为上下文附加到消息

#### TC-10.2 Knowledge 类别

**操作**：点击 Knowledge 标签

**预期**：
- [x] 列表显示知识库文档（13 篇），包含 Phase 1.6 新增文档
- [x] 选择后作为上下文附加

#### TC-10.3 搜索过滤

**操作**：在 Knowledge tab 中直接输入 `git`

**预期**：
- [x] 键盘输入自动转发到搜索框（BUG-020 修复）
- [x] 结果过滤为包含 "git" 关键词的项，列表实时更新

---

### 场景 11：FileExplorer CRUD

> 验证文件树右键菜单操作。实现方式：右键菜单 + `window.prompt()` 对话框。
> 已修复 Bug：BUG-001~009。

#### TC-11.1 新建文件

**操作**：右键空白区域 → New File → 输入文件名；右键文件夹 → New File

**预期**：
- [x] 右键空白区域菜单含 New File / New Folder
- [x] 右键文件菜单含 Open / New File / New Folder / Copy Path / Rename / AI Explain / Delete
- [x] 弹出 `window.prompt`，确认后文件创建成功，编辑器自动打开
- [x] 右键文件夹内文件 → New File 默认路径为父目录
- [x] 创建同名文件时弹出 "already exists" 提示（同级校验）

#### TC-11.2 新建文件夹

**操作**：右键 → New Folder → 输入文件夹名

**预期**：
- [x] 文件夹出现在文件树中，带文件夹图标，可展开/折叠
- [x] 支持多级嵌套目录创建
- [x] 创建同名文件夹时弹出提示

#### TC-11.3 重命名文件

**操作**：右键文件 → Rename → 修改路径

**预期**：
- [x] `window.prompt` 预填当前完整路径
- [x] 修改后文件树更新，编辑器自动打开重命名后的文件
- [x] 重命名为已有同名文件时弹出提示

#### TC-11.4 删除文件和文件夹

**操作**：右键文件/文件夹 → Delete

**预期**：
- [x] 弹出 `window.confirm` 确认框
- [x] 文件从文件树消失；文件夹递归删除所有子文件
- [x] 取消则不删除

---

### 场景 12：编辑器增强 — 未保存标记 + 自动保存

#### TC-12.1 编辑后出现未保存标记

**操作**：打开文件，修改内容

**预期**：
- [x] 文件 tab 上出现蓝色圆点（unsaved indicator）
- [x] 继续编辑，圆点保持显示

#### TC-12.2 Cmd+S 手动保存

**操作**：在有未保存标记的文件上按 Cmd+S

**预期**：
- [x] 文件保存成功，蓝色圆点消失

#### TC-12.3 5 秒自动保存

**操作**：修改文件内容后等待 5 秒以上

**预期**：
- [x] 蓝色圆点自动消失，文件自动保存
- [x] 刷新页面后内容是最新修改后的

---

### 场景 13：Keycloak SSO 认证

> 验证 Keycloak OIDC PKCE 登录、JWT 验证和登出流程。

#### TC-13.1 Keycloak 管理后台可访问

**操作**：浏览器访问 http://localhost:8180

**预期**：
- [x] Keycloak 管理后台正常显示，admin/admin 可登录
- [x] realm 列表中包含 `forge`
- [x] forge realm → Clients 中有 `forge-web-ide` 客户端
- [x] 客户端配置：Access Type = public，Valid Redirect URIs 包含 `http://localhost:9000/*`

#### TC-13.2 SSO 登录流程（需 FORGE_SECURITY_ENABLED=true）

**操作**：设置 `FORGE_SECURITY_ENABLED=true` 并重启服务，访问 http://localhost:9000

**预期**：
- [ ] 未登录用户被重定向到 Keycloak 登录页面
- [ ] 输入 demo/demo 登录后回调到 http://localhost:9000
- [ ] 前端通过 PKCE 流程交换 access_token，页面正常加载

#### TC-13.3 JWT Token 验证（需 FORGE_SECURITY_ENABLED=true）

**操作**：登录后在 DevTools → Local Storage 中找到 JWT token，用 curl 验证

**预期**：
- [ ] Token 是标准 JWT 格式（三段 base64）
- [ ] 携带 Token 的 API 请求返回 200，不携带返回 401

#### TC-13.4 登出流程（需 FORGE_SECURITY_ENABLED=true）

**操作**：点击 Header 用户头像 → Logout

**预期**：
- [ ] 跳转到 Keycloak 登出端点，token 被清除
- [ ] 再次访问受保护页面需要重新登录

---

### 场景 14：Header + 导航增强

#### TC-14.1 角色切换影响侧边栏

**操作**：在 Header 角色选择器中切换角色

**预期**：
- [x] Header 显示当前角色名称
- [x] 切换角色后侧边栏导航项可能变化
- [x] 页面内容区域根据角色适配

#### TC-14.2 命令面板（挂起）

**操作**：按 Cmd+K

**预期**：
- [ ] 命令面板弹出（Cmd+K 键盘快捷键未实现，仅按钮点击可用，挂起）
- [ ] 有搜索输入框，按 Escape 关闭（Escape 监听未实现，挂起）

#### TC-14.3 侧边栏折叠/展开

**操作**：点击侧边栏折叠按钮

**预期**：
- [x] 侧边栏折叠为图标模式，主内容区域宽度扩大
- [x] 再次点击恢复展开状态

---

## 三、技术验证与回归（场景 15~21，32 用例）

### 场景 15：API 健康度

> 通过 curl 直接验证后端 API 状态。合并原场景 9 和场景 G。

#### TC-15.1 Skills 加载（32 Skills）

**操作**：`curl http://localhost:9000/api/chat/skills`

**预期**：
- [x] 返回 JSON 数组，包含 32 个 skills
- [x] 包含 3 个 Phase 1.5 skill：`deployment-readiness-check`、`design-baseline-guardian`、`environment-parity`

#### TC-15.2 Profiles 加载（5 Profiles）

**操作**：`curl http://localhost:9000/api/chat/profiles`

**预期**：
- [x] 返回 5 个 profiles：planning / design / development / testing / ops
- [x] development-profile 的 skills 列表最长

#### TC-15.3 MCP Tools（9 工具 + workspace inputSchema）

> 合并原 TC-9.3 和 TC-G.1。

**操作**：`curl http://localhost:9000/api/mcp/tools`

**预期**：
- [x] 返回 9 个工具：search_knowledge、read_file、query_schema、run_baseline、list_baselines、get_service_info、workspace_write_file、workspace_read_file、workspace_list_files
- [x] 每个工具有 name、description、inputSchema
- [x] workspace_write_file inputSchema 包含 path + content
- [x] workspace_read_file inputSchema 包含 path
- [x] workspace_list_files inputSchema 为空对象

#### TC-15.4 Knowledge 搜索 API

**操作**：`curl http://localhost:9000/api/knowledge/search`

**预期**：
- [x] 返回 JSON 数组，包含知识库文档

#### TC-15.5 Context Search API（4 类别）

**操作**：
```bash
curl "http://localhost:9000/api/context/search?category=files&workspaceId=1"
curl "http://localhost:9000/api/context/search?category=knowledge"
curl "http://localhost:9000/api/context/search?category=schema"
curl "http://localhost:9000/api/context/search?category=services"
```

**预期**：
- [x] 4 个类别都返回 200
- [x] files 返回 workspace 文件列表，knowledge 返回知识库文档列表

#### TC-15.6 Auth API

**操作**：
```bash
curl http://localhost:9000/api/auth/me
curl http://localhost:9000/api/auth/me/jwt
```

**预期**：
- [x] `/api/auth/me` 返回 JSON，未登录时 `authenticated: false, username: "anonymous"`
- [x] `/api/auth/me/jwt` 未携带 JWT 时 `authenticated: false`

---

### 场景 16：Actuator + Prometheus 度量

> 验证 Spring Boot Actuator 和 Forge 自定义 Micrometer 指标。
> 注：forge.* 指标为 Micrometer 延迟注册，需先通过 AI Chat 发送消息触发。

#### TC-16.1 Actuator 健康端点

**操作**：`curl http://localhost:9000/actuator/health`

**预期**：
- [x] 返回 JSON，`status` 为 `UP`，包含 `groups`（liveness, readiness）

#### TC-16.2 Metrics 列表

**操作**：`curl http://localhost:9000/actuator/metrics`

**预期**：
- [x] `names` 数组包含 forge 自定义指标：`forge.profile.route`、`forge.tool.calls`、`forge.ooda.phases`、`forge.message.duration`、`forge.turn.duration`、`forge.tool.duration`

#### TC-16.3 Profile 路由指标

**操作**：`curl http://localhost:9000/actuator/metrics/forge.profile.route`

**预期**：
- [x] COUNT > 0，`availableTags` 包含 `profile` 和 `method`

#### TC-16.4 OODA 阶段指标

**操作**：`curl http://localhost:9000/actuator/metrics/forge.ooda.phases`

**预期**：
- [x] `availableTags` 包含 `phase`，值含 observe / orient / decide / complete / act

#### TC-16.5 工具调用指标

**操作**：`curl http://localhost:9000/actuator/metrics/forge.tool.calls`

**预期**：
- [x] `availableTags` 包含 `tool` 和 `status`

#### TC-16.6 Prometheus 格式导出

**操作**：`curl http://localhost:9000/actuator/prometheus | grep forge_`

**预期**：
- [x] 输出包含 `forge_profile_route_total`、`forge_ooda_phases_total`、`forge_tool_calls_total`、`forge_message_duration_seconds`
- [x] 每行有正确的 tag（label）格式

#### TC-16.7 Turn Duration 和 Tool Duration

**操作**：
```bash
curl http://localhost:9000/actuator/metrics/forge.turn.duration
curl http://localhost:9000/actuator/metrics/forge.tool.duration
```

**预期**：
- [x] `forge.turn.duration` 有 `turn` tag，COUNT > 0
- [x] `forge.tool.duration` 有 `tool` tag，COUNT > 0

---

### 场景 17：MCP 工具直接调用

> 通过 REST API 直接调用 MCP 工具，验证 McpProxyService 实连。

#### TC-17.1 search_knowledge

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"search_knowledge","arguments":{"query":"Spring Boot"}}'
```

**预期**：
- [x] `content` 数组非空，`isError` 为 false

#### TC-17.2 list_baselines

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"list_baselines","arguments":{}}'
```

**预期**：
- [x] 返回 5 个底线：code-style / security / test-coverage / api-contract / architecture

#### TC-17.3 run_baseline

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"run_baseline","arguments":{"baseline":"code-style-baseline"}}'
```

**预期**：
- [x] 返回底线执行结果（Docker Alpine 中可能 isError=true，因缺少 bash）

#### TC-17.4 get_service_info

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"get_service_info","arguments":{"service":"order-service"}}'
```

**预期**：
- [x] 返回服务信息

#### TC-17.5 不存在的工具

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"nonexistent_tool","arguments":{}}'
```

**预期**：
- [x] 返回错误（HTTP 400 或 `isError: true`），不导致服务崩溃

---

### 场景 18：工作流编辑器

#### TC-18.1 打开工作流页面

**操作**：导航到 `/workflows`

**预期**：
- [x] 左侧有节点面板（Node Palette），中间有画布区域，有 New / Save / Run 按钮

#### TC-18.2 拖放节点

**操作**：从 Node Palette 拖 Trigger 和 Action 节点到画布

**预期**：
- [x] 节点出现在画布上，可拖动，有颜色区分

#### TC-18.3 连线

**操作**：从 Trigger 输出端口拖线到 Action 输入端口

**预期**：
- [x] 连线成功，箭头指向 Action

---

### 场景 19：知识库内容升级

#### TC-19.1 知识库文档数量

**操作**：
```bash
find knowledge-base/ -name "*.md" | wc -l
```

**预期**：
- [x] 总文件数 12+，新增文档包括：git-workflow.md、code-review-checklist.md、forge-mcp-tools.md、troubleshooting-guide.md、adr/ADR-004-*.md

#### TC-19.2 新增文档可搜索

**操作**：
```bash
curl "http://localhost:9000/api/knowledge/search?query=git-workflow"
curl "http://localhost:9000/api/knowledge/search?query=troubleshooting"
```

**预期**：
- [x] 搜索返回对应文档，有标题和摘要

---

### 场景 20：Docker 部署完整性

> 合并原场景 15 和场景 I，验证 4 容器架构。

#### TC-20.1 容器状态

**操作**：`docker compose -f infrastructure/docker/docker-compose.trial.yml ps`

**预期**：
- [x] 4 个容器全部 running：backend, frontend, nginx, keycloak
- [x] backend 和 keycloak 的 health status 为 `healthy`

#### TC-20.2 容器间网络连通性

**操作**：
```bash
docker compose exec backend curl -s http://keycloak:8080/realms/forge/.well-known/openid-configuration | head -5
docker compose exec nginx curl -s http://backend:8080/actuator/health
docker compose exec nginx curl -s http://frontend:3000 | head -5
```

**预期**：
- [x] backend → keycloak OIDC 端点返回 JSON
- [x] nginx → backend 返回 `{"status":"UP"}`
- [x] nginx → frontend 返回 HTML
- [x] 无 DNS 解析失败或连接拒绝

#### TC-20.3 Docker 日志验证

**操作**：`docker compose logs backend | head -50`

**预期**：
- [x] 日志中有 `Skill loading complete: 32 skills, 5 profiles`
- [x] 日志中有 `Started ForgeWebIdeApplication`
- [x] 无 ERROR 级别日志

#### TC-20.4 Volume 挂载验证

**操作**：
```bash
docker compose exec backend ls /plugins
docker compose exec backend ls /knowledge-base
```

**预期**：
- [x] `/plugins` 包含 forge-foundation / forge-superagent / forge-deployment / forge-knowledge
- [x] `/knowledge-base` 包含 adr / api-docs / conventions / runbooks，总文件数 12+

#### TC-20.5 Keycloak realm 导入

**操作**：`docker compose logs keycloak | grep -i "realm"`

**预期**：
- [x] 日志中有 `forge` realm 创建或导入成功记录
- [x] realm 包含 `forge-web-ide` 客户端和测试用户

#### TC-20.6 Workspace 工具端到端（Docker 内）

**操作**：
```bash
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"workspace_list_files","arguments":{"workspaceId":"1"}}'
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"workspace_write_file","arguments":{"workspaceId":"1","path":"test-docker.txt","content":"hello from docker"}}'
curl -X POST http://localhost:9000/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"name":"workspace_read_file","arguments":{"workspaceId":"1","path":"test-docker.txt"}}'
```

**预期**：
- [x] 三个 workspace 工具均正常工作，无路径权限问题
- [x] write 后 read 返回 `"hello from docker"`

---

### 场景 21：全量测试回归

> 合并原场景 13（agent-eval）和场景 14（全量单元测试）。

#### TC-21.1 agent-eval 结构验证模式

**操作**：
```bash
./gradlew :agent-eval:run
```

**预期**：
- [x] 输出 `WARNING: ANTHROPIC_API_KEY not set, running structure validation only`
- [x] 扫描 `eval-sets/` 目录下的 YAML 文件
- [x] 输出 `Evaluation complete: X passed, Y failed out of Z total`

#### TC-21.2 agent-eval 真实评估模式（需 API Key）

**操作**：`ANTHROPIC_API_KEY=sk-ant-xxx ./gradlew :agent-eval:run`

**预期**：
- [ ] 输出 `Using Claude API for real evaluation`
- [ ] 每个场景调用 Claude API，断言基于真实输出评估

#### TC-21.3 全量单元测试（147 tests）

**操作**：
```bash
./gradlew :web-ide:backend:test :adapters:model-adapter:test :agent-eval:test
```

**预期**：
- [x] BUILD SUCCESSFUL
- [x] backend: 118 tests + model-adapter: 11 tests + agent-eval: 18 tests = 147 tests, 0 failures

---

## 测试结果汇总

| 场景 | 用例数 | 通过 | 备注 |
|------|--------|------|------|
| **一、用户旅程** | | | |
| 1. 新人入职 | 3 | 3/3 | Session 15 |
| 2. 开发日常 | 5 | 3/5 | TC-2.3/2.4 部分阻塞（BUG-016） |
| 3. AI 工具调用 | 4 | 4/4 | Session 16 |
| 4. 代码审查 | 2 | 2/2 | Session 17 |
| 5. 知识库探索 | 5 | 5/5 | Session 16 |
| 6. 对话高级功能 | 5 | 5/5 | Session 17（TC-6.4 BUG-018 修复后） |
| 7. Profile 轮转 | 5 | 5/5 | Session 17 |
| 8. 边界异常 | 4 | 4/4 | Session 17 |
| **用户旅程小计** | **33** | **31/33** | |
| **二、Phase 1.6 核心功能** | | | |
| 9. AI 交付闭环 ★ | 5 | 5/5 | Session 18（TC-9.5 BUG-019 修复后） |
| 10. Context Picker | 3 | 3/3 | Session 18（TC-10.3 BUG-020 修复后） |
| 11. FileExplorer CRUD | 4 | 4/4 | Session 15（修复 9 个 Bug） |
| 12. 编辑器增强 | 3 | 3/3 | Session 15 |
| 13. Keycloak SSO | 4 | 1/4 | TC-13.2~13.4 需 SECURITY_ENABLED=true |
| 14. Header + 导航 | 3 | 2/3 | TC-14.2 Cmd+K 未实现，挂起 |
| **核心功能小计** | **22** | **18/22** | |
| **三、技术验证与回归** | | | |
| 15. API 健康度 | 6 | 6/6 | Session 16~17 |
| 16. Actuator 度量 | 7 | 7/7 | Session 16~18 |
| 17. MCP 直接调用 | 5 | 5/5 | Session 17 |
| 18. 工作流编辑器 | 3 | 3/3 | Session 17 |
| 19. 知识库升级 | 2 | 2/2 | Session 17 |
| 20. Docker 部署 | 6 | 6/6 | Session 17 |
| 21. 全量测试回归 | 3 | 2/3 | TC-21.2 需真实 API Key |
| **技术验证小计** | **32** | **31/32** | |
| **总计** | **87** | **80/87 (92.0%)** | |

### 未通过用例说明

| 用例 | 原因 | 处置 |
|------|------|------|
| TC-2.3/2.4（各 3/2 项） | BUG-016: Agentic loop 耗尽无输出 | ⏸ 挂起 |
| TC-13.2~13.4 | 需启用 FORGE_SECURITY_ENABLED=true | 待安全模式测试 |
| TC-14.2 | Cmd+K / Escape 键盘快捷键未实现 | ⏸ 挂起 |
| TC-21.2 | 需在 Docker 内配置真实 ANTHROPIC_API_KEY | 待补测 |

---

## 启动命令

```bash
# 构建
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home
./gradlew :web-ide:backend:bootJar -x test --no-daemon
cd web-ide/frontend && npm install && npm run build && cd ../..

# Docker 启动（4 容器）
cd infrastructure/docker
docker compose -f docker-compose.trial.yml --env-file .env.trial up --build

# 访问
open http://localhost:9000       # 主应用
open http://localhost:8180       # Keycloak（admin/admin）
```

---

## 关键观察点

1. **OODA 是否流畅**：Observe → Orient → Decide → Act → Done 顺序流转
2. **Profile 切换准确**：显式标签 100% 正确，关键词路由合理
3. **Skills 体现在回复中**：development 应有 Kotlin/Spring Boot 规范
4. **流式体验顺畅**：文字逐字出现，非大段突然出现
5. **AI 写文件而非仅展示代码**：workspace_write_file 工具调用必须出现（Phase 1.6 核心）
6. **文件树自动刷新**：file_changed 事件驱动，无需手动刷新
7. **MCP 工具返回真实数据**：search_knowledge 返回知识库文档，非 mock
8. **度量指标正确记录**：forge.* 指标递增，Prometheus 可导出
9. **Context Picker 实连**：4 类别从后端 API 获取，非 mock
10. **自动保存可靠**：5 秒后蓝色圆点消失，内容持久化

---

## 验收标准对照

### Phase 1.6 验收标准

| # | 验收标准 | 对应测试 | 状态 |
|---|---------|----------|------|
| 1 | AI → Workspace 交付闭环 | TC-9.1~9.5 | ✅ 5/5 |
| 2 | Keycloak SSO | TC-13.1~13.4 | ⚠️ 1/4（需安全模式） |
| 3 | Context Picker 实连 4 类别 | TC-10.1~10.3 | ✅ 3/3 |
| 4 | FileExplorer CRUD | TC-11.1~11.4 | ✅ 4/4 |
| 5 | 未保存标记 + 自动保存 | TC-12.1~12.3 | ✅ 3/3 |
| 6 | 知识库 12+ 文档 | TC-19.1~19.2 | ✅ 2/2 |
| 7 | MCP 9 工具注册 | TC-15.3, TC-20.6 | ✅ 全通过 |
| 8 | Docker 4 容器健康 | TC-20.1~20.6 | ✅ 6/6 |

### Phase 0~1.5 回归标准

| # | 验收标准 | 对应测试 | 状态 |
|---|---------|----------|------|
| 1 | Web IDE 全功能可用 | TC-1~8 | ✅ 31/33 |
| 2 | 32 Skills + 5 Profiles | TC-15.1~15.2 | ✅ |
| 3 | agent-eval 可运行 | TC-21.1~21.2 | ⚠️ 结构模式 ✅ |
| 4 | 147 单元测试全过 | TC-21.3 | ✅（现 164） |

---

## Sprint 2.3 多模型适配器验收

> Session 19 新增。验证多模型提供商支持、YAML 配置化、用户级覆盖、前端选择器。

### 场景 22：多模型提供商注册

> 验证 ModelRegistry 正确注册多个提供商的 Adapter

| TC | 操作 | 预期 |
|----|------|------|
| 22.1 | 设置 ANTHROPIC_API_KEY 启动后端，GET /api/models/providers | - [x] providers 包含 "anthropic"，totalModels >= 3 |
| 22.2 | 设置多个 API Key 启动，GET /api/models | - [x] 返回所有提供商的模型列表，按 provider 分组 |
| 22.3 | GET /api/models/providers/anthropic | - [x] 返回 3 个 Claude 模型 |
| 22.4 | 单元测试 ModelRegistryTest 12 个 | - [x] 全部通过 |

### 场景 23：Adapter 流式 + Tool Calling

> 验证各 Adapter 的 SSE 解析和 tool calling 事件

| TC | 操作 | 预期 |
|----|------|------|
| 23.1 | ClaudeAdapterToolCallingTest 11 个 | - [x] SSE 解析、tool_use 事件、错误处理全通过 |
| 23.2 | QwenAdapterTest 9 个 | - [x] OpenAI 格式 tool calling、流式、请求体格式全通过 |
| 23.3 | GeminiAdapterTest 10 个 | - [x] Gemini functionCall 格式、functionResponse 全通过 |
| 23.4 | 42 个 model-adapter 测试 | - [x] 全部通过 |

### 场景 24：application.yml 配置化

> 验证模型列表从 YAML 外部化配置

| TC | 操作 | 预期 |
|----|------|------|
| 24.1 | application.yml 包含 forge.models 配置段 | - [x] 5 个提供商配置，16 个模型定义 |
| 24.2 | ModelProperties @ConfigurationProperties 绑定 | - [x] 编译通过，后端启动正常 |
| 24.3 | Adapter customModels 参数覆盖内置列表 | - [x] YAML 配置的模型列表优先于 Adapter 硬编码 |

### 场景 25：用户模型配置（加密存储）

> 验证用户可覆盖系统模型配置，API Key 加密存储

| TC | 操作 | 预期 |
|----|------|------|
| 25.1 | PUT /api/user/model-configs/anthropic（含 apiKey） | - [x] 返回 hasApiKey=true, apiKeyMasked 脱敏 |
| 25.2 | GET /api/user/model-configs | - [x] 返回用户所有配置，API Key 脱敏 |
| 25.3 | DELETE /api/user/model-configs/anthropic | - [x] 204 No Content |
| 25.4 | EncryptionServiceTest 9 个 | - [x] 加解密往返、不同密文、错误密钥检测全通过 |
| 25.5 | UserModelConfigServiceTest 8 个 | - [x] CRUD + 脱敏 + 空 key 保留全通过 |

### 场景 26：前端模型选择器 + 设置弹窗

> 验证前端 UI 组件

| TC | 操作 | 预期 |
|----|------|------|
| 26.1 | 打开 AI Chat，header 显示 ModelSelector | - [x] 按提供商分组下拉，显示费用图标 |
| 26.2 | 点击 Settings 齿轮按钮 | - [x] 弹出 ModelSettingsDialog，5 个提供商卡片 |
| 26.3 | npm run build 零错误 | - [x] 编译通过 |

---

### Sprint 2.3 验收标准

| # | 验收标准 | 对应测试 | 状态 |
|---|---------|----------|------|
| 1 | 4 提供商 Adapter 实现 | TC-22~23 | ✅ |
| 2 | 模型清单 YAML 配置化 | TC-24 | ✅ |
| 3 | 用户配置加密存储 | TC-25 | ✅ |
| 4 | 前端选择器 + 设置弹窗 | TC-26 | ✅ |
| 5 | 164 单元测试全过 | TC-23.4 + TC-25.4~25.5 | ✅ |

---

## Bug 清单

> 详细记录见 `docs/buglist.md`

| Session | Bug ID | 等级 | 影响场景 | 简述 |
|---------|--------|------|---------|------|
| 15 | BUG-001 | P1 | TC-11.1 | FileExplorer 空白区域右键无菜单 |
| 15 | BUG-002 | P2 | TC-11.1 | 右键文件夹路径被事件冒泡覆盖 |
| 15 | BUG-003 | P2 | TC-11.1 | 文件可重名创建 |
| 15 | BUG-004 | P2 | TC-11.1 | 重名创建无反馈 |
| 15 | BUG-005 | P1 | TC-11.4 | 无法删除文件夹 |
| 15 | BUG-006 | P2 | TC-11.1 | 重名校验全局级别 |
| 15 | BUG-007 | P2 | TC-11.1 | New File 创建到根目录 |
| 15 | BUG-008 | P0 | TC-11.2 | 文件树不显示层级（枚举序列化） |
| 15 | BUG-009 | P2 | TC-11.2 | rebuildFileTree 只支持 2 层 |
| 15 | BUG-010 | P2 | TC-21.3 | McpControllerTest mock 签名不匹配 |
| 15 | BUG-011 | P2 | 构建 | handleFileSelect 声明顺序错误 |
| 15 | BUG-012 | P0 | TC-2.1/9.1 | AI 不写文件到 workspace |
| 16 | BUG-013 | P1 | TC-2.2 | sessionId 未持久化 |
| 16 | BUG-014 | P2 | TC-2.3 | ContextPicker 抢焦点 |
| 16 | BUG-015 | P2 | TC-2.3 | @Profile 标签被拦截 |
| 16 | BUG-016 | P2 | TC-2.3 | Agentic loop 耗尽无输出（⏸ 挂起） |
| 16 | BUG-017 | P1 | TC-5.4 | Services 页面白屏 |
| 17 | BUG-018 | P2 | TC-6.4 | Knowledge tab 无内容 |
| 18 | BUG-019 | P2 | TC-9.5 | Apply/Copy 按钮不可见 |
| 18 | BUG-020 | P2 | TC-10.3 | 搜索过滤无反应 |

**累计 20 个 Bug：19 个已修复，1 个挂起（BUG-016）。**
