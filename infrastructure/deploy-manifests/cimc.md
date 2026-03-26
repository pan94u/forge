# CIMC 灯塔工厂部署清单

## 基本信息

| 项目 | 值 |
|------|------|
| 仓库路径 | `/home/deploy/CIMC` |
| Compose 文件 | `docker-compose.yml` |
| 镜像来源 | 本地构建（通过 `docker-build.sh`） |
| 日志文件 | `/opt/forge/infrastructure/DEPLOYMENT_LOGBOOK.md` |

## 部署方式

CIMC 有专用构建脚本，会处理依赖复制和 Docker 构建：

```
git pull --ff-only
bash docker-build.sh
```

`docker-build.sh` 会从 `../Synapse-AI/` 复制 `@synapse/shared` 和 `@synapse/agent-core` 到 `.deps/`，然后执行 `docker compose up --build -d`。

## 前置条件

- `/home/deploy/Synapse-AI/` 必须存在且为最新版本
- 如果 Synapse 有更新，**必须先部署 Synapse 再部署 CIMC**

## 服务清单

| 服务 | 端口 | 说明 |
|------|------|------|
| web | 3888 | Next.js 前端 |
| server | 3889 | Bun + Hono 后端 |
| ope | 5888 / 5889 | 人效系统（前端 + API） |

## 已知陷阱

1. **Dockerfile 中 public 目录**：OPE 的 Dockerfile 曾引用不存在的 `public` 目录，构建失败时检查此问题
2. **端口配置**：Next.js 默认监听 3000，需要在 Dockerfile 中用 `ENV PORT=<实际端口>` 覆盖
3. **OPE 依赖**：OPE 容器需要单独 `bun install` 安装服务端依赖

## 部署后验证

```bash
curl -sf -o /dev/null -w "%{http_code}" http://124.156.192.129:3888
curl -sf -o /dev/null -w "%{http_code}" http://124.156.192.129:3889
docker compose logs --tail 10
```
