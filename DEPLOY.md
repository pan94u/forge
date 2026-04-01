# Forge Platform 部署清单

## 基本信息

| 项目 | 值 |
|------|------|
| 应用名 | forge |
| 仓库路径 | `/opt/forge` |
| Compose 文件 | `infrastructure/haier/docker-compose.yml`（海尔/当前生产）或 `infrastructure/docker/docker-compose.production.yml`（独立生产）。根据服务器上实际存在的 env 文件判断用哪个。 |
| Env 文件 | `infrastructure/haier/.env`（海尔）或 `infrastructure/docker/.env.production`（独立生产） |
| 镜像来源 | GHCR (`ghcr.io/pan94u/forge`) |

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

## ⚠ 部署经验

### 1. 必须使用 production compose，不要用 haier compose

**问题**：`infrastructure/haier/docker-compose.yml` 是旧的海尔内网配置，**缺少 `forge-gateway` 服务**和对应的 `nginx-production.conf`。误用会导致：
- API 请求绕过 gateway 直连 backend → 无 `X-User-*` header 注入 → `/api/auth/me` 返回 `authenticated: false`
- 前端检测未认证 → 跳转 `/login` → 无 gateway 处理 OIDC → 页面来回跳转循环

**正确命令**：
```bash
cd /opt/forge
docker compose -f infrastructure/docker/docker-compose.production.yml \
  --env-file infrastructure/docker/.env.production pull
docker compose -f infrastructure/docker/docker-compose.production.yml \
  --env-file infrastructure/docker/.env.production up -d --remove-orphans
```

**两套配置的关键差异**：

| 差异 | haier (❌ 勿用) | production (✅ 正确) |
|------|---------|-----------|
| gateway 服务 | 无 | `forge-gateway` (JWT 验签 + X-User-* 注入) |
| nginx 配置 | `nginx.conf` (API 直连 backend) | `nginx-production.conf` (API 经 gateway) |
| SSO 网络 | 依赖 `sso-net` external (Keycloak) | 不需要 (auth.synapse.gold 公网) |
| Env 文件 | `haier/.env` | `docker/.env.production` |

### 2. Gateway 的 X-User-* header 必须编码非 ASCII 字符

**问题**：`proxy.ts` 中 `headers.set('X-User-Name', '胖弟弟')` 直接报 TypeError — HTTP header 值不允许非 ASCII。
**修复**：注入时 `encodeURIComponent(value)`，后端读取时 `decodeURIComponent(header)`。

### 3. 端口不能绑定 127.0.0.1

**问题**：尝试将 forge-nginx/enterprise-console 端口从 `0.0.0.0` 改为 `127.0.0.1`，导致 gateway-nginx 502。
**原因**：gateway-nginx 运行在独立 Docker 网络（`gateway_default`），通过 `host.docker.internal` 访问宿主机端口。`127.0.0.1` 绑定的端口从容器内不可达。
**结论**：端口安全加固需用云厂商安全组/防火墙，不能靠绑定地址。

### 4. Gateway 镜像更新

```bash
docker pull pan9pang/synapse-gateway:latest
cd /opt/forge
docker compose -f infrastructure/docker/docker-compose.production.yml \
  --env-file infrastructure/docker/.env.production up -d forge-gateway
```

**注意**：容器内热修复（`docker cp`）在镜像更新后会丢失，不要依赖。

## 部署后验证

```bash
curl -sf https://forge.delivery/api/health
curl -sf -o /dev/null -w "%{http_code}" https://forge.delivery/console
curl -sf -o /dev/null -w "%{http_code}" https://sso.synapse.gold/auth/realms/forge
```
