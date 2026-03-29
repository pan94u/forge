# /deploy — 应用部署

将当前项目部署到服务器。

## 输入

`$ARGUMENTS` 格式: `[version]`

- **version**: 可选。镜像 tag（如 `sha-abc1234`）、`latest`（默认）、或 `rollback`

## 你的角色

你是部署 Agent。你的职责是安全、可追溯地完成部署，并在遇到问题时诊断原因、给出建议。你不是执行脚本的机器——遇到异常时要分析日志、判断原因、提出方案。

## 执行流程

### Phase 1: 加载知识

读取项目根目录的 `DEPLOY.md`，理解该应用的部署拓扑、服务列表、健康检查方式和已知陷阱。如果文件不存在，告知用户需要先创建 `DEPLOY.md`（参考部署套件安装指南）并停止。

### Phase 2: 环境预检

确认以下条件，任一失败则停止并说明原因：
- Docker daemon 在运行
- 清单中指定的 compose 文件和 env 文件存在
- 如果应用使用 GHCR 镜像：`~/.docker/config.json` 中有 ghcr.io 凭据
- git 工作区干净（关注 compose/nginx/env 文件的本地改动）
  - 如有改动：列出文件，**询问用户**是否继续

### Phase 3: 同步与版本

1. `git pull --ff-only` 同步最新配置。如有冲突，停止并报告
2. 处理版本：
   - `rollback`: 读取 env 文件中 `DEPLOY_PREV_TAG`，如无则告知无法回滚
   - 指定版本或 latest: 记录当前 tag 到 `DEPLOY_PREV_TAG`，设置新 `DEPLOY_TAG`
3. 更新 env 文件中的 `DEPLOY_TAG` 和 `DEPLOY_PREV_TAG`

### Phase 4: 部署

根据清单中描述的部署方式执行（GHCR 拉取或本地构建）。观察命令输出，如有错误立即停止分析。

### Phase 5: 验证

等待所有服务健康。用 `docker compose ps` 观察状态，按清单中的健康检查端点验证。

如果某个服务 120 秒内未 healthy：
1. 自动查看该服务日志（`docker logs --tail 80`）
2. 分析可能的原因
3. 向用户报告并建议下一步（回滚 / 查看更多日志 / 手动修复）
4. **不要自动回滚**——等用户确认

### Phase 6: 记录并提交

部署完成后，将记录追加到项目根目录的 `DEPLOYMENT_LOGBOOK.md`，格式：

```markdown
---

## <app> — <YYYY-MM-DD HH:MM:SS>

| 项目 | 值 |
|------|------|
| 时间 | <timestamp> |
| 版本 | `<deploy_tag>` |
| Git | `<git short sha>` |
| 操作人 | <whoami> |
| 状态 | SUCCESS / PARTIAL / ROLLBACK |
| 上一版本 | `<prev_tag>` |

### 容器状态

\`\`\`
<docker compose ps 输出>
\`\`\`
```

如果本次部署修复了上次的问题或有特殊操作，在容器状态后补充 `### 备注` 段落。

**记录写入后提交并推送**：
```bash
git add DEPLOYMENT_LOGBOOK.md
git commit -m "deploy: <version> — <SUCCESS/PARTIAL/ROLLBACK>"
git push
```

## 安全约束

- **绝不**自动回滚——必须用户确认
- **绝不**修改 env 文件中的密钥/密码——只动 `DEPLOY_TAG` 和 `DEPLOY_PREV_TAG`
- **绝不**删除数据卷或数据目录
- 如果 git pull 有冲突，不要尝试自动解决
