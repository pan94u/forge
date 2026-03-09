#!/usr/bin/env bash
# =============================================================================
# Forge 海尔内部部署 — 一键构建 + 启动
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "========================================="
echo " Forge 海尔内部部署"
echo " App:  http://forge.haier.net (主机:9000)"
echo " SSO:  http://forge-sso.haier.net (主机:9100)"
echo "========================================="

# --- 检查 .env ---
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    echo "[ERROR] 未找到 .env 文件"
    echo "  cp .env.example .env && vim .env"
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
if command -v pnpm &>/dev/null; then
    pnpm install --frozen-lockfile 2>/dev/null || pnpm install
    pnpm run build
else
    npm install
    npm run build
fi
echo "[OK] Enterprise Console 构建完成"

# --- Docker Compose 启动 ---
echo ""
echo "========================================="
echo " 启动 Docker 容器（9 个）"
echo "========================================="
cd "$SCRIPT_DIR"
docker compose up --build -d

echo ""
echo "========================================="
echo " 部署完成！"
echo ""
echo " Web IDE:    http://forge.haier.net"
echo " Console:    http://forge.haier.net/console"
echo " SSO Admin:  http://forge-sso.haier.net/auth/admin"
echo ""
echo " 默认账号:"
echo "   admin / admin (管理员)"
echo "   dev1  / dev1  (开发者)"
echo "========================================="
echo ""
echo "查看日志: docker compose logs -f"
echo "查看状态: docker compose ps"
