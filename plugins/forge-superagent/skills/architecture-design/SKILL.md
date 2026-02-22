---
name: architecture-design
description: "架构设计 — C4 建模、ADR 决策记录、NFR 映射、技术选型。"
stage: design
type: delivery-skill
version: "2.0"
scope: platform
category: delivery
---

# Architecture Design Skill — Forge 项目约定

## C4 模型使用规则

| Level | 何时使用 | 要点 |
|-------|----------|------|
| L1 系统上下文 | 始终 | WHO 使用系统 + WHAT 外部依赖，关系标注动作+协议 |
| L2 容器 | 始终（新服务/修改边界时） | 每个可部署单元一个方框，标注技术栈和通信协议 |
| L3 组件 | 新建/大幅修改的服务 | 验证依赖方向：Controller→Service→Repository |
| L4 代码 | 仅复杂/关键组件 | 聚焦接口、抽象类、设计模式 |

## ADR 强制规则

1. **一个 ADR 只记一个决策**
2. **必须列出所有考虑过的选项**（包括被否决的）
3. **解释"为什么"** — 理由比决策本身更重要
4. **承认权衡** — 每个决策都有缺点，必须记录
5. **用具体证据**（"基准测试快 3x" 而非 "更快"）
6. **ADR 不可变** — 变更时创建新 ADR 标注 Superseded by
7. **关联上下文** — 引用 PRD 需求、约束、其他 ADR

## 设计审查检查清单

- [ ] 系统上下文图展示所有外部依赖
- [ ] 容器图展示所有可部署单元和通信
- [ ] 新建/修改服务有组件图
- [ ] 所有重大决策有 ADR
- [ ] NFR 已映射到架构模式
- [ ] 无跨层依赖违规
- [ ] 无循环依赖
- [ ] 服务边界清晰且有理由
- [ ] 数据流可端到端追踪
- [ ] 故障模式已识别并有缓解措施
- [ ] 安全考量已在各层体现
- [ ] 可扩展路径明确
- [ ] 运维需求已考虑（日志、监控、告警）

## 验证脚本

- `scripts/layer_violation_check.py` — 检测分层架构违规（Controller→Repository 直连）
- `scripts/circular_dep_check.py` — 检测循环依赖
- `scripts/adr_template.py` — 生成 ADR 模板（`--title "决策标题" --context "背景"）
