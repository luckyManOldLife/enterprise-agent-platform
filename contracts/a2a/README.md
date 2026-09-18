# A2A Contract

A2A 是 Agent 间任务委派边界。每次调用必须携带 `taskId`、`parentTaskId`、`traceId`、`tenantId`、`userId`、`deadline` 和协议版本。子 Agent 必须重新验证关键权限，不得信任 Supervisor 的授权结论。
