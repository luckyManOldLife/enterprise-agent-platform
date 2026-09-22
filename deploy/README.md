# Deployment

部署目录预留 Docker Compose、Kubernetes、Nacos、Prometheus/Grafana 和 Jenkins Pipeline。
正式部署前先完成版本矩阵和密钥管理。

当前 Docker Compose 支持两种路径：

- `compose/docker-compose.yml`：项目自带的隔离本地栈，独立启动 PostgreSQL 和 Redis。
- `compose/docker-compose.infra.yml`：接入现有 `docker-stack` 的 `infra-stack_infra`
  网络，复用 `infra-postgres` 和 `infra-redis`，运行平台 API 和 Worker 容器。

API 与 Worker 分别构建并共享 PostgreSQL 任务、Outbox、审批和审计记录。Worker 使用
Outbox 认领任务并调用 CLIProxyAPI；两者镜像必须先执行：

```bash
cd java-platform
./mvnw -pl apps/agent-platform-server,apps/agent-worker -am package -DskipTests
docker build -f ../deploy/docker/agent-platform.Dockerfile -t enterprise-agent-platform:local .
docker build -f ../deploy/docker/agent-worker.Dockerfile -t enterprise-agent-worker:local .
```

现有 infra 栈复用时必须使用独立数据库、独立数据库用户和独立 Redis DB，真实密码只写入
本地 `.env.infra`，不得提交到仓库。

CLIProxyAPI 容器在同一 `infra-stack_infra` 网络中以 `infra-cli-proxy-api:8317` 提供
服务。Worker 应使用容器服务名，不能依赖发布到宿主机 `127.0.0.1:8317` 的端口。
