package com.xr.agent.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public interface TaskEventStorePort {

    void append(TaskEvent event);

    List<TaskEvent> listByTask(UUID taskId);

    record TaskEvent(
            UUID eventId,
            UUID taskId,
            String traceId,
            String eventType,
            Map<String, Object> payload,
            Instant createdAt) {

        public TaskEvent {
            eventId = Objects.requireNonNull(eventId, "eventId");
            taskId = Objects.requireNonNull(taskId, "taskId");
            traceId = requireText(traceId, "traceId");
            eventType = requireText(eventType, "eventType");
            payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }
    }
}
