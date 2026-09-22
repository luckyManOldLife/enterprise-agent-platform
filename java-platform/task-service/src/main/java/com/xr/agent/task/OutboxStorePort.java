package com.xr.agent.task;

import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStorePort {

    void append(TaskOutboxMessage event);

    /**
     * Claims eligible records, including records whose previous worker lease expired.
     */
    List<OutboxRecord> claim(int limit, Instant now, Duration processingLease);

    void markPublished(UUID eventId, UUID claimToken, Instant publishedAt);

    void markFailed(UUID eventId, UUID claimToken, String error, Instant nextAttemptAt);
}
