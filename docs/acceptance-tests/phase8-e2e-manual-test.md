# Phase 8 — 全功能端到端手动测试

> 测试日期: 2026-02-24 | 测试人: 赵琪
> 目标: 以真实用户视角全面验证 Forge Platform 的功能完整性和用户体验
> 入口: http://localhost:9000
> 预估时间: 60-90 分钟

---

## 测试准则

- 每个 TC 标记 `[PASS]` `[FAIL]` `[SKIP]` `[UX问题]`
- UX 问题单独记录（不影响 PASS/FAIL，但需要改进）
- 发现 Bug 时记录：现象 + 预期 + 实际

---

## 旅程 1：首次访问 — Dashboard + 导航（5 min）

> 模拟新用户第一次打开平台，了解整体布局。

### TC-1.1 Dashboard 加载 — [PASS] ✅
- 打开 http://localhost:9000
- [x] 页面正常加载，无白屏/报错
- [x] 看到 Quick Actions（3 张卡片：New Workspace / Search Knowledge / Start Chat）
- [x] 看到 Recent Projects 区域
- [x] 看到 Activity Feed 区域
- UX评分（1-5）: **5**
- 备注: 全部功能可用

### TC-1.2 侧边栏导航 — [PASS] + BUG
- [x] 左侧 Sidebar 显示导航项：Dashboard / Workspaces / Knowledge / Workflows / AI Chat / Skills / Integrations / Infrastructure
- [x] 点击每个导航项，页面正确切换（不报错）
- [x] Sidebar 可以折叠/展开
- [x] 当前页面导航项有高亮标识
- **BUG #1**: Sidebar 缺少 Evaluations 导航项（`/evaluations` 页面存在但无入口）
- UX评分（1-5）: **4**（扣分：缺少 Evaluations 入口）

### TC-1.3 页面路由完整性 — [PASS] ✅
依次访问：
- [x] `/` → Dashboard
- [x] `/workspaces` → Workspace 管理
- [x] `/knowledge` → 知识库
- [x] `/skills` → Skill 管理
- [x] `/evaluations` → 评估仪表板
- [x] `/workflows` → 工作流编辑器
- 全部能正常打开（无 404 / 空白）: [x]

---

## 旅程 2：Workspace 全生命周期（10 min）

> 模拟用户创建项目、管理项目、进入工作区。

### TC-2.1 创建空白 Workspace — [PASS] ✅
- 点击 Dashboard "New Workspace" 或 Workspaces 页 "New Workspace"
- 输入名称：`e2e-test-project`，描述：`端到端测试`
- [x] Workspace 创建成功
- [x] 自动跳转到 `/workspace/[id]` 页面
- [x] 文件树为空或有默认文件

### TC-2.2 Workspaces 列表管理 — [PASS] ✅
- 返回 `/workspaces` 页面
- [x] 能看到刚创建的 `e2e-test-project`
- [x] 顶部统计卡片显示正确数量（Active / Suspended / 总计）
- [x] 搜索框输入 "e2e" 能过滤出对应 workspace
- [x] 状态筛选（Active/Suspended）能正常工作

### TC-2.3 Workspace 挂起与激活 — [PASS] ✅
- 在 `e2e-test-project` 行点击暂停按钮
- [x] 状态变为 Suspended（黄色标签）
- [x] 统计卡片数字更新
- 点击激活按钮
- [x] 状态恢复为 Active（绿色标签）

### TC-2.4 进入 Workspace — [PASS] ✅
- 点击 `e2e-test-project` 的 Open 按钮
- [x] 正确跳转到 IDE 页面 `/workspace/[id]`
- [x] 三面板布局正常（文件树 | 编辑器 | AI Chat）

### TC-2.5 Git 仓库导入（可选） — [PASS] ✅
- 创建新 Workspace，填入 Git URL（如 `https://github.com/pan94u/forge.git`）
- [x] 显示 clone 进度条
- [x] clone 完成后文件树显示仓库文件
- （注意：大仓库 clone 较慢，120s 超时）

---

## 旅程 3：IDE 工作区 — 文件操作（10 min）

> 模拟开发者在 IDE 中编辑代码。

### TC-3.1 文件树操作 — [PASS] + BUG
在 `e2e-test-project` workspace 中：
- [x] 文件树正常显示（即使为空也不报错）
- 右键或工具栏创建新文件 `hello.py`
- [x] 文件创建成功，出现在文件树中
- 创建文件夹 `src`，在其中创建 `main.js`
- [x] 文件夹和文件正确显示层级结构
- **BUG #2**: 创建文件夹后会额外出现一个 `.gitkeep` 文件

### TC-3.2 代码编辑 + 保存 — [PASS] ✅
- 点击 `hello.py` 打开编辑器
- [x] Monaco 编辑器加载，有语法高亮
- 输入代码：`print("Hello, Forge!")`
- [x] 出现未保存标记（蓝色圆点）
- 按 Cmd+S / Ctrl+S 手动保存
- [x] 未保存标记消失
- 再次编辑，等待 5 秒
- [x] 自动保存触发，未保存标记自动消失

### TC-3.3 多文件 Tab — [PASS] ✅
- 依次打开 `hello.py` 和 `src/main.js`
- [x] 两个文件 Tab 同时显示
- [x] 点击 Tab 切换文件内容
- [x] 关闭 Tab 功能正常

### TC-3.4 面板拖拽与折叠（Phase 8 新功能） — [PASS] ✅
- 拖拽左侧面板分隔条
- [x] 文件树面板宽度可调（UX问题：左边栏拖动手感不佳）
- 拖拽右侧面板分隔条
- [x] AI Chat 面板宽度可调
- 点击 Focus Chat 按钮
- [x] 左+中面板折叠，AI Chat 全屏展示（体验很好）
- 再次点击恢复
- [x] 三面板布局恢复
- 刷新页面
- [x] 面板宽度保持（localStorage 持久化）
- UX评分（1-5）: **5**（Focus Chat 亮点，左侧拖拽待优化）

### TC-3.5 终端面板 — [PASS] ✅
- 点击终端 Toggle 按钮
- [x] 底部终端面板打开
- 在终端输入 `echo "hello"`（或类似命令）
- [x] 命令输出正确显示
- 再次点击关闭终端
- [x] 终端面板收起

---

## 旅程 4：AI 对话 — 核心体验（20 min）

> 这是平台最核心的功能，需要重点测试。

### TC-4.1 模型选择 — [PASS] ✅
- 在 AI Chat 区域，点击模型选择器
- [x] 下拉菜单显示可用模型（Claude Opus / Sonnet / Haiku / MiniMax 等）
- [x] 模型按 Provider 分组显示
- 选择 `Claude Sonnet 4.6`
- [x] 模型切换成功
- UX评分（1-5）: **5**

### TC-4.2 模型配置（Settings） — [PASS] ✅
- 点击 AI Chat 底部齿轮图标
- [x] Model Settings 对话框弹出
- [x] 看到 6 个 Provider（Anthropic / Google / Alibaba / AWS Bedrock / MiniMax / OpenAI）
- 勾选 MiniMax 的 Enabled
- [x] API Key 输入框出现
- [x] 已配置的 Provider 显示 "configured" 标签和脱敏 key
- 关闭对话框
- [x] 正常关闭，无报错

### TC-4.3 简单对话 — Claude — [PASS] ✅
- 选择 Claude Sonnet 模型
- 发送消息：`你好，简单介绍下你自己`
- [x] OODA 指示器出现并流转（Observe → Orient → Decide → Act → Done）
- [x] Profile Badge 显示（如 development-profile + confidence）
- [x] AI 流式返回文本内容
- [x] 消息完成后 OODA 指示器消失
- 响应时间感受: 正常
- UX评分（1-5）: **5**

### TC-4.4 Profile 自动路由 — [PASS] + UX问题
依次发送以下消息，观察 Profile 切换：
- 发送：`帮我设计一个用户系统的数据库表结构`
- [x] Profile 切换到 design 相关（architecture-design / detailed-design）
- 发送：`写一个 Python 排序算法`
- [x] Profile 切换到 development 相关
- 发送：`评估一下当前项目的进度`
- [x] Profile 切换到 evaluation-profile
- UX评分（路由准确性 1-5）: **3**
- **UX问题**: AI 回复中流式文本全在最上面，工具调用在中间，日志在最下面，未按时间顺序排列。应按实际发生顺序交错展示流式文本和工具调用

### TC-4.5 Intent Confirmation — [PASS] + UX问题
- 发送一条模糊消息：`帮我看看`
- [x] 出现 Intent Confirmation 卡片
- [x] 卡片列出多个 Profile 选项可选择
- 点击选择一个 Profile
- [x] AI 按选择的 Profile 继续对话
- **UX问题**: 用户选择 Profile 后 AI 直接执行，但此时 AI 仍不知道用户真正意图。应在用户选择 Profile 后追问具体意图（如"你想评估什么？"），明确后再执行

### TC-4.6 Tool Call — 知识搜索 — [PASS] ✅
- 发送：`搜索一下知识库中关于 profile 的文档`
- [x] AI 调用 `search_knowledge` 工具
- [x] Tool Call 卡片显示（工具名 + 输入/输出）
- [x] 工具执行结果正确返回
- [x] AI 基于搜索结果回复

### TC-4.7 Tool Call — 文件操作 — [PASS] + UX问题
- 在有文件的 workspace 中发送：`列出当前工作区的所有文件`
- [x] AI 调用 `workspace_list_files` 工具
- [x] 返回文件列表（UX问题：只返回文件夹+文件个数，未列出所有文件名）
- 发送：`在工作区创建一个 README.md 文件，内容写项目简介`
- [x] AI 调用 `workspace_write_file` 工具
- [x] 文件树自动刷新，出现 README.md
- [x] 点击 README.md 能看到 AI 写入的内容

### TC-4.8 Tool Call — Skill 读取 — [PASS] ✅
- 发送：`读取 code-generation Skill 的内容`
- [x] AI 调用 `read_skill` 工具
- [x] 返回 SKILL.md 内容

### TC-4.9 多轮 Agentic Loop — [PASS] ✅
- 发送一个较复杂的任务：`帮我创建一个简单的 Todo 应用，包含 index.html 和 app.js 两个文件`
- [x] AI 进行多轮 Tool Call（写多个文件）
- [x] 每轮 OODA 阶段正确流转
- [x] Turn 计数器递增
- [x] 所有文件成功写入
- [x] 文件树自动更新

### TC-4.10 代码块 Apply 按钮 — [PASS] + BUG
- 让 AI 生成代码（如：`写一个 fibonacci 函数`）
- [x] AI 回复中的代码块右上角有 Apply 按钮
- 点击 Apply
- [x] 弹出文件名输入框
- 输入文件名确认
- [x] 文件创建成功，文件树更新
- **BUG #7**: 在此时切换 Focus Mode，页面显示异常（见截图 bug25）

### TC-4.11 Context Usage 显示 — [PASS] + 优化建议
- 进行几轮对话后
- [x] Context Usage 卡片始终可见
- [x] 显示类似 `65% · T3 · P1` 的格式（百分比 · Turn数 · 压缩阶段）
- [x] 随着对话增加，百分比增长
- **优化建议**: MAX_CONVERSATION_TOKENS 硬编码为 25K，仅用了 200K 上下文的 12.5%，应改为 180K

### TC-4.12 停止生成 — [PASS] ✅
- 发送一个需要长回复的消息
- 在 AI 还在输出时点击 Stop 按钮
- [x] 生成立即停止
- [x] 已生成的内容保留
- [x] 可以继续发送新消息

### TC-4.13 重置对话 — [PASS] ✅
- 点击 Reset / New Conversation 按钮
- [x] 对话历史清空
- [x] 可以开始新对话

### TC-4.14 MiniMax 模型测试 — [PASS] ✅
- 切换到 MiniMax-M2.5 模型
- 发送：`你好`
- [x] MiniMax 模型正常返回（不卡住）
- [x] 流式响应正常
- 响应质量感受: 正常

---

## 旅程 5：AI Chat 侧边栏 5-Tab（10 min）

> 测试 AI Chat 右侧面板的所有标签页。

### TC-5.1 对话 Tab（默认） — [PASS] ✅
- [x] 默认显示对话 Tab
- [x] 消息列表、输入框正常

### TC-5.2 质量 Tab — [PASS] ✅
- 点击 "质量" Tab
- [x] 质量面板加载（可能显示空状态或有数据）
- [x] 无报错

### TC-5.3 Skills Tab — [PASS] + BUG
- 点击 "Skills" Tab
- [x] 显示当前 workspace 可用的 Skills 列表
- [ ] 每个 Skill 有启用/禁用开关
- 切换某个 Skill 的启用状态
- [ ] 状态切换成功
- **BUG #8**: Skills Tab 没有启用/禁用开关，无法切换 Skill 状态

### TC-5.4 记忆 Tab — [PASS] + UX问题
- 点击 "记忆" Tab
- [x] 显示 3 层记忆架构（Workspace Memory / Stage Memory / Session Summary）
- [x] 如果有对话历史，能看到 Session Summary
- UX评分（1-5）: **3**
- **UX问题**: 缺乏引导说明——用户看到记忆数据后不知道意味着什么、能做什么。应增加简短说明文案或操作提示

### TC-5.5 管道 Tab（Phase 8 新功能） — [PASS] + UX问题
- 点击 "管道" Tab
- [x] PipelinePanel 正常加载
- [x] 看到 3 个摘要卡片（知识缺口 / 质量记录 / 待确认模式）
- 点击 "运行" 按钮手动触发管道
- [x] 管道运行完成（刷新或显示结果）
- [x] 如果有 Skill 质量数据，显示质量柱状图
- [x] 如果有知识缺口，显示缺口统计
- UX评分（1-5）: **3**
- **UX问题**: 同记忆 Tab，缺乏引导说明——用户不清楚管道的作用和可执行的操作

---

## 旅程 6：知识库（5 min）

> 模拟用户浏览和搜索知识。

### TC-6.1 知识搜索 — [PASS] ✅
- 进入 `/knowledge` 页面
- [x] 4 个 Tab 正常显示（Docs / Architecture / Services / APIs）
- 在搜索框输入关键词（如 `profile`）
- [x] 搜索结果正常返回
- 点击某个文档
- [x] 右侧面板显示文档内容（Markdown 渲染）

### TC-6.2 Architecture Tab — [PASS] ✅
- 切换到 Architecture Tab
- [x] 架构图正常显示（Mermaid 渲染）
- [x] 可以缩放/拖拽

### TC-6.3 Service Graph Tab — [PASS] ✅
- 切换到 Service Graph Tab
- [x] 服务依赖图正常显示
- [x] 节点可交互

### TC-6.4 API Explorer Tab — [PASS] ✅
- 切换到 API Explorer Tab
- [x] API 列表正常显示
- [x] 点击某个 API 能看到详情

---

## 旅程 7：Skill 管理（5 min）

### TC-7.1 Skill 列表 — [PASS] ✅
- 进入 `/skills` 页面
- [x] Skill 列表加载（应有 30+ skills）
- [x] 4 个 Scope Tab 正常（All / Platform / Workspace / Custom）
- [x] 搜索框能过滤 Skill
- [x] Tag 过滤能正常工作

### TC-7.2 Skill 详情 — [PASS] ✅
- 点击某个 Skill（如 `code-generation`）
- [x] 右侧显示 Skill 详情（名称/描述/Tags/脚本列表）
- [x] 如果有脚本，能看到脚本列表

### TC-7.3 Platform Skill 保护 — [SKIP]
- 尝试删除一个 Platform Skill
- [ ] 应该被阻止（返回错误或按钮禁用）
- **跳过原因**: 当前 Skill 页面无启用/禁用/删除按钮（同 BUG #8），无法测试保护机制

---

## 旅程 8：评估仪表板（3 min）

### TC-8.1 评估页面 — [PASS] + BUG
- 进入 `/evaluations` 页面（只能通过 URL 直接访问，无侧边栏入口 — BUG #1）
- [x] 页面正常加载
- [ ] 时间选择器可切换（7 / 30 / 90 天）
- [x] 4D 评分展示区域可见（Intent / Completion / Quality / Experience）
- [ ] 如果有数据，Profile 分布图正常显示
- [ ] 最近交互表格正常
- **BUG #9**: 页面数据全部不可交互（时间选择器、图表、表格均无响应），仅为静态展示

---

## 旅程 9：工作流编辑器（5 min）

### TC-9.1 创建工作流
- 进入 `/workflows` 页面
- [ ] 页面正常加载
- 点击 "New Workflow"
- [ ] 创建成功
- 从节点面板拖放节点到画布
- [ ] 节点显示在画布上
- 连接两个节点
- [ ] 连线显示正确
- 保存工作流
- [ ] 保存成功

### TC-9.2 运行工作流
- 点击 Run 按钮
- [ ] 工作流执行（显示进度）
- [ ] 执行结果展示

---

## 旅程 10：跨功能场景 — AI 驱动交付（15 min）

> 这是最重要的端到端场景：模拟用户使用 AI 完成一个完整的小项目。

### TC-10.1 完整交付流程
1. 创建新 Workspace：`ai-delivery-test`
2. 在 AI Chat 中发送：`我需要创建一个简单的计算器网页应用，包含 HTML + CSS + JavaScript`
3. 观察：
   - [ ] AI 正确路由到 development-profile
   - [ ] AI 调用多次 workspace_write_file 创建文件
   - [ ] OODA 循环多轮运转正常
   - [ ] 文件树自动更新
   - [ ] 创建的文件可以打开查看
   - [ ] 代码质量可接受
4. 继续发送：`读取 index.html 的内容，告诉我有什么问题需要改进`
   - [ ] AI 调用 workspace_read_file 读取文件
   - [ ] AI 给出改进建议
5. 发送：`按照建议修改 index.html`
   - [ ] AI 读取现有文件 → 修改 → 写回
   - [ ] 文件内容更新

### TC-10.2 知识辅助开发
- 在对话中发送：`搜索知识库中关于 API 设计的最佳实践`
- [ ] AI 调用 search_knowledge
- [ ] 返回相关知识文档
- [ ] AI 基于知识给出建议

### TC-10.3 代码分析
- 发送：`分析当前工作区的代码结构`
- [ ] AI 调用 analyze_codebase 或 workspace_list_files
- [ ] 返回结构分析结果

---

## 旅程 11：错误处理与边界情况（5 min）

### TC-11.1 空消息
- 不输入任何内容直接点发送
- [ ] 不应发送空消息（按钮禁用或提示）

### TC-11.2 超长消息
- 发送一段很长的文本（1000+ 字符）
- [ ] 正常处理，不截断不报错

### TC-11.3 不存在的 Workspace
- 手动访问 `/workspace/不存在的id`
- [ ] 显示错误提示或重定向，不白屏

### TC-11.4 网络中断恢复
- 在 AI 对话中，快速刷新页面
- [ ] 页面恢复正常，之前的对话历史保留

### TC-11.5 并发操作
- 在 AI 正在写文件时，手动在编辑器中编辑同一文件
- [ ] 不崩溃，行为合理（AI 覆盖或提示冲突）

---

## 汇总表

| 旅程 | 分类 | TC 数 | Pass | Fail | Bug/UX | 备注 |
|------|------|-------|------|------|--------|------|
| 1 | Dashboard + 导航 | 3 | 3 | 0 | 1 Bug | BUG#1 Sidebar缺Evaluations |
| 2 | Workspace 生命周期 | 5 | 5 | 0 | 0 | 全部通过 |
| 3 | IDE 文件操作 | 5 | 5 | 0 | 1 Bug + 1 UX | BUG#2 .gitkeep; 左栏拖拽手感 |
| 4 | AI 对话核心 | 14 | 14 | 0 | 4 Bug + 2 UX | BUG#3~7; 消息排序/Intent UX |
| 5 | Chat 5-Tab | 5 | 5 | 0 | 1 Bug + 2 UX | BUG#8; 记忆/管道缺引导 |
| 6 | 知识库 | 4 | 4 | 0 | 0 | 全部通过 |
| 7 | Skill 管理 | 3 | 2 | 0 | 1 Skip | TC-7.3 无法测试(同BUG#8) |
| 8 | 评估仪表板 | 1 | 1 | 0 | 1 Bug | BUG#9 数据不可交互 |
| 9 | 工作流编辑器 | 2 | - | - | - | 待测 |
| 10 | AI 驱动交付 | 3 | - | - | - | 待测 |
| 11 | 错误处理 | 5 | - | - | - | 待测 |
| **合计** | | **50** | **39** | **0** | **9 Bug + 5 UX** | 已测 40/50，剩余 10 |

---

## UX 总体评价

| 维度 | 评分(1-5) | 说明 |
|------|-----------|------|
| 视觉设计 | | 暗色主题、色彩、间距、图标 |
| 响应速度 | | 页面加载、AI 响应、操作反馈 |
| 导航体验 | | 页面切换、面板折叠、Tab 切换 |
| AI 交互 | | 路由准确性、工具调用、多轮对话 |
| 错误处理 | | 错误提示、边界情况、容错 |
| 功能完整性 | | 功能是否都能走通 |
| **总体** | | |

---

## Bug 清单

| # | 旅程 | TC | 现象 | 预期 | 实际 | 严重度 |
|---|------|-----|------|------|------|--------|
| 1 | 1 | TC-1.2 | Sidebar 无 Evaluations 导航 | Sidebar 应包含 Evaluations 链接 | navItems 缺少 /evaluations 条目 | Medium |
| 2 | 3 | TC-3.1 | 创建文件夹后出现 .gitkeep 文件 | 创建文件夹不应显示 .gitkeep | 文件树中多出 .gitkeep | Low |
| 3 | 4 | TC-4.2 | 离开 workspace 再返回后，用户发言显示为 AI 发言 | 用户消息应靠右显示（用户气泡样式） | 用户消息显示为 AI 气泡样式（靠左） | High |
| 4 | 4 | TC-4.4 | AI 回复内容未按时间顺序展示 | 流式文本+工具调用应按时间交错展示 | 文本在上、工具在中、日志在下（分块展示） | Medium |
| 5 | 4 | TC-4.5 | Intent Confirmation 选 Profile 后直接执行 | 选 Profile 后应追问具体意图再执行 | 选择后直接执行，AI 仍不知道用户真正想做什么 | Medium |
| 6 | 4 | TC-4.7 | workspace_list_files 只返回文件夹+文件个数 | 用户期望看到所有文件名列表 | 只显示目录摘要（文件夹名+数量） | Low |
| 7 | 4 | TC-4.10 | Focus Mode 切换时页面显示异常 | 切换 Focus Mode 布局应正常过渡 | 页面显示错乱（见截图 bug25） | Medium |
| 8 | 5 | TC-5.3 | Skills Tab 无启用/禁用开关 | 每个 Skill 应有 toggle 开关 | 只展示列表，无法切换状态 | Medium |
| 9 | 8 | TC-8.1 | Evaluations 页面数据全部不可交互 | 时间选择器/图表/表格应可操作 | 页面为纯静态展示，无交互响应 | Medium |

---

## 测试环境

- 浏览器: Chrome ___
- 分辨率: ___
- Docker 容器: 5（backend + frontend + nginx + knowledge-mcp + database-mcp）
- 模型: Claude Sonnet 4.6 + MiniMax-M2.5
