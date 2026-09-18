package com.xr.agent.domain.event;

import com.xr.agent.domain.model.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskStatusChangedEvent(
        UUID eventId,
        UUID aggregateId,
        String traceId,
        TaskStatus from,
        TaskStatus to,
        Instant occurredAt) implements DomainEvent {

    public TaskStatusChangedEvent(
            UUID aggregateId,
            String traceId,
            TaskStatus from,
            TaskStatus to) {
        this(UUID.randomUUID(), aggregateId, traceId, from, to, Instant.now());
    }
}
