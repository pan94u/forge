---
name: deployment-readiness-check
version: "1.0"
triggers:
  - "deploy"
  - "release"
  - "docker"
  - "kubernetes"
  - "部署"
  - "发布"
  - "上线"
tags: [deployment, preflight, quality-gate, foundation]
baselines:
  - code-style-baseline
  - security-baseline
  - test-coverage-baseline
---

# Deployment Readiness Check — 部署前飞行检查

## 目标

在任何部署操作之前，系统性地验证所有前置条件已满足，防止"编译通过但运行失败"的情况。

## 检查清单

### 1. 构建产物完整性
- [ ] 锁文件存在且最新（`package-lock.json`, `gradle.lockfile`）
- [ ] 构建产物存在（`build/libs/*.jar`, `.next/standalone/`）
- [ ] 构建产物与源码版本匹配（git commit hash 一致）
- [ ] 所有依赖已解析（无 SNAPSHOT 依赖指向不存在的仓库）

### 2. 配置一致性
- [ ] Dockerfile 与 `.dockerignore` 无矛盾（COPY 的文件未被 ignore）
- [ ] 环境变量在 `application.yml` 中有默认值或文档说明
- [ ] `docker-compose.yml` 中所有 service 的 image/build 路径有效
- [ ] 端口映射无冲突

### 3. 工具链版本
- [ ] 运行时 JDK 版本 ≥ 编译时 JDK 版本
- [ ] Node.js 版本与 `package.json` 的 `engines` 字段匹配
- [ ] Docker 版本支持所用的 Compose 特性

### 4. 安全检查
- [ ] 无硬编码凭据（grep 敏感关键词: password, secret, api_key, token）
- [ ] `.env` 文件不在 Git 仓库中
- [ ] 敏感端口未暴露到公网（H2 Console、Debug Port）

### 5. 数据库迁移
- [ ] Flyway 迁移脚本版本连续无间断
- [ ] 迁移脚本内容与 JPA Entity 定义一致
- [ ] 无破坏性迁移（DROP TABLE、ALTER COLUMN 类型变更）在生产部署中

### 6. 前后端契约
- [ ] 前端请求体格式与后端 DTO 匹配
- [ ] SSE 事件类型在前后端定义一致
- [ ] WebSocket 消息格式对齐

## 使用方式

当用户要求部署或发布时，在 OODA 的 Orient 阶段自动加载此 Skill。在 Act 阶段之前，逐项检查上述清单。如有任何项不通过，在 Decide 阶段明确告知用户并提供修复建议。

## 经验教训（来源：Session 5 Docker 部署调试）

以下问题曾导致部署失败，本 Skill 的检查清单已覆盖：
1. 缺少 `package-lock.json` → 清单 1.1
2. `.dockerignore` 排除了 `build/` 导致 COPY 失败 → 清单 2.1
3. JDK 8 运行 Spring Boot 3 代码 → 清单 3.1
4. `npm run dev` 通过但 `npm run build` 失败 → 清单 1.2
5. WebClient bean 缺失导致启动崩溃 → 清单 6（前后端契约）
