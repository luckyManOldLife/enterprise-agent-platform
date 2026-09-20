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

    AgentTask saveWithOutbox(AgentTask task, TaskOutboxMessage event);

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
