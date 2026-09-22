# task-service

任务持久化与生命周期事件模块。

## 当前实现

- `TaskPersistencePort`：应用层定义的原子边界，要求任务记录和 Outbox 事件同事务提交，
  并通过 `agent_task.version` 执行任务状态更新的乐观锁校验。
- `OutboxRecord`：Pending、Processing、Published、Failed 状态和重试次数。
- `InMemoryTaskPersistence`：测试和本地开发适配器，不用于生产。

## 生产约束

PostgreSQL 适配器必须实现同样的原子提交语义，使用
`database/migrations/V2__task_outbox.sql` 的 `task_outbox` 表。Outbox 消费者必须
支持批量 claim、发布成功确认、失败退避和重复消息幂等。业务 Agent 不得直接写
`agent_task` 或 `task_outbox`。
