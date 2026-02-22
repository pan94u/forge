# Forge Platform 账号权限 SSO 架构设计

> 版本: v1.1
> 作者: Claude Sonnet 4.5
> 日期: 2026-02-21
> 状态: ✅ Phase 1-5 已实现

---

## 1. 设计背景与目标

### 1.1 当前架构现状

| 组件 | 技术 | 说明 |
|------|------|------|
| 身份认证 | Keycloak | OAuth2/OIDC provider, Realm: forge |
| 前端 | Next.js 15 | 无状态 JWT 认证 |
| 后端 | Spring Boot 3.3 | OAuth2 Resource Server |
| 网关/路由 | Nginx | 反向代理, SSL termination |
| 用户管理 | 无 | 仅通过 Keycloak 内置用户 |

### 1.2 需求目标

1. **账号功能**: 本地账号体系 + 第三方联合登录
2. **权限功能**: 细粒度 RBAC + ABAC 权限控制
3. **SSO 功能**: 单点登录, 支持多种身份源

### 1.3 设计原则

1. **渐进式增强**: 保留现有 Keycloak 集成，扩展而非替换
2. **松耦合**: User Service 与现有后端通过 REST API 通信
3. **安全优先**: 敏感数据加密, JWT 短期令牌
4. **可观测**: 完整审计日志, 异常检测

---

## 2. 目标架构

### 2.1 架构拓扑图

```
                                    ┌─────────────────────────────────────┐
                                    │            用户浏览器                 │
                                    └──────────────┬──────────────────────┘
                                                   │
                              ┌────────────────────┼────────────────────┐
                              │                    │                    │
                    HTTPS:9443│                    │                    │
                              ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            API Gateway (Spring Cloud Gateway)                     │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │  • 请求路由 / 负载均衡                                                    │    │
│  │  • JWT 验证 & Token 刷新                                                 │    │
│  │  • 权限校验 (RBAC + ABAC)                                                │    │
│  │  • 限流 & 熔断                                                           │    │
│  │  • 请求日志 & 审计                                                       │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│                    │                    │                    │                    │
│    ┌───────────────┼────────────────────┼────────────────────┼───────────────────┤
│    │               │                    │                    │                   │
│    ▼               ▼                    ▼                    ▼                   ▼
│ ┌────────┐   ┌────────────┐    ┌────────────┐    ┌────────────┐     ┌────────────┐
│ │ Keycloak│   │User Service│    │Web IDE     │    │ MCP Servers│     │PostgreSQL  │
│ │ :8180  │   │:8086       │    │Backend:8080│    │:8081-8085  │     │:5432       │
│ └────────┘   └────────────┘    └────────────┘    └────────────┘     └────────────┘
│                                                                                  │
│  身份认证 ←──────── 用户管理 ←────────── 业务服务 ←──────── 数据存储               │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 组件职责

| 组件 | 职责 | 技术栈 |
|------|------|--------|
| **Keycloak** | 身份认证, Token 发行, 身份源 Federation | 内置 |
| **API Gateway** | 请求路由, JWT 验证, 权限校验, 限流 | Spring Cloud Gateway |
| **User Service** | 用户 CRUD, 权限管理, 用户配置 | Spring Boot 3.3 |
| **Web IDE Backend** | 业务逻辑, MCP 工具聚合 | Spring Boot 3.3 |
| **MCP Servers** | 专业领域工具 (知识库/数据库/服务图等) | Ktor |

---

## 3. 详细设计

### 3.1 身份认证架构

#### 3.1.1 认证流程

```
用户登录流程 (多身份源支持):

┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户访问 Web IDE                                 │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │    API Gateway          │
                    │  • 拦截未认证请求        │
                    │  • 重定向到认证页面      │
                    └────────────┬────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │  登录页 (Next.js)       │
                    │  显示登录方式选择:       │
                    │  • 邮箱/密码            │
                    │  • GitHub              │
                    │  • 手机号/验证码         │
                    │  • 企业 SSO (SAML/OIDC) │
                    └────────────┬────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                          │
                    ▼                          ▼
         ┌─────────────────┐         ┌─────────────────┐
         │ 本地账号认证      │         │ 第三方 OAuth2   │
         │ User Service    │         │ Keycloak        │
         │ 验证用户名密码   │         │ (GitHub等)      │
         └────────┬────────┘         └────────┬────────┘
                  │                          │
                  │          ┌───────────────┘
                  │          │
                  ▼          ▼
         ┌─────────────────────────┐
         │  JWT Token 发行         │
         │  • Access Token (15min) │
         │  • Refresh Token (7d)   │
         │  • Token 存入 Redis     │
         └────────────┬────────────┘
                      │
                      ▼
         ┌─────────────────────────┐
         │   后续请求携带 Token     │
         │   Authorization: Bearer │
         └─────────────────────────┘
```

#### 3.1.2 支持的身份源

| 身份源 | 协议 | 说明 | 实现方式 |
|--------|------|------|----------|
| **邮箱/密码** | 内部 | 本地账号 | User Service + BCrypt |
| **GitHub** | OAuth 2.0 | 开发者首选 | Keycloak User Federation |
| **手机号/验证码** | SMS OTP | 国内场景 | User Service + 短信网关 |
| **企业邮箱** | SAML 2.0 / OIDC | 企业客户 | Keycloak Identity Brokering |
| **钉钉/企微** | OAuth 2.0 | 国内企业 | Keycloak User Federation |

#### 3.1.3 Token 设计

```
JWT Access Token Payload:
{
  "sub": "user-uuid",                    // 用户 ID (UUID)
  "username": "zhaoqi",                  // 用户名
  "email": "zhaoqi@example.com",         // 邮箱
  "roles": ["developer", "admin"],        // 角色列表
  "permissions": ["workspace:read", ...], // 权限列表
  "org_id": "org-uuid",                  // 组织 ID (多租户)
  "iss": "forge-platform",               // 发行者
  "aud": "forge-api",                    // 受众
  "exp": 1739999999,                     // 过期时间
  "iat": 1739999999,                     // 签发时间
  "jti": "token-id"                      // Token 唯一 ID (用于撤销)
}

JWT Refresh Token:
{
  "sub": "user-uuid",
  "type": "refresh",
  "exp": 1740599999,                     // 7 天后过期
  "jti": "refresh-token-id"              // 关联 Redis 中的 token 记录
}
```

### 3.2 权限架构

#### 3.2.1 RBAC + ABAC 混合模型

```
权限层级结构:

                    ┌─────────────────┐
                    │  Organization   │  ← 多租户隔离
                    │  (组织)         │
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    │     Role        │  ← 角色定义
                    │  (角色)         │     admin / developer / viewer
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    │   Permission    │  ← 细粒度权限
                    │  (权限)         │
                    └─────────────────┘
                             ▲
                             │ 继承
                             │
                    ┌────────┴────────┐
                    │     User       │  ← 用户
                    │  (用户)        │
                    └─────────────────┘

ABAC 条件示例:
- "workspace:write": resource.owner == user.id
- "workflow:run": user.role in ["admin", "developer"] AND user.org_id == resource.org_id
```

#### 3.2.2 权限定义

| 资源 | 操作 | 权限标识 | 说明 |
|------|------|----------|------|
| **Workspace** | read | `workspace:read` | 读取工作空间 |
| **Workspace** | write | `workspace:write` | 创建/修改文件 |
| **Workspace** | delete | `workspace:delete` | 删除工作空间 |
| **Chat** | read | `chat:read` | 查看对话 |
| **Chat** | write | `chat:write` | 发送消息 |
| **Workflow** | read | `workflow:read` | 查看工作流 |
| **Workflow** | create | `workflow:create` | 创建工作流 |
| **Workflow** | run | `workflow:run` | 运行工作流 |
| **Admin** | access | `admin:access` | 访问管理后台 |
| **User** | manage | `user:manage` | 管理系统用户 |
| **MCP Tools** | call | `mcp:{tool_name}:call` | 调用特定 MCP 工具 |

#### 3.2.3 角色定义

| 角色 | 权限范围 | 说明 |
|------|----------|------|
| **admin** | 所有权限 | 平台管理员 |
| **developer** | workspace/chat/workflow/mcp | 普通开发者 |
| **viewer** | read-only | 只读用户 |
| **viewer-ai** | chat + read | AI 机器人专用 |

### 3.3 User Service 设计

#### 3.3.1 服务职责

```
User Service 核心功能:

┌─────────────────────────────────────────────────────────────────────────┐
│                           User Service (:8086)                          │
├─────────────────────────────────────────────────────────────────────────┤
│  用户管理模块                                                           │
│  ├── POST /api/users/register          # 用户注册 (邮箱/手机)          │
│  ├── GET  /api/users/{id}              # 获取用户详情                   │
│  ├── PUT  /api/users/{id}              # 更新用户信息                   │
│  ├── DELETE /api/users/{id}             # 注销用户                       │
│  ├── POST /api/users/{id}/password     # 修改密码                       │
│  └── POST /api/users/{id}/avatar       # 上传头像                       │
├─────────────────────────────────────────────────────────────────────────┤
│  认证模块                                                              │
│  ├── POST /api/auth/login              # 邮箱/密码登录                  │
│  ├── POST /api/auth/logout             # 退出登录                        │
│  ├── POST /api/auth/refresh           # 刷新 Token                     │
│  ├── POST /api/auth/forgot-password   # 忘记密码 (发送邮件)             │
│  └── POST /api/auth/reset-password    # 重置密码                        │
├─────────────────────────────────────────────────────────────────────────┤
│  第三方绑定模块                                                        │
│  ├── GET  /api/users/{id}/identities   # 获取已绑定的第三方账号          │
│  ├── POST /api/users/{id}/identities/github   # 绑定 GitHub            │
│  ├── DELETE /api/users/{id}/identities/{provider} # 解绑               │
├─────────────────────────────────────────────────────────────────────────┤
│  组织/团队模块 (多租户)                                                 │
│  ├── POST /api/orgs                    # 创建组织                        │
│  ├── GET  /api/orgs/{id}              # 获取组织详情                    │
│  ├── POST /api/orgs/{id}/members      # 添加成员                        │
│  ├── PUT  /api/orgs/{id}/members/{uid}/role  # 修改成员角色            │
│  └── DELETE /api/orgs/{id}/members/{uid}  # 移除成员                    │
├─────────────────────────────────────────────────────────────────────────┤
│  权限模块                                                              │
│  ├── GET  /api/users/{id}/roles       # 获取用户角色                    │
│  ├── POST /api/users/{id}/roles       # 授予角色                         │
│  ├── DELETE /api/users/{id}/roles/{role} # 移除角色                      │
│  ├── GET  /api/roles                  # 获取所有角色                    │
│  └── POST /api/roles                  # 创建自定义角色                   │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 3.3.2 数据模型

```kotlin
// 用户实体
@Entity
@Table(name = "users")
class UserEntity(
    @Id val id: String,                              // UUID
    @Column(nullable = false, unique = true) val username: String,
    @Column(unique = true) val email: String?,        // 邮箱 (可为空)
    @Column(unique = true) val phone: String?,       // 手机号 (可为空)
    @Column(nullable = false) val passwordHash: String, // BCrypt
    @Column(nullable = false) val status: UserStatus,  // ACTIVE / INACTIVE / SUSPENDED
    @Column(nullable = false) val createdAt: Instant,
    @Column(nullable = false) val updatedAt: Instant,
    @Column val lastLoginAt: Instant? = null,
    @Column val lastLoginIp: String? = null
)

enum class UserStatus {
    ACTIVE,      // 正常
    INACTIVE,    // 未激活
    SUSPENDED,   // 已封禁
    DELETED     // 已删除
}

// 组织实体 (多租户)
@Entity
@Table(name = "organizations")
class OrganizationEntity(
    @Id val id: String,
    @Column(nullable = false) val name: String,
    @Column val avatar: String? = null,
    @Column(nullable = false) val ownerId: String,
    @Column(nullable = false) val plan: OrgPlan,     // FREE / PRO / ENTERPRISE
    @Column(nullable = false) val createdAt: Instant
)

enum class OrgPlan {
    FREE, PRO, ENTERPRISE
}

// 组织成员关联
@Entity
@Table(name = "org_members")
class OrgMemberEntity(
    @Id val id: String,
    @Column(nullable = false) val orgId: String,
    @Column(nullable = false) val userId: String,
    @Column(nullable = false) val role: OrgRole,
    @Column(nullable = false) val joinedAt: Instant
)

enum class OrgRole {
    OWNER,      // 所有者 (唯一)
    ADMIN,      // 管理员
    MEMBER,     // 普通成员
    VIEWER      // 只读
}

// 用户角色 (全局 + 组织级)
@Entity
@Table(name = "user_roles")
class UserRoleEntity(
    @Id val id: String,
    @Column(nullable = false) val userId: String,
    @Column(nullable = false) val roleName: String,
    @Column val orgId: String? = null,  // null 表示全局角色
    @Column(nullable = false) val grantedBy: String,
    @Column(nullable = false) val grantedAt: Instant
)

// 第三方登录绑定
@Entity
@Table(name = "user_identities")
class UserIdentityEntity(
    @Id val id: String,
    @Column(nullable = false) val userId: String,
    @Column(nullable = false) val provider: IdentityProvider,
    @Column(nullable = false) val providerUserId: String,  // 第三方用户 ID
    @Column nullable val accessToken: String?,   // 加密存储
    @Column nullable val refreshToken: String?,
    @Column nullable val expiresAt: Instant?,
    @Column(nullable = false) val linkedAt: Instant
)

enum class IdentityProvider {
    GITHUB,
    GOOGLE,
    WECHAT,
    DINGTALK,
    EMAIL,      // 邮箱密码
    PHONE       // 手机验证码
}
```

#### 3.3.3 与 Keycloak 集成

```
User Service 与 Keycloak 的关系:

┌─────────────────────────────────────────────────────────────────────────┐
│                              用户登录流程                                 │
└─────────────────────────────────────────────────────────────────────────┘

邮箱/密码登录:
  前端 → User Service /api/auth/login
       → User Service 验证 BCrypt 密码
       → User Service 发行 JWT (不使用 Keycloak)

GitHub 登录:
  前端 → 重定向到 Keycloak /auth/realms/forge/protocol/openid-connect/auth?provider=github
       → Keycloak 跳转到 GitHub OAuth
       → GitHub 返回 code
       → Keycloak 兑换 token, 创建/查找 user
       → Keycloak 返回 JWT
       → 前端存储 JWT
       → 前端调用 User Service 同步用户信息 (可选)

手机号登录:
  前端 → User Service /api/auth/send-sms-code
       → User Service 调用短信网关
       → 前端输入验证码
       → User Service /api/auth/verify-phone
       → User Service 验证验证码
       → User Service 发行 JWT

设计决策:
  1. 邮箱/密码登录: 完全由 User Service 管理 (不经过 Keycloak)
  2. 第三方登录: Keycloak Identity Brokering, User Service 同步数据
  3. JWT 发行: User Service 自签发 (非 Keycloak JWT)
     - 原因: 与现有前端 Token 格式兼容
     - 密钥管理: 通过 Key Management Service (KMS)
```

### 3.4 API Gateway 设计

#### 3.4.1 网关职责

```
Spring Cloud Gateway 核心功能:

┌─────────────────────────────────────────────────────────────────────────┐
│                      API Gateway (:9443)                                │
├─────────────────────────────────────────────────────────────────────────┤
│  1. 请求路由                                                            │
│     ├── /api/users/* → User Service:8086                               │
│     ├── /api/orgs/* → User Service:8086                                │
│     ├── /api/* → Web IDE Backend:8080                                  │
│     └── /ws/* → Web IDE Backend:8080 (WebSocket)                        │
├─────────────────────────────────────────────────────────────────────────┤
│  2. JWT 验证                                                            │
│     ├── 解析 Authorization Header                                       │
│     ├── 验证签名 (从 JWKS 或本地密钥)                                    │
│     ├── 检查 Token 有效期                                                │
│     ├── 检查 Token 是否在黑名单 (Redis)                                  │
│     └── 提取用户信息到 Request Header                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  3. 权限校验                                                            │
│     ├── 基于 URL 和 Method 的路由配置                                    │
│     ├── 匿名访问: /api/auth/*, /health                                  │
│     ├── 角色检查: @RolesRequired("admin")                              │
│     ├── 权限检查: @PermissionRequired("user:read")                      │
│     └── 组织归属: 检查用户是否属于资源所属组织                            │
├─────────────────────────────────────────────────────────────────────────┤
│  4. 限流 & 熔断                                                          │
│     ├── 限流: 基于 IP/User/Route 的限流策略                               │
│     │     └── 匿名: 100 req/min                                         │
│     │     └── 登录用户: 1000 req/min                                    │
│     │     └── API: 5000 req/min                                         │
│     ├── 熔断: Circuit Breaker (Resilience4j)                            │
│     │     └── 快速失败, 返回 503 Service Unavailable                    │
│     └── 缓存: Redis 缓存常见响应                                          │
├─────────────────────────────────────────────────────────────────────────┤
│  5. 请求日志 & 审计                                                      │
│     ├── 结构化日志 (JSON)                                               │
│     ├── 敏感数据脱敏                                                     │
│     ├── 审计事件: login, logout, permission_denied                      │
│     └── 链路追踪: OpenTelemetry                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 3.4.2 路由配置

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        # User Service
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**,/api/auth/**,/api/orgs/**,/api/roles/**
          filters:
            - RewritePath=/api/(?<segment>.*), /$\{segment}
            - ValidateJwt
            - RateLimit=1000
            - AddRequestHeaders:
                X-User-Id: "${jwt.subject}"
                X-User-Roles: "${jwt.roles}"

        # Web IDE Backend
        - id: webide-backend
          uri: lb://webide-backend
          predicates:
            - Path=/api/**,/ws/**
          filters:
            - RewritePath=/api/(?<segment>.*), /$\{segment}
            - ValidateJwt
            - RateLimit=3000

        # Health check
        - id: health
          uri: lb://webide-backend
          predicates:
            - Path=/actuator/health,/actuator/info
          filters:
            - RewritePath=/actuator/(?<segment>.*), /actuator/$\{segment}

      default-filters:
        - StripPrefix=1
        - AddRequestHeader=X-Forwarded-For, "${caller.ip}"
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials

      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "${CORS_ALLOWED_ORIGINS:*}"
            allowed-methods: GET,POST,PUT,DELETE,OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
```

#### 3.4.3 JWT 验证过滤器

```kotlin
@Component
class JwtValidationFilter : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val request = exchange.request

        // 跳过匿名路由
        if (isAnonymousRoute(request.path)) {
            return chain.filter(exchange)
        }

        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?: return Mono.error(UnauthenticatedException("Missing authorization header"))

        if (!authHeader.startsWith("Bearer ")) {
            return Mono.error(UnauthenticatedException("Invalid authorization header format"))
        }

        val token = authHeader.substring(7)

        try {
            val claims = jwtService.validateAndParse(token)

            // 检查 Token 黑名单 (Redis)
            if (redisTemplate.hasKey("token:blacklist:$token")) {
                return Mono.error(TokenRevokedException("Token has been revoked"))
            }

            // 将用户信息添加到 Request Header
            val modifiedRequest = request.mutate()
                .header("X-User-Id", claims.subject)
                .header("X-User-Username", claims.username)
                .header("X-User-Roles", claims.roles.joinToString(","))
                .header("X-User-Org-Id", claims.orgId ?: "")
                .build()

            return chain.filter(exchange.mutate().request(modifiedRequest).build())
        } catch (e: Exception) {
            return Mono.error(UnauthenticatedException("Invalid token: ${e.message}"))
        }
    }
}
```

### 3.5 数据库设计

#### 3.5.1 表结构

```sql
-- 用户表
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_login_ip INET,
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE
);

-- 组织表
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    avatar TEXT,
    owner_id UUID NOT NULL REFERENCES users(id),
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 组织成员表
CREATE TABLE org_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(org_id, user_id)
);

-- 用户角色表 (全局 + 组织级)
CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_name VARCHAR(64) NOT NULL,
    org_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    granted_by UUID NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(user_id, role_name, org_id)
);

-- 权限表
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    description TEXT,
    conditions JSONB,  -- ABAC 条件
    UNIQUE(resource, action)
);

-- 角色权限关联表
CREATE TABLE role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(64) NOT NULL,
    permission_id UUID NOT NULL REFERENCES permissions(id),
    granted_by UUID NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(role_name, permission_id)
);

-- 第三方登录绑定表
CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}',
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_user_id)
);

-- 登录日志表 (审计)
CREATE TABLE login_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    provider VARCHAR(32) NOT NULL,
    ip_address INET NOT NULL,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Token 黑名单表 (Redis)
-- Redis Key: token:blacklist:{jti} -> user_id, TTL = token_expiration

-- 索引
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_org_members_org_id ON org_members(org_id);
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_login_logs_user_id ON login_logs(user_id);
CREATE INDEX idx_login_logs_created_at ON login_logs(created_at);
```

### 3.6 Docker 部署配置

#### 3.6.1 Docker Compose 架构

```yaml
# docker-compose.account.yml
version: '3.8'

services:
  # API Gateway
  gateway:
    build: infrastructure/gateway
    ports:
      - "9443:9443"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JAVA_OPTS=-Xmx512m -Xms256m
    depends_on:
      - keycloak
      - user-service
      - backend
      - redis
    networks:
      - forge-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "https://localhost:9443/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # User Service
  user-service:
    build: services/user-service
    ports:
      - "8086:8086"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DATABASE_URL=jdbc:postgresql://postgres:5432/forge
      - DATABASE_USERNAME=${DB_USERNAME}
      - DATABASE_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - JWT_EXPIRATION_MS=900000
      - JWT_REFRESH_EXPIRATION_MS=604800000
      - REDIS_URL=redis://redis:6379
      - KEYCLOAK_URL=${KEYCLOAK_URL}
      - SMS_API_KEY=${SMS_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - forge-network
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8086/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Redis (缓存 + Session + Token 黑名单)
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
    networks:
      - forge-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

networks:
  forge-network:
    driver: bridge

volumes:
  redis-data:
```

---

## 4. 实施计划

### 4.1 Phase 1: 基础设施 (第 1-2 周)

#### 目标
- 部署 Redis (缓存 + Token 黑名单)
- 创建 User Service 项目框架
- 配置数据库表结构

#### 任务

| 任务 | 负责人 | 依赖 | 工时 |
|------|--------|------|------|
| 创建 User Service Gradle 模块 | Claude | - | 2h |
| 配置 PostgreSQL 数据库表 | Claude | - | 4h |
| 部署 Redis 实例 | Claude | docker-compose | 1h |
| 实现 User Entity 和 Repository | Claude | - | 4h |
| 实现基础 CRUD API | Claude | - | 4h |
| 单元测试 (User CRUD) | Claude | - | 4h |

#### 交付物
- [ ] User Service 基础框架
- [ ] 数据库迁移脚本
- [ ] Docker 配置

### 4.2 Phase 2: 认证功能 (第 3-4 周)

#### 目标
- 实现邮箱/密码登录
- 实现 JWT Token 发行和验证
- 实现 Token 刷新和退出

#### 任务

| 任务 | 负责人 | 依赖 | 工时 |
|------|--------|------|------|
| 实现 BCrypt 密码加密 | Claude | Phase 1 | 2h |
| 实现 /api/auth/login | Claude | Phase 1 | 4h |
| 实现 JWT Token Service | Claude | - | 4h |
| 实现 /api/auth/refresh | Claude | Token Service | 2h |
| 实现 /api/auth/logout | Claude | Token Service | 2h |
| 集成 Keycloak Federation | Claude | Keycloak | 4h |
| 实现 GitHub OAuth 登录 | Claude | Keycloak | 4h |
| 单元测试 (认证) | Claude | - | 4h |
| 集成测试 (认证流) | Claude | - | 4h |

#### 交付物
- [ ] 邮箱/密码登录
- [ ] GitHub OAuth 登录
- [ ] JWT Token 生命周期管理

### 4.3 Phase 3: 权限系统 (第 5-6 周)

#### 目标
- 实现 RBAC 权限模型
- 实现权限校验注解
- 实现 API Gateway 权限集成

#### 任务

| 任务 | 负责人 | 依赖 | 工时 |
|------|--------|------|------|
| 设计权限数据模型 | Claude | Phase 1 | 2h |
| 实现 Role 和 Permission Entity | Claude | - | 2h |
| 实现 @RolesRequired 注解 | Claude | - | 4h |
| 实现 @PermissionRequired 注解 | Claude | - | 4h |
| 实现权限校验 Service | Claude | - | 4h |
| 实现 User Role Management API | Claude | - | 4h |
| 实现 API Gateway 权限过滤器 | Claude | - | 6h |
| 单元测试 (权限) | Claude | - | 4h |
| 集成测试 (权限流) | Claude | - | 4h |

#### 交付物
- [ ] RBAC 权限模型
- [ ] 权限校验注解
- [ ] API Gateway 集成

### 4.4 Phase 4: API Gateway (第 7-8 周)

#### 目标
- 部署 Spring Cloud Gateway
- 实现 JWT 验证
- 实现限流和熔断

#### 任务

| 任务 | 负责人 | 依赖 | 工时 |
|------|--------|------|------|
| 创建 Gateway Gradle 模块 | Claude | - | 2h |
| 实现 JWT 验证过滤器 | Claude | Phase 2 | 4h |
| 实现路由配置 | Claude | - | 2h |
| 实现限流过滤器 (Redis) | Claude | Phase 1 | 4h |
| 实现熔断器配置 | Claude | - | 2h |
| 实现请求日志中间件 | Claude | - | 2h |
| 配置 HTTPS | Claude | - | 2h |
| 单元测试 (Gateway) | Claude | - | 4h |
| E2E 测试 (网关路由) | Claude | - | 4h |

#### 交付物
- [ ] Spring Cloud Gateway
- [ ] JWT 验证
- [ ] 限流和熔断

### 4.5 Phase 5: 组织管理 (第 9-10 周)

#### 目标
- 实现多租户组织管理
- 实现组织成员管理
- 实现组织级权限

#### 任务

| 任务 | 负责人 | 依赖 | 工时 |
|------|--------|------|------|
| 实现 Organization Entity | Claude | Phase 1 | 2h |
| 实现 Org Member Entity | Claude | Phase 1 | 2h |
| 实现组织 CRUD API | Claude | - | 4h |
| 实现成员管理 API | Claude | - | 4h |
| 实现组织级权限校验 | Claude | Phase 3 | 4h |
| 实现邀请链接生成 | Claude | - | 2h |
| 单元测试 (组织) | Claude | - | 4h |

#### 交付物
- [ ] 多租户组织
- [ ] 成员管理
- [ ] 组织级权限

### 4.6 Phase 6: 高级功能 (第 11-12 周)

#### 目标
- 实现手机号登录
- 实现企业 SSO
- 实现审计日志

#### 任务

| 任务 | 负责人 | 依赖 | 工时 |
|------|--------|------|------|
| 集成短信网关 | Claude | Phase 2 | 4h |
| 实现 /api/auth/send-sms | Claude | - | 2h |
| 实现 /api/auth/verify-phone | Claude | - | 2h |
| 配置 SAML Identity Brokering | Claude | Phase 2 | 4h |
| 实现审计日志 Service | Claude | Phase 1 | 4h |
| 实现登录日志记录 | Claude | Phase 2 | 2h |
| 实现安全告警 | Claude | - | 4h |
| 单元测试 (高级功能) | Claude | - | 4h |

#### 交付物
- [ ] 手机号登录
- [ ] 企业 SSO (SAML/OIDC)
- [ ] 完整审计日志

### 4.7 总体时间线

```
周次    Phase          任务                状态    里程碑
───────────────────────────────────────────────────────────────
1-2    Phase 1         基础设施            ⏳      User Service 框架
3-4    Phase 2         认证功能            ⏳      登录 + Token
5-6    Phase 3         权限系统            ⏳      RBAC 模型
7-8    Phase 4         API Gateway         ⏳      Gateway 部署
9-10   Phase 5         组织管理            ⏳      多租户
11-12  Phase 6         高级功能            ⏳      完整功能
───────────────────────────────────────────────────────────────
                   总工时: ~120 小时
```

---

## 5. 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| **JWT 安全** | 高 | 中 | 短期 Token (15min) + Token 黑名单 + Redis |
| **密码泄露** | 高 | 低 | BCrypt, 密码强度校验, 登录失败锁定 |
| **SSO 配置复杂** | 中 | 中 | 提供配置模板, 详细文档 |
| **性能瓶颈** | 中 | 低 | 限流, 缓存, 熔断 |
| **多租户隔离** | 高 | 低 | 数据库级隔离 + 行级安全策略 |
| **Keycloak 升级** | 低 | 低 | 容器化部署, 配置即代码 |

---

## 6. 成本估算

### 6.1 服务器资源

| 服务 | CPU | 内存 | 实例数 | 月成本估算 |
|------|-----|------|--------|------------|
| API Gateway | 0.5 | 512MB | 1 | $10 |
| User Service | 0.5 | 512MB | 1 | $10 |
| Redis | 0.25 | 256MB | 1 | $5 |
| **合计** | - | - | - | **$25/月** |

### 6.2 人力成本

| Phase | 任务 | 估算工时 |
|-------|------|----------|
| Phase 1 | 基础设施 | 19h |
| Phase 2 | 认证功能 | 28h |
| Phase 3 | 权限系统 | 34h |
| Phase 4 | API Gateway | 30h |
| Phase 5 | 组织管理 | 22h |
| Phase 6 | 高级功能 | 26h |
| **合计** | - | **~159h** |

---

## 7. 附录

### 7.1 参考文档

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [JWT Best Practices](https://auth0.com/blog/a-look-at-the-latest-draft-for-jwt-bcp/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

### 7.2 配置示例

#### Keycloak Realm 配置

```json
{
  "realm": "forge",
  "enabled": true,
  "registrationAllowed": true,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,
  "resetPasswordAllowed": true,
  "editUsernameAllowed": false,
  "bruteForceProtected": true,
  "identityProviders": [
    {
      "alias": "github",
      "providerId": "github",
      "enabled": true,
      "updateProfileFirstLoginMode": "on",
      "config": {
        "clientId": "${GITHUB_CLIENT_ID}",
        "clientSecret": "${GITHUB_CLIENT_SECRET}",
        "redirectUri": "https://forge.example.com/auth/callback/github"
      }
    }
  ]
}
```

### 7.3 变更日志

| 版本 | 日期 | 作者 | 变更 |
|------|------|------|------|
| v1.0 | 2026-02-21 | Claude | 初始设计文档 |