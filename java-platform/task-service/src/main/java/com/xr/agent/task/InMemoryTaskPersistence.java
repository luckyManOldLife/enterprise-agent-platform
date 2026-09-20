package com.xr.agent.task;

import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.domain.model.AgentTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Deterministic local adapter for tests and local development.
 *
 * <p>The PostgreSQL adapter must preserve the same atomic save contract and
 * enforce the event idempotency constraint in its transaction.</p>
 */
public final class InMemoryTaskPersistence implements TaskPersistencePort, OutboxStorePort {

    private final ConcurrentMap<UUID, AgentTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, OutboxRecord> outbox = new ConcurrentHashMap<>();

    @Override
    public synchronized AgentTask saveWithOutbox(AgentTask task, TaskOutboxMessage event) {
        if (!task.taskId().equals(event.taskId())) {
            throw new IllegalArgumentException("Task and outbox aggregate ids must match");
        }
        if (tasks.putIfAbsent(task.taskId(), task) != null) {
            throw new IllegalStateException("Task already exists: " + task.taskId());
        }
        try {
            append(event);
            return task;
        } catch (RuntimeException exception) {
            tasks.remove(task.taskId(), task);
            throw exception;
        }
    }

    @Override
    public Optional<AgentTask> findById(UUID taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public void append(TaskOutboxMessage event) {
        if (outbox.putIfAbsent(event.eventId(), new OutboxRecord(event)) != null) {
            throw new IllegalStateException("Outbox event already exists: " + event.eventId());
        }
    }

    @Override
    public synchronized List<OutboxRecord> claim(int limit, Instant now) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<OutboxRecord> claimed = new ArrayList<>();
        outbox.values().stream()
                .filter(record -> record.isClaimable(now))
                .sorted(Comparator.comparing(record -> record.message().occurredAt()))
                .limit(limit)
                .forEach(record -> {
                    record.claim(now);
                    claimed.add(record);
                });
        return List.copyOf(claimed);
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt) {
        record(eventId).markPublished(publishedAt);
    }

    @Override
    public void markFailed(UUID eventId, String error, Instant nextAttemptAt) {
        record(eventId).markFailed(error, nextAttemptAt);
    }

    private OutboxRecord record(UUID eventId) {
        OutboxRecord record = outbox.get(eventId);
        if (record == null) {
            throw new IllegalArgumentException("Outbox event not found: " + eventId);
        }
        return record;
    }
}
