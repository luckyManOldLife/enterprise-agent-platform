# agent-worker

异步任务 Worker。负责 Outbox 事件发布、长任务恢复、超时扫描和重试；写操作不得绕过 Policy Engine。

当前 `TaskOutboxWorker` 消费 `TASK_CREATED`：

- 校验 Outbox 事件与任务的租户和 `traceId` 上下文一致。
- 对已过截止时间的任务持久化为 `TIMED_OUT`，其他新建任务持久化为 `RUNNING`。
- 使用任务版本号处理并发更新，重复投递在任务不再是 `CREATED` 时幂等完成。
- 失败记录固定错误码并按退避时间重试，不持久化外部异常详情。
- 每次认领都带有租约令牌；租约过期的 `PROCESSING` 记录可回收，旧令牌不能完成新认领。

## CLIProxyAPI 模型调用

`CliProxyApiWorkerConfiguration` 将已登记的 Agent 接入本机 OpenAI 兼容的 CLIProxyAPI。
Worker 将任务先持久化为 `RUNNING`，调用 Agent 后再通过乐观锁持久化为 `SUCCEEDED` 或
`FAILED`。模型内容、模型 ID 与 token 用量会作为任务输出保存；代理错误正文和密钥不会
被保存。

运行环境变量：

```text
CLIPROXY_API_BASE_URL=http://127.0.0.1:8317/v1
CLIPROXY_API_KEY=<CLIProxyAPI API key>
CLIPROXY_MODEL=gpt-5.5
CLIPROXY_TIMEOUT_SECONDS=30
```

`CLIPROXY_API_KEY` 必须由部署系统注入，不能提交到 `.env`、代码或数据库。
