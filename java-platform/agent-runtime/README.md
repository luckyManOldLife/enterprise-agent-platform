# agent-runtime

Supervisor 编排、子任务创建、上下文继承、超时和恢复入口。

## 当前实现

- `SupervisorRuntime`：顺序执行声明式 Agent 步骤，创建继承租户、用户和 trace 的子任务。
- `AgentInvokerPort`：应用层 Agent 调用端口，后续由 A2A/HTTP/MQ 适配器实现。
- 不可用 Agent、空计划和子 Agent 失败会使父任务进入 `FAILED`，并保留错误码。

## 边界

运行时不直接依赖 Spring、前端、Python 服务或具体通信协议。跨进程调用只能通过
应用层端口和 A2A/MCP 契约接入。
