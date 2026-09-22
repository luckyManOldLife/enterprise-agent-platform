package com.xr.agent.persistence.postgres;

import com.xr.agent.application.port.out.ApprovalRepositoryPort;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.application.port.out.TaskEventStorePort.TaskEvent;
import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.Approval;
import com.xr.agent.domain.model.ApprovalStatus;
import com.xr.agent.domain.model.TaskStatus;
import com.xr.agent.domain.model.TaskVersionConflictException;
import com.xr.agent.task.OutboxRecord;
import com.xr.agent.task.OutboxClaimLostException;
import com.xr.agent.task.OutboxStatus;
import com.xr.agent.task.OutboxStorePort;

import javax.sql.DataSource;
import java.time.Duration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPostgresPersistenceAdapter
        implements TaskPersistencePort, OutboxStorePort, ApprovalRepositoryPort, TaskEventStorePort {

    private final DataSource dataSource;
    private final JsonMapCodec jsonCodec;

    public JdbcPostgresPersistenceAdapter(DataSource dataSource, JsonMapCodec jsonCodec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
    }

    @Override
    public AgentTask saveWithOutbox(
            AgentTask task,
            TaskOutboxMessage event,
            String idempotencyKey) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(event, "event");
        if (!task.taskId().equals(event.taskId())) {
            throw new IllegalArgumentException("Task and outbox aggregate ids must match");
        }

        return inTransaction(connection -> {
            if (!insertTask(connection, task, normalizeIdempotencyKey(idempotencyKey))) {
                return findByIdempotencyKey(connection, task.tenantId(), idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException("Idempotent task was not found"));
            }
            insertOutbox(connection, event);
            return task;
        });
    }

    @Override
    public Optional<AgentTask> findById(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT task_id, parent_task_id, tenant_id, user_id, trace_id, conversation_id,
                           source_agent, target_agent, status, input, output, error_code,
                           retry_count, deadline, started_at, completed_at, version
                      FROM agent_task
                     WHERE task_id = ?
                    """)) {
                statement.setObject(1, taskId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapTask(rows));
                }
            }
        });
    }

    @Override
    public AgentTask update(AgentTask task, long expectedVersion) {
        Objects.requireNonNull(task, "task");
        if (task.version() != expectedVersion) {
            throw new IllegalArgumentException("Task version does not match expectedVersion");
        }

        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE agent_task
                       SET status = ?,
                           output = ?::jsonb,
                           error_code = ?,
                           retry_count = ?,
                           deadline = ?,
                           started_at = ?,
                           completed_at = ?,
                           version = version + 1
                     WHERE task_id = ?
                       AND tenant_id = ?
                       AND version = ?
                    """)) {
                statement.setString(1, task.status().name());
                statement.setString(2, task.output() == null ? null : jsonCodec.toJson(task.output()));
                statement.setString(3, task.errorCode());
                statement.setInt(4, task.retryCount());
                setNullableInstant(statement, 5, task.deadline());
                setNullableInstant(statement, 6, task.startedAt());
                setNullableInstant(statement, 7, task.completedAt());
                statement.setObject(8, task.taskId());
                statement.setString(9, task.tenantId());
                statement.setLong(10, expectedVersion);
                if (statement.executeUpdate() != 1) {
                    throw new TaskVersionConflictException(task.taskId(), expectedVersion);
                }
            }
            return task.copyWithVersion(expectedVersion + 1);
        });
    }

    @Override
    public void append(TaskOutboxMessage event) {
        Objects.requireNonNull(event, "event");
        inTransaction(connection -> {
            insertOutbox(connection, event);
            return null;
        });
    }

    @Override
    public List<OutboxRecord> claim(int limit, Instant now, Duration processingLease) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Objects.requireNonNull(now, "now");
        if (processingLease == null || processingLease.isZero() || processingLease.isNegative()) {
            throw new IllegalArgumentException("processingLease must be positive");
        }

        return inTransaction(connection -> {
            List<OutboxRecord> claimed = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT event_id, task_id, tenant_id, trace_id, event_type, payload,
                           attempts, available_at, claimed_at, claim_token, published_at, last_error, created_at
                      FROM task_outbox
                     WHERE (status IN ('PENDING', 'FAILED') AND available_at <= ?)
                        OR (status = 'PROCESSING' AND claimed_at <= ?)
                     ORDER BY created_at
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now.minus(processingLease)));
                statement.setInt(3, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        UUID eventId = rows.getObject("event_id", UUID.class);
                        int nextAttempts = rows.getInt("attempts") + 1;
                        UUID claimToken = UUID.randomUUID();
                        updateOutboxClaim(connection, eventId, nextAttempts, now, claimToken);
                        claimed.add(mapOutbox(
                                rows, OutboxStatus.PROCESSING, nextAttempts, now, claimToken));
                    }
                }
            }
            return List.copyOf(claimed);
        });
    }

    @Override
    public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(publishedAt, "publishedAt");
        updateOutboxStatus("""
                UPDATE task_outbox
                   SET status = 'PUBLISHED', published_at = ?, last_error = NULL,
                       claimed_at = NULL, claim_token = NULL
                 WHERE event_id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, statement -> {
            statement.setTimestamp(1, Timestamp.from(publishedAt));
            statement.setObject(2, eventId);
            statement.setObject(3, claimToken);
        }, eventId);
    }

    @Override
    public void markFailed(UUID eventId, UUID claimToken, String error, Instant nextAttemptAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(claimToken, "claimToken");
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        updateOutboxStatus("""
                UPDATE task_outbox
                   SET status = 'FAILED', last_error = ?, available_at = ?,
                       claimed_at = NULL, claim_token = NULL
                 WHERE event_id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, statement -> {
            statement.setString(1, error);
            statement.setTimestamp(2, Timestamp.from(nextAttemptAt));
            statement.setObject(3, eventId);
            statement.setObject(4, claimToken);
        }, eventId);
    }

    @Override
    public Approval save(Approval approval) {
        Objects.requireNonNull(approval, "approval");
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO approval_request (
                        approval_id, task_id, tenant_id, requested_by, reason, status,
                        expires_at, decided_by, decided_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (approval_id) DO UPDATE SET
                        status = EXCLUDED.status,
                        decided_by = EXCLUDED.decided_by,
                        decided_at = EXCLUDED.decided_at,
                        updated_at = now()
                    """)) {
                statement.setObject(1, approval.approvalId());
                statement.setObject(2, approval.taskId());
                statement.setString(3, approval.tenantId());
                statement.setString(4, approval.requestedBy());
                statement.setString(5, approval.reason());
                statement.setString(6, approval.status().name());
                statement.setTimestamp(7, Timestamp.from(approval.expiresAt()));
                statement.setString(8, approval.decidedBy());
                setNullableInstant(statement, 9, approval.decidedAt());
                statement.executeUpdate();
            }
            return approval;
        });
    }

    @Override
    public Optional<Approval> findApprovalById(UUID approvalId) {
        Objects.requireNonNull(approvalId, "approvalId");
        return query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT approval_id, task_id, tenant_id, requested_by, reason, status,
                           expires_at, decided_by, decided_at
                      FROM approval_request
                     WHERE approval_id = ?
                    """)) {
                statement.setObject(1, approvalId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapApproval(rows));
                }
            }
        });
    }

    @Override
    public List<Approval> findPendingByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return queryApprovals("""
                SELECT approval_id, task_id, tenant_id, requested_by, reason, status,
                       expires_at, decided_by, decided_at
                  FROM approval_request
                 WHERE tenant_id = ? AND status = 'PENDING'
                 ORDER BY expires_at
                """, statement -> statement.setString(1, tenantId));
    }

    @Override
    public List<Approval> findExpiredPending(Instant now) {
        Objects.requireNonNull(now, "now");
        return queryApprovals("""
                SELECT approval_id, task_id, tenant_id, requested_by, reason, status,
                       expires_at, decided_by, decided_at
                  FROM approval_request
                 WHERE status = 'PENDING' AND expires_at <= ?
                 ORDER BY expires_at
                """, statement -> statement.setTimestamp(1, Timestamp.from(now)));
    }

    @Override
    public void append(TaskEvent event) {
        Objects.requireNonNull(event, "event");
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO audit_event (
                        event_id, task_id, trace_id, event_type, actor_type, payload, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                    """)) {
                statement.setObject(1, event.eventId());
                statement.setObject(2, event.taskId());
                statement.setString(3, event.traceId());
                statement.setString(4, event.eventType());
                statement.setString(5, "platform");
                statement.setString(6, jsonCodec.toJson(event.payload()));
                statement.setTimestamp(7, Timestamp.from(event.createdAt()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<TaskEvent> listByTask(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return query(connection -> {
            List<TaskEvent> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT event_id, task_id, trace_id, event_type, payload, created_at
                      FROM audit_event
                     WHERE task_id = ?
                     ORDER BY created_at
                    """)) {
                statement.setObject(1, taskId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        events.add(new TaskEvent(
                                rows.getObject("event_id", UUID.class),
                                rows.getObject("task_id", UUID.class),
                                rows.getString("trace_id"),
                                rows.getString("event_type"),
                                jsonCodec.fromJson(rows.getString("payload")),
                                instant(rows, "created_at")));
                    }
                }
            }
            return List.copyOf(events);
        });
    }

    private boolean insertTask(
            Connection connection,
            AgentTask task,
            String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_task (
                    task_id, parent_task_id, conversation_id, trace_id, tenant_id, user_id,
                    source_agent, target_agent, status, input, output, error_code, retry_count,
                    deadline, started_at, completed_at, version, idempotency_key
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key)
                    WHERE idempotency_key IS NOT NULL
                    DO NOTHING
                """)) {
            statement.setObject(1, task.taskId());
            statement.setObject(2, task.parentTaskId());
            statement.setString(3, task.conversationId());
            statement.setString(4, task.traceId());
            statement.setString(5, task.tenantId());
            statement.setString(6, task.userId());
            statement.setString(7, task.sourceAgent());
            statement.setString(8, task.targetAgent());
            statement.setString(9, task.status().name());
            statement.setString(10, jsonCodec.toJson(task.input()));
            statement.setString(11, task.output() == null ? null : jsonCodec.toJson(task.output()));
            statement.setString(12, task.errorCode());
            statement.setInt(13, task.retryCount());
            setNullableInstant(statement, 14, task.deadline());
            setNullableInstant(statement, 15, task.startedAt());
            setNullableInstant(statement, 16, task.completedAt());
            statement.setLong(17, task.version());
            statement.setString(18, idempotencyKey);
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<AgentTask> findByIdempotencyKey(
            Connection connection,
            String tenantId,
            String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT task_id, parent_task_id, tenant_id, user_id, trace_id, conversation_id,
                       source_agent, target_agent, status, input, output, error_code,
                       retry_count, deadline, started_at, completed_at, version
                  FROM agent_task
                 WHERE tenant_id = ? AND idempotency_key = ?
                """)) {
            statement.setString(1, tenantId);
            statement.setString(2, idempotencyKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapTask(rows)) : Optional.empty();
            }
        }
    }

    private void insertOutbox(Connection connection, TaskOutboxMessage event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO task_outbox (
                    event_id, task_id, tenant_id, trace_id, event_type, payload,
                    status, available_at, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?, ?)
                """)) {
            statement.setObject(1, event.eventId());
            statement.setObject(2, event.taskId());
            statement.setString(3, event.tenantId());
            statement.setString(4, event.traceId());
            statement.setString(5, event.eventType());
            statement.setString(6, jsonCodec.toJson(event.payload()));
            statement.setTimestamp(7, Timestamp.from(event.occurredAt()));
            statement.setTimestamp(8, Timestamp.from(event.occurredAt()));
            statement.executeUpdate();
        }
    }

    private void updateOutboxClaim(
            Connection connection,
            UUID eventId,
            int attempts,
            Instant claimedAt,
            UUID claimToken)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE task_outbox
                   SET status = 'PROCESSING', attempts = ?, claimed_at = ?, claim_token = ?
                 WHERE event_id = ?
                """)) {
            statement.setInt(1, attempts);
            statement.setTimestamp(2, Timestamp.from(claimedAt));
            statement.setObject(3, claimToken);
            statement.setObject(4, eventId);
            statement.executeUpdate();
        }
    }

    private AgentTask mapTask(ResultSet rows) throws SQLException {
        return AgentTask.restore(
                rows.getObject("task_id", UUID.class),
                rows.getObject("parent_task_id", UUID.class),
                rows.getString("tenant_id"),
                rows.getString("user_id"),
                rows.getString("trace_id"),
                rows.getString("conversation_id"),
                rows.getString("source_agent"),
                rows.getString("target_agent"),
                jsonCodec.fromJson(rows.getString("input")),
                instant(rows, "deadline"),
                TaskStatus.valueOf(rows.getString("status")),
                jsonMapOrNull(rows, "output"),
                rows.getString("error_code"),
                rows.getInt("retry_count"),
                instant(rows, "started_at"),
                instant(rows, "completed_at"),
                rows.getLong("version"));
    }

    private OutboxRecord mapOutbox(
            ResultSet rows,
            OutboxStatus status,
            int attempts,
            Instant claimedAt,
            UUID claimToken) throws SQLException {
        TaskOutboxMessage message = new TaskOutboxMessage(
                rows.getObject("event_id", UUID.class),
                rows.getObject("task_id", UUID.class),
                rows.getString("tenant_id"),
                rows.getString("trace_id"),
                rows.getString("event_type"),
                jsonCodec.fromJson(rows.getString("payload")),
                instant(rows, "created_at"));
        return OutboxRecord.restore(
                message,
                status,
                attempts,
                instant(rows, "available_at"),
                claimedAt,
                claimToken,
                instant(rows, "published_at"),
                rows.getString("last_error"));
    }

    private Approval mapApproval(ResultSet rows) throws SQLException {
        return Approval.restore(
                rows.getObject("approval_id", UUID.class),
                rows.getObject("task_id", UUID.class),
                rows.getString("tenant_id"),
                rows.getString("requested_by"),
                rows.getString("reason"),
                instant(rows, "expires_at"),
                ApprovalStatus.valueOf(rows.getString("status")),
                rows.getString("decided_by"),
                instant(rows, "decided_at"));
    }

    private List<Approval> queryApprovals(String sql, StatementBinder binder) {
        return query(connection -> {
            List<Approval> approvals = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        approvals.add(mapApproval(rows));
                    }
                }
            }
            return List.copyOf(approvals);
        });
    }

    private void updateOutboxStatus(String sql, StatementBinder binder, UUID eventId) {
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                int updated = statement.executeUpdate();
                if (updated != 1) {
                    throw new OutboxClaimLostException(eventId);
                }
            }
            return null;
        });
    }

    private Map<String, Object> jsonMapOrNull(ResultSet rows, String column) throws SQLException {
        String json = rows.getString(column);
        if (json == null) {
            return null;
        }
        return jsonCodec.fromJson(json);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Timestamp timestamp = rows.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setNullableInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 256) {
            throw new IllegalArgumentException("idempotencyKey must not exceed 256 characters");
        }
        return value;
    }

    private <T> T query(SqlOperation<T> operation) {
        try (Connection connection = dataSource.getConnection()) {
            return operation.execute(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL query failed", exception);
        }
    }

    private <T> T inTransaction(SqlOperation<T> operation) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL transaction failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
