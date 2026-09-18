# agent-worker

异步任务 Worker。负责 Outbox 事件发布、长任务恢复、超时扫描和重试；写操作不得绕过 Policy Engine。
