# AI-Native Deploy Kit

让 Claude Code 成为你的部署 Agent。人说"部署"，AI 执行、验证、记录。

## 理念

传统 CI/CD 的 CD 部分通常是 shell 脚本——预定义逻辑、固定路径、遇到异常就挂。

AI-Native 部署不同：
- **Skill** 告诉 agent "你要做什么、有什么约束"
- **Manifest** 告诉 agent "这个应用长什么样、有什么坑"
- **Agent 自己决定怎么执行**，遇到问题读日志、分析原因、提方案

```
/deploy my-app
    │
    ├── 读 Skill → 理解部署流程和安全约束
    ├── 读 Manifest → 理解应用拓扑和已知陷阱
    ├── 预检 → Docker / 文件 / Git 状态
    ├── 执行 → pull 镜像或本地构建
    ├── 验证 → 健康检查，失败则读日志诊断
    └── 记录 → 追加到 DEPLOYMENT_LOGBOOK.md
```

## 套件结构

```
deploy-kit/
├── skill/
│   └── deploy.md              → 复制到 .claude/commands/deploy.md
├── manifests/
│   ├── _template.md           → 复制并填写你的应用信息
│   └── examples/
│       ├── spring-boot-ghcr.md
│       ├── nextjs-local-build.md
│       └── monorepo-multi-service.md
├── ci/
│   ├── ghcr-push-gradle.yml   → 复制到 .github/workflows/
│   └── ghcr-push-node.yml     → 复制到 .github/workflows/
├── setup.sh                   → 一键安装
└── README.md
```

## 快速开始

### 方式 1: 自动安装

```bash
cd /path/to/your/project
bash /path/to/deploy-kit/setup.sh
```

自动创建：
- `.claude/commands/deploy.md`
- `deploy-manifests/_template.md`
- `DEPLOYMENT_LOGBOOK.md`

### 方式 2: 手动安装

```bash
# 1. 复制 Skill
mkdir -p .claude/commands
cp deploy-kit/skill/deploy.md .claude/commands/deploy.md

# 2. 创建应用清单
mkdir -p deploy-manifests
cp deploy-kit/manifests/_template.md deploy-manifests/my-app.md
# 编辑 my-app.md，填入你的应用信息

# 3. (可选) 添加 CI workflow
cp deploy-kit/ci/ghcr-push-gradle.yml .github/workflows/ci.yml
```

### 使用

```bash
ssh root@server
cd /opt/my-project
claude

> /deploy my-app              # 部署最新版
> /deploy my-app sha-abc1234  # 部署指定版本
> /deploy my-app rollback     # 回滚到上一版本
```

## CI 配置（可选）

如果你希望 CI 自动构建镜像推到 GHCR（推荐），复制对应的 workflow 模板：

| 项目类型 | 模板 |
|----------|------|
| Gradle / Spring Boot / Kotlin | `ci/ghcr-push-gradle.yml` |
| Node.js / Next.js | `ci/ghcr-push-node.yml` |

配置后，push 到 main 会自动构建镜像，服务器上 `/deploy` 时直接拉取，无需本地编译。

如果不配 CI，也能用——Manifest 中标记为"本地构建"即可，`/deploy` 会在服务器上执行 `docker compose up --build`。

## 编写 Manifest 的建议

Manifest 是给 AI 看的"领域知识"。写好 Manifest 的关键：

1. **已知陷阱是最有价值的部分**。每次部署踩坑后，把现象、原因、解决方案加到 Manifest 里。下次 agent 遇到同样问题能秒级定位。

2. **写"是什么"而非"怎么做"**。agent 知道怎么用 docker compose，你要告诉它的是"这个应用有什么特殊的"。

3. **保持更新**。Manifest 过时了比没有更糟——agent 会按错误信息操作。架构变更时同步更新。

## DEPLOYMENT_LOGBOOK.md

每次 `/deploy` 执行后自动追加一条记录，包含：
- 应用名、版本、Git SHA、操作人、时间
- 所有容器的运行状态
- 特殊操作备注

这个文件是你的部署审计日志——谁在什么时间部署了什么版本，一目了然。
