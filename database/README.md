# Database

数据库迁移由 Java 应用统一管理，生产变更必须关联 Yearning 工单。任务主状态、Agent/Tool 注册、审批、Outbox 和审计事件使用 PostgreSQL；Redis 只保存缓存、锁和短期游标。
