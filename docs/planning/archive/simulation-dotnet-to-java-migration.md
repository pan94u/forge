# 模拟验证：用 Forge 平台完成 .NET → Java 跨栈迁移

> 日期：2026-02-17
> 目的：通过真实场景模拟，校验 Forge 平台的设计合理性和提效预期

---

## 场景定义

| 维度 | 描述 |
|------|------|
| 源系统 | ~50,000 行 C# / .NET 历史项目 |
| 目标栈 | Java / Kotlin + Spring Boot 3 |
| 关键约束 | **团队没有人会 .NET**——无法靠人工阅读理解旧代码 |
| 迁移目标 | 功能完全等价迁移 + 架构现代化（Clean Architecture） |
| Forge 状态 | Phase 3 完成，全平台可用 |

### 场景本质

| | 同栈重构 | 跨栈迁移（本场景） |
|---|---|---|
| **核心动作** | 改良现有代码 | 理解旧代码 → 用新技术栈重写 |
| **最大风险** | 改错了回归 Bug | 业务逻辑理解错误导致功能丢失 |
| **团队痛点** | 规范不一致 | **没人读得懂 .NET 源码** |
| **Forge 价值锚点** | 编码提效 | **知识抢救 + 跨语言翻译** |
| **目标栈** | 需要 .NET Skill | **正好是 Forge 的 Java/Kotlin/Spring Boot Skill 全覆盖** |

---

## 传统模式分析（5-7 人 Scrum，无 Forge）

没人会 .NET 的情况下，传统模式会非常痛苦：

| 阶段 | 传统做法 | 耗时 | 核心困难 |
|------|---------|------|---------|
| **代码考古** | 招一个 .NET 外包 or 硬读代码 | 3-5 周 | 没人能回答"这段代码为什么这样写"；文档大概率过时或不存在 |
| **业务逻辑提取** | 人工逐模块梳理业务规则、边界条件 | 3-4 周 | 最容易遗漏的阶段——漏一条业务规则就是一个生产 Bug |
| **架构设计** | Java 架构师设计目标结构 | 2-3 周 | 需要同时懂 .NET 和 Java 的人——这种人很少 |
| **逐模块重写** | 开发团队对照 .NET 用 Java 重写 | 14-20 周 | 边看边写，效率极低；C#→Java 翻译重复劳动大 |
| **测试验证** | 对比新旧系统行为是否一致 | 3-5 周 | 如何证明"功能完全等价"？ |
| **总计** | | **25-37 周，5-7 人** | **~700-1300 人天** |

比同栈重构多 50-70% 时间，因为"理解旧代码"这个环节的成本急剧上升。

---

## Forge 模式工作流（1-2 人 + SuperAgent）

### 第 1-3 天：AI 代码考古（人不需要懂 .NET）

```
@规划 这是一个 50K 行的 .NET 项目，我们团队没人会 .NET，
      需要迁移到 Java/Spring Boot。请先做全面画像。
```

SuperAgent（planning-profile）→ codebase-profiler：

- 解析 .sln / .csproj → 项目结构和模块依赖图
- 扫描 Controllers → API 端点清单（路由、参数、返回值）
- 扫描 Services → 业务逻辑摘要（每个方法做什么）
- 扫描 DbContext / EF Migrations → 数据库 Schema + 关系图
- 扫描 Domain Models → 领域模型 + 聚合边界
- 识别设计模式（Repository? CQRS? Event-driven?）
- 标记技术债和反模式

**产出**：`knowledge-base/profiles/dotnet-project-profile.md`
- 一份完整的"项目说明书"，用中文 + Java 对照描述
- 人工审核：虽然不懂 .NET，但画像是用业务语言写的，可以审核

**关键价值**：这一步替代了"找懂 .NET 的人来讲解"这个不可能完成的任务

### 第 3-5 天：业务规则提取（最关键的一步）

```
@规划 基于画像，提取所有业务规则和边界条件，按领域分组输出。
```

SuperAgent → convention-miner + 专项分析：

- 逐 Service 类分析 → 提取业务规则
  ```
  例：OrderService.CreateOrder():
    - 规则1: 库存 < 订单数量时拒绝（库存校验）
    - 规则2: 会员享受 9 折（折扣规则）
    - 规则3: 单笔订单不超过 50 万（风控规则）
    - 边界: amount <= 0 抛 ArgumentException
  ```
- 扫描 catch/throw → 异常处理策略清单
- 扫描 Authorize/Policy → 权限规则清单
- 扫描 Validation → 数据校验规则清单
- 扫描 配置/Feature Flag → 可配置行为清单

**产出**：`knowledge-base/generated-docs/business-rules/`（按领域分模块的业务规则文档）

**人工审核** → 懂业务但不懂 .NET 的人审查业务规则是否正确。**这是整个项目最重要的 HITL 审批点。**

### 第 5-8 天：Java 架构设计

```
@设计 基于画像和业务规则文档，设计 Java/Spring Boot 目标架构。
      源系统是 .NET 三层架构，目标是 Spring Boot Clean Architecture。
```

SuperAgent（design-profile）→ OODA：

- **Observe**: 读取画像（.NET 项目结构）+ 业务规则文档
- **Orient**: .NET 概念 → Java 映射：

  | .NET | Java/Spring |
  |------|-------------|
  | Controller | @RestController |
  | Service | @Service |
  | DbContext / EF | Spring Data JPA |
  | DI (Constructor) | Spring DI (构造器注入) |
  | Options Pattern | @ConfigurationProperties |
  | Middleware | Filter / Interceptor |
  | FluentValidation | Jakarta Validation |

- **Decide**: 模块划分 + 迁移顺序（按依赖拓扑 + 业务优先级）
- **Act**: 输出 Java 架构设计文档 + Gradle 模块结构

→ architecture-baseline 检查通过
→ HITL：架构评审（完全能评审，因为目标是 Java）

### 第 2-6 周：逐模块迁移（核心阶段）

每个模块的循环（以 Order 模块为例）：

```
@开发 将 .NET OrderModule 迁移到 Java，
      严格按照业务规则文档实现，不遗漏任何规则。
```

SuperAgent（development-profile）→ OODA：

- **Observe**:
  - 读取 .NET 源码：OrderController.cs, OrderService.cs
  - 读取业务规则文档：order-module-rules.md
  - 读取架构设计：target-architecture.md

- **Orient**:
  - 加载 java-conventions → Java 命名规范
  - 加载 spring-boot-patterns → 分层架构 + DI 模式
  - 加载 api-design → REST API 规范
  - 加载 database-patterns → JPA Entity 规范
  - 加载 error-handling → 异常处理模式
  - 加载 testing-standards → 测试规范

- **Decide**:
  - .NET OrderController → Java OrderController
  - .NET OrderService (1200行) → 拆分为 CreateOrderUseCase, CancelOrderUseCase, RefundOrderUseCase
  - EF Entity → JPA @Entity
  - FluentValidation → Jakarta Validation

- **Act**:
  - 生成 Java 代码（完全新写，不是逐行翻译）
  - 逐条对照业务规则文档 → 确保每条规则都有对应实现
  - 生成单元测试（覆盖每条业务规则）
  - 生成集成测试
  - code-style-baseline 通过（ktlint/checkstyle 零违规）
  - security-baseline 通过（无硬编码、无注入）
  - test-coverage-baseline 通过（Service 层 85%+）
  - HITL：Code Review（完全能审查，因为是 Java 代码）

50K LOC .NET ≈ 15-20 个模块，每个模块 1.5-2 天 ≈ 4-6 周。

AI 做 C#→Java 翻译 + 规范对齐非常高效，因为两种语言语法相似度 ~70%。

### 第 7-8 周：行为等价性验证

```
@测试 验证新 Java 系统与旧 .NET 系统的行为等价性。
      对照业务规则文档逐条验证。
```

SuperAgent（testing-profile）→

- 基于业务规则文档生成验收测试
- 对比 API 契约（.NET OpenAPI vs Java OpenAPI）
- 数据库 Schema 兼容性验证
- 边界条件测试（从 .NET catch/throw 提取的边界）

### 第 9-10 周：部署 + 灰度切换

```
@运维 生成从 .NET 切换到 Java 的灰度方案
```

SuperAgent（ops-profile）→

- 双系统并行运行方案
- 流量按比例切换策略
- 数据一致性校验脚本
- 回滚方案

---

## 效率对比汇总

| 指标 | 传统模式 | Forge 模式 | 提效比 |
|------|---------|-----------|--------|
| **人力** | 5-7 人 | 1-2 人 | **3-5x** |
| **总耗时** | 25-37 周 | 8-10 周 | **3-4x** |
| **人天** | 700-1300 人天 | 60-100 人天 | **10-13x** |
| **代码考古 + 业务提取** | 6-9 周（最痛） | 3-5 天 | **8-12x** |
| **架构设计** | 2-3 周 | 3-5 天 | **3-5x** |
| **编码** | 14-20 周 | 4-6 周 | **3-4x** |
| **测试** | 3-5 周 | 1-2 周 | **2-3x** |

比同栈重构的提效比更高，原因：

1. **"没人懂 .NET"这个约束被 AI 化解了**——AI 读 C# 和读 Java 一样容易
2. **C# → Java 翻译是 AI 的强项**——语法相似度 ~70%，AI 几乎可以做到逐行对应
3. **目标栈完全匹配 Foundation Skills**——生成的 Java 代码天然符合规范
4. **全新编写比重构更快**——不需要兼容旧代码，直接按最佳实践写

---

## 对平台设计的校验结论

| # | 发现 | 影响 | 建议 |
|---|------|------|------|
| 1 | **codebase-profiler 必须支持 .NET 项目解析**（.sln/.csproj/C# AST） | Skill 内容需要增加 .NET 源码解析指引 | 在 profiler Skill 中增加多语言解析模块 |
| 2 | **"业务规则提取"是最高价值步骤**——之前设计中没有单独强调 | 这是迁移成败的关键 | 考虑在 planning-profile 中增加专门的 `business-rule-extraction` Skill |
| 3 | **跨栈迁移场景下，Foundation Skills 的价值最大化** | 目标栈 = Java/Kotlin/Spring Boot，完全覆盖 | 验证了 Phase 1 深化 4 个 Skill（spring-boot-patterns 等）的优先级正确 |
| 4 | **行为等价性验证需要专门支持** | 需要对比新旧系统行为 | testing-profile 应包含"API 契约对比"能力 |
| 5 | **之前担心的".NET Skill 缺口"不存在** | 目标是 Java，Forge 的 Skill 完全覆盖 | 只需 profiler 能*读*懂 .NET，不需要*写* .NET 的 Skill |
| 6 | **这是 Forge 的甜点场景** | "团队不懂旧技术栈，需要迁移到熟悉的新技术栈" | 可作为平台价值的标杆案例 |
| 7 | **进化环在第二个项目真正发力** | 第一个项目建立知识；第二个项目复用 | 第二个 .NET 项目迁移时间可再缩短 ~30% |

---

## 核心结论

1. **Forge 平台设计对跨栈迁移场景有效**——SuperAgent + Skill Profile + OODA 循环 + 底线体系 + HITL 审批 的设计在此场景完全适用
2. **AI 解决了"没人懂旧技术栈"的死结**——这是传统模式下几乎无解的问题
3. **人天提效 10-13x 的核心来源**：考古阶段 AI 替代人（8-12x）+ 编码阶段 AI 辅助（3-4x）+ 团队规模缩减（3-5x）
4. **HITL 审批是必要的安全阀**——业务规则审核、架构评审、Code Review 不能省略
5. **Foundation Skills 的投资回报在迁移场景尤为明显**——确保生成的 Java 代码从一开始就符合组织规范
