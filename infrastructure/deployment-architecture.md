# Forge 部署架构

## 1. 架构总览

Forge 平台采用 **SSO 独立部署 + 应用集群** 的双机架构。SSO（Keycloak）运行在独立机器上，通过域名对外服务；应用机器运行 nginx、前后端、数据库等 7 个容器。

两套环境（Trial / Production）架构相同，仅在域名、SSL、网络连接方式上有差异。

```
浏览器                          应用机器                       SSO 机器
┌──────────┐                   ┌────────────────────┐        ┌──────────────────┐
│          │  HTTPS / HTTP     │  nginx (统一入口)   │        │  Keycloak 24     │
│  用户    │──────────────────►│   ├─ frontend      │        │   realm: forge   │
│          │                   │   ├─ backend       │ HTTPS  │   3 个 OIDC 客户端│
│          │  OIDC 登录重定向   │   ├─ console      │───────►│                  │
│          │──────────────────►│   ├─ postgres      │ DNS    │  SSO PostgreSQL  │
│          │  (浏览器直达SSO)   │   ├─ knowledge-mcp│        │                  │
└──────────┘                   │   └─ database-mcp  │        │                  │
                               │                    │        │                  │
                               │  forge.delivery    │        │  sso.forge.delivery│
                               └────────────────────┘        └──────────────────┘
```

## 2. 两套环境对比

| 维度 | Trial（本地开发） | Production（生产） |
|------|------------------|-------------------|
| **部署机器** | 1 台（SSO + 应用同机） | 2 台（SSO 独立机器） |
| **应用域名** | `forge.local:19000` | `forge.delivery` |
| **SSO 域名** | `sso.forge.local:8180` | `sso.forge.delivery` |
| **SSL** | 无（HTTP） | HTTPS (443) |
| **SSO 连接** | Docker 共享网络 `sso-net` | 公网 DNS + HTTPS |
| **Compose** | `docker-compose.trial.yml` | `docker-compose.production.yml` |
| **Nginx** | `nginx-trial.conf` (端口 9000) | `nginx-production.conf` (80/443) |
| **环境变量** | `.env` (从 `.env.trial.example`) | `.env.production` (从 `.env.production.example`) |
| **SSO_URL** | `http://sso.forge.local:8180/auth` | `https://sso.forge.delivery/auth` |

## 3. SSO 认证流程

采用标准 OIDC Authorization Code + PKCE 流程。**浏览器直接与 SSO 域名交互**，nginx 不代理 SSO 请求。

```
浏览器                        nginx (应用)              backend            SSO (Keycloak)
  │                              │                       │                    │
  │  1. GET /                    │                       │                    │
  │─────────────────────────────►│──► frontend           │                    │
  │  ◄── 页面加载                 │                       │                    │
  │                              │                       │                    │
  │  2. GET /api/auth/sso-config │                       │                    │
  │─────────────────────────────►│──────────────────────►│                    │
  │  ◄── {ssoUrl, realm, clientId}                       │                    │
  │                              │                       │                    │
  │  3. 浏览器直接跳转 SSO 域名                                                │
  │──────────────────────────────────────────────────────────────────────────►│
  │  ◄── SSO 登录页面                                                         │
  │                              │                       │                    │
  │  4. 登录成功 → SSO 回调 /auth/callback                                     │
  │─────────────────────────────►│──► frontend           │                    │
  │                              │                       │                    │
  │  5. Token exchange (fetch 直接 POST SSO)                                   │
  │──────────────────────────────────────────────────────────────────────────►│
  │  ◄── access_token + refresh_token                                         │
  │                              │                       │                    │
  │  6. API 请求 (Bearer token)  │                       │                    │
  │─────────────────────────────►│──────────────────────►│                    │
  │                              │  JWT 验证 (JWK Set)   │───► 获取公钥 ──────►│
```

### 关键设计决策

- **Runtime SSO Discovery**: 前端运行时从 `/api/auth/sso-config` 获取 SSO 地址，不依赖 `NEXT_PUBLIC_*` 构建时变量
- **PKCE 公开客户端**: Web IDE 使用 `forge-web-ide` 公开客户端 + PKCE，无需 client_secret
- **Enterprise Console**: 使用 `forge-enterprise` 机密客户端 + Auth.js (NextAuth)，服务端 OIDC
- **跨域 Token Exchange**: Keycloak `webOrigins` 配置允许应用域名直接调用 token 端点（CORS）
- **`crypto.subtle` 降级**: HTTP 环境（forge.local）下 Web Crypto API 不可用，PKCE 使用 `js-sha256` 降级

## 4. 容器清单

### 应用机器（7 个容器）

| 容器 | 镜像 | CPU | 内存 | 端口 | 说明 |
|------|------|-----|------|------|------|
| nginx | `nginx:alpine` | 0.25C | 64M | 80, 443 (prod) / 9000 (trial) | 统一入口 + SSL 终止 |
| frontend | 自建 (Next.js 15) | 0.25C | 128M | — | Web IDE 前端 |
| backend | 自建 (Spring Boot 3) | 1.5C | 1.5G | 8080 (内部) | API + WebSocket + Workspace |
| enterprise-console | 自建 (Next.js 15) | 0.25C | 128M | 9001 (内部) | 企业管理控制台 |
| postgres | `postgres:16-alpine` | 0.5C | 512M | 5432 (内部) | Forge 应用数据库 |
| knowledge-mcp | 自建 (Ktor) | 0.25C | 128M | — | 知识库 MCP |
| database-mcp | 自建 (Ktor) | 0.25C | 128M | — | 数据库查询 MCP (只读) |
| **合计** | | **3.25C** | **2.6G** | | |

### SSO 机器（2 个容器）

| 容器 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| keycloak | `keycloak:24.0` | 8180 | 认证中心（realm: forge） |
| sso-postgres | `postgres:16-alpine` | 5432 (内部) | Keycloak 专属数据库 |

## 5. 网络拓扑

### Trial — Docker 共享网络

本地开发时 SSO 和应用在同一台机器，通过 Docker 共享网络 `sso-net` 连接：

```
sso-net (Docker network)                  default (Docker network)
┌────────────────────────┐                ┌──────────────────────┐
│  sso-postgres          │                │  postgres            │
│  keycloak              │                │  knowledge-mcp       │
│    (alias: sso.forge.local)             │  database-mcp        │
│                        │                │  frontend            │
│  backend ──────────────┼────────────────┼── backend            │
│  enterprise-console ───┼────────────────┼── enterprise-console │
└────────────────────────┘                │  nginx               │
                                          └──────────────────────┘
宿主机端口:
  19000 → nginx:9000 (应用入口)
  8180  → keycloak:8180 (SSO 管理)
```

### Production — 公网 DNS

生产环境 SSO 在独立机器，应用通过公网 DNS 访问，无需 Docker 网络互联：

```
应用机器 (forge.delivery)                 SSO 机器 (sso.forge.delivery)
┌──────────────────────┐                 ┌──────────────────────┐
│  default (Docker)    │                 │  sso-net (Docker)    │
│  ┌─────────────────┐ │                 │  ┌─────────────────┐ │
│  │ nginx (80/443)  │ │                 │  │ nginx (80/443)  │ │
│  │ frontend        │ │   公网 HTTPS    │  │ keycloak (8180) │ │
│  │ backend ────────┼─┼────────────────►│  │ sso-postgres    │ │
│  │ console ────────┼─┼────────────────►│  └─────────────────┘ │
│  │ postgres        │ │  DNS 解析       │                      │
│  │ knowledge-mcp   │ │  sso.forge.delivery                    │
│  │ database-mcp    │ │                 │                      │
│  └─────────────────┘ │                 │                      │
└──────────────────────┘                 └──────────────────────┘
```

## 6. Nginx 路由规则

| 路径 | 目标 | 特殊处理 |
|------|------|---------|
| `/auth/callback` | frontend:3000 | OIDC 回调页 |
| `/console` | — | 302 → `/console/zh` |
| `/console/*` | enterprise-console:9001 | strip `/console` 前缀 |
| `/console/api/*` | enterprise-console:9001 | Console API |
| `/api/auth/me` | backend:8080 | 后端用户信息 |
| `/api/auth/sso-config` | backend:8080 | SSO 配置发现 |
| `/api/auth/*` | enterprise-console:9001 | Auth.js OIDC（buffer 16k） |
| `/api/*` | backend:8080 | SSE: `proxy_buffering off`，限速 30r/s |
| `/ws/*` | backend:8080 | WebSocket upgrade，限速 5r/s |
| `/actuator/health` | backend:8080 | 健康检查 |
| `/actuator/prometheus` | backend:8080 | 监控指标 |
| `/actuator/*` | — | 403 |
| `/h2-console/*` | — | 403 |
| `/*` | frontend:3000 | catch-all |

Production 额外：Gzip 压缩、HTTPS 重定向、安全头（X-Frame-Options / X-Content-Type-Options / XSS-Protection）、`server_tokens off`。

## 7. 数据持久化

| 数据 | 持久化方式 | 位置 |
|------|-----------|------|
| PostgreSQL | bind mount (prod) / Docker volume (trial) | `/data/forge/postgres` |
| Backend 应用数据 | bind mount (prod) / Docker volume (trial) | `/data/forge/backend` |
| Plugins | 源码目录只读挂载 | `../../plugins:/plugins:ro` |
| Knowledge Base | 源码目录只读挂载 | `../../knowledge-base:/knowledge-base:ro` |
| SSO PostgreSQL | Docker volume | `sso-postgres-data` |
| 容器日志 | Docker json-file driver | Docker 默认位置，已配 logrotate |

## 8. 启动依赖链

```
postgres ──(healthy)──┐
                      ├──► knowledge-mcp ──(healthy)──┐
                      ├──► database-mcp  ──(healthy)──┤
                      │                               ├──► backend ──(healthy)──┐
                      │                               │                        ├──► frontend
                      │                               │                        ├──► enterprise-console
                      │                               │                        └──► nginx
```

SSO（Keycloak）独立启动，不在应用依赖链中。应用通过 SSO_URL 环境变量配置连接。

## 9. 安全设计

| 项目 | 说明 |
|------|------|
| 认证 | OAuth2 / Keycloak OIDC（已启用，`FORGE_SECURITY_ENABLED=true`） |
| TLS | Production: nginx 做 SSL 终止 (443)；Trial: HTTP 明文 |
| 端口暴露 | 仅 nginx 对外，PG/Backend 绑定 `127.0.0.1` |
| H2 Console | Production: 403；Trial: 放行 |
| Actuator | 仅 health + prometheus 放行，其余 403 |
| 密码管理 | 环境变量注入，不硬编码 |
| 安全响应头 | X-Frame-Options / X-Content-Type-Options / XSS-Protection |
| SSO 客户端 | Web IDE: 公开 + PKCE；Console: 机密 + client_secret |

## 10. 文件结构

```
infrastructure/
├── docker/                          # 应用部署
│   ├── docker-compose.trial.yml     # Trial 环境（单机，含 sso-net 网络）
│   ├── docker-compose.production.yml # Production 环境（应用机器，纯 DNS 连 SSO）
│   ├── nginx-trial.conf             # Trial nginx（端口 9000，HTTP）
│   ├── nginx-production.conf        # Production nginx（80/443，HTTPS + SSL）
│   ├── .env.trial.example           # Trial 环境变量模板
│   └── .env.production.example      # Production 环境变量模板
├── sso/                             # SSO 独立部署
│   ├── docker-compose.yml           # Keycloak + PostgreSQL
│   ├── realm-export.json            # Realm 配置（3 个客户端）
│   └── .env.example                 # SSO 环境变量模板
├── deployment-architecture.md       # 本文档
└── deployment-manual.md             # 部署操作手册
```
