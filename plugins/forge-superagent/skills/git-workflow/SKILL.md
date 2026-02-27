---
name: git-workflow
description: "Git 工作流 — 版本控制、提交规范、分支管理、.gitignore 治理"
stage: development
type: delivery-skill
version: "1.0"
scope: platform
category: delivery
tags: [git, commit, push, pull, branch, gitignore, version-control]
---

# Git 工作流技能

## 1. 操作原则

- **用户确认机制**：在执行 `workspace_git_commit` / `workspace_git_push` / `workspace_git_pull` 前，系统会自动向用户弹出确认卡。如用户点击"取消"，工具返回"用户已取消"，应立即停止该 git 操作并告知用户，不要重试。
- **提交前读 diff**：使用 `workspace_git_diff` 查看改动，确认无误再提交
- **Commit message 规范**：格式为 `type: 描述`，type 可选：
  - `feat`: 新功能
  - `fix`: Bug 修复
  - `docs`: 文档更新
  - `refactor`: 代码重构
  - `test`: 测试相关
  - `chore`: 构建/配置变更
- **不提交敏感/临时文件**：`.env`、日志文件、IDE 配置、编译产物
- **每次提交只包含逻辑相关的改动**，避免大杂烩提交

## 2. 工作流模型

```
main/master
  └── feature/xxx (从 main 切出)
        ├── 多次 commit
        └── PR → code review → merge to main
```

- **禁止直推 main/master**：所有变更通过 feature branch + PR
- feature branch 命名：`feature/描述`、`bugfix/描述`、`hotfix/描述`
- 保持 branch 生命周期短，尽早合并

## 3. Commit 三步骤

执行提交时，**必须按顺序**：

1. `workspace_git_status` — 查看当前分支和修改文件
2. `workspace_git_diff` — 确认具体改动内容
3. `workspace_git_add` — 精确暂存需要提交的文件
4. `workspace_git_commit` — 提交（自动附加 `[Forge-Agent]` 标注）

> **示例消息**："帮我提交登录功能的实现" → 执行以上 4 步，commit message 为 `feat: 实现用户登录功能 [Forge-Agent]`

## 4. .gitignore 治理

初始化 `.gitignore` 时，根据项目类型选择模板。

**常见忽略类别**：
- **Secrets**：`.env`, `*.key`, `*.pem`, `credentials.json`
- **Build artifacts**：`build/`, `dist/`, `target/`, `*.class`, `*.jar`
- **IDE files**：`.idea/`, `.vscode/`, `*.iml`
- **OS files**：`.DS_Store`, `Thumbs.db`
- **Dependencies**：`node_modules/`, `.gradle/`
- **Logs**：`*.log`, `logs/`

**Kotlin/Spring Boot 项目模板**：
```gitignore
# Build
build/
*.jar
*.war

# IDE
.idea/
*.iml
.gradle/

# Logs
*.log
logs/

# Env
.env
*.env.local

# OS
.DS_Store
Thumbs.db
```

**Node.js/TypeScript 项目模板**：
```gitignore
# Dependencies
node_modules/

# Build
dist/
.next/
out/

# Env
.env
.env.local
.env.*.local

# IDE
.vscode/
.idea/

# OS
.DS_Store
Thumbs.db

# Logs
*.log
npm-debug.log*
```

## 5. 分支命名约定

| 类型 | 格式 | 示例 |
|------|------|------|
| 新功能 | `feature/描述` | `feature/user-login` |
| Bug 修复 | `bugfix/描述` | `bugfix/fix-null-pointer` |
| 紧急修复 | `hotfix/描述` | `hotfix/security-patch` |
| 发布准备 | `release/版本` | `release/v1.2.0` |

## 6. 常用场景 SOP

### 场景 A：开发新功能
```
1. workspace_git_status     # 确认当前在 main，工作区干净
2. workspace_git_branch name="feature/xxx"   # 创建并切换到新分支
3. [编写代码...]
4. workspace_git_diff       # 查看改动
5. workspace_git_add paths=["src/..."]       # 暂存文件
6. workspace_git_commit message="feat: xxx"  # 提交
```

### 场景 B：修复 Bug
```
1. workspace_git_status
2. workspace_git_diff
3. workspace_git_add all=true
4. workspace_git_commit message="fix: 修复 xxx 问题"
```

### 场景 C：同步远程代码
```
1. workspace_git_pull rebase=true    # 拉取并 rebase
2. workspace_git_status              # 确认状态
```

### 场景 D：初始化 .gitignore
```
1. workspace_list_files              # 查看项目类型
2. workspace_write_file path=".gitignore" content=<模板内容>
3. workspace_git_add paths=[".gitignore"]
4. workspace_git_commit message="chore: 初始化 .gitignore"
```

## 7. 可用 MCP 工具清单

| 工具 | 说明 |
|------|------|
| `workspace_git_status` | 查看当前分支 + 修改文件列表 |
| `workspace_git_diff` | 查看 staged + unstaged 改动 |
| `workspace_git_add` | 暂存文件（精确或全量） |
| `workspace_git_commit` | 提交暂存的改动 |
| `workspace_git_push` | 推送到远程（main/master 时给出警告） |
| `workspace_git_pull` | 拉取远程变更（默认 --rebase） |
| `workspace_git_branch` | 创建新分支或列出所有分支 |
