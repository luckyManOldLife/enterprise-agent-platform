# Database

数据库迁移由 Java 应用统一管理，生产变更必须关联 Yearning 工单。任务主状态、Agent/Tool 注册、审批、Outbox 和审计事件使用 PostgreSQL；Redis 只保存缓存、锁和短期游标。

`schemas/V1__platform_core.sql` 定义平台基础表，`migrations/V2__task_outbox.sql`
增加任务生命周期 Outbox，`migrations/V4__task_outbox_claim_lease.sql` 增加 Worker
认领令牌和恢复索引。PostgreSQL 适配器必须在同一事务中提交任务记录和对应 Outbox
事件，并使用租户范围内的幂等键防止重复创建。重复提交相同 `(tenant_id,
idempotency_key)` 时必须返回已有任务，且不得创建额外 Outbox 记录。
