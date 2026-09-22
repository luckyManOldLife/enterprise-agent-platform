package com.xr.agent.task;

import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OutboxRecord {

    private final TaskOutboxMessage message;
    private OutboxStatus status;
    private int attempts;
    private Instant availableAt;
    private Instant claimedAt;
    private UUID claimToken;
    private Instant publishedAt;
    private String lastError;

    public OutboxRecord(TaskOutboxMessage message) {
        this.message = Objects.requireNonNull(message, "message");
        this.status = OutboxStatus.PENDING;
        this.availableAt = message.occurredAt();
    }

    private OutboxRecord(
            TaskOutboxMessage message,
            OutboxStatus status,
            int attempts,
            Instant availableAt,
            Instant claimedAt,
            UUID claimToken,
            Instant publishedAt,
            String lastError) {
        this.message = Objects.requireNonNull(message, "message");
        this.status = Objects.requireNonNull(status, "status");
        this.attempts = attempts;
        this.availableAt = Objects.requireNonNull(availableAt, "availableAt");
        this.claimedAt = claimedAt;
        this.claimToken = claimToken;
        this.publishedAt = publishedAt;
        this.lastError = lastError;
    }

    public static OutboxRecord restore(
            TaskOutboxMessage message,
            OutboxStatus status,
            int attempts,
            Instant availableAt,
            Instant claimedAt,
            UUID claimToken,
            Instant publishedAt,
            String lastError) {
        return new OutboxRecord(
                message, status, attempts, availableAt, claimedAt, claimToken, publishedAt, lastError);
    }

    public boolean isClaimable(Instant now, Duration processingLease) {
        Objects.requireNonNull(now, "now");
        if (status == OutboxStatus.PENDING || status == OutboxStatus.FAILED) {
            return !availableAt.isAfter(now);
        }
        return status == OutboxStatus.PROCESSING
                && claimedAt != null
                && !claimedAt.plus(requirePositive(processingLease, "processingLease")).isAfter(now);
    }

    public void claim(Instant now, UUID nextClaimToken, Duration processingLease) {
        if (!isClaimable(now, processingLease)) {
            throw new IllegalStateException("Outbox event is not claimable: " + message.eventId());
        }
        status = OutboxStatus.PROCESSING;
        attempts++;
        claimedAt = Objects.requireNonNull(now, "now");
        claimToken = Objects.requireNonNull(nextClaimToken, "nextClaimToken");
    }

    public void markPublished(UUID completedClaimToken, Instant now) {
        requireClaimOwner(completedClaimToken);
        status = OutboxStatus.PUBLISHED;
        publishedAt = Objects.requireNonNull(now, "now");
        claimedAt = null;
        claimToken = null;
        lastError = null;
    }

    public void markFailed(UUID completedClaimToken, String error, Instant nextAttemptAt) {
        requireClaimOwner(completedClaimToken);
        status = OutboxStatus.FAILED;
        lastError = Objects.requireNonNull(error, "error");
        availableAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        claimedAt = null;
        claimToken = null;
    }

    public OutboxRecord snapshot() {
        return restore(
                message, status, attempts, availableAt, claimedAt, claimToken, publishedAt, lastError);
    }

    private void requireClaimOwner(UUID completedClaimToken) {
        if (status != OutboxStatus.PROCESSING
                || !Objects.equals(claimToken, Objects.requireNonNull(completedClaimToken, "claimToken"))) {
            throw new OutboxClaimLostException(message.eventId());
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    public TaskOutboxMessage message() { return message; }
    public UUID eventId() { return message.eventId(); }
    public OutboxStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant availableAt() { return availableAt; }
    public Instant claimedAt() { return claimedAt; }
    public UUID claimToken() { return claimToken; }
    public Instant publishedAt() { return publishedAt; }
    public String lastError() { return lastError; }
}
