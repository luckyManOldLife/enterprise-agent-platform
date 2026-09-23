# agent-platform-server

Java 平台部署单元，组合 REST API、Supervisor、Registry、Task、Policy、Tool 和 Model Gateway。

`PLATFORM_RUNTIME=local` 是默认 JDK 21 本地 HTTP 入口，使用内存任务、审批和 Agent
Registry 适配器验证网络边界。`PLATFORM_RUNTIME=postgres` 使用 JDBC PostgreSQL
适配器，和 `agent-worker` 共享 `agent_task`、`task_outbox`、审批和审计表。

```bash
cd java-platform
./mvnw -pl apps/agent-platform-server -am package -DskipTests

java -cp \
  apps/agent-platform-server/target/classes:\
  platform-api/target/classes:\
  platform-application/target/classes:\
  platform-domain/target/classes:\
  agent-registry/target/classes:\
  task-service/target/classes:\
  policy-engine/target/classes \
  com.xr.agent.server.AgentPlatformApplication
```

PostgreSQL 运行时需要：

```text
PLATFORM_RUNTIME=postgres
POSTGRES_URL=jdbc:postgresql://postgres:5432/agent_platform
POSTGRES_USER=agent_platform_app
POSTGRES_PASSWORD=<injected database password>
MODEL_AGENT_IDS=supervisor
SERVER_HOST=0.0.0.0
```

`MODEL_AGENT_IDS` 是 API 与 Worker 共同使用的模型 Agent 白名单，必须保持一致。任务提交
时的 `idempotencyKey` 以租户为范围持久化，重复请求返回原任务且不会创建额外 Outbox
记录。

所有业务 API 都从请求头读取服务端身份上下文，而不是信任请求体或查询参数：

```text
X-Tenant-Id=tenant-a
X-User-Id=user-a
X-Trace-Id=trace-a
X-Roles=support_operator,approver
```

`X-Tenant-Id` 对查询接口必填；`X-User-Id` 对任务创建和审批决策必填。

服务默认监听 `127.0.0.1:8080`，可通过 `-Dserver.port=8081` 或 `PORT=8081` 覆盖；
容器运行必须设置 `SERVER_HOST=0.0.0.0`。
已实现 `/healthz`、`/api/agents`、`/api/tasks`、`/api/tasks/{taskId}`、
`/api/tasks/{taskId}/events` 和审批查询/决策路由。任务创建会写入 `TASK_CREATED`
审计事件，Worker 状态变化会继续写入 `TASK_RUNNING`、`TASK_SUCCEEDED`、
`TASK_FAILED` 或 `TASK_TIMED_OUT`。
