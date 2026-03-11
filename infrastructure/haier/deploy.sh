#!/usr/bin/env bash
# =============================================================================
# Forge 海尔内部部署 — 一键构建 + 启动
# 架构与 trial 一致: SSO 独立 + App 通过 sso-net 连接
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SSO_DIR="$PROJECT_ROOT/infrastructure/sso"

echo "========================================="
echo " Forge 海尔内部部署"
echo " App: https://forge.haier.net (主机:9000)"
echo " SSO: https://forge-sso.haier.net (主机:9100)"
echo "========================================="

# --- 检查 .env ---
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    echo "[ERROR] 未找到 App .env"
    echo "  cp .env.example .env && vim .env"
    exit 1
fi

if [ ! -f "$SSO_DIR/.env" ]; then
    echo "[ERROR] 未找到 SSO .env"
    echo "  cp $SCRIPT_DIR/sso.env.example $SSO_DIR/.env && vim $SSO_DIR/.env"
    exit 1
fi

# --- 检查 JDK 版本 ---
JAVA_VER=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}' | cut -d. -f1)
if [ "$JAVA_VER" -lt 21 ] 2>/dev/null; then
    echo "[ERROR] 需要 JDK 21+，当前: $(java -version 2>&1 | head -1)"
    exit 1
fi
echo "[OK] JDK $JAVA_VER"

# --- 创建数据目录 ---
DATA_ROOT=$(grep -E '^DATA_ROOT=' "$SCRIPT_DIR/.env" 2>/dev/null | cut -d= -f2 || echo "/data/forge")
DATA_ROOT="${DATA_ROOT:-/data/forge}"
echo "[INFO] 数据目录: $DATA_ROOT"
sudo mkdir -p "$DATA_ROOT"/{sso-postgres,forge-postgres,backend-data}
sudo chmod 777 "$DATA_ROOT"/{sso-postgres,forge-postgres,backend-data}

# --- 构建后端 ---
echo ""
echo "[1/4] 构建后端 JAR..."
cd "$PROJECT_ROOT"
./gradlew :web-ide:backend:clean :web-ide:backend:bootJar -x test --no-daemon -q
echo "[OK] 后端构建完成"

# --- 构建 MCP Servers ---
echo ""
echo "[2/4] 构建 MCP Servers..."
./gradlew :mcp-servers:forge-knowledge-mcp:shadowJar :mcp-servers:forge-database-mcp:shadowJar -x test --no-daemon -q
echo "[OK] MCP Servers 构建完成"

# --- 构建前端 ---
echo ""
echo "[3/4] 构建前端..."
cd "$PROJECT_ROOT/web-ide/frontend"
if command -v pnpm &>/dev/null; then
    pnpm install --frozen-lockfile 2>/dev/null || pnpm install
    pnpm run build
else
    npm install
    npm run build
fi
echo "[OK] 前端构建完成"

# --- 构建 Enterprise Console ---
echo ""
echo "[4/4] 构建 Enterprise Console..."
cd "$PROJECT_ROOT/enterprise-console"
# NEXT_PUBLIC_* 是构建时变量，必须在 build 前确保 .env 存在
cp -n .env.example .env 2>/dev/null || true
if command -v pnpm &>/dev/null; then
    pnpm install --frozen-lockfile 2>/dev/null || pnpm install
    pnpm run build
else
    npm install
    npm run build
fi
echo "[OK] Enterprise Console 构建完成"

# --- 启动 SSO（独立部署，与 trial 架构一致）---
echo ""
echo "========================================="
echo " 启动 SSO（2 容器: keycloak + postgres）"
echo "========================================="
cd "$SSO_DIR"
docker compose up -d

# --- 启动 App ---
echo ""
echo "========================================="
echo " 启动 App（7 容器）"
echo "========================================="
cd "$SCRIPT_DIR"
docker compose up --build -d

echo ""
echo "========================================="
echo " 部署完成！"
echo ""
echo " Web IDE:    https://forge.haier.net"
echo " Console:    https://forge.haier.net/console"
echo " SSO Admin:  https://forge-sso.haier.net/auth/admin"
echo ""
echo " 默认账号:"
echo "   admin / admin (管理员)"
echo "   dev1  / dev1  (开发者)"
echo "========================================="
echo ""
echo "查看日志: docker compose logs -f"
echo "查看状态: docker compose ps"
echo "SSO 日志: cd ../sso && docker compose logs -f"
