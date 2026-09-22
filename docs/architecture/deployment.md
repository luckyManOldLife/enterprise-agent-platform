# 部署策略

本地开发使用 Docker Compose；测试环境使用 Kubernetes；生产环境通过 Jenkins 构建、评测、灰度和回滚。Java 平台首期为模块化单体，业务 Agent 和 Python 服务独立部署。

## 现有 Docker Infra 复用

当前机器已有 `docker-stack` 基础设施栈时，平台应用可以挂载到外部
`infra-stack_infra` 网络，并复用 `infra-postgres`、`infra-redis`：

- PostgreSQL：为本项目创建 `agent_platform` 数据库和专用登录用户。
- Redis：使用独立 DB，例如 `3`，不得复用其他应用的 Redis DB。
- 网络：应用 compose 使用外部网络 `infra-stack_infra`，不再暴露本地 `5432/6379`。
- 密钥：真实数据库密码和 Redis 密码只放在本地 `.env.infra`。

项目内模板位于 `deploy/compose/docker-compose.infra.yml` 和
`deploy/compose/.env.infra.example`。
