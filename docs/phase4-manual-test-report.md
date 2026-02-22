# Phase 4 手工测试报告

> 测试日期: 2026-02-22 | 测试环境: Docker 4+2 容器 | 测试人: zhaoqi

---

## 一、测试时间线

| 时间 (UTC) | 事件 | 结果 |
|-----------|------|------|
| 06:42:12 | Spring Boot 启动 | 3.6s 启动，28 skills + 5 profiles 加载成功 |
| 06:43:07 | 尝试删除 PLATFORM skill `kotlin-conventions` | WARN 拒绝删除（保护机制生效） |
| 06:43:31 | 禁用 skill `kotlin-conventions` for workspace `test-ws` | 成功 |
| 06:44:05 | 重新启用 skill `kotlin-conventions` | 成功 |
| 06:45:43 | 创建工作区「印章管理系统」 | 成功（ID: `fdac9647`） |
| 06:52:15 | **Session 1** — 用户发送「设计」相关消息 | 路由到 `design-profile` |
| 06:52:38 | 尝试执行 `adr_template.py` | ERROR: `python3` not found |
| 06:53:19–06:54:08 | Agent 写了 4 个设计文档 | 成功 |
| 06:54:08 | 8 轮 turn 用完，强制生成总结 | 总结 8843 chars |
| 06:55:06 | Baseline 自动检查 | 2/2 通过（首次尝试） |
| 06:55:20 | HITL 审批 | 用户 APPROVE |
| 06:55:24–06:56:42 | HITL 后续处理：写 `STAGE-SUMMARY.md` | 成功 |
| 06:57:44 | **Session 2** — 用户发送「继续」 | 路由到 `development-profile`（默认） |
| 06:58:11–06:58:34 | Agent 写了 `package.json` + `prisma/schema.prisma` | 成功 |
| 06:58:35 | Turn 6 触发 API | ERROR: Rate limited (30K tokens/min) |

---

## 二、关键指标

### System Prompt 大小

| Profile | Skill 数量 | System Prompt | Phase 3 基线 | 降幅 |
|---------|-----------|---------------|-------------|------|
| design-profile | 4 | **20,344 chars** | ~55K | -63% |
| development-profile | 8 | **25,219 chars** | ~55K | -54% |

### Profile 路由

| 输入 | 路由结果 | 置信度 | 评价 |
|------|---------|-------|------|
| 含「设计」关键字 | `design-profile` | 0.6 | 正确 |
| 「继续」（无关键字） | `development-profile`（默认） | — | 正确 |

### Agentic 执行效率

| 指标 | Session 1 (设计) | Session 2 (开发) |
|------|-----------------|-----------------|
| Profile | design-profile | development-profile |
| Skills 加载 | 4 / 28 | 8 / 28 |
| Turns 使用 | 8/8（用满） + 4 后续 | 5/8（第 6 轮 rate limit） |
| 总耗时 | ~4.5 min | ~50s |
| 工具调用总数 | ~18 | ~14 |
| 文件产出 | 4 设计文档 + 1 阶段总结 | package.json + prisma/schema.prisma |
| 平均 turn 首 token 延迟 | ~1.6s | ~1.5s |

### Turn 耗时明细（Session 1）

| Turn | 耗时 | stopReason | tools | 说明 |
|------|------|-----------|-------|------|
| 1 | 6.3s | TOOL_USE | 3 | 搜索知识库（adr_search + wiki_search） |
| 2 | 2.9s | TOOL_USE | 3 | 继续搜索 |
| 3 | 2.6s | TOOL_USE | 2 | 继续搜索 |
| 4 | 10.5s | TOOL_USE | 2 | 尝试运行脚本（失败） + 文本输出 |
| 5 | 41.4s | TOOL_USE | 1 | 写 `01-architecture-overview.md`（4494 bytes） |
| 6 | 16.4s | TOOL_USE | 1 | 写 `02-adr-001-monolith.md` |
| 7 | 17.0s | TOOL_USE | 1 | 写 `03-adr-002-state-machine.md` |
| 8 | 15.3s | TOOL_USE | 1 | 写 `04-adr-003-database.md` |
| 总结 | 58.5s | — | — | 强制总结（8843 chars） |

---

## 三、发现的问题

### P0：脚本执行失败（Alpine 无 python3）

```
ERROR: Script execution failed: skill=architecture-design,
  script=scripts/adr_template.py:
  Cannot run program "python3": error: 2 (No such file or directory)
```

- **根因**：Docker 镜像 `eclipse-temurin:21-jre-alpine` 不含 python3
- **影响**：Agent 优雅降级——跳过脚本直接用 LLM 能力手写 ADR 文档。用户感知无异常
- **修复方向**（Phase 5）：Alpine 安装 python3 / 改 Kotlin 脚本 / 独立脚本执行容器

### P1：Rate Limit 无重试/退避

```
ERROR: Rate limited: 30,000 input tokens per minute
```

- **根因**：Session 2 累积大量 context，第 6 轮请求触发限流
- **影响**：Agent 直接中断，无重试/退避/降级策略
- **修复方向**：ClaudeAgentService 加入 RateLimitException 指数退避重试

### P2：Turn 限制（8 轮）偏紧

- Session 1 用满 8 轮（3 轮搜索 + 1 轮脚本 + 4 轮写文件）
- 当前 `turnsExhausted` 强制总结机制正确工作

---

## 四、正面表现

1. **渐进式加载生效**：system prompt 从 ~55K 降至 20-25K
2. **Profile 路由准确**：关键字匹配和默认 fallback 都正确
3. **Skill 过滤精确**：design 4 个 / development 8 个，与 Profile 声明一致
4. **Baseline 自动检查正常**：2 个 baseline 首次尝试全部通过
5. **HITL 审批流程完整**：APPROVE → 继续执行 → 写 STAGE-SUMMARY.md → 正常结束
6. **脚本失败优雅降级**：不中断、不循环重试
7. **文件写入无错误**：5 个文件全部成功写入 workspace
8. **平台 Skill 保护机制生效**：`Cannot delete platform skill` 正确拒绝

---

## 五、curl API 测试（14 TC）

| # | 测试项 | 结果 |
|---|--------|------|
| 1 | GET /api/skills（列出全部） | PASS — 28 skills，全部 PLATFORM |
| 2 | GET /api/skills?scope=PLATFORM | PASS — 28 条 |
| 3 | GET /api/skills?scope=WORKSPACE | PASS — 0 条 |
| 4 | GET /api/skills?scope=CUSTOM | PASS — 0 条 |
| 5 | GET /api/skills/delivery-methodology | PASS — 完整详情含 subFiles + scripts |
| 6 | GET /api/skills/delivery-methodology/content/reference/session-pdca.md | PASS — 子文件内容 |
| 7 | DELETE /api/skills/kotlin-conventions?workspaceId=test | PASS — 400 拒绝 |
| 8 | POST /api/skills/kotlin-conventions/disable?workspaceId=test | PASS |
| 9 | POST /api/skills/kotlin-conventions/enable?workspaceId=test | PASS |
| 10 | GET /api/skills/kotlin-conventions/stats | PASS — 统计数据 |
| 11 | GET /api/skill-analytics/skill-ranking | PASS — 排行榜 |
| 12 | GET /api/skill-analytics/skill-suggestions | PASS — 28 UNUSED 建议 |
| 13 | GET /api/skill-analytics/skill-triggers?trigger=workspace_init | PASS — 2 建议 |
| 14 | GET /api/skills/domain-order | PASS — 404（D 级已移除） |

---

## 六、总结评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 渐进式加载 | **A** | prompt 体积 55K → 20-25K，目标达成 |
| Profile 路由 | **A** | 关键字路由 + 默认 fallback 正确 |
| Skill 管理 API | **A** | CRUD + enable/disable + 保护全部正常 |
| 脚本执行 | **C** | Alpine 无 python3，需 Phase 5 解决 |
| 错误处理 | **B-** | 脚本降级好，rate limit 无重试 |
| 端到端交付 | **A-** | 设计阶段完整闭环 |

**总体**：Phase 4 核心目标全部达成。两个遗留问题（python3 + rate limit 退避）列入 Phase 5。
