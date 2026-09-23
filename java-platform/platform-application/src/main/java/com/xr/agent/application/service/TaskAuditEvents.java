package com.xr.agent.application.service;

import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.domain.model.AgentTask;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TaskAuditEvents {

    private TaskAuditEvents() {
    }

    public static TaskEventStorePort.TaskEvent fromTask(
            AgentTask task,
            String eventType,
            Map<String, Object> payload,
            Instant createdAt) {
        Objects.requireNonNull(task, "task");
        String type = requireText(eventType, "eventType");
        Instant timestamp = Objects.requireNonNull(createdAt, "createdAt");
        return new TaskEventStorePort.TaskEvent(
                stableEventId(task, type),
                task.taskId(),
                task.traceId(),
                type,
                safePayload(task, payload),
                timestamp);
    }

    public static UUID stableEventId(AgentTask task, String eventType) {
        Objects.requireNonNull(task, "task");
        String material = task.taskId() + ":" + requireText(eventType, "eventType");
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> safePayload(AgentTask task, Map<String, Object> payload) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tenantId", task.tenantId());
        values.put("status", task.status().name());
        if (task.targetAgent() != null) {
            values.put("targetAgent", task.targetAgent());
        }
        if (task.sourceAgent() != null) {
            values.put("sourceAgent", task.sourceAgent());
        }
        if (task.errorCode() != null) {
            values.put("errorCode", task.errorCode());
        }
        if (payload != null) {
            values.putAll(payload);
        }
        return Map.copyOf(values);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
