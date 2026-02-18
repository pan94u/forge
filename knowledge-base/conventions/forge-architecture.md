# Forge 平台架构概览

## 模块结构

```
forge-platform/
├── plugins/                    # 插件系统（纯 Markdown/JSON，无需编译）
│   ├── forge-superagent/       # SuperAgent 指令、Skill Profiles、Baselines
│   ├── forge-foundation/       # Foundation Skills（通用知识）
│   ├── forge-deployment/       # 部署相关 Skills
│   └── forge-domain-skills/    # 领域 Skills（按业务域组织）
├── mcp-servers/                # MCP Server 实现（Kotlin/Ktor）
│   ├── forge-mcp-common/       # 共享基础库
│   ├── forge-knowledge-mcp/    # 知识搜索（Wiki/ADR/Runbook）
│   ├── forge-database-mcp/     # 数据库查询（Schema/SQL）
│   ├── forge-service-graph-mcp/# 服务依赖图
│   ├── forge-artifact-mcp/     # 构建产物管理
│   └── forge-observability-mcp/# 可观测性（日志/指标/追踪）
├── web-ide/                    # Web IDE
│   ├── frontend/               # Next.js 15 + React 19
│   └── backend/                # Spring Boot 3 + Kotlin
├── adapters/                   # 适配器层
│   └── model-adapter/          # ClaudeAdapter（API 隔离）
├── cli/                        # Forge CLI
├── agent-eval/                 # Agent 评估框架
└── knowledge-base/             # 知识库（文档、规范、ADR）
```

## 核心架构决策
1. **SuperAgent 优于 Multi-Agent** (ADR-001)
2. **Skill 优于 Prompt** (ADR-002)
3. **Baseline 保障质量下限** (ADR-003)
4. **适配器隔离运行时** — Skills 和 Baselines 稳定，模型和运行时可替换

## 通信架构
- **前端 → 后端**: HTTP REST + WebSocket（SSE 降级）
- **后端 → Claude API**: OkHttp SSE 流式
- **后端 → MCP Servers**: WebClient HTTP (POST /mcp/tools/list, POST /mcp/tools/call)
- **Nginx**: 统一入口 :9000，反向代理 REST/WS/SSE

## 数据流
```
用户消息 → ProfileRouter(路由) → SkillLoader(加载Skills)
  → SystemPromptAssembler(组装Prompt) → ClaudeAdapter(调用API)
  → Agentic Loop(最多5轮Tool Calling) → McpProxyService(执行工具)
  → BaselineRunner(质量门禁) → 返回结果
```
