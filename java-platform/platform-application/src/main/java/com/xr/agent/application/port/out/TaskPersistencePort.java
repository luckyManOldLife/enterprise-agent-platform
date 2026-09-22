package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentTask;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic task persistence boundary.
 *
 * <p>The task row and its outbox event must be committed in one transaction
 * by the infrastructure adapter. This port deliberately exposes no database
 * or messaging types.</p>
 */
public interface TaskPersistencePort {

    /**
     * Saves a new task and its outbox event atomically.
     *
     * <p>When {@code idempotencyKey} is present, implementations must enforce
     * uniqueness within the task tenant and return the previously persisted task
     * without adding a duplicate outbox event.</p>
     */
    AgentTask saveWithOutbox(AgentTask task, TaskOutboxMessage event, String idempotencyKey);

    default AgentTask saveWithOutbox(AgentTask task, TaskOutboxMessage event) {
        return saveWithOutbox(task, event, null);
    }

    /**
     * Persists a state change only when the aggregate still has {@code expectedVersion}.
     *
     * <p>Implementations must atomically increment the persisted version and return the
     * resulting task snapshot. A version mismatch must not overwrite the current state.</p>
     */
    AgentTask update(AgentTask task, long expectedVersion);

    Optional<AgentTask> findById(UUID taskId);

    record TaskOutboxMessage(
            UUID eventId,
            UUID taskId,
            String tenantId,
            String traceId,
            String eventType,
            Map<String, Object> payload,
            Instant occurredAt) {

        public TaskOutboxMessage {
            eventId = Objects.requireNonNull(eventId, "eventId");
            taskId = Objects.requireNonNull(taskId, "taskId");
            tenantId = requireText(tenantId, "tenantId");
            traceId = requireText(traceId, "traceId");
            eventType = requireText(eventType, "eventType");
            payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }
    }
}
