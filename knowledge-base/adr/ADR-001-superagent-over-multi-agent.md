# ADR-001: SuperAgent 优于 Multi-Agent

## 状态
已接受 (2026-02-16)

## 背景
在设计 Forge 平台的 AI 架构时，需要决定使用单一智能体（SuperAgent）还是多个专门智能体（Multi-Agent）方案。

## 决策
采用 SuperAgent 模式：一个统一的智能体通过动态加载 Skill Profile 来切换角色，而非为每个交付阶段创建独立的 Agent。

## 理由
1. **上下文连续性**：单一 Agent 在整个交付流程中保持完整上下文，无需跨 Agent 传递状态
2. **Skill 复用**：Foundation Skills（如 kotlin-conventions, spring-boot-patterns）在所有阶段共享
3. **简化部署**：无需管理多个 Agent 的生命周期、路由和协调
4. **一致性**：用户始终与同一个 Agent 交互，体验一致

## 替代方案
- Multi-Agent：每个交付阶段独立 Agent，通过编排层协调
- 混合模式：核心 Agent + 专门子 Agent

## 后果
- 需要设计高效的 Profile 路由机制（已通过 ProfileRouter 实现）
- System Prompt 可能很大（通过 Prompt Caching 解决，成本降低 90%）
- 需要防止 Profile 切换时的知识泄漏（通过 SkillLoader 的隔离加载机制解决）
