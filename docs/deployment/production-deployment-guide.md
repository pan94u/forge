# Forge Platform — 生产部署指南

> 快速迭代阶段的单机全包方案。一台机器搞定一切。

---

## 你需要买什么

一台海外 VPS：2核4GB，Ubuntu 22.04，50GB SSD，固定公网 IP。

没了。域名你自己准备一个，SSL 免费，GitHub Actions 免费，PG 跑在同机 Docker 里。

---

## 1. 服务器初始化

SSH 登录后以 root 执行：

```bash
# 系统更新
apt update && apt upgrade -y
apt install -y curl wget git vim ufw rsync

# 时区（按你实际改）
timedatectl set-timezone Asia/Tokyo

# 安装 Docker
curl -fsSL https://get.docker.com | sh
systemctl enable docker && systemctl start docker

# 创建部署用户
useradd -m -s /bin/bash forge
usermod -aG docker forge
echo "forge ALL=(ALL) NOPASSWD: /usr/bin/docker" > /etc/sudoers.d/forge
chmod 440 /etc/sudoers.d/forge

# SSH Key（GitHub Actions 部署用）
mkdir -p /home/forge/.ssh
chmod 700 /home/forge/.ssh
touch /home/forge/.ssh/authorized_keys
chmod 600 /home/forge/.ssh/authorized_keys
chown -R forge:forge /home/forge/.ssh
# 在你本地：
#   ssh-keygen -t ed25519 -f forge-deploy-key -C "forge-deploy"
#   把 forge-deploy-key.pub 内容粘贴到上面的 authorized_keys

# 防火墙
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable

# 目录
mkdir -p /opt/forge/{docker,plugins,knowledge-base,backups}
chown -R forge:forge /opt/forge

# 安全加固
sed -i 's/PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart sshd
apt install -y fail2ban && systemctl enable fail2ban
apt install -y unattended-upgrades
```

---

## 2. SSL 证书

```bash
apt install -y certbot
certbot certonly --standalone -d YOUR_DOMAIN

# 自动续期 hook
cat > /etc/letsencrypt/renewal-hooks/post/restart-nginx.sh << 'EOF'
#!/bin/bash
docker restart forge-nginx 2>/dev/null || true
EOF
chmod +x /etc/letsencrypt/renewal-hooks/post/restart-nginx.sh
```

---

## 3. 环境变量

```bash
su - forge
cat > /opt/forge/docker/.env.production << 'EOF'
FORGE_EXTERNAL_URL=https://YOUR_DOMAIN
DB_PASSWORD=替换为强密码
ANTHROPIC_API_KEY=sk-ant-api03-xxxx
FORGE_ENCRYPTION_KEY=替换（openssl rand -base64 32 生成）
FORGE_SECURITY_ENABLED=false
EOF
chmod 600 /opt/forge/docker/.env.production
```

---

## 4. Nginx 域名替换

```bash
# 首次手动替换，后续由 CI/CD 同步
sed -i 's/YOUR_DOMAIN/你的实际域名/g' /opt/forge/docker/nginx-production.conf
```

---

## 5. GitHub 配置

### Secrets（仓库 Settings → Secrets）

| Secret | 值 |
|--------|-----|
| `DEPLOY_HOST` | 服务器公网 IP |
| `DEPLOY_USER` | `forge` |
| `DEPLOY_SSH_KEY` | 私钥全文（`cat forge-deploy-key`） |
| `FORGE_EXTERNAL_URL` | `https://YOUR_DOMAIN` |

### Environment

Settings → Environments → 创建 `production`。

### GHCR 登录（服务器上执行一次）

```bash
echo "YOUR_GITHUB_PAT" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

---

## 6. 部署

### 首次：GitHub Actions 手动触发

Actions → `CD - Deploy Production` → Run workflow → deploy_target: `all`

### 或手动：

```bash
cd /opt/forge/docker
docker compose -f docker-compose.production.yml --env-file .env.production up -d
docker compose -f docker-compose.production.yml ps
```

### 验证

```bash
curl -k https://YOUR_DOMAIN/api/knowledge/search
```

---

## 7. 日常运维

```bash
cd /opt/forge/docker
DC="docker compose -f docker-compose.production.yml"

$DC logs -f backend          # 看日志
$DC restart backend           # 重启单个服务
$DC ps                        # 查看状态
docker stats --no-stream      # 资源占用

# 数据库备份（加入 crontab -e）
# 0 3 * * * docker exec forge-postgres pg_dump -U forge forge | gzip > /opt/forge/backups/forge-$(date +\%Y\%m\%d).sql.gz && find /opt/forge/backups -mtime +7 -delete

# 磁盘清理
docker system prune -f --filter "until=168h"
```

---

## 架构图

> 可视化版本：[architecture.html](architecture.html)（浏览器打开）

```
用户浏览器
    │ HTTPS (:443)
    ▼
┌──────────────────────────────────────────────────────────────┐
│              海外 VPS (2C4G · Ubuntu 22.04 · 50GB SSD)       │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Nginx (:80/:443)                                      │  │
│  │  TLS 终结 · 反向代理 · WebSocket · SSE                  │  │
│  │  Let's Encrypt 自动续期                                 │  │
│  └──────┬─────────────────────────────┬───────────────────┘  │
│         │                             │                      │
│    /api/ & /ws/                  / (兜底)                    │
│         │                             │                      │
│         ▼                             ▼                      │
│  ┌─────────────┐              ┌─────────────┐               │
│  │  Backend    │              │  Frontend   │               │
│  │  :8080      │              │  :3000      │               │
│  │  Spring Boot│              │  Next.js 15 │               │
│  │  512MB      │              │  200MB      │               │
│  └──────┬──────┘              └─────────────┘               │
│         │                                                    │
│         ├──▶ Knowledge MCP :8081 (知识库检索, 256MB)         │
│         ├──▶ Database MCP  :8082 (Schema 查询, 256MB)       │
│         ├──▶ PostgreSQL 16 :5432 (Docker Volume 持久化)      │
│         └──▶ AI Provider API (外部 HTTPS 出站)               │
│                                                              │
│  除 Nginx 外所有端口仅 Docker 内网，不暴露公网                 │
└──────────────────────────────────────────────────────────────┘
         │
         │ HTTPS 出站
         ▼
┌──────────────────────────────────────────────────────────────┐
│  AI Provider: Claude · DashScope · Gemini · Ollama/vLLM     │
└──────────────────────────────────────────────────────────────┘
```

### CI/CD 流水线

```
git push main
    → 检测变更 (paths-filter: backend / frontend / mcp)
    → 构建 & 测试 (并行, JDK 21 + Node 20)
    → Docker 镜像 → ghcr.io
    → SSH 部署 (rsync 配置 + docker compose pull + up -d)
    → 健康检查 (HTTP 200/401)
    → 失败自动回滚到 :latest
```

### 内存分配 (4GB)

```
Backend (Spring Boot)     512MB  ████████████░░░░░░░░░░░░░░░░░░░░
Frontend (Next.js)        200MB  █████░░░░░░░░░░░░░░░░░░░░░░░░░░░
Knowledge MCP             256MB  ██████░░░░░░░░░░░░░░░░░░░░░░░░░░
Database MCP              256MB  ██████░░░░░░░░░░░░░░░░░░░░░░░░░░
PostgreSQL 16             200MB  █████░░░░░░░░░░░░░░░░░░░░░░░░░░░
Nginx                      30MB  █░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
系统 + Docker              500MB  ████████████░░░░░░░░░░░░░░░░░░░░
───────────────────────────────
合计                     ~2.0GB  剩余 ~2.0GB 空闲
```

---

## 采购清单

| 项目 | 费用 | 说明 |
|------|------|------|
| 海外 VPS 2C4G | $10~30/月 | 唯一需要购买的东西 |
| 域名 | ¥50~100/年 | A 记录解析到 VPS IP |
| SSL 证书 | 免费 | Let's Encrypt 自动续期 |
| GitHub Actions | 免费 | 公开仓库无限 / 私有 2000 分钟/月 |
| GHCR 镜像仓库 | 免费 | 500MB 免费存储 |
| AI API | 按量付费 | Claude / 通义千问 / Gemini |

---

## 故障排查

| 现象 | 排查 |
|------|------|
| 容器挂了 | `docker compose logs 服务名` |
| 502 | `docker ps` 看 backend 状态 |
| 数据库连不上 | `docker exec forge-postgres pg_isready` |
| SSL 过期 | `certbot renew --dry-run` |
| 磁盘满 | `df -h` + `docker system df` |
| 内存不足 | `free -h` + `docker stats --no-stream` |
| 部署异常 | GitHub Actions 日志 → 手动回滚（第 7 节） |

---

## 文件清单

| 文件 | 用途 |
|------|------|
| `infrastructure/docker/docker-compose.production.yml` | 服务编排（含 PG） |
| `infrastructure/docker/nginx-production.conf` | Nginx 反向代理 |
| `infrastructure/docker/.env.production.example` | 环境变量模板 |
| `.github/workflows/deploy-production.yml` | CI/CD 流水线 |
| `docs/deployment/architecture.html` | 可视化架构图 |
