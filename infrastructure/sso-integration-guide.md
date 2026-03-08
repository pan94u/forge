# Forge SSO 接入指南

> 本文档面向需要接入 Forge SSO（Keycloak）的新应用开发者。
> SSO 地址：`https://sso.forge.delivery/auth`，Realm：`forge`

---

## 一、概述

Forge SSO 基于 Keycloak 24，支持标准 OIDC / OAuth 2.0 协议。任何支持 OIDC 的应用都可以接入。

接入共三步：
1. **在 Keycloak 注册客户端**（配置 redirect URI、权限等）
2. **应用侧集成 OIDC**（登录、回调、token 管理）
3. **后端验证 JWT**（校验 token 合法性、提取用户角色）

---

## 二、Keycloak 端配置

### 2.1 两种客户端类型

| | 公开客户端 (Public) | 机密客户端 (Confidential) |
|---|---|---|
| **适用场景** | SPA 纯前端、移动端 | 有后端的 Web 应用 |
| **有 client_secret** | ❌ 没有 | ✅ 有 |
| **认证流程** | Authorization Code + PKCE | Authorization Code + client_secret |
| **Token 存储** | 浏览器 localStorage/内存 | 后端 Session（更安全） |
| **现有示例** | `forge-web-ide` | `forge-enterprise`、`forge-synapse` |

**选择建议：** 有后端的应用优先用机密客户端。纯前端 SPA 用公开客户端 + PKCE。

### 2.2 通过 Admin API 注册客户端

```bash
# ① 获取 Admin Token
TOKEN=$(curl -s -X POST "https://sso.forge.delivery/auth/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=<ADMIN_PASSWORD>&grant_type=password&client_id=admin-cli" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# ② 创建客户端（机密客户端示例）
curl -s -X POST "https://sso.forge.delivery/auth/admin/realms/forge/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "my-app",
    "name": "My Application",
    "enabled": true,
    "publicClient": false,
    "secret": "<自定义密钥或留空自动生成>",
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": false,
    "redirectUris": [
      "https://my-app.forge.delivery/*",
      "http://localhost:3000/*"
    ],
    "webOrigins": [
      "https://my-app.forge.delivery",
      "http://localhost:3000"
    ],
    "protocol": "openid-connect",
    "defaultClientScopes": ["openid", "profile", "email", "roles"]
  }'

# ③ 如果未自定义 secret，查询自动生成的 secret
CLIENT_UUID=$(curl -s "https://sso.forge.delivery/auth/admin/realms/forge/clients?clientId=my-app" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")
curl -s "https://sso.forge.delivery/auth/admin/realms/forge/clients/$CLIENT_UUID/client-secret" \
  -H "Authorization: Bearer $TOKEN"
```

如果习惯 GUI 操作，也可以在 https://sso.forge.delivery/auth/admin 中手动创建。

### 2.3 创建公开客户端（SPA 场景）

与上面相同，区别仅在：
```json
{
  "publicClient": true,
  "attributes": {
    "pkce.code.challenge.method": "S256"
  }
}
```
不需要 `secret` 字段。

### 2.4 关键参数说明

| 参数 | 说明 |
|------|------|
| `redirectUris` | 登录成功后允许回调的 URL，支持通配符 `*`，**必须包含所有环境的地址** |
| `webOrigins` | 允许跨域请求的来源域名（Keycloak 用此设置 CORS），通常与应用域名一致 |
| `defaultClientScopes` | 默认请求的 scope。`roles` 作用域会将 realm 角色写入 token |
| `post.logout.redirect.uris` | 登出后允许跳转的 URL（多个用 `##` 分隔），设在 `attributes` 中 |

---

## 三、应用侧集成

### 3.1 OIDC 端点一览

| 端点 | URL |
|------|-----|
| 发现文档 | `https://sso.forge.delivery/auth/realms/forge/.well-known/openid-configuration` |
| 授权 | `https://sso.forge.delivery/auth/realms/forge/protocol/openid-connect/auth` |
| Token | `https://sso.forge.delivery/auth/realms/forge/protocol/openid-connect/token` |
| UserInfo | `https://sso.forge.delivery/auth/realms/forge/protocol/openid-connect/userinfo` |
| JWKS | `https://sso.forge.delivery/auth/realms/forge/protocol/openid-connect/certs` |
| 登出 | `https://sso.forge.delivery/auth/realms/forge/protocol/openid-connect/logout` |

大多数 OIDC 库只需配置 **issuer URL**，即可自动发现所有端点：
```
https://sso.forge.delivery/auth/realms/forge
```

### 3.2 方案 A：纯前端 SPA（Authorization Code + PKCE）

参考实现：`web-ide/frontend/src/lib/auth.ts`

```typescript
// 1. 登录 — 生成 PKCE 并跳转 Keycloak
async function login() {
  const verifier = generateCodeVerifier();       // 随机字符串
  const challenge = await sha256Base64Url(verifier); // S256 哈希
  sessionStorage.setItem("pkce_verifier", verifier);

  const params = new URLSearchParams({
    client_id: "my-app",
    redirect_uri: `${location.origin}/auth/callback`,
    response_type: "code",
    scope: "openid profile email roles",
    code_challenge: challenge,
    code_challenge_method: "S256",
  });

  location.href =
    `https://sso.forge.delivery/auth/realms/forge/protocol/openid-connect/auth?${params}`;
}

// 2. 回调页 — 用 code 换 token
async function handleCallback(code: string) {
  const verifier = sessionStorage.getItem("pkce_verifier");

  const res = await fetch(
    "https://sso.forge.delivery/auth/realms/forge/protocol/openid-connect/token",
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        client_id: "my-app",
        code,
        redirect_uri: `${location.origin}/auth/callback`,
        code_verifier: verifier,
      }),
    }
  );

  const { access_token, refresh_token, expires_in } = await res.json();
  // 存储 token，设置定时刷新...
}

// 3. 调用 API 时携带 token
fetch("/api/data", {
  headers: { Authorization: `Bearer ${access_token}` },
});
```

### 3.3 方案 B：Next.js + Auth.js（推荐有后端的 Web 应用）

参考实现：`enterprise-console/src/auth.ts`

```bash
npm install next-auth@beta
```

```typescript
// auth.ts
import NextAuth from "next-auth";

const issuer = "https://sso.forge.delivery/auth/realms/forge";

export const { handlers, signIn, signOut, auth } = NextAuth({
  basePath: "/api/auth",
  providers: [{
    id: "keycloak",
    name: "Keycloak",
    type: "oauth",
    clientId: "my-app",
    clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
    issuer,
    authorization: {
      url: `${issuer}/protocol/openid-connect/auth`,
      params: { scope: "openid email profile roles" },
    },
    token:    `${issuer}/protocol/openid-connect/token`,
    userinfo: `${issuer}/protocol/openid-connect/userinfo`,
    jwks_endpoint: `${issuer}/protocol/openid-connect/certs`,
    checks: ["pkce", "state"],
  }],
  callbacks: {
    jwt({ token, account }) {
      if (account) {
        token.accessToken = account.access_token;
        token.refreshToken = account.refresh_token;
      }
      return token;
    },
    session({ session, token }) {
      session.accessToken = token.accessToken as string;
      return session;
    },
  },
});
```

### 3.4 方案 C：Spring Boot 后端

参考实现：`web-ide/backend`（纯资源服务器，校验 JWT）

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://sso.forge.delivery/auth/realms/forge
```

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .oauth2ResourceServer { it.jwt {} }
            .authorizeHttpRequests {
                it.requestMatchers("/api/public/**").permitAll()
                it.requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
                it.anyRequest().authenticated()
            }
        return http.build()
    }
}
```

### 3.5 方案 D：其他语言 / 框架

任何支持 OIDC 的库都可以接入，只需配置 issuer：

| 语言/框架 | 推荐库 | 配置要点 |
|-----------|--------|---------|
| Go | `coreos/go-oidc` | `provider, _ := oidc.NewProvider(ctx, issuerURL)` |
| Python Flask | `authlib` | `oauth.register("keycloak", server_metadata_url=issuer+"/.well-known/openid-configuration")` |
| Python FastAPI | `fastapi-oidc` | 配置 `OIDC_DISCOVERY_URL` |
| Java (非 Spring) | `nimbus-jose-jwt` | 校验 JWT 签名，验证 issuer/audience |
| Nginx (反向代理) | `lua-resty-openidc` | 在 nginx 层做 OIDC，透传 token 给上游 |

---

## 四、JWT Token 结构

登录成功后，Keycloak 颁发的 access_token 包含以下关键字段：

```json
{
  "iss": "https://sso.forge.delivery/auth/realms/forge",
  "sub": "a1b2c3d4-...",
  "preferred_username": "dev1",
  "email": "dev1@forge.dev",
  "name": "Developer One",
  "realm_roles": ["developer"],
  "scope": "openid email profile roles",
  "exp": 1741500000,
  "iat": 1741496400
}
```

| 字段 | 说明 |
|------|------|
| `iss` | 签发者，用于验证 token 来源 |
| `sub` | 用户唯一 ID（UUID） |
| `preferred_username` | 用户名 |
| `realm_roles` | 用户在 forge realm 中的角色列表 |
| `exp` | 过期时间（Unix 时间戳），默认 1 小时 |

**角色说明：**

| 角色 | 权限 |
|------|------|
| `admin` | 平台管理员，所有权限 |
| `developer` | 开发者，可创建/编辑工作区 |
| `viewer` | 只读访问 |

---

## 五、完整接入清单

```
□ 1. Keycloak 端
  □ 确定客户端类型（公开 / 机密）
  □ 通过 Admin API 或 Admin Console 创建客户端
  □ 配置 redirectUris（所有环境：生产 + 开发）
  □ 配置 webOrigins（允许 CORS 的域名）
  □ 记录 clientId 和 secret（机密客户端）
  □ 将客户端配置同步到 realm-export.json（作为声明式备份）

□ 2. 应用前端
  □ 集成 OIDC 登录（选择上述方案之一）
  □ 实现回调页处理 authorization code
  □ 实现 token 刷新（access_token 默认 1 小时过期）
  □ API 请求携带 Authorization: Bearer <token>
  □ 实现登出（清除本地 token + 调用 Keycloak logout 端点）

□ 3. 应用后端
  □ 配置 JWT 验证（issuer-uri 指向 Keycloak）
  □ 从 token 中提取用户信息和角色
  □ 按角色控制 API 访问权限

□ 4. 验证
  □ 登录跳转到 SSO，登录后正确回调
  □ 已登录用户跨应用免登录（SSO 单点登录）
  □ Token 过期后自动刷新
  □ 登出后所有应用同时失效（SSO 单点登出）
```

---

## 六、常见问题

### redirect_uri 不匹配
**现象：** Keycloak 报 `Invalid parameter: redirect_uri`
**原因：** 应用发送的 redirect_uri 不在客户端的 `redirectUris` 白名单中
**解决：** 在 Keycloak 客户端配置中添加对应的 URI（支持通配符 `*`）

### CORS 错误
**现象：** 浏览器 Console 报 CORS 跨域错误
**原因：** 应用域名不在客户端的 `webOrigins` 中
**解决：** 在 Keycloak 客户端配置中添加应用域名到 webOrigins

### Token issuer 不匹配
**现象：** 后端报 JWT issuer 验证失败
**原因：** 后端配置的 `issuer-uri` 与 token 中的 `iss` 不一致
**解决：** 确保两端使用完全相同的 URL（注意 http/https、端口、路径结尾是否有 `/`）

### realm-export.json 与运行态不同步
**现象：** 代码中 realm-export.json 已更新，但 Keycloak 未生效
**原因：** Keycloak 仅在首次启动时导入 realm，后续不自动同步
**解决：** 通过 Admin API 或 Admin Console 手动更新，保持 realm-export.json 作为声明式备份

---

## 附录：管理 API 速查

```bash
# 获取 Admin Token
TOKEN=$(curl -s -X POST "https://sso.forge.delivery/auth/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=<PASSWORD>&grant_type=password&client_id=admin-cli" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# 列出所有客户端
curl -s "https://sso.forge.delivery/auth/admin/realms/forge/clients" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 查看指定客户端
curl -s "https://sso.forge.delivery/auth/admin/realms/forge/clients?clientId=my-app" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 更新客户端（PUT，需传完整 client 对象或要更新的字段）
curl -s -X PUT "https://sso.forge.delivery/auth/admin/realms/forge/clients/<UUID>" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"redirectUris": ["https://my-app.example.com/*"]}'

# 列出 realm 用户
curl -s "https://sso.forge.delivery/auth/admin/realms/forge/users?max=50" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 创建用户
curl -s -X POST "https://sso.forge.delivery/auth/admin/realms/forge/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","enabled":true,"credentials":[{"type":"password","value":"pass","temporary":false}]}'
```
