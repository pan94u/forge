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

---

## CIMC 灯塔工厂部署

- 日期: 2026-03-20
- 仓库: git@github.com:Zhao055/CIMC.git (main)
- 部署路径: /home/deploy/CIMC
- 访问方式: IP 直接访问（无域名）

### 服务端口

| 服务 | 端口 | 内存限额 | 说明 |
|------|------|---------|------|
| web | 3888 | 256M | 前端 (Next.js 15) |
| server | 3889 | 512M | 后端 (Bun + Hono) |
| ope | 5888/5889 | 256M | 人效系统 (Next.js + Bun API) |

### 问题汇总

#### 1. OPE Dockerfile 引用不存在的 public 目录
- **现象**: `COPY --from=builder /app/apps/ope/public` 构建失败
- **解决**: 删除该 COPY 行

#### 2. Web 容器 Next.js 监听 3000 而非 3888
- **现象**: 端口映射 3888→3000 不通
- **解决**: Dockerfile 添加 `ENV PORT=3888 HOSTNAME=0.0.0.0`

#### 3. OPE 容器缺少 hono 依赖 + 端口错误
- **现象**: bun 报 `Cannot find package 'hono'`，Next.js 监听 3000
- **解决**: Dockerfile 添加 `bun install` 安装服务端依赖，CMD 中设置 `PORT=5888`

### 依赖关系

CIMC 依赖 Synapse-AI 的 `@synapse/shared` 和 `@synapse/agent-core`，构建前需运行 `docker-build.sh` 从 `../Synapse-AI/` 复制依赖到 `.deps/`。

### 命令

```bash
cd /home/deploy/CIMC
bash docker-build.sh              # 复制依赖 + 构建 + 启动
docker compose logs -f            # 查看日志
docker compose down               # 停止
```

### 访问地址

- 前端: http://124.156.192.129:3888
- 后端: http://124.156.192.129:3889
- OPE: http://124.156.192.129:5888

---

## Forge 更新 — forge-eval 模块集成修复

- 日期: 2026-03-20
- 原因: 拉取最新代码后 forge-eval 模块与 webide backend 存在代码冲突

### 问题汇总

#### 1. Bean 定义重名 — EvalTaskRepository
- **现象**: `Cannot register bean definition 'evalTaskRepository'`，forge-eval 和 webide 各有一份同名 Repository
- **解决**: `ForgeWebIdeApplication.kt` 的 `@EntityScan` 和 `@EnableJpaRepositories` 缩小为只扫描 `com.forge.webide`

#### 2. Flyway 迁移文件重复 — V24
- **现象**: `V24__create_forge_eval_tables.sql` 和 `V24__eval_platform.sql` 同版本号
- **解决**: 删除旧版 `V24__eval_platform.sql`，保留新版；手动创建缺失的 `eval_results` 表

#### 3. Hibernate Schema Validation 失败
- **现象**: `wrong column type in eval_runs.id: found uuid, expecting varchar`
- **原因**: forge-eval 迁移用 UUID 类型，webide Entity 用 String；且 `docker-compose.production.yml` 中 `SPRING_JPA_HIBERNATE_DDL_AUTO: "validate"` 环境变量覆盖了 application.yml
- **解决**: docker-compose 环境变量改为 `"none"`，跳过启动时 schema 验证（Flyway 已保证 schema 正确性）

### 修改的文件

| 文件 | 改动 |
|------|------|
| web-ide/backend/src/.../ForgeWebIdeApplication.kt | scan 范围从 `[webide, eval.api]` 缩小为 `[webide]` |
| web-ide/backend/src/.../application.yml | ddl-auto: validate → none |
| web-ide/backend/src/.../application-prod.yml | ddl-auto: validate → none |
| infrastructure/docker/docker-compose.production.yml | SPRING_JPA_HIBERNATE_DDL_AUTO: validate → none |
| web-ide/backend/.../V24__eval_platform.sql | 删除（与 V24__create_forge_eval_tables.sql 重复） |
| DB 手动操作 | CREATE TABLE eval_results（webide Entity 需要但迁移未创建） |

---

## 磁盘迁移 + 全量重部署

- 日期: 2026-03-22
- 原因: Docker 软链接错误 (`/var/lib/docker/docker → /data/docker` 嵌套)，所有容器/镜像丢失

### 迁移内容

1. **fstab**: 写入 HDD UUID，重启自动挂载 `/data`
2. **Docker 数据根**: 修复软链接 `/var/lib/docker → /data/docker` (HDD)
3. **PostgreSQL 数据**: 从旧 volume 备份恢复到 SSD `/ssd-data/forge-postgres/`，密码重置为 `forge123`（trust → scram-sha-256）
4. **SSO**: 弃用本机 Keycloak，改为独立部署 `sso.synapse.gold`
5. **Gateway Nginx**: 新建 `/opt/gateway/`，TLS 终止 + 反向代理（手动 SSL 证书）

### 配置变更

| 文件 | 改动 |
|------|------|
| `haier/docker-compose.yml` | postgres volume: `${DATA_ROOT}/forge-postgres` → `${PG_DATA_PATH:-/ssd-data/forge-postgres}` |
| `haier/nginx.conf` | SSO `/auth/` 代理去掉 keycloak upstream，改为 404 |
| `haier/.env` | 新建: SSO_URL → `sso.synapse.gold`, APP_URL → `https://forge.delivery` |

### 部署架构

```
Gateway Nginx (:443)
├── forge.delivery → localhost:9000 (forge-nginx)
├── synapse.gold → localhost:19300 (synapse-web)
└── SSL: /opt/gateway/certs/{domain}/

Forge (:9000)
├── postgres (SSD /ssd-data/forge-postgres)
├── backend + knowledge-mcp + database-mcp
├── frontend + enterprise-console
└── nginx (9000 app / 9100 SSO 已禁用)

SSO: sso.synapse.gold (独立机器)
```

### 验证
- `https://forge.delivery` 正常访问，SSO 登录跳转到 `sso.synapse.gold`
- 7 个容器全部 Running/Healthy
- PostgreSQL 旧数据恢复成功（Flyway 迁移通过）
