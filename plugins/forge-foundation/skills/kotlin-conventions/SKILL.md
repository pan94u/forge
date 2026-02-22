---
name: kotlin-conventions
description: "Forge 项目 Kotlin 约定。data class 边界、scope function 选用、协程结构化并发、null safety 规则。"
trigger: when writing or modifying .kt files
tags: [kotlin, conventions, coroutines, sealed-classes]
version: "2.0"
scope: platform
category: foundation
---

# Kotlin Conventions — Forge 项目约定

## Data Class 规则

- DTO / Value Object / Config 用 `data class`
- **不用 data class 的场景**: 有可变状态的 Entity、继承层次、字段数 > 7
- `init` 块用于前置条件校验（`require`）

## Scope Function 选用规则

| 函数 | 对象引用 | 返回值 | 使用场景 |
|------|---------|--------|----------|
| `let` | `it` | Lambda 结果 | null 检查 + 变换 |
| `apply` | `this` | 对象本身 | 对象配置（builder 风格） |
| `also` | `it` | 对象本身 | 副作用（日志、验证） |
| `run` | `this` | Lambda 结果 | 对象上计算结果 |

**禁止**: 嵌套超过 2 层 scope function。

## 协程规则

- 并行操作用 `coroutineScope` + `async`
- 独立操作（一个失败不影响其他）用 `supervisorScope`
- 禁止 `GlobalScope`

## Null Safety 规则

- 可选链用 `?.let`
- 默认值用 `?: elvis`
- 边界处用 `requireNotNull` / `checkNotNull`
- **禁止生产代码使用 `!!`**（测试除外）

## 扩展函数规则

- 用于添加对象的自然行为（`order.isEditable()`）
- 用于领域特定格式化（`money.toDisplayString()`）
- **禁止** 通过扩展函数添加不相关行为（`order.sendEmail()` 不属于 Order）

## Companion Object 约定

- Logger 放在 companion object
- 工厂方法放在 companion object（`Order.create(...)`）

## 枚举序列化约定

- 所有枚举必须加 `@JsonValue` 返回小写
- 新增枚举时立即全局排查 `grep -r "enum class" --include="*.kt"`
