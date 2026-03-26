# my-platform 部署清单

## 基本信息

| 项目 | 值 |
|------|------|
| 仓库路径 | `/opt/my-platform` |
| Compose 文件 | `infrastructure/docker-compose.production.yml` |
| Env 文件 | `infrastructure/.env.production` |
| 镜像来源 | GHCR (`ghcr.io/myorg/my-platform`) |
| 日志文件 | `infrastructure/DEPLOYMENT_LOGBOOK.md` |

## 部署方式

GHCR 预构建镜像，monorepo 多服务。每个服务独立镜像，共享 `DEPLOY_TAG`。

```
docker compose -f infrastructure/docker-compose.production.yml --env-file infrastructure/.env.production pull
docker compose -f infrastructure/docker-compose.production.yml --env-file infrastructure/.env.production up -d --remove-orphans
```

## 服务清单

| 服务 | 镜像 | 健康检查 | 说明 |
|------|------|---------|------|
| api-gateway | `ghcr.io/myorg/my-platform/gateway` | `curl http://localhost:8080/health` | 入口网关 |
| user-service | `ghcr.io/myorg/my-platform/user-svc` | `curl http://localhost:8081/health` | 用户服务 |
| order-service | `ghcr.io/myorg/my-platform/order-svc` | `curl http://localhost:8082/health` | 订单服务 |
| web | `ghcr.io/myorg/my-platform/web` | 容器 running | 前端 |
| postgres | `postgres:16-alpine` | `pg_isready` | 共享数据库 |
| redis | `redis:7-alpine` | `redis-cli ping` | 共享缓存 |
| nginx | `nginx:alpine` | 容器 running | 反向代理 |

## 已知陷阱

1. **服务启动顺序**: user-service 必须在 order-service 之前就绪（order-service 启动时调用 user-service 做初始化）。compose `depends_on` 只等容器启动不等 healthy，如果 user-service 启动慢可能导致 order-service 重启几次
2. **数据库迁移**: 每个服务各自管理迁移。如果多个服务同时迁移可能锁表。deploy 后如果有服务 unhealthy，先查迁移日志
3. **nginx 配置**: bind mount 的 nginx.conf 修改后需要 `docker restart` 才能生效，`up -d` 不会自动 reload
4. **redis 内存**: 没有设 maxmemory，长期运行可能 OOM。监控内存使用

## 前置条件

- 外部 SSO 服务必须可达（gateway 启动时获取 JWK）
- DNS 解析到本机 IP

## 部署后验证

```bash
# 网关健康
curl -sf http://localhost:8080/health | jq .

# 各服务通过网关可达
curl -sf http://localhost:8080/api/users/health
curl -sf http://localhost:8080/api/orders/health

# 前端
curl -sf -o /dev/null -w "%{http_code}" https://my-platform.example.com
```
