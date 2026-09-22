# Deployment

部署目录预留 Docker Compose、Kubernetes、Nacos、Prometheus/Grafana 和 Jenkins Pipeline。
正式部署前先完成版本矩阵和密钥管理。

当前 Docker Compose 支持两种路径：

- `compose/docker-compose.yml`：项目自带的隔离本地栈，独立启动 PostgreSQL 和 Redis。
- `compose/docker-compose.infra.yml`：接入现有 `docker-stack` 的 `infra-stack_infra`
  网络，复用 `infra-postgres` 和 `infra-redis`，只运行平台应用容器。

现有 infra 栈复用时必须使用独立数据库、独立数据库用户和独立 Redis DB，真实密码只写入
本地 `.env.infra`，不得提交到仓库。
