# my-api 部署清单

## 基本信息

| 项目 | 值 |
|------|------|
| 仓库路径 | `/opt/my-api` |
| Compose 文件 | `docker-compose.production.yml` |
| Env 文件 | `.env.production` |
| 镜像来源 | GHCR (`ghcr.io/myorg/my-api`) |
| 日志文件 | `DEPLOYMENT_LOGBOOK.md` |

## 部署方式

GHCR 预构建镜像。CI 在 push main 时自动构建并推送。

```
docker compose -f docker-compose.production.yml --env-file .env.production pull
docker compose -f docker-compose.production.yml --env-file .env.production up -d --remove-orphans
```

## 服务清单

| 服务 | 端口 | 健康检查 | 说明 |
|------|------|---------|------|
| api | 8080 | `curl http://localhost:8080/actuator/health` | Spring Boot 3, JDK 21 |
| postgres | 5432 | `pg_isready` | 数据持久化，不走 GHCR |
| redis | 6379 | `redis-cli ping` | 缓存 |

## 已知陷阱

1. **Flyway 迁移冲突**: 多人开发时可能出现同版本号的迁移文件。检查 `docker logs my-api` 中的 `FlywayException`
2. **JVM 启动慢**: 冷启动约 30-45 秒，首次 health check 前需耐心等待
3. **Redis 连接**: 如果 Redis 容器未就绪，API 启动会失败并不断重启。确认 `depends_on` 中有 Redis 的 health 条件

## 部署后验证

```bash
curl -sf http://localhost:8080/actuator/health | jq .status
curl -sf http://localhost:8080/api/v1/ping
```
