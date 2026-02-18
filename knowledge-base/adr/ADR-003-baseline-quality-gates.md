# ADR-003: 底线（Baseline）保障质量下限

## 状态
已接受 (2026-02-16)

## 背景
AI 生成的代码质量不稳定，需要自动化质量门禁确保最低质量标准。

## 决策
实现 Baseline 系统：一组 shell 脚本作为质量门禁，在 OODA Act 阶段之后自动执行。

## Baseline 列表
| 脚本 | 检查内容 | 退出码 |
|------|---------|--------|
| `code-style-baseline.sh` | 代码风格（ktlint/eslint） | 0=pass, 1=fail |
| `security-baseline.sh` | 安全漏洞（OWASP Top 10） | 0=pass, 1=fail |
| `test-coverage-baseline.sh` | 测试覆盖率（≥80%） | 0=pass, 1=fail |
| `api-contract-baseline.sh` | API 契约一致性（OpenAPI） | 0=pass, 1=fail |
| `architecture-baseline.sh` | 架构约束（依赖方向） | 0=pass, 1=fail |

## 执行机制
1. BaselineRunner 读取 Profile 定义的 baseline 列表
2. 按顺序执行每个 .sh 脚本
3. 收集 stdout/stderr 和退出码
4. 生成结构化报告（JSON + 人类可读摘要）
5. 如果任何 baseline 失败，OODA 循环回到 Observe 重新修复

## 后果
- 目标：底线一次通过率 ≥ 70%
- 最多 3 次 OODA 循环尝试修复，之后升级给人类
- Baseline 脚本本身也需要维护和版本管理
