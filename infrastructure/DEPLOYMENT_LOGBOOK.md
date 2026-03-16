# Forge 部署记录

## 环境

- 服务器: Rocky Linux / 2 CPU / 4G RAM / 内网 IP 10.3.0.16
- 部署方式: 生产环境 (docker-compose.production-single.yml)
- 日期: 2026-03-04

## 步骤与耗时

| 步骤 | 操作 | 耗时 |
|------|------|------|
| 1 | 安装 git, docker, jdk21, nodejs20 | ~5min |
| 2 | 克隆仓库 + 子模块 | ~1min |
| 3 | 构建后端 JAR | 4min |
| 4 | 构建 MCP Server | 1min |
| 5 | 构建前端 + 控制台 | 2min |
| 6 | 配置环境变量 + nginx ssl | ~1min |
| 7 | 启动容器 | ~3min |

## 问题汇总

### 1. Git SSH 认证失败
- **现象**: 克隆仓库时 `git@github.com: Permission denied (publickey)`
- **原因**: 未配置 GitHub SSH 公钥
- **解决**: 生成 SSH 密钥并将公钥添加到 GitHub 账户

### 2. Docker CPU 限制过高
- **现象**: `Error: Range of CPUs is from 0.01 to 2.00`
- **原因**: docker-compose.yml 中 backend 配置 `cpus: 3`，机器只有 2 核
- **解决**: 改为 `cpus: 2`

### 3. 机器内存不足导致 OOM
- **现象**: 机器宕机
- **原因**: backend 配置 `mem_limit: 5g`，机器只有 4G 内存
- **解决**: 改为 `mem_limit: 2g`，JVM 堆 `-Xmx1536m`

### 4. SSO 登录跳转到 localhost (重点!)
- **现象**: 登录跳转 `http://localhost:8180/realms/forge/...`
- **原因**: 多处配置问题
  1. Keycloak `KC_HOSTNAME_URL` 写死为 `https://forge.delivery/auth`
  2. realm-export.json 中的 client redirectUris 包含 localhost
  3. 前端构建时环境变量未正确传入
  4. 前端 .next/standalone 缓存了旧配置
- **解决**:
  1. Keycloak 环境变量改为 `${KEYCLOAK_PUBLIC_URL}`
  2. 重建 Keycloak 数据库重新导入 realm
  3. 前端构建时传入环境变量
  4. 前端容器需要重建才能加载新的构建产物

### 5. /auth/callback 404
- **现象**: 登录成功后跳转 `/auth/callback` 页面不存在
- **原因**: nginx 配置缺少 `/auth/callback` 路由，被 `/auth/` 规则匹配到 keycloak
- **解决**: 添加 `location = /auth/callback` 规则代理到 frontend

### 6. 外部访问异常
- **现象**: DNS 解析到公网 IP 13.248.243.5 但机器只有内网 IP
- **原因**: 云平台网络配置问题，需检查安全组和 NAT 转发

## 最终修改的文件

- `infrastructure/docker/docker-compose.production-single.yml`
  - backend: cpus 3→2, mem_limit 5g→2g
  - nginx: 添加 443 端口和 SSL 挂载
  - keycloak: KC_HOSTNAME_URL 改为环境变量
- `infrastructure/docker/nginx-production.conf`
  - 添加 HTTPS 配置
  - 添加 /auth/callback 路由
- `web-ide/frontend/`
  - 需重新构建才能加载正确的环境变量

## 最终端口

- HTTP: 80, HTTPS: 443
- Keycloak: 8180, Backend: 8080

## 访问地址

- Web IDE: https://forge.delivery
- 控制台: https://forge.delivery/console
- Keycloak: https://forge.delivery/auth

## 命令

```bash
cd /root/forge/infrastructure/docker
docker compose -f docker-compose.production-single.yml --env-file .env.production up -d
docker compose -f docker-compose.production-single.yml logs -f

# 重建前端（修改环境变量后）
cd ../../web-ide/frontend
NEXT_PUBLIC_KEYCLOAK_URL=https://forge.delivery/auth npm run build
cd ../../infrastructure/docker
docker compose -f docker-compose.production-single.yml up -d --build frontend
```

---

## Synapse AI 部署 (synapse.gold)

- 日期: 2026-03-09
- 仓库: git@github.com:pan94u/Synapse-AI.git (main)
- 部署路径: /home/deploy/Synapse-AI
- 域名: https://synapse.gold
- SSO: Keycloak forge-synapse client 接入

### 步骤与耗时

| 步骤 | 操作 | 耗时 |
|------|------|------|
| 1 | 生成 SSH key + 克隆仓库 | ~2min |
| 2 | 安装 bun | ~1min |
| 3 | bun install + next build | ~1min |
| 4 | docker compose up --build | ~3min |
| 5 | SSL 证书部署 + nginx 重载 | ~1min |
| 6 | Keycloak client 更新 redirectUris | ~1min |
| 7 | DNS 修正 + 端到端验证 | ~2min |

### 问题汇总

#### 1. NEXTAUTH_SECRET 未传入容器
- **现象**: synapse-web 启动后报 `MissingSecret`，`/api/auth/providers` 返回 500
- **原因**: docker-compose.production.yml 中 `NEXTAUTH_SECRET=${NEXTAUTH_SECRET}` 从 shell 环境变量读取，覆盖了 env_file 中的值，而 shell 中未 export 该变量
- **解决**: 移除 environment 中的 `NEXTAUTH_SECRET` 行，让 env_file (.env.production) 自动加载

#### 2. nginx 配置修改后未生效
- **现象**: nginx -s reload 后 synapse.gold server block 未加载，只有 forge.delivery
- **原因**: Docker bind mount 单文件时，宿主机文件 inode 变化后容器内不会自动更新
- **解决**: `docker restart forge-nginx` 重启容器使新配置生效

#### 3. synapse.gold DNS 指向错误 IP
- **现象**: 浏览器访问 synapse.gold 跳转到 `/auth/admin/master/console/`（Keycloak Admin Console）
- **原因**: synapse.gold DNS A 记录指向 43.156.185.112（非本机），请求到了另一台机器的 nginx，该 nginx 无 synapse.gold server block，fallback 到 Keycloak
- **解决**: 将 DNS A 记录改为 124.156.192.129（App 机器公网 IP，与 forge.delivery 相同）

### 架构

```
浏览器 → synapse.gold (DNS: 124.156.192.129)
       → forge-nginx (443, SSL 终止)
       → proxy_pass http://172.19.0.1:19300 (docker gateway → 宿主机端口)
       → synapse-web 容器 (3000)
       → synapse-server 容器 (3001)

认证流:
synapse.gold → 307 → /api/auth/signin → 302 → sso.synapse.gold/auth/realms/forge/...
→ 用户登录 → callback → session cookie → synapse.gold/chat
```

### 修改的文件

| 仓库 | 文件 | 改动 | Commit |
|------|------|------|--------|
| forge | infrastructure/docker/nginx-production.conf | SYNAPSE_APP_HOST → 172.19.0.1 | d3c661e |
| Synapse-AI | docker-compose.production.yml | 移除 environment 中的 NEXTAUTH_SECRET | b40eb1f |
| Synapse-AI | .env.production | 填入 MINIMAX_API_KEY + NEXTAUTH_SECRET（未提交，含密钥） | — |
| Keycloak | forge-synapse client | redirectUris/webOrigins 添加 https://synapse.gold | API 更新 |

### 端口

- synapse-web: 19300 (宿主机) → 3000 (容器)
- synapse-server: 19301 (宿主机) → 3001 (容器)

### 访问地址

- Synapse AI: https://synapse.gold
- SSO 登录: https://sso.synapse.gold/auth/realms/forge

### 命令

```bash
# 启动 Synapse
cd /home/deploy/Synapse-AI
docker compose -f docker-compose.production.yml up --build -d

# 查看日志
docker logs synapse-web --tail 50
docker logs synapse-server --tail 50

# 重启
docker compose -f docker-compose.production.yml down
docker compose -f docker-compose.production.yml up -d

# 重载 nginx（修改 nginx-production.conf 后需 restart 而非 reload）
docker restart forge-nginx
```

---

## SSO 迁移：sso.forge.delivery → sso.synapse.gold

- 日期: 2026-03-16
- 原因: SSO 独立部署到 sso.synapse.gold，弃用 sso.forge.delivery

### 修改内容

| 应用 | 文件 | 改动 |
|------|------|------|
| Forge | infrastructure/docker/.env.production | `SSO_URL` 改为 `https://sso.synapse.gold/auth` |
| Synapse-AI | .env.production | KEYCLOAK_ISSUER / KEYCLOAK_INTERNAL_URL / KEYCLOAK_JWKS_URI 全部改为 sso.synapse.gold |
| Synapse-AI | docker-compose.production.yml | environment 中 4 处 sso.forge.delivery → sso.synapse.gold |

### 影响的容器

- **forge-backend**: `JWT_ISSUER_URI` + `JWK_SET_URI` （通过 `${SSO_URL}` 变量）
- **forge-enterprise-console**: `KEYCLOAK_ISSUER` （通过 `${SSO_URL}` 变量）
- **synapse-web**: `KEYCLOAK_ISSUER` + `KEYCLOAK_INTERNAL_URL`
- **synapse-server**: `KEYCLOAK_ISSUER` + `KEYCLOAK_JWKS_URI`

### 操作

```bash
# Forge — 只改 .env.production 一处，compose 通过 ${SSO_URL} 引用
cd /opt/forge/infrastructure/docker
docker compose -f docker-compose.production.yml --env-file .env.production up -d

# Synapse — 改 .env.production + docker-compose.production.yml
cd /home/deploy/Synapse-AI
export NEXTAUTH_SECRET=$(grep NEXTAUTH_SECRET .env.production | cut -d= -f2-)
docker compose -f docker-compose.production.yml down
docker compose -f docker-compose.production.yml up -d
```
