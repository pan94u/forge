# Forge 部署手册

> 两套环境，架构相同，差异仅在域名和 SSL：
>
> | 环境 | Compose 文件 | 应用容器 | SSO | 入口 |
> |------|-------------|---------|-----|------|
> | Trial | `docker-compose.trial.yml` | 7 个 | 同机（sso-net 网络） | `http://forge.local:19000` |
> | Production | `docker-compose.production.yml` | 7 个 | 独立机器（DNS + HTTPS） | `https://forge.delivery` |

---

## 一、环境准备

### 1.1 安装 Docker + Compose

**Ubuntu 22.04+：**

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

**Rocky Linux / RHEL / CentOS Stream：**

```bash
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl start docker
sudo systemctl enable docker
```

**验证：**

```bash
docker --version         # 需要 24+
docker compose version   # 需要 v2.20+
```

### 1.2 配置国内镜像加速（国内服务器必须）

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << 'EOF'
{
  "registry-mirrors": ["https://docker.m.daocloud.io"]
}
EOF
systemctl daemon-reload
systemctl restart docker
```

### 1.3 安装构建工具

**Ubuntu：**

```bash
sudo apt install -y openjdk-21-jdk
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs git
```

**Rocky Linux：**

```bash
sudo dnf install -y java-21-openjdk-devel git
curl -fsSL https://rpm.nodesource.com/setup_20.x | sudo bash -
sudo dnf install -y nodejs
```

**验证：**

```bash
java -version   # 必须 21+
node -v         # 需要 20+
npm -v
git --version
```

---

## 二、获取代码

```bash
cd /opt
git clone git@github.com:pan94u/forge.git
cd forge
git submodule update --init --recursive
```

> SSH 未配置时用 HTTPS：`git clone https://github.com/pan94u/forge.git`

---

## 三、构建产物

> 原则：**宿主机构建 + Docker 只打包**。不在容器内编译（避免网络/依赖问题）。

```bash
cd /opt/forge

# 后端 JAR（约 10 分钟，首次需下载依赖）
./gradlew :web-ide:backend:clean :web-ide:backend:bootJar -x test

# MCP Server JAR
./gradlew :mcp-servers:forge-knowledge-mcp:shadowJar
./gradlew :mcp-servers:forge-database-mcp:shadowJar

# Web IDE 前端
cd web-ide/frontend && npm ci && npm run build && cd ../..

# 企业控制台（首次需 cp .env.example .env）
cd enterprise-console && cp -n .env.example .env && npm ci && npm run build && cd ..
```

**验证产物：**

```bash
ls -lh web-ide/backend/build/libs/backend-*.jar
ls -lh mcp-servers/forge-knowledge-mcp/build/libs/*-all.jar
ls -lh mcp-servers/forge-database-mcp/build/libs/*-all.jar
ls -d web-ide/frontend/.next/standalone
ls -d enterprise-console/.next/standalone
```

---

## 四、部署 SSO（两种环境共用）

SSO 独立部署在 `infrastructure/sso/`，Trial 和 Production 使用同一套 SSO compose，仅环境变量不同。

### 4.1 Trial（本地开发，SSO 与应用同机）

```bash
# 1. 配置 /etc/hosts
echo "127.0.0.1 sso.forge.local" | sudo tee -a /etc/hosts
echo "127.0.0.1 forge.local" | sudo tee -a /etc/hosts

# 2. 启动 SSO（使用默认配置）
cd /opt/forge/infrastructure/sso
docker compose up -d

# 3. 验证
curl -sf http://sso.forge.local:8180/auth/realms/forge | python3 -c "import sys,json; print(json.load(sys.stdin)['realm'])"
# 预期: forge
```

### 4.2 Production（独立 SSO 机器）

前置：DNS 已将 `sso.forge.delivery` 解析到本机。在 SSO 机器上操作：

```bash
cd /opt/forge/infrastructure/sso

# 1. 配置环境变量
cp .env.example .env.production

# 编辑 .env.production:
f=.env.production
echo 'SSO_URL=https://sso.forge.delivery/auth' > $f
echo 'KC_COMMAND=start' >> $f                      # 生产模式
echo 'KC_PORT=8180' >> $f
echo 'KC_ADMIN=admin' >> $f
echo "KC_ADMIN_PASSWORD=$(openssl rand -base64 16)" >> $f
echo "KC_DB_PASSWORD=$(openssl rand -base64 16)" >> $f
echo 'KC_PROXY=edge' >> $f                         # 反向代理模式
echo 'COMPOSE_PROFILES=production' >> $f           # 启用 nginx 容器
echo 'SSL_CERT_PATH=/opt/sso-ssl' >> $f            # SSL 证书目录

# 2. 放置 SSL 证书
mkdir -p /opt/sso-ssl
# 将 sso.forge.delivery_bundle.crt 和 sso.forge.delivery.key 复制到 /opt/sso-ssl/

# 3. 启动（含 nginx）
docker compose --env-file .env.production up -d

# 4. 验证
curl -sf https://sso.forge.delivery/auth/realms/forge | python3 -c "import sys,json; print(json.load(sys.stdin)['realm'])"
# 预期: forge
```

> nginx 配置文件为 `infrastructure/sso/nginx-sso.conf`，通过 compose volume 挂载，无需手动安装 nginx。

---

## 五、Trial 环境部署

适合：开发调试、功能演示。SSO 与应用在同一台机器。

### 5.1 前置条件

- SSO 已启动（见第四节）
- `/etc/hosts` 已添加 `sso.forge.local` 和 `forge.local`

### 5.2 配置环境变量

```bash
cd /opt/forge/infrastructure/docker
cp .env.trial.example .env
```

编辑 `.env`：

```bash
f=/opt/forge/infrastructure/docker/.env
echo 'MODEL_PROVIDER=minimax' > $f
echo 'MINIMAX_API_KEY=<your-key>' >> $f
echo 'MINIMAX_API_URL=https://api.minimaxi.com/anthropic' >> $f
echo "FORGE_ENCRYPTION_KEY=$(openssl rand -base64 32)" >> $f
```

### 5.3 启动

```bash
cd /opt/forge/infrastructure/docker
docker compose -f docker-compose.trial.yml up --build -d
docker compose -f docker-compose.trial.yml logs -f
```

### 5.4 访问地址

| 服务 | 地址 |
|------|------|
| Web IDE | http://forge.local:19000 |
| 企业控制台 | http://forge.local:19000/console |
| Keycloak Admin | http://sso.forge.local:8180/auth/admin |
| 后端直连 | http://localhost:19000/api/ |

---

## 六、Production 环境部署

适合：生产环境。SSO 在独立机器（sso.forge.delivery），应用在本机。

### 6.1 前置条件

- SSO 已在远端机器部署并可通过 `https://sso.forge.delivery/auth` 访问
- 宿主机已创建数据目录：`mkdir -p /data/forge/{postgres,backend}`
- SSL 证书已放置在 `/opt/forge-ssl/forge.delivery_nginx/`

### 6.2 生成密钥

```bash
echo "DB_PASSWORD=$(openssl rand -base64 16)"
echo "FORGE_ENCRYPTION_KEY=$(openssl rand -base64 32)"
echo "NEXTAUTH_SECRET=$(openssl rand -base64 32)"
```

记录以上输出，下一步用到。

### 6.3 配置环境变量

```bash
cd /opt/forge/infrastructure/docker
cp .env.production.example .env.production
```

编辑 `.env.production`：

```bash
f=/opt/forge/infrastructure/docker/.env.production
echo 'SSO_URL=https://sso.forge.delivery/auth' > $f
echo 'FORGE_DOMAIN=forge.delivery' >> $f
echo 'SSL_CERT_PATH=/opt/forge-ssl/forge.delivery_nginx' >> $f
echo 'MODEL_PROVIDER=minimax' >> $f
echo 'MINIMAX_API_KEY=<your-minimax-key>' >> $f
echo 'MINIMAX_API_URL=https://api.minimaxi.com/anthropic' >> $f
echo 'DB_PASSWORD=<生成的密码>' >> $f
echo 'FORGE_ENCRYPTION_KEY=<生成的密钥>' >> $f
echo 'FORGE_FRONTEND_URL=https://forge.delivery' >> $f
echo 'FORGE_CORS_ALLOWED_ORIGINS=https://forge.delivery' >> $f
echo 'KEYCLOAK_ENTERPRISE_SECRET=enterprise-secret' >> $f
echo 'NEXTAUTH_URL=https://forge.delivery' >> $f
echo 'NEXTAUTH_SECRET=<生成的密钥>' >> $f

# 验证
cat $f
```

**必填项说明：**

| 变量 | 说明 | 示例 |
|------|------|------|
| `SSO_URL` | SSO 服务器公开 URL | `https://sso.forge.delivery/auth` |
| `MODEL_PROVIDER` | 模型提供商 | `minimax` |
| `MINIMAX_API_KEY` | API Key | `sk-cp-...` |
| `DB_PASSWORD` | PostgreSQL 密码 | `openssl rand -base64 16` |
| `FORGE_ENCRYPTION_KEY` | 数据加密密钥 | `openssl rand -base64 32` |
| `FORGE_FRONTEND_URL` | 应用入口 URL | `https://forge.delivery` |
| `FORGE_CORS_ALLOWED_ORIGINS` | CORS 白名单 | `https://forge.delivery` |
| `NEXTAUTH_URL` | NextAuth 回调地址 | `https://forge.delivery` |
| `NEXTAUTH_SECRET` | NextAuth 会话密钥 | `openssl rand -base64 32` |

### 6.4 首次部署

> 建议在 tmux 中执行，防止 SSH 断开中断：

```bash
tmux new -s forge

cd /opt/forge/infrastructure/docker
docker compose -f docker-compose.production.yml --env-file .env.production up --build -d

# 查看进度
docker compose -f docker-compose.production.yml --env-file .env.production logs -f
```

SSH 断开后恢复：`tmux attach -t forge`

### 6.5 访问地址

| 服务 | 地址 |
|------|------|
| Web IDE | https://forge.delivery |
| 企业控制台 | https://forge.delivery/console |
| Keycloak Admin | https://sso.forge.delivery/auth/admin |
| PostgreSQL | 127.0.0.1:5432（仅本机） |

---

## 七、验证部署

### 7.1 容器状态

```bash
# Trial
docker compose -f docker-compose.trial.yml ps

# Production
docker compose -f docker-compose.production.yml --env-file .env.production ps
```

预期：7 个应用容器全部 `Up`，其中 4 个带 `(healthy)`：

```
NAME                        STATUS
forge-postgres              Up (healthy)
forge-knowledge-mcp         Up (healthy)
forge-database-mcp          Up (healthy)
forge-backend               Up (healthy)
forge-frontend              Up
forge-enterprise-console    Up
forge-nginx                 Up
```

### 7.2 逐服务验证

```bash
# 根据环境选择 URL
URL="http://forge.local:19000"        # Trial
# URL="https://forge.delivery"        # Production

# SSO 配置发现
curl -sf ${URL}/api/auth/sso-config
# 预期: {"ssoUrl":"...","realm":"forge","clientId":"forge-web-ide"}

# Web IDE 首页
curl -s -o /dev/null -w "%{http_code}" ${URL}/
# 预期: 200

# 企业控制台
curl -s -o /dev/null -w "%{http_code}" ${URL}/console/zh
# 预期: 200

# 后端健康检查
curl -sf ${URL}/actuator/health
# 预期: {"status":"UP"}
```

### 7.3 SSO 登录验证

1. 浏览器访问 Web IDE，点击登录
2. 浏览器跳转到 SSO 域名（`sso.forge.local:8180` 或 `sso.forge.delivery`）
3. 使用内置账号登录
4. 登录成功后自动回调到应用

**内置账号：**

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin` | admin + developer |
| `dev1` | `dev1` | developer |
| `viewer1` | `viewer1` | viewer |

> **生产环境**：请在 Keycloak Admin 控制台修改默认密码并删除测试账号。

### 7.4 资源使用

```bash
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

---

## 八、更新部署

```bash
cd /opt/forge

# 1. 拉取最新代码
git pull && git submodule update --recursive

# 2. 重新构建（只构建有变更的模块）
./gradlew :web-ide:backend:clean :web-ide:backend:bootJar -x test
# cd web-ide/frontend && npm ci && npm run build && cd ../..
# cd enterprise-console && npm ci && npm run build && cd ..

# 3. 重建并启动容器
# Trial:
docker compose -f infrastructure/docker/docker-compose.trial.yml up --build -d

# Production:
docker compose -f infrastructure/docker/docker-compose.production.yml \
  --env-file infrastructure/docker/.env.production up --build -d

# 4. 清理旧镜像
docker image prune -f
```

---

## 九、日常运维

### 9.1 查看日志

```bash
# 单个容器
docker logs forge-backend --tail 100 -f

# 全部容器
# Trial:
docker compose -f docker-compose.trial.yml logs -f --tail 30
# Production:
docker compose -f docker-compose.production.yml --env-file .env.production logs -f --tail 30
```

### 9.2 重启服务

```bash
# 重启单个容器（以 Production 为例）
docker compose -f docker-compose.production.yml --env-file .env.production restart backend

# 全部重启
docker compose -f docker-compose.production.yml --env-file .env.production restart
```

### 9.3 数据库备份

```bash
mkdir -p /data/forge/backups

# 手动备份 — Forge 应用数据库
docker exec forge-postgres pg_dump -U forge forge | \
  gzip > /data/forge/backups/forge_$(date +%Y%m%d_%H%M%S).sql.gz

# 每日自动备份（crontab -e）
# 0 3 * * * docker exec forge-postgres pg_dump -U forge forge | gzip > /data/forge/backups/forge_$(date +\%Y\%m\%d).sql.gz
# 0 4 * * * find /data/forge/backups -name "*.sql.gz" -mtime +7 -delete
```

> SSO 数据库备份应在 SSO 机器上独立执行：
> ```bash
> docker exec sso-postgres pg_dump -U keycloak keycloak | gzip > /data/sso/backups/keycloak_$(date +%Y%m%d).sql.gz
> ```

### 9.4 Keycloak 用户管理

在 SSO 机器上操作：

```bash
# 添加用户
docker exec sso-keycloak /opt/keycloak/bin/kcadm.sh \
  create users -r forge \
  -s username=newuser -s enabled=true \
  --server http://localhost:8180/auth --realm master \
  --user admin --password <admin-password>

# 设置密码
docker exec sso-keycloak /opt/keycloak/bin/kcadm.sh \
  set-password -r forge --username newuser --new-password newpass \
  --server http://localhost:8180/auth --realm master \
  --user admin --password <admin-password>

# 分配角色（developer / viewer / admin）
docker exec sso-keycloak /opt/keycloak/bin/kcadm.sh \
  add-roles -r forge --uusername newuser --rolename developer \
  --server http://localhost:8180/auth --realm master \
  --user admin --password <admin-password>
```

或直接访问 Keycloak Admin 控制台：
- Trial: http://sso.forge.local:8180/auth/admin
- Production: https://sso.forge.delivery/auth/admin

---

## 十、故障排查

### 健康检查失败排查

```bash
docker inspect <container-name> --format '{{json .State.Health.Log}}' | python3 -m json.tool | tail -20
```

### 常见问题速查

| 现象 | 检查命令 | 常见原因 |
|------|---------|---------|
| SSO 不可达 | `curl -sf ${SSO_URL}/realms/forge` | SSO 未启动，或 DNS/hosts 未配置 |
| 前端登录跳转失败 | 浏览器 F12 → Network | `sso-config` 返回的 `ssoUrl` 不可达 |
| Token exchange 失败 | 浏览器 F12 → Console | Keycloak `webOrigins` 未包含应用域名 |
| `crypto.subtle` 错误 | 浏览器 Console | HTTP 环境需确认 `js-sha256` 降级生效 |
| Backend 401 | `docker logs forge-backend \| grep jwt` | `SSO_URL` 与 token issuer 不匹配 |
| Console 登录循环 | `docker logs forge-enterprise-console` | `NEXTAUTH_SECRET` 未设或 `KEYCLOAK_ISSUER` 错误 |
| Console 502 Bad Gateway | `docker logs forge-nginx \| grep upstream` | Auth.js header 过大，检查 nginx `proxy_buffer_size` |
| `/console` 404 | — | nginx 缺少 `location = /console` 精确匹配 |
| `/console/api/auth/signin` 404 | `docker exec console wget -qO- http://localhost:9001/console/api/auth/providers` | **构建时未设置 NEXT_PUBLIC_BASE_PATH**（见"高频陷阱 1"）|
| 容器健康检查 `500 Privoxy Error` | `docker exec <container> wget -qO- http://localhost:<port>/health/live` | **~/.docker/config.json 全局代理泄漏到容器**（见"高频陷阱 2"）|
| `sso-net` 网络 label 错误 | `docker network inspect sso-net` | **手动创建了 sso-net，需由 SSO compose 创建**（见"高频陷阱 3"）|
| 端口重定向错误 | 检查 nginx `Host` header | Trial: 需用 `$http_host`（保留端口） |
| 内存 OOM | `docker stats --no-stream` | 检查容器 mem_limit 配置 |
| 磁盘满 | `df -h /data/forge` | 清理备份和旧镜像：`docker image prune -f` |

### 高频部署陷阱（从实际部署经验总结）

#### 陷阱 1：enterprise-console 构建时缺少 .env → `/console` 路由全部 404

**现象**：访问 `/console` 跳转到 `/console/api/auth/signin` 后返回 Next.js 404。

**根因**：`NEXT_PUBLIC_BASE_PATH` 是 Next.js **构建时变量**，`npm run build` 时烘焙进产物。`.env.example` 不会被 Next.js 自动读取，必须复制为 `.env`。运行时通过 Docker 环境变量设置**无效**。

**修复**：构建前 `cp -n .env.example .env`，验证：`grep basePath .next/required-server-files.json` 应含 `"/console"`。

#### 陷阱 2：~/.docker/config.json 代理泄漏到所有容器

**现象**：容器健康检查返回 `500 Internal Privoxy Error`，所有服务 unhealthy。

**根因**：`~/.docker/config.json` 中的 `proxies.default` 配置会注入到**所有容器**的环境变量，容器内 `wget http://localhost:8081/health` 被路由到代理服务器。

**修复**：拉取完镜像后**立即删除** config.json 中的 proxies 字段，然后重建容器。拉取被墙镜像应使用 skopeo + 代理（宿主机进程，不污染容器）而非 Docker 全局代理。

#### 陷阱 3：sso-net 必须由 SSO Compose 创建，不能手动创建

**现象**：`docker compose up` 报错 `network sso-net has incorrect label com.docker.compose.network`。

**根因**：`docker network create sso-net` 创建的网络缺少 Compose 管理标签。SSO compose 定义了 `sso-net`（非 external），Forge/Synapse 以 `external: true` 引用。

**正确顺序**：SSO compose up → Forge compose up → Synapse compose up。若已手动创建，需先 `docker network rm sso-net` 再让 SSO compose 重建。

---

## 附录 A：macOS 本地开发部署速查

适用于 Apple Silicon Mac（M1/M2/M3/M4），从零开始的完整流程。

### A.1 安装构建工具

```bash
# OpenJDK 21（Homebrew formula，不需要 sudo）
brew install openjdk@21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Node.js
brew install node

# 验证
java -version   # 21+
node -v          # 20+
```

> **注意**：`brew install --cask temurin@21` 需要 sudo（.pkg 安装器），建议用 `brew install openjdk@21` 替代。使用时需手动设置 `JAVA_HOME`。

### A.2 一键部署脚本

```bash
cd /path/to/forge
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# 1. 配置 hosts（仅首次）
echo "127.0.0.1 sso.forge.local forge.local" | sudo tee -a /etc/hosts

# 2. 预拉取所有 Docker 镜像（避免构建时网络问题）
for img in node:20-alpine eclipse-temurin:21-jre-alpine postgres:16-alpine nginx:alpine; do
  docker pull $img
done
# Keycloak 需要特殊处理（见陷阱 3）

# 3. 构建所有产物
./gradlew :web-ide:backend:bootJar -x test
./gradlew :mcp-servers:forge-knowledge-mcp:shadowJar :mcp-servers:forge-database-mcp:shadowJar -x test
cd web-ide/frontend && npm ci && npm run build && cd ../..
cd enterprise-console && cp -n .env.example .env && npm ci && npm run build && cd ..

# 4. 配置环境变量
cd infrastructure/sso
cp .env.example .env
sed -i '' 's|PG_DATA_PATH=.*|PG_DATA_PATH=/Users/'$USER'/data/sso-postgres|' .env
sed -i '' 's|COMPOSE_PROFILES=.*|COMPOSE_PROFILES=|' .env
mkdir -p /Users/$USER/data/sso-postgres

cd ../docker
cp .env.trial.example .env
# 编辑 .env 填入 API Key

# 5. 按顺序启动
cd ../sso && docker compose up -d
cd ../docker && docker compose -f docker-compose.trial.yml up --build -d
```

---

## 十一、文件清单

```
infrastructure/
├── docker/                              # 应用部署（应用机器）
│   ├── docker-compose.trial.yml         # Trial 环境（7 容器 + sso-net 网络）
│   ├── docker-compose.production.yml    # Production 环境（7 容器，纯 DNS 连 SSO）
│   ├── nginx-trial.conf                 # Trial nginx（端口 9000，HTTP）
│   ├── nginx-production.conf            # Production nginx（80/443，HTTPS + SSL）
│   ├── .env.trial.example               # Trial 环境变量模板
│   ├── .env.production.example          # Production 环境变量模板
│   └── .env / .env.production           # 实际环境变量（不入库）
├── sso/                                 # SSO 独立部署（SSO 机器或同机）
│   ├── docker-compose.yml               # Keycloak 24 + PostgreSQL 16 + Nginx（生产）
│   ├── nginx-sso.conf                   # SSO nginx 配置（SSL 终止 + 反向代理）
│   ├── realm-export.json                # Realm 配置（3 客户端 + 角色 + 测试账号）
│   └── .env.example                     # SSO 环境变量模板
├── deployment-architecture.md           # 部署架构文档
└── deployment-manual.md                 # 本文件
```
