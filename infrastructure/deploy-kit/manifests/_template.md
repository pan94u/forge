# <应用名> 部署清单

<!--
  使用说明：
  1. 复制本文件，重命名为 <app>.md（app 是 /deploy 命令的参数）
  2. 填写下面各节内容
  3. 放入项目的 deploy-manifests/ 目录

  设计原则：
  - 这个文件是给 AI Agent 看的"领域知识"，不是给人执行的操作手册
  - 写清楚"是什么"和"有什么坑"，agent 会自己决定"怎么做"
  - 已知陷阱是最有价值的部分——把踩过的坑都写上来
-->

## 基本信息

| 项目 | 值 |
|------|------|
| 仓库路径 | `/path/to/project` |
| Compose 文件 | `docker-compose.yml` |
| Env 文件 | `.env.production`（可选，没有则删除此行） |
| 镜像来源 | `GHCR` / `本地构建` / `自定义` |
| 日志文件 | `DEPLOYMENT_LOGBOOK.md` |

## 部署方式

<!-- 三选一，删掉不适用的 -->

### 方式 A: GHCR 预构建镜像

Compose 文件中各服务使用 `image: ghcr.io/<org>/<repo>/<service>:${DEPLOY_TAG:-latest}`。

```
docker compose -f <compose> --env-file <env> pull
docker compose -f <compose> --env-file <env> up -d --remove-orphans
```

### 方式 B: 本地构建

```
docker compose -f <compose> up --build -d
```

### 方式 C: 自定义脚本

```
bash deploy.sh
```

<!-- 说清楚脚本做了什么，agent 需要理解而非盲目执行 -->

## 服务清单

| 服务 | 端口 | 健康检查 | 说明 |
|------|------|---------|------|
| service-a | 8080 | `curl http://localhost:8080/health` | 主服务 |
| service-b | 3000 | 容器 running | 前端 |
| postgres | 5432 | `pg_isready` | 数据库，不走远程镜像 |

## 已知陷阱

<!--
  这是最重要的部分。把历史上踩过的坑都写在这里。
  格式不限，但要写清楚：现象、原因、怎么避免/解决。
  Agent 会在部署前阅读这些，遇到类似问题时能快速定位。
-->

1. **示例陷阱**: 现象描述。原因：xxx。解决：xxx

## 前置条件

<!-- 部署前必须满足的外部依赖，如：其他应用必须先部署、外部服务必须可达等 -->

无特殊前置条件。

## 部署后验证

<!-- 可选的端到端验证命令，agent 在 Phase 5 后可执行这些做额外确认 -->

```bash
curl -sf http://localhost:8080/health
```
