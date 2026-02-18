---
name: environment-parity
version: "1.0"
triggers:
  - "environment"
  - "docker"
  - "production"
  - "staging"
  - "local"
  - "环境"
  - "差异"
tags: [environment, parity, infrastructure, foundation]
---

# Environment Parity — 环境一致性检测

## 目标

检测和防止本地开发、Docker 容器和生产环境之间的差异，确保"本地通过 = Docker 通过 = 生产通过"。

## 环境差异维度

### 1. 工具链版本
| 维度 | 检查方式 | 常见陷阱 |
|------|---------|---------|
| JDK 版本 | `java -version` | 本地 JDK 8，Docker JDK 21 |
| Node.js 版本 | `node --version` | 本地 v18，Docker v20 |
| Gradle 版本 | `gradle/wrapper/gradle-wrapper.properties` | 本地全局 vs wrapper 版本 |
| npm/pnpm | `npm --version` | lockfile 格式不兼容 |

### 2. 网络环境
| 维度 | 检查方式 | 常见陷阱 |
|------|---------|---------|
| DNS 解析 | `nslookup` 服务域名 | Docker 内使用服务名，本地使用 localhost |
| TLS 证书 | `curl -v https://` | Docker 内 TLS 握手失败（macOS Docker 已知问题） |
| 代理设置 | `$HTTP_PROXY`, `$HTTPS_PROXY` | 企业代理未传入 Docker |
| 端口映射 | `docker compose ps` | 端口冲突或未暴露 |

### 3. 文件系统
| 维度 | 检查方式 | 常见陷阱 |
|------|---------|---------|
| 路径分隔符 | `File.separator` | Windows `\` vs Unix `/` |
| 大小写敏感 | 创建测试文件 | macOS 默认不敏感，Linux 敏感 |
| Volume 挂载 | `docker inspect` | `:ro` 只读挂载无法写入 |
| 文件权限 | `ls -la` | 脚本未设置 `+x` |

### 4. 配置差异
| 维度 | 检查方式 | 常见陷阱 |
|------|---------|---------|
| Spring Profile | `SPRING_PROFILES_ACTIVE` | 本地 `local`，Docker 无 profile |
| 安全配置 | `forge.security.enabled` | 测试关闭，生产开启 |
| 数据库 | `DB_URL` | 本地 H2，生产 PostgreSQL |
| CORS 源 | `forge.cors.allowed-origins` | 未包含实际访问域名 |

## 检测流程

在 OODA 的 Orient 阶段，当任务涉及跨环境操作时：

1. **识别目标环境**：从任务描述或上下文推断（deploy → 生产，docker → 容器）
2. **对比配置**：读取 `application.yml` 的 profile 差异
3. **检查版本**：对比 Dockerfile 中的基础镜像版本与本地工具版本
4. **验证网络**：确认目标环境的服务端点可达
5. **报告差异**：列出所有发现的不一致，按严重程度排序

## 修复建议模板

```
环境差异报告:
┌──────────────┬────────────┬────────────┬──────────┐
│ 维度          │ 本地        │ 目标环境     │ 风险等级   │
├──────────────┼────────────┼────────────┼──────────┤
│ JDK          │ 1.8        │ 21         │ 🔴 阻塞    │
│ CORS Origins │ :3000      │ :9000      │ 🟡 警告    │
│ 数据库        │ H2         │ PostgreSQL │ 🟡 警告    │
└──────────────┴────────────┴────────────┴──────────┘

建议操作:
1. [阻塞] 设置 JAVA_HOME=/opt/homebrew/opt/openjdk@21
2. [警告] 在 FORGE_CORS_ALLOWED_ORIGINS 中添加 http://localhost:9000
```
