# Forge 部署手册

> 两套 compose 文件，按场景选择：
>
> | 文件 | 用途 | 容器数 | SSO |
> |------|------|--------|-----|
> | `docker-compose.trial.yml` | 试用 / 开发环境 | **8 个**（含 Keycloak） | ✅ 开启 |
> | `docker-compose.production-single.yml` | 8C16G 单机生产 | **8 个**（含 Keycloak） | ✅ 开启 |

---

## 一、环境准备

### 1.1 安装 Docker + Compose

```bash
# Ubuntu 22.04+
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# 重新登录使 docker 组生效

# 验证
docker --version         # 需要 24+
docker compose version   # 需要 v2.20+
```

### 1.2 安装构建工具

```bash
# JDK 21（必须！JDK 8/17 会编译失败）
sudo apt install -y openjdk-21-jdk
java -version   # 确认 21+

# Node.js 20+
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
node -v && npm -v
```

### 1.3 创建数据目录（生产模式必须）

```bash
sudo mkdir -p /data/forge/{postgres,backend}
sudo chown -R $USER:$USER /data/forge
```

---

## 二、获取代码

```bash
cd /opt
git clone git@github.com:pan94u/forge.git
cd forge
git submodule update --init --recursive
```

---

## 三、构建产物

> 原则：本地构建 + Docker 只打包。不在容器内编译（避免网络问题）。

```bash
cd /opt/forge

# 后端 JAR
./gradlew :web-ide:backend:clean :web-ide:backend:bootJar -x test

# MCP Server fat JAR
./gradlew :mcp-servers:forge-knowledge-mcp:shadowJar
./gradlew :mcp-servers:forge-database-mcp:shadowJar

# Web IDE 前端
cd web-ide/frontend && npm ci && npm run build && cd ../..

# 企业控制台
cd enterprise-console && npm ci && npm run build && cd ..
```

验证产物：

```bash
ls -lh web-ide/backend/build/libs/backend-*.jar
ls -lh mcp-servers/forge-knowledge-mcp/build/libs/*-all.jar
ls -lh mcp-servers/forge-database-mcp/build/libs/*-all.jar
ls -d web-ide/frontend/.next/standalone
ls -d enterprise-console/.next/standalone
```

---

## 四、试用环境部署（docker-compose.trial.yml）

适合：开发调试、功能演示。数据存 Docker volume（非 bind mount）。

### 4.1 配置环境变量

```bash
cd infrastructure/docker
cp .env.trial.example .env
vim .env
```

**必填：**

| 变量 | 说明 |
|------|------|
| `MODEL_PROVIDER` | `minimax` / `anthropic` / `gemini` |
| `MINIMAX_API_KEY` | 模型 API Key |
| `FORGE_ENCRYPTION_KEY` | `openssl rand -base64 32` |

### 4.2 启动

```bash
cd /opt/forge/infrastructure/docker

docker compose -f docker-compose.trial.yml up --build -d

# 查看进度（Keycloak 首次启动约 60s）
docker compose -f docker-compose.trial.yml logs -f
```

### 4.3 访问地址（试用）

| 服务 | 地址 |
|------|------|
| Web IDE | http://localhost:19000 |
| 企业控制台 | http://localhost:19001 |
| Keycloak Admin | http://localhost:8180 |

---

## 五、生产环境部署（docker-compose.production-single.yml）

适合：8C16G 单机生产。数据 bind mount 到 `/data/forge/`，资源有明确 CPU/内存限制。

### 5.1 配置环境变量

```bash
cd /opt/forge/infrastructure/docker
cp .env.production.example .env.production
vim .env.production
```

**必填项（不填部署必定失败）：**

| 变量 | 说明 | 生成方式 |
|------|------|---------|
| `MODEL_PROVIDER` | 模型提供商 | `minimax` / `anthropic` |
| `MINIMAX_API_KEY` | 模型 API Key | 从平台获取 |
| `DB_PASSWORD` | PostgreSQL 密码 | `openssl rand -base64 16` |
| `FORGE_ENCRYPTION_KEY` | 数据加密密钥 | `openssl rand -base64 32` |
| `FORGE_FRONTEND_URL` | Web IDE 公网地址 | `http://<server-ip>:9000` |
| `FORGE_CORS_ALLOWED_ORIGINS` | CORS 白名单 | `http://<server-ip>:9000,http://<server-ip>:9001` |
| `KEYCLOAK_PUBLIC_URL` | Keycloak 浏览器地址 | `http://<server-ip>:8180` |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak 管理员密码 | 自定义强密码 |
| `CONSOLE_PUBLIC_URL` | 企业控制台公网地址 | `http://<server-ip>:9001` |
| `NEXTAUTH_SECRET` | NextAuth 会话密钥 | `openssl rand -base64 32` |

### 5.2 首次部署（全新机器）

```bash
cd /opt/forge/infrastructure/docker

# 首次启动（构建镜像 + 初始化 PG 两个数据库 + 导入 Keycloak realm）
# 约 5-8 分钟
docker compose -f docker-compose.production-single.yml \
  --env-file .env.production \
  up --build -d

# 查看启动进度
docker compose -f docker-compose.production-single.yml \
  --env-file .env.production logs -f
```

> **注意**：PostgreSQL 首次启动时会执行 `init-keycloak-db.sql`，自动创建 `keycloak` 数据库。Keycloak 启动需 60-90s，backend 会等待其 healthy 后才启动，总体等待约 3-5 分钟。

### 5.3 数据已存在时升级部署

如果已有旧版 PostgreSQL 数据，`keycloak` 数据库不会自动创建（init 脚本只在 PGDATA 首次初始化时执行）。需要手动创建：

```bash
# 确认 keycloak 数据库是否存在
docker exec forge-postgres psql -U forge -l | grep keycloak

# 不存在则手动创建（一次性操作）
docker exec forge-postgres psql -U forge -c "CREATE DATABASE keycloak;"
docker exec forge-postgres psql -U forge -c "GRANT ALL PRIVILEGES ON DATABASE keycloak TO forge;"

# 再启动 Keycloak
docker compose -f docker-compose.production-single.yml \
  --env-file .env.production \
  up -d keycloak
```

### 5.4 访问地址（生产）

| 服务 | 地址 | 说明 |
|------|------|------|
| Web IDE | `http://<server-ip>:9000` | 需要 Keycloak 登录 |
| 企业控制台 | `http://<server-ip>:9001` | 自动跳转 Keycloak |
| Keycloak Admin | `http://<server-ip>:8180` | 管理账号和权限 |
| PostgreSQL | `127.0.0.1:5432` | 仅本机可达 |

---

## 六、验证部署

### 6.1 容器状态

```bash
# 试用
docker compose -f docker-compose.trial.yml ps

# 生产
docker compose -f docker-compose.production-single.yml --env-file .env.production ps
```

预期：8 个容器全部 `Up`，其中 5 个带 `(healthy)`

```
NAME                        STATUS
forge-keycloak              Up (healthy)
forge-postgres              Up (healthy)
forge-knowledge-mcp         Up (healthy)
forge-database-mcp          Up (healthy)
forge-backend               Up (healthy)
forge-frontend              Up
forge-enterprise-console    Up
forge-nginx                 Up
```

### 6.2 逐服务验证（以生产为例，端口 9000）

```bash
SERVER=<server-ip>   # 试用环境替换为 localhost，端口改 19000/19001/8180

# 1. Keycloak 就绪
curl -sf http://${SERVER}:8180/health/ready && echo " OK"

# 2. Keycloak realm 已导入
curl -sf http://${SERVER}:8180/realms/forge | python3 -c "import sys,json; print(json.load(sys.stdin)['realm'])"
# 预期: forge

# 3. Backend 健康
curl -sf http://${SERVER}:9000/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])"
# 预期: UP

# 4. Web IDE 首页（返回 200 或 302 跳转登录）
curl -s -o /dev/null -w "%{http_code}" http://${SERVER}:9000/

# 5. 企业控制台（未登录应 302 跳转 Keycloak）
curl -s -o /dev/null -w "%{http_code}" http://${SERVER}:9001/
# 预期: 302

# 6. WebSocket
curl -s -o /dev/null -w "%{http_code}" \
  -H "Upgrade: websocket" -H "Connection: Upgrade" \
  http://${SERVER}:9000/ws/
# 预期: 101
```

### 6.3 SSO 登录验证

1. 浏览器访问企业控制台
2. 自动跳转 Keycloak 登录页
3. 使用内置账号登录

**内置账号：**

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin` | admin + developer |
| `dev1` | `dev1` | developer |
| `viewer1` | `viewer1` | viewer |

> 生产环境请在 Keycloak Admin 控制台修改默认密码，并禁用或删除测试账号。

### 6.4 资源使用（生产）

```bash
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"
```

---

## 七、日常运维

### 7.1 查看日志

```bash
# 单容器
docker logs forge-backend --tail 100 -f
docker logs forge-keycloak --tail 50 -f

# 全部（生产）
docker compose -f docker-compose.production-single.yml --env-file .env.production logs -f --tail 30
```

### 7.2 重启服务

```bash
# 重启单个（生产）
docker compose -f docker-compose.production-single.yml --env-file .env.production restart backend

# 全部重启
docker compose -f docker-compose.production-single.yml --env-file .env.production restart
```

### 7.3 更新部署

```bash
cd /opt/forge

# 拉取最新代码
git pull && git submodule update --recursive

# 重新构建改动的模块（按实际选择）
./gradlew :web-ide:backend:clean :web-ide:backend:bootJar -x test
# cd web-ide/frontend && npm ci && npm run build && cd ../..
# cd enterprise-console && npm ci && npm run build && cd ..

# 重建容器（生产）
cd infrastructure/docker
docker compose -f docker-compose.production-single.yml \
  --env-file .env.production \
  up --build -d

# 清理旧镜像
docker image prune -f
```

### 7.4 数据库备份

```bash
# 手动备份 forge 数据库
docker exec forge-postgres pg_dump -U forge forge | \
  gzip > /data/forge/backups/forge_$(date +%Y%m%d_%H%M%S).sql.gz

# 手动备份 keycloak 数据库
docker exec forge-postgres pg_dump -U forge keycloak | \
  gzip > /data/forge/backups/keycloak_$(date +%Y%m%d_%H%M%S).sql.gz

# 配置每日自动备份（crontab）
crontab -e
# 添加：
# 0 3 * * * docker exec forge-postgres pg_dump -U forge forge | gzip > /data/forge/backups/forge_$(date +\%Y\%m\%d).sql.gz
# 0 3 * * * docker exec forge-postgres pg_dump -U forge keycloak | gzip > /data/forge/backups/keycloak_$(date +\%Y\%m\%d).sql.gz
# 0 4 * * * find /data/forge/backups -name "*.sql.gz" -mtime +7 -delete
```

### 7.5 数据库恢复

```bash
# 停止依赖服务
docker compose -f docker-compose.production-single.yml --env-file .env.production stop backend keycloak

# 恢复 forge 数据库
gunzip -c /data/forge/backups/forge_20260228.sql.gz | \
  docker exec -i forge-postgres psql -U forge forge

# 恢复 keycloak 数据库（如需）
gunzip -c /data/forge/backups/keycloak_20260228.sql.gz | \
  docker exec -i forge-postgres psql -U forge keycloak

# 重启
docker compose -f docker-compose.production-single.yml --env-file .env.production start keycloak backend
```

### 7.6 Keycloak 管理

```bash
# 浏览器访问 Admin 控制台（推荐）
# http://<server-ip>:8180  →  admin / ${KEYCLOAK_ADMIN_PASSWORD}

# 命令行添加用户
docker exec forge-keycloak /opt/keycloak/bin/kcadm.sh \
  create users -r forge \
  -s username=newuser -s enabled=true \
  --server http://localhost:8080 --realm master \
  --user admin --password ${KEYCLOAK_ADMIN_PASSWORD}

# 设置密码
docker exec forge-keycloak /opt/keycloak/bin/kcadm.sh \
  set-password -r forge --username newuser --new-password newpass \
  --server http://localhost:8080 --realm master \
  --user admin --password ${KEYCLOAK_ADMIN_PASSWORD}

# 分配角色（developer / viewer / admin）
docker exec forge-keycloak /opt/keycloak/bin/kcadm.sh \
  add-roles -r forge --uusername newuser --rolename developer \
  --server http://localhost:8080 --realm master \
  --user admin --password ${KEYCLOAK_ADMIN_PASSWORD}
```

---

## 八、故障排查

| 现象 | 检查命令 | 常见原因 |
|------|---------|---------|
| Keycloak 启动慢 | `docker logs forge-keycloak \| tail -30` | 首次启动 60-90s 属正常，等待 realm 导入 |
| Keycloak DB 连接失败 | `docker logs forge-keycloak \| grep "FATAL\|ERROR"` | `keycloak` 数据库未创建，见 5.3 节 |
| Backend 401 Unauthorized | `docker logs forge-backend \| grep -i "jwt\|401"` | `KEYCLOAK_PUBLIC_URL` 与 token iss 不符，或 Keycloak 未 healthy |
| EC 登录后 403 | `docker logs forge-backend \| grep -i "403\|role"` | 用户缺少角色，在 Keycloak Admin 中分配 |
| EC 登录循环重定向 | `docker logs forge-enterprise-console` | `NEXTAUTH_SECRET` 未设置或 `KEYCLOAK_ISSUER` 地址错误 |
| Backend 启动失败 | `docker logs forge-backend` | JDK 版本 / DB 连不上 / Flyway 迁移失败 |
| 内存 OOM | `docker stats --no-stream` | 检查 `mem_limit`，生产 backend 应有 5G |
| 磁盘满 | `df -h /data/forge` | 清理备份和旧镜像 |

---

## 九、文件清单

```
infrastructure/
├── docker/
│   ├── docker-compose.trial.yml              # 试用环境（含 Keycloak，volume 存储）
│   ├── docker-compose.production-single.yml  # 8C16G 生产（含 Keycloak，bind mount）
│   ├── nginx-trial.conf                      # 试用 nginx
│   ├── nginx-production.conf                 # 生产 nginx
│   ├── keycloak/
│   │   ├── realm-export.json                 # Keycloak realm（客户端/角色/账号）
│   │   └── init-keycloak-db.sql              # PG 首次初始化时创建 keycloak 数据库
│   ├── .env.trial.example                    # 试用环境变量模板
│   ├── .env.production.example               # 生产环境变量模板
│   └── .env / .env.production                # 实际环境变量（不入库）
├── deployment-architecture.md                # 部署架构文档
└── deployment-manual.md                      # 本文件
```
