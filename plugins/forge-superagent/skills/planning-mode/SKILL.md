---
name: planning-mode
description: "Planning Mode skill for guided execution of large tasks. Provides structured task decomposition, user confirmation, per-task verification, retry logic, and quality summary."
stage: planning
type: delivery-skill
version: "1.0"
tools:
  - plan_create
  - plan_update_task
  - plan_ask_user
  - plan_complete
  - update_workspace_memory
---

# Planning Mode — 大任务引导执行 Skill

## 概述

Planning Mode 是 SuperAgent 处理大型、多步骤任务的结构化工作模式。它通过"先想后做"强制 AI 在执行前澄清意图、拆解任务、获得用户确认，执行过程中验证每步结果，失败时执行重试决策树，完成后提供质量总结。

---

## 需求澄清框架（5W1H）

在意图不明确时，使用 `plan_ask_user` 提问，覆盖以下维度：

| 维度 | 问题示例 | 类型 |
|------|---------|------|
| **What** | 哪些文件/模块需要修改？整个模块还是特定功能？ | choice/text |
| **Why** | 这个改动的业务目标是什么？ | text |
| **Who** | 影响哪些角色/用户？ | choice |
| **When** | 是否有截止日期或优先级约束？ | choice |
| **Where** | 修改范围：前端/后端/全栈/基础设施？ | choice |
| **How** | 技术方案偏好（如有）：新建 vs 改造，框架选型等？ | choice/text |

澄清原则：
- 每次 `plan_ask_user` 最多提 3 个问题，避免信息过载
- 选择题（choice）提供 3-4 个选项，覆盖最常见场景
- 问答题（text）用于开放性背景信息收集
- 回答收到后，结合上下文推断其余细节，不再反复询问

---

## 任务分解原则（MECE）

**MECE = Mutually Exclusive, Collectively Exhaustive**（相互独立、完整覆盖）

### 分解规则

1. **独立性**：每个任务可以单独执行，不依赖其他任务的中间状态
   - 好：`创建 UserService`、`创建 UserController`
   - 差：`创建 UserService 并修复它在 UserController 里的调用`

2. **显式依赖**：若任务有先后关系，在任务描述中明确标注
   - 例：`任务 3 依赖任务 1 和任务 2 完成`

3. **完整覆盖**：所有子任务的并集 = 完整需求，没有遗漏也没有重叠

4. **粒度控制**：单个任务预计耗时 1-4 小时（对应改动行数 50-200 行）
   - 改动 >200 行的任务必须继续拆分
   - 改动 <20 行的任务可以合并（避免过细）

5. **文件绑定**：每个任务必须列出主要涉及文件（≥1 个），让用户能评估影响范围

### 任务结构模板

```
{
  "id": "task-001",
  "title": "创建 UserService（≤20字）",
  "files": ["src/service/UserService.kt", "src/model/User.kt"],
  "successCriteria": "workspace_compile 返回零错误；UserServiceTest 所有用例通过",
  "estimatedLines": 120,
  "dependsOn": []
}
```

---

## 成功标准写法（可验证）

成功标准必须是可以通过工具调用客观验证的，而非主观描述。

| 类型 | 可验证写法 | 不可验证写法 |
|------|-----------|------------|
| 编译 | `workspace_compile 返回零错误` | "代码写正确" |
| 测试 | `workspace_test 全部通过` | "功能正常" |
| API | `GET /api/users 返回 200 + JSON 数组` | "接口可用" |
| 文件 | `workspace_read_file 返回非空内容` | "文件已创建" |
| 构建 | `npm run build 无 TypeScript 错误` | "前端完成" |

**每个任务至少有 1 个编译/测试类的可验证标准**，不能只依赖代码生成即视为完成。

---

## 失败决策树

任务执行后验证失败时，按以下决策树处理：

```
验证失败
    │
    ├─ 第 1 次失败
    │       ├─ 诊断根因（读错误信息、定位问题文件）
    │       ├─ 制定修正方案（明确与第 1 次的区别）
    │       └─ 自动重试 → plan_update_task(taskId, "in_progress", detail="重试1")
    │
    ├─ 第 2 次失败
    │       ├─ 换替代方案（不同实现路径）
    │       └─ 自动重试 → plan_update_task(taskId, "in_progress", detail="重试2:替代方案")
    │
    └─ 第 3 次仍失败
            ├─ plan_update_task(taskId, "blocked")
            ├─ plan_ask_user 说明：
            │     - 已尝试的 3 种方案
            │     - 当前错误信息（关键部分）
            │     - 可选方向（跳过/换方案/提供更多信息）
            └─ 等待用户决策后继续
```

诊断原则：
- 每次重试前，先用 `workspace_read_file` 读取报错文件的相关代码
- 不要在同一错误上重复执行相同操作超过 2 次
- 编译错误优先看具体行号；测试错误优先看断言失败的值

---

## 总结质量标准（三段式）

`plan_complete` 的 summary 必须包含以下三段：

### 做了什么（What）
- 完成任务列表（打勾格式）
- 失败/跳过任务说明
- 关键技术决策：选择了什么方案，为什么

### 遇到什么（Issues）
- 遇到的技术障碍及解决方式
- 未解决的已知问题（标注影响范围）
- 与原计划的偏差（如有）

### 建议什么（Next Steps）
- 至少 2 条具体的后续工作建议
- 按优先级排序（P1/P2/P3）
- 每条建议有明确的行动描述

**总结示例**：

```
## 做了什么
- ✅ 创建 UserService（120行）- 选用 Spring Cache 而非手动 Redis 以减少依赖
- ✅ 创建 UserController（80行）
- ❌ 用户头像上传（已跳过）- S3 配置信息缺失，需运维提供

## 遇到什么
- Kotlin 枚举序列化问题：添加了 @JsonValue 注解，全局排查完毕
- 测试数据库连接超时：改用 @DataJpaTest 内存模式解决

## 建议什么
- P1: 补充 S3 配置后完成头像上传功能（约 2 小时）
- P2: 为 UserService 添加缓存单元测试（缓存击穿场景）
- P3: 考虑将 User 领域拆为独立模块（当前 UserService 已超 300 行）
```

---

## Context 管理建议

当预计任务集总 context 超过 300k tokens 时：

1. **分 Session 规划**：在 `plan_create` 的任务列表中，将任务划分为 Session A / Session B
2. **记忆持久化**：完成 Session A 后，用 `update_workspace_memory` 记录：
   - 已完成的任务和关键决策
   - 未完成任务的当前状态
   - 下一 Session 的起点
3. **Session B 启动**：新 Session 开始时先读 workspace memory，从记录的起点继续
