package com.xr.agent.task;

import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.TaskVersionConflictException;

import java.time.Duration;
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
    private final ConcurrentMap<IdempotencyKey, UUID> idempotencyTasks = new ConcurrentHashMap<>();

    @Override
    public synchronized AgentTask saveWithOutbox(
            AgentTask task,
            TaskOutboxMessage event,
            String idempotencyKey) {
        if (!task.taskId().equals(event.taskId())) {
            throw new IllegalArgumentException("Task and outbox aggregate ids must match");
        }
        if (task.version() != 0) {
            throw new IllegalArgumentException("New task version must be zero");
        }
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            UUID existingTaskId = idempotencyTasks.get(new IdempotencyKey(
                    task.tenantId(), normalizedIdempotencyKey));
            if (existingTaskId != null) {
                return findById(existingTaskId).orElseThrow();
            }
        }
        AgentTask stored = task.copyWithVersion(task.version());
        if (tasks.putIfAbsent(task.taskId(), stored) != null) {
            throw new IllegalStateException("Task already exists: " + task.taskId());
        }
        try {
            append(event);
            if (normalizedIdempotencyKey != null) {
                idempotencyTasks.put(new IdempotencyKey(task.tenantId(), normalizedIdempotencyKey), task.taskId());
            }
            return stored.copyWithVersion(stored.version());
        } catch (RuntimeException exception) {
            tasks.remove(task.taskId(), stored);
            throw exception;
        }
    }

    @Override
    public Optional<AgentTask> findById(UUID taskId) {
        return Optional.ofNullable(tasks.get(taskId))
                .map(task -> task.copyWithVersion(task.version()));
    }

    @Override
    public synchronized AgentTask update(AgentTask task, long expectedVersion) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (task.version() != expectedVersion) {
            throw new IllegalArgumentException("Task version does not match expectedVersion");
        }
        AgentTask current = tasks.get(task.taskId());
        if (current == null || current.version() != expectedVersion) {
            throw new TaskVersionConflictException(task.taskId(), expectedVersion);
        }

        AgentTask next = task.copyWithVersion(expectedVersion + 1);
        if (!tasks.replace(task.taskId(), current, next)) {
            throw new TaskVersionConflictException(task.taskId(), expectedVersion);
        }
        return next.copyWithVersion(next.version());
    }

    @Override
    public void append(TaskOutboxMessage event) {
        if (outbox.putIfAbsent(event.eventId(), new OutboxRecord(event)) != null) {
            throw new IllegalStateException("Outbox event already exists: " + event.eventId());
        }
    }

    @Override
    public synchronized List<OutboxRecord> claim(int limit, Instant now, Duration processingLease) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (processingLease == null || processingLease.isZero() || processingLease.isNegative()) {
            throw new IllegalArgumentException("processingLease must be positive");
        }
        List<OutboxRecord> claimed = new ArrayList<>();
        outbox.values().stream()
                .filter(record -> record.isClaimable(now, processingLease))
                .sorted(Comparator.comparing(record -> record.message().occurredAt()))
                .limit(limit)
                .forEach(record -> {
                    record.claim(now, UUID.randomUUID(), processingLease);
                    claimed.add(record.snapshot());
                });
        return List.copyOf(claimed);
    }

    @Override
    public synchronized void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
        record(eventId).markPublished(claimToken, publishedAt);
    }

    @Override
    public synchronized void markFailed(UUID eventId, UUID claimToken, String error, Instant nextAttemptAt) {
        record(eventId).markFailed(claimToken, error, nextAttemptAt);
    }

    private OutboxRecord record(UUID eventId) {
        OutboxRecord record = outbox.get(eventId);
        if (record == null) {
            throw new IllegalArgumentException("Outbox event not found: " + eventId);
        }
        return record;
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

    private record IdempotencyKey(String tenantId, String value) {
    }
}
