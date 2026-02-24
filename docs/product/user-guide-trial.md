> **⚠️ 本文档已废弃** — 此指南基于 Sprint 2A 版本，不覆盖多模型（13+ 模型）、6 容器部署等新功能。
> 请使用最新指南：**[TRIAL-GUIDE.md](./TRIAL-GUIDE.md)**（Sprint 2.4，2026-02-21 更新）

---

# ~~Forge Web IDE — 内部试用指南~~（已废弃）

> 版本: Sprint 2A | 更新: 2026-02-18

## 快速开始

### 1. 启动平台

```bash
# 本地构建
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :web-ide:backend:bootJar -x test --no-daemon
cd web-ide/frontend && npm install && npm run build && cd ../..

# Docker 启动
cd infrastructure/docker
cp .env.trial.example .env.trial   # 编辑填入 ANTHROPIC_API_KEY
docker compose -f docker-compose.trial.yml --env-file .env.trial up --build
```

### 2. 访问

打开浏览器访问 **http://localhost:9000**

---

## AI 对话功能

### Profile 自动切换

Forge AI 不是普通的 ChatGPT。它会根据你的意图自动切换到对应的专家角色：

| 你说的话 | AI 自动切换到 | 加载的专业知识 |
|---------|-------------|-------------|
| `@规划 写一个 PRD` | 规划专家 | 需求分析、用户故事 |
| `@设计 支付系统架构` | 设计专家 | 架构模式、API 设计、数据库模式 |
| `@开发 实现订单服务` | 开发专家 | Kotlin 规范、Spring Boot 模式、错误处理等 17 个 Skills |
| `@测试 写测试用例` | 测试专家 | 测试策略、边界条件、覆盖率 |
| `@运维 部署到生产` | 运维专家 | 部署检查、环境差异、监控 |

**不加标签也行** — AI 会根据关键词自动识别。比如说 "帮我实现一个订单服务" 会自动识别为开发任务。

### OODA 思考过程

对话时你会看到 AI 的思考阶段指示器：

```
👁 Observe → 🧭 Orient → 🧠 Decide → ⚡ Act → ✅ Done
```

| 阶段 | 含义 |
|------|------|
| Observe | 理解你的意图和上下文 |
| Orient | 分析上下文、路由到合适的专家角色 |
| Decide | AI 制定回复方案 |
| Act | 执行工具调用（如果需要） |
| Done | 回复完成 |

### Profile Badge

OODA 指示器下方会显示当前激活的 Profile 信息：

```
● development | kotlin-conventions, spring-boot-patterns +15 | keyword '实现' (conf=0.6)
```

- **绿色圆点**: 高置信度 (>=80%) — AI 很确定你的意图
- **黄色圆点**: 中置信度 (50-80%) — AI 根据关键词推断
- **灰色圆点**: 低置信度 (<50%) — 使用默认 Profile

### @ 上下文附加

在输入框中输入 `@` 可以附加上下文：

| 类型 | 说明 |
|------|------|
| Files | 当前 Workspace 中的文件 |
| Knowledge | 知识库文档 |
| Schema | 数据库 Schema |
| Services | 服务依赖信息 |

### 工具调用

AI 可以调用 MCP 工具（如搜索知识库、查询数据库）。工具调用会以折叠卡片形式展示：

- 点击可展开查看输入/输出
- 运行中显示旋转图标，完成后显示绿色勾

---

## 已知限制

| 限制 | 说明 | 预计解决 |
|------|------|---------|
| Rate Limit | 连续使用 @开发 Profile 可能触发 API 限流（24K tokens/次） | 缓存命中后费用降 90% |
| MCP 工具 | Trial 模式下 MCP 工具返回模拟数据 | Sprint 2B 实连 |
| 底线检查 | 尚未集成自动代码质量检查 | Sprint 2B 集成 |
| 代码执行 | AI 不能直接执行代码或修改文件 | 需要手动复制 |

## 反馈

使用中遇到问题或有建议，请记录到内部反馈渠道。你的反馈将直接影响平台迭代方向。
