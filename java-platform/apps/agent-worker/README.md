# agent-worker

异步任务 Worker。负责 Outbox 事件发布、长任务恢复、超时扫描和重试；写操作不得绕过 Policy Engine。

当前 `TaskOutboxWorker` 消费 `TASK_CREATED`：

- 校验 Outbox 事件与任务的租户和 `traceId` 上下文一致。
- 对已过截止时间的任务持久化为 `TIMED_OUT`，其他新建任务持久化为 `RUNNING`。
- 使用任务版本号处理并发更新，重复投递在任务不再是 `CREATED` 时幂等完成。
- 失败记录固定错误码并按退避时间重试，不持久化外部异常详情。
- 每次认领都带有租约令牌；租约过期的 `PROCESSING` 记录可回收，旧令牌不能完成新认领。
