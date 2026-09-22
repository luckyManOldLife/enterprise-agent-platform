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

```json
{"tenantId":"demo","userId":"u-1001","roles":["support_operator"],"input":"查询客户 CUST-1001 最近订单并创建售后任务"}
```
