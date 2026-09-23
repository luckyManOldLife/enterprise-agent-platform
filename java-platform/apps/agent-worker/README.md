# agent-worker

异步任务 Worker。负责 Outbox 事件发布、长任务恢复、超时扫描和重试；写操作不得绕过 Policy Engine。

当前 `TaskOutboxWorker` 消费 `TASK_CREATED`：

- 校验 Outbox 事件与任务的租户和 `traceId` 上下文一致。
- 对已过截止时间的任务持久化为 `TIMED_OUT`，其他新建任务持久化为 `RUNNING`。
- 使用任务版本号处理并发更新，重复投递在任务不再是 `CREATED` 时幂等完成。
- 失败记录固定错误码并按退避时间重试，不持久化外部异常详情。
- 每次认领都带有租约令牌；租约过期的 `PROCESSING` 记录可回收，旧令牌不能完成新认领。
- 状态变化写入审计流：`TASK_RUNNING`、`TASK_SUCCEEDED`、`TASK_FAILED`、
  `TASK_TIMED_OUT`。
- 模型返回单个 Tool Call 时先查 Tool Registry，再经 Policy Engine；拒绝、审批和执行
  分别写入 Tool 级审计事件。多 Tool Call 暂以 `TOOL_CALL_UNSUPPORTED` 失败关闭。
- 执行中的任务在 Outbox 租约过期后会继续重试；达到 `WORKER_MAX_ATTEMPTS` 后以
  `TASK_EXECUTION_ATTEMPTS_EXHAUSTED` 进入稳定失败态。

## Tool 治理状态

Worker 已具备 Tool Call 的策略门禁：低风险允许调用进入 `ToolExecutorPort`，高/关键风险
创建持久化 Approval 并把任务转为 `WAITING_APPROVAL`，拒绝则以策略码失败。写工具幂等键
由平台按 `taskId:toolId` 生成，不接受模型提供的幂等键作为授权依据。

当前默认 Tool Registry 是本地 seed，覆盖订单/客户查询和售后任务创建；默认 executor
明确 fail-closed，返回 `TOOL_EXECUTOR_UNAVAILABLE`，不会伪造业务执行成功。真正接入 MCP
前，低风险 Tool Call 会失败关闭。

此阶段还没有把 Tool 的输入 schema 发送给 CLIProxyAPI，因此不应认为模型 Tool Calling
已端到端启用；也尚未把审批通过事件接回 Worker 以恢复并执行原始 Tool Call。审批请求会
保存，但批准后的任务暂时停留在 `WAITING_APPROVAL`，需要后续恢复流程完成闭环。

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
WORKER_MAX_ATTEMPTS=3
WORKER_APPROVAL_TTL_SECONDS=14400
MODEL_AGENT_IDS=supervisor
```

`MODEL_AGENT_IDS` 是 API 与 Worker 共用的逗号分隔模型 Agent seed。Worker 启动后会把
这些 Agent upsert 到 PostgreSQL `agent_definition`，执行时再从持久化 registry 查询
可用 Agent；未知或未授权的任务会以 `AGENT_UNAVAILABLE` 结束。当前 seed Agent 使用
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
