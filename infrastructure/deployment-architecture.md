# Forge 生产部署架构 — 8C16G 单机

## 1. 架构总览

```
                    ┌──────── 8C / 16G 单机 ────────────────────────────┐
                    │                                                    │
  用户 ─── :9000 ── │  ┌──────────────────────────────────┐             │
                    │  │         Nginx (0.25C/128M)        │             │
                    │  │  统一入口 · Gzip · 限速 · 安全头   │             │
                    │  └──────┬──────┬──────┬──────┬───────┘             │
                    │         │      │      │      │                     │
                    │    /console  /api   /ws    /* (catch-all)          │
                    │         │      │      │      │                     │
                    │         ▼      ▼      ▼      ▼                     │
                    │  ┌────────┐ ┌──────────────┐ ┌──────────┐         │
                    │  │Console │ │   Backend     │ │ Frontend │         │
                    │  │Next.js │ │ Spring Boot 3 │ │ Next.js  │         │
                    │  │0.25C   │ │ + Workspace   │ │ 0.25C    │         │
                    │  │256M    │ │ 3C / 5G       │ │ 256M     │         │
                    │  └────────┘ └──────┬────────┘ └──────────┘         │
                    │                    │                                │
                    │          ┌─────────┴──────────┐                    │
                    │          ▼                     ▼                    │
                    │   ┌──────────────┐     ┌──────────────┐           │
                    │   │ Knowledge    │     │ Database     │           │
                    │   │ MCP (Ktor)   │     │ MCP (Ktor)   │           │
                    │   │ 0.25C/256M   │     │ 0.25C/256M   │           │
                    │   └──────────────┘     └──────┬───────┘           │
                    │                               ▼                    │
                    │                      ┌──────────────────┐         │
                    │                      │ PostgreSQL 16    │         │
                    │                      │ 1C / 1.5G        │         │
                    │                      └────────┬─────────┘         │
                    │                               │                    │
                    │                    /data/forge/ (宿主机磁盘)        │
                    └────────────────────────────────────────────────────┘
```

## 2. 容器清单 · 7 个

| # | 容器 | 镜像 / 构建 | CPU | 内存 | 对外端口 | 职责 |
|---|------|------------|-----|------|---------|------|
| 1 | postgres | `postgres:16-alpine` | 1C | 1.5G | 127.0.0.1:5432 | 数据持久化 |
| 2 | knowledge-mcp | 本地构建 (Ktor) | 0.25C | 256M | — | 知识库检索 |
| 3 | database-mcp | 本地构建 (Ktor) | 0.25C | 256M | — | 数据库只读查询 |
| 4 | backend | 本地构建 (Spring Boot 3) | 3C | 5G | 127.0.0.1:8080 | API + WebSocket + Workspace 运行时 |
| 5 | frontend | 本地构建 (Next.js 15) | 0.25C | 256M | — | Web IDE 前端 |
| 6 | enterprise-console | 本地构建 (Next.js 15) | 0.25C | 256M | — | 企业管理控制台 |
| 7 | nginx | `nginx:alpine` | 0.25C | 128M | **9000** | 统一入口反向代理 |

> 用户访问入口：`http://<host>:9000`（Web IDE）、`http://<host>:9000/console/`（企业控制台）

## 3. 资源分配策略

```
总资源        8C / 16G
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
容器占用       5.25C / 7.7G    (66% CPU, 48% 内存)
OS + Docker    ~0.75C / ~1G
弹性余量       2C / 7.3G       (workspace 编译峰值 + PG 查询峰值)
```

**核心设计决策**：Backend 容器分配 3C/5G，因为它不仅运行 Spring Boot（JVM 堆 1.5G），还在容器内执行 workspace 子进程（npm build、python、git 等用户编译任务），需要充足的 CPU 和内存余量。

## 4. 网络拓扑

```
外部流量
   │
   ▼
┌─────────┐     Docker 内部网络 (bridge)
│ :9000   │     ┌──────────────────────────────────────────┐
│  Nginx  │────►│ frontend:3000       (/* catch-all)       │
│         │────►│ enterprise-console:3000 (/console/*)     │
│         │────►│ backend:8080        (/api/* /ws/*)       │
└─────────┘     │                                          │
                │ backend:8080 ────► knowledge-mcp:8081    │
                │               ────► database-mcp:8082    │
                │                                          │
                │ database-mcp:8082 ──► postgres:5432      │
                │ backend:8080      ──► postgres:5432      │
                └──────────────────────────────────────────┘
```

**端口暴露原则**：
- 只有 Nginx 的 9000 端口对外开放
- PostgreSQL 和 Backend 的端口绑定 `127.0.0.1`（仅本机运维可达，外部不可访问）
- MCP 服务、Frontend、Console 不暴露端口（仅 Docker 内部通信）

## 5. 数据持久化

```
/data/forge/                          宿主机持久化根目录
├── postgres/                         PostgreSQL PGDATA (bind mount)
├── backend/                          Backend 应用数据 (bind mount)
└── backups/                          PG 自动备份 (cron)
    └── pg_dump_YYYYMMDD.sql.gz
```

| 数据 | 持久化方式 | 位置 |
|------|-----------|------|
| PostgreSQL | bind mount volume | /data/forge/postgres |
| Backend 应用数据 | bind mount volume | /data/forge/backend |
| Plugins | 源码目录只读挂载 | `../../plugins:/plugins:ro` |
| Knowledge Base | 源码目录只读挂载 | `../../knowledge-base:/knowledge-base:ro` |
| 容器日志 | Docker json-file driver | Docker 默认位置，已配 logrotate |

## 6. JVM 调优

| 参数 | 值 | 说明 |
|------|-----|------|
| `-Xms` | 512m | 初始堆，按需增长 |
| `-Xmx` | 1536m | 最大堆 1.5G，容器 5G 中留 3.5G 给 workspace 进程 |
| `-XX:+UseG1GC` | — | JDK 21 默认 GC，低延迟 |
| `-XX:MaxGCPauseMillis` | 200 | GC 暂停目标 |
| `-XX:+UseStringDeduplication` | — | 减少字符串内存占用 |

MCP Server（Ktor）运行在 256M 容器中，JVM 21 容器感知自动设堆 ~192M，无需手动调。

## 7. PostgreSQL 调优

```
shared_buffers          = 384MB     # 容器内存的 25%
effective_cache_size    = 1GB       # 容器内存的 66%（含 OS cache）
work_mem                = 8MB       # 单查询排序内存
maintenance_work_mem    = 128MB     # VACUUM/索引构建
max_connections         = 50        # HikariCP max=20，留余量
wal_buffers             = 16MB
checkpoint_completion_target = 0.9
random_page_cost        = 1.1       # SSD 磁盘
log_min_duration_statement = 1000   # 慢查询记录 >1s
```

## 8. Nginx 路由规则

| 路径 | 目标 | 特殊处理 |
|------|------|---------|
| `/console/*` | enterprise-console:3000 | strip `/console` 前缀 |
| `/api/*` | backend:8080 | SSE: `proxy_buffering off`，限速 30r/s |
| `/ws/*` | backend:8080 | WebSocket upgrade，限速 5r/s |
| `/actuator/health` | backend:8080 | 放行 |
| `/actuator/prometheus` | backend:8080 | 建议限制源 IP |
| `/actuator/*` | — | 返回 403 |
| `/h2-console/*` | — | 返回 403 |
| `/*` | frontend:3000 | catch-all |

额外能力：Gzip 压缩、安全响应头（X-Frame-Options / X-Content-Type-Options / XSS-Protection）、`server_tokens off`。

## 9. 启动依赖链

```
postgres ──(healthy)──┐
                      ├──► knowledge-mcp ──(healthy)──┐
                      ├──► database-mcp  ──(healthy)──┤
                      │                               ├──► backend ──(healthy)──┐
                      │                               │                        ├──► frontend
                      │                               │                        ├──► enterprise-console
                      │                               │                        └──► nginx
```

## 10. 日志管理

所有容器统一使用 Docker `json-file` 日志驱动 + 大小限制：

| 容器 | max-size | max-file | 最大磁盘占用 |
|------|----------|----------|-------------|
| backend | 100m | 5 | 500MB |
| postgres | 50m | 3 | 150MB |
| nginx | 50m | 3 | 150MB |
| 其他 4 个 | 20m | 2 | 160MB |
| **总计** | | | **~960MB** |

## 11. 健康检查

| 容器 | 检查方式 | 间隔 | 超时 | 重试 | 启动宽限 |
|------|---------|------|------|------|---------|
| postgres | `pg_isready -U forge` | 10s | 5s | 5 | — |
| knowledge-mcp | `GET /health/live` | 10s | 5s | 5 | 15s |
| database-mcp | `GET /health/live` | 10s | 5s | 5 | 15s |
| backend | `GET /api/knowledge/search` | 15s | 5s | 5 | 45s |

## 12. 安全设计

| 项目 | 状态 | 说明 |
|------|------|------|
| 认证 (OAuth2/Keycloak) | **暂未启用** | `FORGE_SECURITY_ENABLED=false`，后续可外接集团账号中心 |
| TLS | 未配置 | 当前 HTTP 明文，建议部署时前置负载均衡或网关做 TLS 终止 |
| 端口暴露 | 最小化 | 仅 Nginx:9000 对外，PG/Backend 绑定 127.0.0.1 |
| H2 Console | 禁止 | Nginx 返回 403 |
| Actuator | 限制 | 仅 health + prometheus 放行，其余 403 |
| DB 密码 | 环境变量注入 | 不硬编码，通过 `--env-file` 加载 |
| 安全响应头 | 已配置 | X-Frame-Options / X-Content-Type-Options / XSS-Protection |

## 13. 备份策略

```
每日 03:00   pg_dump → /data/forge/backups/pg_dump_YYYYMMDD.sql.gz
每日 04:00   清理 7 天前的备份
```

## 14. 扩展路径（超出 8C16G 时）

| 瓶颈 | 扩展方向 |
|------|---------|
| Workspace 并发不足 | 拆分 workspace 到独立机器，backend 仅做编排 |
| 数据库压力 | PostgreSQL 迁移到独立机器或 RDS |
| 需要认证 | 外接集团账号中心或部署独立 Keycloak 实例 |
| 高可用 | Nginx → 2 节点 + Keepalived；Backend 无状态化后水平扩展 |
