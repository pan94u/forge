---
name: code-generation
description: "代码生成 — design-first 生成、约定遵循、增量编码、测试同步生成。"
stage: development
type: delivery-skill
version: "2.0"
scope: platform
category: delivery
---

# Code Generation Skill — Forge 项目约定

## 核心原则（Forge 强制规则）

1. **Design-First**: 无设计不编码。必须先读架构设计（C4/ADR）、详细设计、API 契约、数据库 Schema
2. **Convention-First**: 编码前先加载 Foundation Skills，读 2-3 个同包已有文件了解本地模式
3. **实现 + 测试同步**: 每个代码单元必须同时生成测试，禁止无测试代码提交
4. **增量生成**: 不重写已有代码，只修改设计要求的部分，保留未变更区域的注释和格式
5. **编译验证**: 提交前必须验证编译通过（`./gradlew compileKotlin` 或等效命令）

## 生成流程

```
读设计 → 生成清单 → 数据层 → 服务层 → API 层 → 测试 → 编译验证 → Baseline 检查
```

### 生成清单模板

| 设计元素 | 代码产出 | 测试产出 |
|----------|----------|----------|
| Entity 类图 | Entity + Value Object | Entity 单元测试 |
| Repository 接口 | Repository 接口 + 实现 | Repository 集成测试 |
| Service 序列图 | Service 类 + 方法 | Service 单元测试 |
| Controller 端点 | Controller + DTO + Mapper | Controller 集成测试 |
| 错误场景 | Exception 类 + Handler | 错误处理测试 |
| 数据库 Schema | Migration 脚本 | Migration 验证测试 |

## 代码质量规则

1. 编译无错误
2. 所有测试通过
3. 无 TODO/FIXME（必须关联 Issue）
4. 无注释代码（git 有历史）
5. 无未使用 import
6. 有意义的命名
7. 函数单一职责
8. 错误消息含上下文（ID、值）
9. 日志级别恰当：INFO=业务事件 / DEBUG=细节 / ERROR=故障
10. 无硬编码值

## 增量生成检查清单

- [ ] 变更集只含新建/修改文件
- [ ] 已有文件未变更部分完全保留
- [ ] 新代码遵循相邻代码模式
- [ ] Import 排序符合项目约定
- [ ] 文件位置符合包结构
- [ ] 无重复逻辑（复用已有工具类）

## 验证脚本

- `scripts/compile_check.py` — 检测常见编译错误（括号匹配、缺失声明）
- `scripts/import_check.py` — 检测未使用 import 和通配符 import
- `scripts/naming_check.py` — 检测命名规范违规（PascalCase/camelCase/UPPER_SNAKE）
