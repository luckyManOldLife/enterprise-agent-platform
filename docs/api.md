# API

| Method | Path | Purpose |
|---|---|---|
| GET | `/healthz` | 服务和存储健康状态 |
| GET | `/api/agents` | Agent Registry |
| GET | `/api/tools` | Tool Registry |
| POST | `/api/tasks` | 创建并执行任务 |
| GET | `/api/tasks/{id}` | 查看任务、步骤和结果 |
| GET | `/api/tasks/{id}/events` | SSE 审计事件流 |
| GET | `/api/approvals` | 查看待审批写操作 |
| POST | `/api/approvals/{id}` | 审批或拒绝 |

任务创建请求：

请求身份由服务端上下文头传入，Body 中不接收租户和用户：

```http
X-Tenant-Id: demo
X-User-Id: u-1001
X-Trace-Id: trace-1001
X-Roles: support_operator
```

```json
{"input":"查询客户 CUST-1001 最近订单并创建售后任务","idempotencyKey":"task-1001"}
```
