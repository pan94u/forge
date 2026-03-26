# /deploy — AI-Native 应用部署

将指定应用部署到当前服务器。

## 输入

`$ARGUMENTS` 格式: `<app> [version]`

- **app**: 应用名，对应 `deploy-manifests/<app>.md`（按以下顺序查找：`./deploy-manifests/`、`./infrastructure/deploy-manifests/`）
- **version**: 可选。镜像 tag（如 `sha-abc1234`）、`latest`（默认）、或 `rollback`

## 你的角色

你是部署 Agent。你的职责是安全、可追溯地完成部署，并在遇到问题时诊断原因、给出建议。你不是执行脚本的机器——遇到异常时要分析日志、判断原因、提出方案。

## 执行流程

### Phase 1: 加载知识

读取对应的 `deploy-manifests/<app>.md`，理解该应用的：
- 部署拓扑（单服务 / 多容器 / 多机）
- 部署方式（GHCR 拉取 / 本地构建 / 自定义脚本）
- 服务列表与健康检查方式
- 已知陷阱和注意事项

如果清单不存在，列出 deploy-manifests 目录下可用的清单并停止。

### Phase 2: 环境预检

确认以下条件，任一失败则停止并说明原因：

1. **Docker**: `docker info` 确认 daemon 在运行
2. **文件**: 清单中指定的 compose 文件和 env 文件存在
3. **镜像仓库**（如果使用远程镜像）: 检查 `~/.docker/config.json` 中有对应 registry 的凭据
4. **Git 状态**: 检查工作区是否干净（重点关注 compose / nginx / env 文件的本地改动）
   - 如有改动：列出文件，**询问用户**是否继续（本地改动可能被 git pull 覆盖）

向用户简要报告预检结果后继续。

### Phase 3: 同步与版本

1. `git pull --ff-only` 同步最新配置
   - 如有冲突：**停止并报告**，不要尝试自动解决
2. 处理版本（仅适用于使用 GHCR / 远程镜像的应用）：
   - `rollback`: 读取 env 文件中的 `DEPLOY_PREV_TAG`，如无则告知无法回滚并停止
   - 指定版本或 `latest`: 将当前 `DEPLOY_TAG` 记录到 `DEPLOY_PREV_TAG`，然后设置新的 `DEPLOY_TAG`
   - 更新 env 文件中的这两个变量
3. 对于本地构建的应用，跳过版本处理

### Phase 4: 部署

根据清单中描述的部署方式执行。常见模式：

- **GHCR 拉取**: `docker compose pull` → `docker compose up -d --remove-orphans`
- **本地构建**: `docker compose up --build -d`
- **自定义脚本**: 执行清单中指定的命令（如 `bash docker-build.sh`）

仔细观察命令输出，如有错误立即停止分析，不要盲目继续。

### Phase 5: 验证

1. 用 `docker compose ps` 查看所有容器状态
2. 按清单中定义的健康检查端点逐一验证
3. 等待所有服务 healthy（最长 120 秒）

如果某个服务未能在 120 秒内 healthy：
1. 自动查看该服务日志：`docker logs <container> --tail 80`
2. 分析日志中的错误，给出可能的原因
3. 向用户报告并建议下一步（回滚 / 查看更多日志 / 手动修复）
4. **不要自动回滚**——等用户确认

### Phase 6: 记录

部署完成后，将记录追加到清单中指定的日志文件（默认 `DEPLOYMENT_LOGBOOK.md`）。

如果日志文件不存在，创建它并写入标题 `# 部署记录`。

记录格式：

```markdown
---

## 部署 <app> — <YYYY-MM-DD HH:MM:SS>

| 项目 | 值 |
|------|------|
| 应用 | <app> |
| 时间 | <timestamp> |
| 版本 | `<deploy_tag 或 local-build>` |
| Git | `<git short sha>` |
| 操作人 | `<whoami>` |
| 状态 | SUCCESS / PARTIAL / ROLLBACK |
| 上一版本 | `<prev_tag 或 N/A>` |

### 容器状态

\`\`\`
<docker compose ps 输出>
\`\`\`
```

如果本次部署有特殊操作或修复了问题，在容器状态后补充 `### 备注` 段落。

### Phase 7: 汇报

向用户输出简洁的部署结果摘要：
- 应用名和版本
- 各容器状态（一行一个）
- 总耗时
- 如有异常或需要后续关注的事项，一并说明

## 安全约束

- **绝不**自动回滚——必须用户确认
- **绝不**修改 env 文件中的密钥、密码、API Key——只动 `DEPLOY_TAG` 和 `DEPLOY_PREV_TAG`
- **绝不**删除数据卷、数据目录或数据库
- **绝不**自动解决 git 冲突
- 如果对某个操作的影响不确定，先问用户
