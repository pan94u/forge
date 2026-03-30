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
