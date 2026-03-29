# Synapse AI 部署清单

## 基本信息

| 项目 | 值 |
|------|------|
| 仓库路径 | `/home/deploy/Synapse-AI` |
| Compose 文件 | `docker-compose.production.yml` |
| Env 文件 | `.env.production` |
| 镜像来源 | 本地构建（`--build`） |
| 日志文件 | `DEPLOYMENT_LOGBOOK.md`（仓库根目录，部署后提交推送） |

## 部署方式

本地构建。Synapse 没有 GHCR 镜像推送流程，在服务器上直接构建。

部署命令：
```
git pull --ff-only
docker compose -f docker-compose.production.yml up --build -d
```

**注意 `--build` 参数**——每次都需要重新构建镜像。

## 服务清单

| 服务 | 宿主机端口 | 容器端口 | 说明 |
|------|-----------|---------|------|
| synapse-web | 19300 | 3000 | Next.js 前端 |
| synapse-server | 19301 | 3001 | 后端 API |

## 已知陷阱

1. **NEXTAUTH_SECRET 不要在 docker-compose 的 environment 中设置**：会覆盖 env_file 中的值。让 `.env.production` 自动加载
2. **流量通过 gateway nginx 转发**：Synapse 自身不监听 443，流量路径是 `/opt/gateway/nginx` → `localhost:19300`
3. **SSO**：使用 `sso.synapse.gold` 的 `forge-synapse` client

## 部署后验证

```bash
curl -sf -o /dev/null -w "%{http_code}" https://synapse.gold
docker logs synapse-web --tail 20
docker logs synapse-server --tail 20
```
