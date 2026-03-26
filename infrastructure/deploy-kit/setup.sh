#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# AI-Native Deploy Kit — 安装脚本
#
# 用法：
#   curl -sf <kit-url>/setup.sh | bash
#   或：
#   cd <项目根目录> && bash /path/to/deploy-kit/setup.sh
#
# 功能：
#   1. 创建 .claude/commands/deploy.md（部署 Skill）
#   2. 创建 deploy-manifests/ 目录 + 模板
#   3. 创建 DEPLOYMENT_LOGBOOK.md
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(pwd)"

echo "================================================"
echo " AI-Native Deploy Kit 安装"
echo " 项目: $PROJECT_ROOT"
echo "================================================"
echo ""

# Step 1: 安装部署 Skill
COMMANDS_DIR="$PROJECT_ROOT/.claude/commands"
if [ -f "$COMMANDS_DIR/deploy.md" ]; then
  echo "⏭  .claude/commands/deploy.md 已存在，跳过"
else
  mkdir -p "$COMMANDS_DIR"
  cp "$SCRIPT_DIR/skill/deploy.md" "$COMMANDS_DIR/deploy.md"
  echo "✅ 已安装 .claude/commands/deploy.md"
fi

# Step 2: 创建 deploy-manifests 目录
MANIFESTS_DIR="$PROJECT_ROOT/deploy-manifests"
if [ -d "$MANIFESTS_DIR" ]; then
  echo "⏭  deploy-manifests/ 已存在，跳过"
else
  mkdir -p "$MANIFESTS_DIR"
  cp "$SCRIPT_DIR/manifests/_template.md" "$MANIFESTS_DIR/_template.md"
  echo "✅ 已创建 deploy-manifests/_template.md"
  echo "   → 复制 _template.md 并重命名为 <你的应用名>.md 来定义部署清单"
fi

# Step 3: 创建部署日志
LOGBOOK="$PROJECT_ROOT/DEPLOYMENT_LOGBOOK.md"
if [ -f "$LOGBOOK" ]; then
  echo "⏭  DEPLOYMENT_LOGBOOK.md 已存在，跳过"
else
  echo "# 部署记录" > "$LOGBOOK"
  echo "" >> "$LOGBOOK"
  echo "由 AI-Native Deploy Kit 自动维护。每次 \`/deploy\` 执行后自动追加记录。" >> "$LOGBOOK"
  echo "✅ 已创建 DEPLOYMENT_LOGBOOK.md"
fi

echo ""
echo "================================================"
echo " 安装完成！"
echo ""
echo " 下一步："
echo "   1. 编辑 deploy-manifests/<app>.md 定义你的应用"
echo "   2. 在服务器上 cd 到项目目录，运行 claude"
echo "   3. 输入 /deploy <app> 执行部署"
echo "================================================"
