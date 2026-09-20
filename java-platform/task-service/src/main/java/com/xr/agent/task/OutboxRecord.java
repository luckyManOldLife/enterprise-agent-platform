package com.xr.agent.task;

import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OutboxRecord {

    private final TaskOutboxMessage message;
    private OutboxStatus status;
    private int attempts;
    private Instant availableAt;
    private Instant publishedAt;
    private String lastError;

    public OutboxRecord(TaskOutboxMessage message) {
        this.message = Objects.requireNonNull(message, "message");
        this.status = OutboxStatus.PENDING;
        this.availableAt = message.occurredAt();
    }

    public boolean isClaimable(Instant now) {
        return (status == OutboxStatus.PENDING || status == OutboxStatus.FAILED)
                && !availableAt.isAfter(now);
    }

    public void claim(Instant now) {
        if (!isClaimable(now)) {
            throw new IllegalStateException("Outbox event is not claimable: " + message.eventId());
        }
        status = OutboxStatus.PROCESSING;
        attempts++;
    }

    public void markPublished(Instant now) {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("Outbox event is not processing: " + message.eventId());
        }
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
    }

    public void markFailed(String error, Instant nextAttemptAt) {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("Outbox event is not processing: " + message.eventId());
        }
        status = OutboxStatus.FAILED;
        lastError = Objects.requireNonNull(error, "error");
        availableAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    public TaskOutboxMessage message() { return message; }
    public UUID eventId() { return message.eventId(); }
    public OutboxStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant availableAt() { return availableAt; }
    public Instant publishedAt() { return publishedAt; }
    public String lastError() { return lastError; }
}
