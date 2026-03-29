# Forge Platform 部署清单

## 基本信息

| 项目 | 值 |
|------|------|
| 仓库路径 | `/opt/forge` |
| Compose 文件 | `infrastructure/haier/docker-compose.yml`（海尔/当前生产）或 `infrastructure/docker/docker-compose.production.yml`（独立生产）。根据服务器上实际存在的 env 文件判断用哪个。 |
| Env 文件 | `infrastructure/haier/.env`（海尔）或 `infrastructure/docker/.env.production`（独立生产） |
| 镜像来源 | GHCR (`ghcr.io/pan94u/forge`) |
| 日志文件 | `DEPLOYMENT_LOGBOOK.md`（仓库根目录，部署后提交推送） |

## 部署方式

GHCR 预构建镜像。Compose 文件中各服务使用 `image: ghcr.io/pan94u/forge/<service>:${DEPLOY_TAG:-latest}`。

部署命令：
```
docker compose -f <compose> --env-file <env> pull
docker compose -f <compose> --env-file <env> up -d --remove-orphans
```

## 服务清单

| 服务 | 镜像 | 健康检查 | 说明 |
|------|------|---------|------|
| backend | `ghcr.io/pan94u/forge/backend` | `wget http://localhost:8080/actuator/health` | Spring Boot, 启动较慢（30-60s） |
| frontend | `ghcr.io/pan94u/forge/frontend` | 容器 running | Next.js standalone |
| enterprise-console | `ghcr.io/pan94u/forge/enterprise-console` | 容器 running | Next.js standalone |
| knowledge-mcp | `ghcr.io/pan94u/forge/forge-knowledge-mcp` | `wget http://localhost:8081/health/live` | Ktor 轻量服务 |
| database-mcp | `ghcr.io/pan94u/forge/forge-database-mcp` | `wget http://localhost:8082/health/live` | Ktor 轻量服务 |
| postgres | `postgres:16-alpine` | `pg_isready` | 不走 GHCR，数据持久化 |
| nginx | `nginx:alpine` | 容器 running | 不走 GHCR |

## 配置热更新

这些文件通过 `git pull` 同步后，`up -d` 会自动生效：
- `plugins/` — backend 挂载为 `:ro`
- `knowledge-base/` — backend 和 knowledge-mcp 挂载
- `infrastructure/docker/nginx-production.conf` — nginx 配置

**注意**：nginx 配置变更后需要 `docker restart forge-nginx`，`up -d` 不会自动 reload。

## 已知陷阱

1. **postgres 不会被重启**：镜像 tag 未变时 `up -d` 不重启 postgres，这是期望行为
2. **frontend NEXT_PUBLIC_* 烘焙在镜像中**：运行时无法覆盖，需重新构建镜像
3. **SSO 外部依赖**：SSO 在 `sso.synapse.gold` 独立部署。部署前确认 SSO 可达，否则 backend 启动会因 JWK 获取失败而 unhealthy
4. **backend 启动慢**：JVM 冷启动 + Flyway 迁移，首次 healthy 可能需要 60s
5. **数据库迁移失败**：如果 Flyway 报版本冲突，查看 `docker logs forge-backend` 中的具体迁移文件名和错误

## 部署后验证（可选）

```bash
curl -sf https://forge.delivery/api/knowledge/search | head -1
curl -sf -o /dev/null -w "%{http_code}" https://forge.delivery/console
curl -sf -o /dev/null -w "%{http_code}" https://sso.synapse.gold/auth/realms/forge
```
