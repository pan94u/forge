# my-web 部署清单

## 基本信息

| 项目 | 值 |
|------|------|
| 仓库路径 | `/home/deploy/my-web` |
| Compose 文件 | `docker-compose.production.yml` |
| Env 文件 | `.env.production` |
| 镜像来源 | 本地构建 |
| 日志文件 | `DEPLOYMENT_LOGBOOK.md` |

## 部署方式

本地构建。没有 GHCR 推送流程，在服务器上直接构建镜像。

```
git pull --ff-only
docker compose -f docker-compose.production.yml up --build -d
```

注意 `--build` 参数——每次都需要重新构建镜像。

## 服务清单

| 服务 | 端口（宿主机） | 端口（容器） | 说明 |
|------|--------------|------------|------|
| web | 3000 | 3000 | Next.js standalone |

## 已知陷阱

1. **NEXT_PUBLIC_* 是构建时变量**: 运行时设置无效，需要在 `docker compose up --build` 时通过 env 传入。如果改了 `NEXT_PUBLIC_*`，必须重新构建
2. **NEXTAUTH_SECRET 优先级**: 不要在 docker-compose.yml 的 `environment` 中重复定义 `.env.production` 中已有的变量，否则空值会覆盖 env_file 的值
3. **node_modules 缓存**: 如果 `package.json` 变了但构建没拉新依赖，加 `--no-cache` 重建：`docker compose build --no-cache web`

## 部署后验证

```bash
curl -sf -o /dev/null -w "%{http_code}" http://localhost:3000
docker logs my-web --tail 20
```
