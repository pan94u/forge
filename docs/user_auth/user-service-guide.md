# User Service 说明手册

> 版本: v1.1
> 日期: 2026-02-21
> 状态: ✅ 生产就绪 (Gateway 待修复 JWT 验证)

---

## 目录

- [快速开始](#快速开始)
- [环境要求](#环境要求)
- [本地开发](#本地开发)
- [API 文档](#api-文档)
- [数据库初始化](#数据库初始化)
- [配置说明](#配置说明)
- [部署指南](#部署指南)
- [常见问题](#常见问题)

---

## API Gateway

User Service 可以通过 API Gateway 统一访问。

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| User Service | 8086 | 用户服务原生端口 |
| Gateway | 9443 | API 网关端口 (推荐) |

### 启动 Gateway

```bash
# 构建 Gateway
export JAVA_HOME="D:\Program Files\Java\jdk-21.0.9"
export PATH="$JAVA_HOME/bin:$PATH"
cd D:\ai\ai-lab\forge
./gradlew :services:gateway:bootJar --no-daemon

# 运行 Gateway
java -jar services/gateway/build/libs/gateway-service.jar
```

### 通过 Gateway 访问

所有请求通过 Gateway 端口 9443 转发到 User Service:

```bash
# 注册 (无需认证)
curl -X POST https://localhost:9443/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test123456!","email":"test@example.com"}'

# 登录 (无需认证)
curl -X POST https://localhost:9443/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Test123456!"}'

# 创建组织 (需要 JWT 认证)
curl -X POST https://localhost:9443/api/orgs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{"name":"My Organization","description":"描述"}'
```

### Gateway 配置

文件: `services/gateway/src/main/resources/application.yml`

```yaml
server:
  port: 9443

spring:
  cloud:
    gateway:
      routes:
        - id: user-service-auth
          uri: http://localhost:8086
          predicates:
            - Path=/api/auth/**

        - id: user-service-api
          uri: http://localhost:8086
          predicates:
            - Path=/api/users/**,/api/orgs/**,/api/roles/**

app:
  jwt:
    secret: ${JWT_SECRET:your_jwt_secret_key_at_least_32_chars_here}
```

### 已知问题

⚠️ **JWT 验证**: Gateway 的 JWT 验证过滤器目前验证失败，组织创建等需要认证的 API 无法正常工作。临时解决方案：

1. 直接访问 User Service (端口 8086)
2. 请求中包含 `X-User-Id` 请求头

```bash
# 直接访问 User Service 创建组织
curl -X POST http://localhost:8086/api/orgs \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <user_uuid>" \
  -d '{"name":"My Org"}'
```

---

## 快速开始

### 1. 环境准备

```bash
# 检查 Java 版本 (必须 JDK 21+)
java -version

# 检查 PostgreSQL (端口 5432)
psql --version

# 检查 Redis (端口 6379)
redis-cli ping
```

### 2. 启动服务

```bash
# 开发模式启动
cd D:\ai\ai-lab\forge
export JAVA_HOME="D:\Program Files\Java\jdk-21.0.9"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :services:user-service:bootJar --no-daemon

# 运行 JAR
java -jar services/user-service/build/libs/user-service.jar --spring.profiles.active=dev
```

### 3. 验证服务

```bash
# 健康检查
curl http://localhost:8086/actuator/health

# 预期响应
{"status":"UP"}
```

---

## 环境要求

| 软件 | 最低版本 | 推荐版本 | 说明 |
|------|----------|----------|------|
| JDK | 21 | 21.0.9 | 必须 JDK 21+，不支持 8/17 |
| PostgreSQL | 16 | 16.12 | 使用 gen_random_uuid() |
| Redis | 7 | 7.x | Token 黑名单缓存 |

### 开发环境路径 (Windows)

```bash
# JDK 21
D:\Program Files\Java\jdk-21.0.9

# PostgreSQL 16
D:\Program Files\PostgreSQL\16

# Redis
D:\Program Files\Redis
```

---

## 本地开发

### 配置文件

**开发配置:** `services/user-service/src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/forge
    username: forge
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379

app:
  jwt:
    secret: forge-platform-jwt-secret-key-at-least-32-chars-long
    refresh-secret: forge-refresh-secret-key-at-least-32-chars-long
    expiration-ms: 900000        # 15分钟
    refresh-expiration-ms: 604800000  # 7天
```

### 构建命令

```bash
# 仅编译
./gradlew :services:user-service:compileKotlin --no-daemon

# 构建 JAR
./gradlew :services:user-service:bootJar --no-daemon

# 跳过测试构建
./gradlew :services:user-service:clean :services:user-service:bootJar -x test --no-daemon

# 使用 batch 文件 (Windows)
build-user-service.bat
```

---

## API 文档

### 认证 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 用户登录 | 否 |
| POST | `/api/auth/refresh` | 刷新 Token | 否 |
| POST | `/api/auth/logout` | 用户登出 | 是 |

#### 注册

```bash
curl -X POST http://localhost:8086/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "SecurePass123!",
    "email": "user@example.com"
  }'
```

**响应 (HTTP 201):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "uuid",
      "username": "newuser",
      "email": "user@example.com",
      "status": "ACTIVE"
    }
  },
  "message": "注册成功"
}
```

#### 登录

```bash
curl -X POST http://localhost:8086/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "SecurePass123!"
  }'
```

#### 刷新 Token

```bash
curl -X POST http://localhost:8086/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbG..."
  }'
```

### 组织 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/orgs` | 创建组织 | 是 |
| GET | `/api/orgs` | 获取我的组织 | 是 |
| GET | `/api/orgs/{id}` | 获取组织详情 | 是 |

#### 创建组织

```bash
curl -X POST http://localhost:8086/api/orgs \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -H "X-User-Id: <user_uuid>" \
  -d '{
    "name": "My Organization",
    "slug": "my-org",
    "description": "组织描述"
  }'
```

---

## 数据库初始化

### 启动时自动初始化

User Service 启动时会自动执行以下检查：

1. **检查管理员用户**
   - 检查是否存在 `admin` 用户
   - 不存在时自动创建

2. **创建默认管理员**

| 属性 | 值 |
|------|-----|
| 用户名 | `admin` |
| 邮箱 | `admin@forge.local` |
| 密码 | `ForgeAdmin123!` |

⚠️ **重要**: 首次登录后请立即修改密码！

### 开发环境

开发模式 (`dev` profile) 会创建：

| 属性 | 值 |
|------|-----|
| 用户名 | `admin` |
| 密码 | `admin123` |

### Flyway 迁移

数据库表结构通过 Flyway 自动管理：

| 版本 | 说明 |
|------|------|
| V1 | 用户表、组织表、权限表 |
| V2 | 初始权限数据 |
| V3 | 登录日志表 |
| V4 | JSONB 改为 TEXT |

**手动执行迁移:**
```bash
# 检查当前版本
psql -d forge -c "SELECT * FROM flyway_schema_history ORDER BY installed_on DESC LIMIT 5;"

# 手动触发迁移 (服务重启时自动执行)
```

---

## 配置说明

### 应用配置

```yaml
app:
  host: 0.0.0.0          # 绑定地址
  port: 8086              # 服务端口

  jwt:
    secret: xxx           # JWT 密钥 (至少32字符)
    refresh-secret: xxx   # Refresh Token 密钥
    expiration-ms: 900000 # Access Token 有效期 (15分钟)
    refresh-expiration-ms: 604800000  # Refresh Token (7天)

  security:
    bcrypt-rounds: 12     # BCrypt 强度
    max-login-attempts: 5  # 最大登录失败次数
    lockout-duration-minutes: 30  # 锁定时间
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` | PostgreSQL 主机 | localhost |
| `DB_PORT` | PostgreSQL 端口 | 5432 |
| `DB_NAME` | 数据库名 | forge |
| `DB_USERNAME` | 用户名 | forge |
| `DB_PASSWORD` | 密码 | forge_local_dev |
| `REDIS_HOST` | Redis 主机 | localhost |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `JWT_SECRET` | JWT 密钥 | - |

---

## 部署指南

### Docker 部署

```yaml
# docker-compose.user-service.yml
services:
  user-service:
    image: forge/user-service:latest
    ports:
      - "8086:8086"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DATABASE_URL=jdbc:postgresql://postgres:5432/forge
      - REDIS_URL=redis://redis:6379
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
```

### 健康检查

```bash
curl http://localhost:8086/actuator/health
```

### 日志位置

```bash
# Docker
docker logs forge-user-service-1

# 本地
tail -f services/user-service/logs/user-service.log
```

---

## 常见问题

### Q1: 端口 8086 被占用

```bash
# 查看占用进程
netstat -ano | findstr 8086

# 终止进程
taskkill /F /PID <PID>
```

### Q2: PostgreSQL 连接失败

```bash
# 检查 PostgreSQL 服务
pg_isready -h localhost -p 5432

# 检查数据库存在
psql -l | grep forge
```

### Q3: JWT Token 验证失败

- 确认使用正确格式: `Authorization: Bearer <token>`
- 检查 Token 是否过期
- 确认 `X-User-Id` 请求头

### Q4: 编译失败 (JDK 版本)

```bash
# 显式指定 JDK 21
export JAVA_HOME="D:\Program Files\Java\jdk-21.0.9"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

### Q5: Flyway 迁移失败

```bash
# 备份数据后重置
# 注意: 这会删除所有数据!

# 1. 删除迁移历史
psql -d forge -c "DROP TABLE IF EXISTS flyway_schema_history CASCADE;"

# 2. 删除所有表
psql -d forge -c "DROP TABLE IF EXISTS users, organizations, org_members CASCADE;"

# 3. 重启服务
```

---

## 文件结构

```
services/user-service/
├── build.gradle.kts              # Gradle 构建配置
├── src/main/
│   ├── kotlin/com/forge/user/
│   │   ├── UserServiceApplication.kt
│   │   ├── config/
│   │   │   ├── AppConfig.kt         # 应用配置
│   │   │   ├── DatabaseConfig.kt    # 数据库配置
│   │   │   ├── RedisConfig.kt       # Redis 配置
│   │   │   ├── JacksonConfig.kt    # JSON 序列化
│   │   │   ├── PasswordConfig.kt   # BCrypt 配置
│   │   │   └── DatabaseInitializationConfig.kt  # 启动初始化
│   │   ├── entity/                 # 实体类
│   │   ├── repository/             # 数据访问层
│   │   ├── service/               # 业务逻辑
│   │   ├── controller/            # REST API
│   │   ├── dto/                   # 数据传输对象
│   │   ├── security/              # 安全相关
│   │   └── exception/             # 异常处理
│   └── resources/
│       ├── application.yml         # 应用配置
│       ├── application-dev.yml     # 开发配置
│       └── db/migration/          # Flyway 迁移
│           ├── V1__init_user_schema.sql
│           ├── V2__init_permissions.sql
│           ├── V3__add_login_logs.sql
│           └── V4__change_jsonb_to_text.sql
└── src/test/                       # 测试
```

---

## 相关文档

- [设计文档](docs/architecture/account-auth-sso-design.md)
- [实施计划](docs/architecture/account-auth-sso-implementation-plan.md)
- [Phase 2 E2E 测试](docs/phase2-e2e-acceptance-test.md)

---

> 最后更新: 2026-02-21 23:50