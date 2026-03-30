# Forge Deployment Logbook

---

## forge — 2026-03-30 23:49:43

| 项目 | 值 |
|------|------|
| 时间 | 2026-03-30 23:49:43 |
| 版本 | `latest` |
| Git | `f01a32f` |
| 操作人 | deploy |
| 状态 | SUCCESS |
| 上一版本 | (none) |

### 容器状态

```
NAME                       IMAGE                                             COMMAND                  SERVICE              STATUS
forge-backend              ghcr.io/pan94u/forge/backend:latest               "java -jar app.jar"      backend              Up (healthy)
forge-database-mcp         ghcr.io/pan94u/forge/forge-database-mcp:latest    "java -jar /app/app.…"   database-mcp         Up (healthy)
forge-enterprise-console   ghcr.io/pan94u/forge/enterprise-console:latest    "docker-entrypoint.s…"   enterprise-console   Up
forge-frontend             ghcr.io/pan94u/forge/frontend:latest              "docker-entrypoint.s…"   frontend             Up
forge-knowledge-mcp        ghcr.io/pan94u/forge/forge-knowledge-mcp:latest   "java -jar /app/app.…"   knowledge-mcp        Up (healthy)
forge-nginx                nginx:alpine                                      "/docker-entrypoint.…"   nginx                Up
forge-postgres             postgres:16-alpine                                "docker-entrypoint.s…"   postgres             Up (healthy)
```

### 备注

旧容器因 compose project 名称变更导致冲突，手动移除后重建成功。
**⚠ 此次部署使用了错误的 compose 文件 (haier)，缺少 forge-gateway，导致认证跳转循环。已在下方记录中修正。**

---

## forge — 2026-03-31 00:00:00

| 项目 | 值 |
|------|------|
| 时间 | 2026-03-31 00:00:00 |
| 版本 | `latest` |
| Git | `f01a32f` |
| 操作人 | deploy |
| 状态 | SUCCESS |
| 上一版本 | (none) |

### 容器状态

```
NAME                       IMAGE                                             SERVICE              STATUS
forge-backend              ghcr.io/pan94u/forge/backend:latest               backend              Up (healthy)
forge-database-mcp         ghcr.io/pan94u/forge/forge-database-mcp:latest    database-mcp         Up (healthy)
forge-enterprise-console   ghcr.io/pan94u/forge/enterprise-console:latest    enterprise-console   Up
forge-frontend             ghcr.io/pan94u/forge/frontend:latest              frontend             Up
forge-gateway              pan9pang/synapse-gateway:latest                   forge-gateway        Up
forge-knowledge-mcp        ghcr.io/pan94u/forge/forge-knowledge-mcp:latest   knowledge-mcp        Up (healthy)
forge-nginx                nginx:alpine                                      nginx                Up
forge-postgres             postgres:16-alpine                                postgres             Up (healthy)
```

### 备注

修正上一次误用 `infrastructure/haier/docker-compose.yml` 的问题。切换到正确的 `infrastructure/docker/docker-compose.production.yml` + `.env.production`。关键差异：production 配置包含 `forge-gateway` 服务（JWT 验签 + X-User-* 注入）和 `nginx-production.conf`（API 路由经过 gateway）。

另发现 gateway `proxy.ts` 对中文用户名（如"胖弟弟"）报 500：HTTP header 不允许非 ASCII 字符。临时在容器内修复（`encodeURIComponent`），需同步到 gateway 源码仓库。
