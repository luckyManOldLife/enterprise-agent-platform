# agent-worker

异步任务 Worker。负责 Outbox 事件发布、长任务恢复、超时扫描和重试；写操作不得绕过 Policy Engine。

当前 `TaskOutboxWorker` 消费 `TASK_CREATED`：

- 校验 Outbox 事件与任务的租户和 `traceId` 上下文一致。
- 对已过截止时间的任务持久化为 `TIMED_OUT`，其他新建任务持久化为 `RUNNING`。
- 使用任务版本号处理并发更新，重复投递在任务不再是 `CREATED` 时幂等完成。
- 失败记录固定错误码并按退避时间重试，不持久化外部异常详情。
- 每次认领都带有租约令牌；租约过期的 `PROCESSING` 记录可回收，旧令牌不能完成新认领。

## CLIProxyAPI 模型调用

`CliProxyApiWorkerConfiguration` 将已登记的 Agent 接入本机 OpenAI 兼容的 CLIProxyAPI。
Worker 将任务先持久化为 `RUNNING`，调用 Agent 后再通过乐观锁持久化为 `SUCCEEDED` 或
`FAILED`。模型内容、模型 ID 与 token 用量会作为任务输出保存；代理错误正文和密钥不会
被保存。

运行环境变量：

```text
CLIPROXY_API_BASE_URL=http://127.0.0.1:8317/v1
CLIPROXY_API_KEY=<CLIProxyAPI API key>
CLIPROXY_MODEL=gpt-5.5
CLIPROXY_TIMEOUT_SECONDS=30
```

`CLIPROXY_API_KEY` 必须由部署系统注入，不能提交到 `.env`、代码或数据库。

## PostgreSQL polling runtime

`AgentWorkerApplication` 是独立 Worker 进程。它使用 JDBC PostgreSQL 适配器认领
`task_outbox` 记录，并将真实 Agent 调用的结果通过乐观锁写回 `agent_task`。

除上述 CLIProxyAPI 变量外，运行时还需要：

```text
POSTGRES_URL=jdbc:postgresql://postgres:5432/agent_platform
POSTGRES_USER=agent_worker
POSTGRES_PASSWORD=<injected database password>
WORKER_BATCH_SIZE=10
WORKER_POLL_INTERVAL_MILLIS=1000
MODEL_AGENT_IDS=supervisor
```

`MODEL_AGENT_IDS` 是 API 与 Worker 共用的逗号分隔模型 Agent 白名单。Worker 只会执行名单中
可用的 Agent；未知或未授权的任务会以 `AGENT_UNAVAILABLE` 结束。当前静态 Agent 使用
`model://cliproxyapi` 端点和 `task.execute` 能力。

构建 Worker 镜像前先准备其运行时依赖：

```bash
cd java-platform
./mvnw -pl apps/agent-worker -am package -DskipTests
docker build -f ../deploy/docker/agent-worker.Dockerfile -t enterprise-agent-worker:local .
```

本机 CLIProxyAPI 位于外部 Docker 网络 `infra-stack_infra` 时，隔离本地栈通过该网络
使用 `http://infra-cli-proxy-api:8317/v1` 访问代理：

```bash
cd deploy/compose
CLIPROXY_API_KEY=... docker compose --profile worker up -d agent-worker
```

不要改为 `host.docker.internal`：当前 CLIProxyAPI 仅发布到宿主机 loopback，普通容器
不能可靠访问该地址。
