# Phase 4 实施计划 — Skill 架构改造（对齐 Anthropic Agent Skills 标准）

> 版本: v1.0 | 日期: 2026-02-22 | 状态: 待实施

---

## 一、背景与目标

### 1.1 问题诊断

Phase 3 验收（Session 23-24，28 个 Bug 修复）暴露了核心架构缺陷：**Skill 不是一等公民**。

当前实现把 Skill 当成"被注入 system prompt 的 Markdown 片段"，与 Anthropic 的 Agent Skills 设计理念存在根本性差距：

| 维度 | 当前实现 | Anthropic 标准 |
|------|----------|----------------|
| 加载方式 | 全量注入 system prompt | Level 1 metadata → 按需读 SKILL.md → 按需读子文件 |
| System prompt 中的 Skill | ~55K chars（全部内容） | ~700 chars（7 skill × 100 tokens metadata） |
| 可执行脚本 | 不支持（只有 Markdown） | scripts/ 目录，Agent 执行脚本只获取输出 |
| 用户管理 | 不可见、不可选 | 可视、可选、可创、可加载、可删除 |
| 生态 | 静态 32 个文件 | 动态生态：使用效果追踪、优胜劣汰 |
| 内容分层 | 单一 SKILL.md | SKILL.md + reference/ + examples/ + scripts/ |

### 1.2 Anthropic Agent Skills 核心理念

**参考**：
- [Agent Skills Overview](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
- [Skill Authoring Best Practices](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices)
- [Equipping Agents for the Real World](https://claude.com/blog/equipping-agents-for-the-real-world-with-agent-skills)

**3 层渐进式披露**：

```
Level 1: Metadata（始终加载，~100 tokens/skill）
  system prompt 只含 name + description
  ↓ 用户请求匹配时
Level 2: SKILL.md 指令（按需读取，< 5K tokens）
  Agent 自行决定是否 read SKILL.md
  ↓ 需要详细参考时
Level 3: 子文件 + 可执行脚本（按需读取/执行，无上限）
  reference/*.md, examples/*.md, scripts/*.py
  脚本执行只返回 stdout，代码不进入 context
```

**关键原则**：
- **Metadata 是发现机制**：system prompt 只含 name + description
- **SKILL.md < 500 行**：只写 Claude 不知道的项目特有知识
- **Scripts 提供确定性操作**：代码保可靠性（validate.py），指令保灵活性（SKILL.md），资源保查阅（reference/）
- **渐进式披露**：Agent 自己决定何时读取何种深度
- Skills 对用户**可视、可选、可创、可删**（system foundation skill 除外）
- Skills 是**生态**：优胜劣汰，使用效果驱动进化

### 1.3 Phase 4 目标

将 Forge Skill 架构从"system prompt 注入物"改造为**对齐 Anthropic 标准的渐进式披露生态**：

1. **Metadata-first**：Agent 先通过 metadata 发现 Skill，再按需加载
2. **按需加载**：SKILL.md 和子文件通过 MCP 工具读取，不注入 system prompt
3. **可执行脚本**：Skill 自带质量验证脚本，Agent 执行获取确定性结果
4. **用户可管理**：Skill 可视、可选择、可创建、可加载、可删除
5. **生态进化**：使用效果追踪 → 优胜劣汰 → 持续改进

---

## 二、4 个 Sprint

### Sprint 4.1：Skill Metadata 架构 + 渐进式加载（后端核心改造）

**目标**：将 Skill 从"system prompt 注入物"改造为"metadata 发现 → 按需加载"的 3 层架构。

#### 2.1.1 SkillDefinition 数据模型改造

**文件**：`web-ide/backend/.../service/skill/SkillModels.kt`

```kotlin
data class SkillDefinition(
    val name: String,
    val description: String,        // Level 1: 注入 system prompt
    val trigger: String? = null,
    val tags: List<String> = emptyList(),
    val stage: String? = null,
    val type: String? = null,

    // Level 2: Agent 按需读取
    val content: String,            // SKILL.md body（保留兼容性）
    val sourcePath: String,

    // NEW: 渐进式披露扩展
    val version: String = "1.0",
    val author: String = "",
    val category: SkillCategory = SkillCategory.CUSTOM,
    val subFiles: List<SkillSubFile> = emptyList(),
    val scripts: List<SkillScript> = emptyList(),
    val enabled: Boolean = true,
    val isUserCreated: Boolean = false
)

enum class SkillCategory { SYSTEM, FOUNDATION, DELIVERY, KNOWLEDGE, CUSTOM }

data class SkillSubFile(
    val path: String,           // 相对路径：examples/entity-pattern.md
    val description: String,
    val type: SkillContentType
)

enum class SkillContentType { REFERENCE, EXAMPLE, TEMPLATE, SCRIPT }

data class SkillScript(
    val path: String,           // scripts/validate.py
    val description: String,
    val language: String,        // python / bash / kotlin
    val executionHint: String    // "run" or "read"
)
```

#### 2.1.2 SkillLoader 改造

**文件**：`web-ide/backend/.../service/skill/SkillLoader.kt`

- `parseSkillFile()` 扩展：解析 version/author/category，扫描子目录和脚本
- **删除** 60K char 剪枝逻辑（不再需要，Skill 内容不入 prompt）
- 新增 `loadSkillMetadataCatalog()` 方法

#### 2.1.3 SystemPromptAssembler 核心改造

**文件**：`web-ide/backend/.../service/skill/SystemPromptAssembler.kt`

`buildSkillsSections()` 完全重写 — 只注入 Level 1 Metadata：

```kotlin
private fun buildSkillsSections(skills: List<SkillDefinition>): List<String> {
    val sb = StringBuilder()
    sb.appendLine("## Available Skills")
    sb.appendLine()
    sb.appendLine("以下 Skills 已为当前 Profile 加载。")
    sb.appendLine("使用 `read_skill` 读取详细指南，使用 `run_skill_script` 执行脚本。")
    sb.appendLine()
    for (skill in skills) {
        sb.appendLine("- **${skill.name}**: ${skill.description}")
        if (skill.scripts.isNotEmpty()) {
            sb.appendLine("  Scripts: ${skill.scripts.joinToString(", ") { it.path }}")
        }
    }
    return listOf(sb.toString().trim())
}
```

**预期**：System prompt 中 Skill 部分从 ~55K → ~1K chars

#### 2.1.4 新增 3 个 MCP 工具

**文件**：`web-ide/backend/.../service/McpProxyService.kt`

| 工具名 | 功能 | 对应 Level |
|--------|------|------------|
| `read_skill` | 读取 SKILL.md 或子文件内容 | Level 2 + 3 |
| `run_skill_script` | 执行 Skill 脚本，返回 stdout + exitCode | Level 3 |
| `list_skills` | 列出所有可用 Skill metadata | Level 1 |

**安全**：路径遍历检查（禁止 `..`），脚本执行 60s 超时。

#### 2.1.5 渐进式加载指引

SystemPromptAssembler `buildToolsSection()` 新增 Agent 行为引导：

```markdown
### Skill 渐进式使用协议
1. **发现**：从 Available Skills 列表了解有哪些 Skill 可用
2. **选择**：根据任务需求选择相关 Skill
3. **读取**：使用 `read_skill(skill_name)` 读取核心指南
4. **深入**：如需详细示例，读取子文件 `read_skill(skill_name, "examples/xxx.md")`
5. **执行**：如 Skill 提供脚本，使用 `run_skill_script` 执行确定性操作
```

#### 验证
- System prompt < 25K chars
- Agent 主动调用 `read_skill` 读取相关 Skill
- `list_skills` 返回 32 个 Skill metadata

**依赖**：无

---

### Sprint 4.2：Skill 可执行脚本 + 目录结构改造

**目标**：为 Skill 添加可执行脚本，重构 SKILL.md 对齐 Anthropic 标准。

#### 2.2.1 Skill 目录结构标准化

```
skills/{skill-name}/
├── SKILL.md              # Level 2: 核心指南（< 500 行）
├── reference/            # Level 3: 详细参考
├── examples/             # Level 3: 代码示例
├── templates/            # Level 3: 输出模板
└── scripts/              # Level 3: 可执行脚本
    ├── validate.py
    └── check.sh
```

#### 2.2.2 核心 Skill 可执行脚本

| Skill | 脚本 | 功能 |
|-------|------|------|
| code-generation | `compile_check.py` | 检测常见编译错误 |
| code-generation | `import_check.py` | 检测缺失导入 |
| code-generation | `naming_check.py` | 检测命名规范违规 |
| architecture-design | `layer_violation_check.py` | 检测分层架构违规 |
| architecture-design | `circular_dep_check.py` | 检测循环依赖 |
| architecture-design | `adr_template.py` | 生成 ADR 模板 |
| api-design | `endpoint_check.py` | 检测 REST 命名违规 |
| api-design | `response_check.py` | 检测响应格式合规性 |
| api-design | `openapi_gen.py` | 生成 OpenAPI 骨架 |
| test-case-writing | `coverage_check.py` | 分析测试覆盖率 |
| test-case-writing | `test_naming_check.py` | 检测测试命名规范 |
| test-case-writing | `missing_test_check.py` | 检测缺少测试的公共方法 |

每个脚本 50-100 行 Python，接受 workspace 路径参数，输出结构化 JSON 结果。

#### 2.2.3 SKILL.md 内容改造

**核心原则**："只写 Claude 不知道的"

**改造示例 — `spring-boot-patterns`**：

改造前（10.8K chars，全是 Claude 已知内容）：分层架构图 + DI 示例 + @ConfigurationProperties 全例 + @Async 全例 + @Cacheable 全例...

改造后（~2K chars，只有项目约定 + 脚本引用）：

```markdown
---
name: spring-boot-patterns
description: "Forge 项目 Spring Boot 约定。构造器注入强制、@ConfigurationProperties 优于 @Value、RFC 7807 错误响应。"
version: "2.0"
category: foundation
---
# Spring Boot Patterns — Forge 项目约定

## 项目强制规则（Claude 不知道的）
- Controller 禁止直接访问 Repository
- Service 禁止导入 HTTP 相关类
- 所有 config 必须用 @ConfigurationProperties（禁止 @Value 散落）
- 错误响应必须包含 correlationId 和 timestamp

## 验证脚本
- `scripts/layer_check.py` — 检测分层违规
- `scripts/config_check.py` — 检测 @Value 使用

## 详细参考
- 完整代码示例：[examples/](examples/)
```

**15 个核心 Skill 全部按此标准改造。**

#### 2.2.4 Baseline 脚本演化

现有 Baseline 脚本可作为 Skill scripts 的一部分 — Skill 自带质量门：
- `code-generation/scripts/style_check.sh` ← 从 `code-style-baseline.sh` 演化
- `code-generation/scripts/security_check.sh` ← 从 `security-baseline.sh` 演化

现有 `BaselineService` 保持兼容。

#### 验证
- `run_skill_script` 执行脚本返回结构化结果
- 所有 SKILL.md < 500 行
- 子文件可通过 `read_skill` 访问

**依赖**：Sprint 4.1

---

### Sprint 4.3：Skill 管理 API + 前端 UI（用户可视可操作）

**目标**：让 Skill 对用户可视、可选择、可创造、可加载、可删除。

#### 2.3.1 REST API

**文件**：新建 `SkillController.kt`

| 端点 | 方法 | 功能 | 权限 |
|------|------|------|------|
| `/api/skills` | GET | 列出所有 Skill | 所有用户 |
| `/api/skills/{name}` | GET | Skill 详情 | 所有用户 |
| `/api/skills/{name}/content/{path}` | GET | 读取子文件 | 所有用户 |
| `/api/skills` | POST | 创建自定义 Skill | 已登录用户 |
| `/api/skills/{name}` | PUT | 更新自定义 Skill | Skill 创建者 |
| `/api/skills/{name}` | DELETE | 删除自定义 Skill | Skill 创建者 |
| `/api/skills/{name}/enable` | POST | 启用 | 已登录用户 |
| `/api/skills/{name}/disable` | POST | 禁用 | 已登录用户 |

#### 2.3.2 存储策略

```
系统 Skill（SYSTEM + FOUNDATION + DELIVERY）:
  来源：plugins/ 目录（Docker volume，只读）
  不可删除、不可修改内容，可启用/禁用

用户自定义 Skill（CUSTOM）:
  来源：workspace/{workspaceId}/.skills/ 目录
  可创建、修改、删除
  与 workspace 绑定
```

用户启用/禁用偏好持久化到 H2（`skill_preferences` 表）。

#### 2.3.3 前端 Skill 管理面板

**新建**：`SkillPanel.tsx` + `SkillDetailView.tsx` + `SkillCreateForm.tsx`

- **列表视图**：分类 Tab（系统/基础/交付/自定义）+ Skill 卡片（名称、描述、子文件数、脚本数、使用次数、启用/禁用）
- **详情视图**：SKILL.md 渲染 + 子文件列表 + 脚本列表（带运行按钮）+ 使用统计
- **创建视图**：名称/描述/标签输入 + Monaco 编辑器写 SKILL.md + 上传子文件/脚本
- **侧边栏入口**：`对话` | `质量面板` | `Skills` Tab

#### 验证
- 前端 Skills Tab 显示所有 Skill + 分类
- 禁用 Skill → Agent 不再看到
- 创建自定义 Skill → Agent 可使用
- 系统 Skill 不可删除

**依赖**：Sprint 4.1

---

### Sprint 4.4：Skill 生态 + 效果追踪 + 文档

**目标**：让 Skill 成为可度量、可进化的生态。

#### 2.4.1 使用效果追踪

新建 `skill_usage` 表，在 `read_skill` / `run_skill_script` 中记录：
- sessionId, skillName, action (READ/SCRIPT_RUN/REFERENCED), profile, durationMs, success

#### 2.4.2 效果分析 API

| 端点 | 说明 |
|------|------|
| `GET /api/skills/{name}/stats` | 单个 Skill 统计 |
| `GET /api/dashboard/skill-ranking` | Skill 排行榜（使用频次 + 成功率） |
| `GET /api/dashboard/skill-trends?days=30` | 30 天使用趋势 |

#### 2.4.3 Skill 进化建议

`SkillFeedbackService` 扩展：
- "从未被读取的 Skill" → 建议废弃
- "脚本失败率高的 Skill" → 建议改进
- "用户频繁创建同类 Skill" → 建议升级为系统 Skill

#### 2.4.4 验收测试

| # | 场景 | TC 数 |
|---|------|-------|
| 1 | Metadata 渐进式加载 | 4 TC |
| 2 | 可执行脚本 | 4 TC |
| 3 | Skill 用户管理 | 6 TC |
| 4 | 系统 Skill 保护 | 3 TC |
| 5 | Skill 生态度量 | 3 TC |
| 6 | 端到端闭环 | 4 TC |
| **合计** | | **24 TC** |

**依赖**：Sprint 4.1-4.3 全部完成

---

## 三、执行顺序与 Session 规划

```
Sprint 4.1（Metadata 架构 + 渐进加载）  ── Session A（后端核心）
         ↓
Sprint 4.2（可执行脚本 + 目录改造）     ── Session B（Skill 文件 + 脚本）
         ↓
Sprint 4.3（管理 API + 前端 UI）        ── Session C（前后端 CRUD）
         ↓
Sprint 4.4（生态度量 + 文档）           ── Session D（度量 + 验收）
```

建议 **4 个 Session**，每个 Session 独立可交付。

---

## 四、关键文件清单

### 后端修改/新建

| 文件 | 操作 | Sprint | 说明 |
|------|------|--------|------|
| `SkillModels.kt` | 修改 | 4.1 | 扩展 SkillDefinition + 新类型 |
| `SkillLoader.kt` | 修改 | 4.1 | 扫描子目录/脚本，删除 60K 剪枝 |
| `SystemPromptAssembler.kt` | 修改 | 4.1 | Level 1 metadata 注入 |
| `McpProxyService.kt` | 修改 | 4.1 | +3 MCP 工具 |
| `SkillController.kt` | **新建** | 4.3 | REST CRUD API |
| `SkillManagementService.kt` | **新建** | 4.3 | Skill CRUD 逻辑 |
| `SkillPreferenceEntity.kt` | **新建** | 4.3 | 用户偏好持久化 |
| `SkillPreferenceRepository.kt` | **新建** | 4.3 | JPA Repository |
| `SkillUsageEntity.kt` | **新建** | 4.4 | 使用追踪 |
| `SkillUsageRepository.kt` | **新建** | 4.4 | JPA Repository |

### 前端新建

| 文件 | Sprint | 说明 |
|------|--------|------|
| `SkillPanel.tsx` | 4.3 | Skill 列表 + 分类 + 管理 |
| `SkillDetailView.tsx` | 4.3 | Skill 详情 + 内容渲染 + 脚本执行 |
| `SkillCreateForm.tsx` | 4.3 | 创建/编辑自定义 Skill |

### Skill 文件改造

| 范围 | Sprint | 说明 |
|------|--------|------|
| 15 个核心 SKILL.md 精简 | 4.2 | 只保留项目特有规则 + 脚本引用 |
| ~20 个可执行脚本 | 4.2 | 4 个核心 Skill × 3-5 脚本 |
| ~30 个子文件 | 4.2 | examples/ + reference/ |
| 17 个小型 SKILL.md | 4.2 | 更新 frontmatter |

---

## 五、预期效果

| 指标 | Phase 3 结束 | Phase 4 完成 |
|------|-------------|-------------|
| System prompt 中 Skill 内容 | ~55K chars | ~1K chars（只有 metadata） |
| System prompt 总大小 | ~78K chars | ~20K chars |
| Agent 可用信息量 | 不变 | 不变（通过 MCP 工具按需获取） |
| Skill 可执行脚本 | 0 | ~20 个 |
| 用户 Skill 管理 | 不可见 | 可视/可选/可创/可删 |
| Skill 使用追踪 | 无 | 每次 read/execute 记录 |
| Skill 数量上限 | ~10 个（受 prompt 限制） | 无上限（只加载 metadata） |
| MCP 工具数 | 11 | 14 (+read_skill, +run_skill_script, +list_skills) |
