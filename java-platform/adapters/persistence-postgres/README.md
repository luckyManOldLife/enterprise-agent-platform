# adapter-persistence-postgres

JDBC PostgreSQL adapter for platform persistence.

## Current scope

- `TaskPersistencePort`: atomic `agent_task` + `task_outbox` insert.
- `OutboxStorePort`: claim, publish and retry state transitions using
  `FOR UPDATE SKIP LOCKED`.
- `ApprovalRepositoryPort`: persist, query and recover approval requests.
- `TaskEventStorePort`: append and list audit events for SSE streams.

The adapter depends on `javax.sql.DataSource` and a caller-provided
`JsonMapCodec`; it does not depend on Spring, JPA or a concrete JSON library.
