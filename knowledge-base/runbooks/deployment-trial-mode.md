# Runbook: Trial 模式部署

## 前置条件
- Docker Desktop 已安装并运行
- JDK 21 已安装
- Node.js 20+ 已安装
- Anthropic API Key 已获取

## 部署步骤

### 1. 本地构建
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :web-ide:backend:bootJar -x test --no-daemon
cd web-ide/frontend && npm install && npm run build && cd ../..
```

### 2. 配置环境变量
```bash
cd infrastructure/docker
cp .env.trial.example .env.trial
# 编辑 .env.trial 填入 ANTHROPIC_API_KEY
```

### 3. 启动 Docker
```bash
docker compose -f docker-compose.trial.yml --env-file .env.trial up --build
```

### 4. 验证
- 前端: http://localhost:9000
- API Health: http://localhost:9000/api/knowledge/search
- WebSocket: ws://localhost:9000/ws/chat/{sessionId}

## 已知限制
- 数据库使用 H2（嵌入式），重启后数据丢失
- MCP 工具使用本地知识库（knowledge-base/ 目录），非 Confluence 实连
- 安全认证已禁用（forge.security.enabled=false）

## 常见问题
| 问题 | 原因 | 解决 |
|------|------|------|
| AI 无响应 | API Key 未配置 | 检查 .env.trial 中 ANTHROPIC_API_KEY |
| WebSocket 403 | CORS 不匹配 | 检查 FORGE_WEBSOCKET_ORIGINS 包含 :9000 |
| 构建失败 | Java 版本 | 确认 JAVA_HOME 指向 JDK 21 |
