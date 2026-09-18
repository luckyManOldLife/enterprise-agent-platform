# Architecture

```text
Client
  -> HTTP API
     -> Supervisor Runtime
        -> Agent Registry
        -> Task State Machine + SQLite
        -> Policy Engine
        -> A2A Agent adapters
           -> Customer Agent (read)
           -> Order Agent (read)
           -> Support Agent (write)
              -> Tool Registry / MCP-like executor
        -> Audit Event Store / SSE
```

## Task lifecycle

`SUBMITTED -> DISPATCHING -> RUNNING -> WAITING_SUB_TASK -> WAITING_APPROVAL -> COMPLETED`

异常状态为 `RETRYING`、`FAILED`、`CANCELLED`、`TIMED_OUT`。所有转换由状态机白名单校验，并写入不可变审计事件。

## Security boundaries

- Supervisor 传递的权限上下文不被子 Agent 直接信任，Support Agent 会再次调用 Policy Engine。
- 写工具要求幂等键，策略不允许自动重试。
- 审计事件只保存裁剪后的输入输出，生产适配器应进一步做 DLP 脱敏。
- 租户 ID、用户 ID、角色和 trace ID 会贯穿任务、子任务、工具调用。
