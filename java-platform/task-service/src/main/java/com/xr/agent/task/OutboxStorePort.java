package com.xr.agent.task;

import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStorePort {

    void append(TaskOutboxMessage event);

    List<OutboxRecord> claim(int limit, Instant now);

    void markPublished(UUID eventId, Instant publishedAt);

    void markFailed(UUID eventId, String error, Instant nextAttemptAt);
}
