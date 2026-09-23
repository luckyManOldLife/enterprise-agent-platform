# adapter-persistence-postgres

JDBC PostgreSQL adapter for platform persistence.

## Current scope

- `TaskPersistencePort`: atomic `agent_task` + `task_outbox` insert and
  tenant-scoped idempotent task creation and optimistic task updates through
  `agent_task.version`.
- `OutboxStorePort`: claim, publish and retry state transitions using
  `FOR UPDATE SKIP LOCKED`, lease recovery and claim-token ownership checks.
- `ApprovalRepositoryPort`: persist, query and recover approval requests.
- `TaskEventStorePort`: append and list audit events for SSE streams.
- `AgentRegistryPort`: upsert and query `agent_definition` records with
  tenant-scoped availability.

The adapter depends on `javax.sql.DataSource` and a caller-provided
`JsonMapCodec`; `JdkJsonMapCodec` is available for JDK-only deployments. It
does not depend on Spring, JPA or a concrete JSON library.

`DriverManagerDataSource` is available for small standalone API and Worker
processes. Production deployments with sustained concurrency should replace it
with a managed connection pool.
