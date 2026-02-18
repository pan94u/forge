# ADR-002: Skill 优于 Prompt

## 状态
已接受 (2026-02-16)

## 背景
AI 辅助开发中，专业知识的注入方式有两种：临时 Prompt 和结构化 Skill。

## 决策
将专业知识编码为可复用、可组合的 Skill（YAML frontmatter + Markdown），而非依赖临时 Prompt。

## 理由
1. **可复用**：Skill 定义一次，所有 Profile 共享
2. **可版本化**：Skill 文件纳入 Git 管理，变更可追溯
3. **可测试**：通过 skill-tests 框架验证 Skill 结构和内容
4. **可进化**：学习环分析执行日志，自动建议 Skill 改进

## Skill 格式
```yaml
---
name: kotlin-conventions
version: "1.0"
triggers:
  - "*.kt"
  - "build.gradle.kts"
tags: [kotlin, conventions, foundation]
---
# Skill 内容（Markdown）
...
```

## 当前 Skill 清单
- **Foundation Skills (15)**: kotlin-conventions, spring-boot-patterns, api-design, database-patterns, error-handling, security-practices, testing-standards, logging-observability, gradle-build, codebase-profiler, convention-miner, business-rule-extraction, java-conventions 等
- **SuperAgent Skills (8)**: requirement-analysis, architecture-design, code-generation, test-case-writing, deployment-ops 等
- **Domain Skills (3)**: domain-payment, domain-inventory, domain-order
