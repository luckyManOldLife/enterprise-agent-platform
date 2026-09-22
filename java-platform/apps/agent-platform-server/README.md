# agent-platform-server

Java 平台首期部署单元，组合 REST API、Supervisor、Registry、Task、Policy、Tool 和 Model Gateway。首期采用可观测的模块化单体。

当前提供 JDK 21 本地 HTTP 运行入口，用于验证 Platform API 的网络边界。默认使用
内存任务、审批和 Agent Registry 适配器，不连接 PostgreSQL、业务 Agent 或模型供应商，
因此不能作为生产部署单元。

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

服务默认监听 `127.0.0.1:8080`，可通过 `-Dserver.port=8081` 或 `PORT=8081` 覆盖。
已实现 `/healthz`、`/api/agents`、`/api/tasks`、`/api/tasks/{taskId}`、
`/api/tasks/{taskId}/events` 和审批查询/决策路由。
