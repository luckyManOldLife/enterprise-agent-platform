package com.xr.agent.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Approval {

    private final UUID approvalId;
    private final UUID taskId;
    private final String tenantId;
    private final String requestedBy;
    private final String reason;
    private final Instant expiresAt;
    private ApprovalStatus status;
    private String decidedBy;
    private Instant decidedAt;

    public Approval(UUID taskId, String tenantId, String requestedBy, String reason, Instant expiresAt) {
        this.approvalId = UUID.randomUUID();
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.requestedBy = requireText(requestedBy, "requestedBy");
        this.reason = requireText(reason, "reason");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = ApprovalStatus.PENDING;
    }

    private Approval(
            UUID approvalId,
            UUID taskId,
            String tenantId,
            String requestedBy,
            String reason,
            Instant expiresAt,
            ApprovalStatus status,
            String decidedBy,
            Instant decidedAt) {
        this.approvalId = Objects.requireNonNull(approvalId, "approvalId");
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.requestedBy = requireText(requestedBy, "requestedBy");
        this.reason = requireText(reason, "reason");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = Objects.requireNonNull(status, "status");
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public static Approval restore(
            UUID approvalId,
            UUID taskId,
            String tenantId,
            String requestedBy,
            String reason,
            Instant expiresAt,
            ApprovalStatus status,
            String decidedBy,
            Instant decidedAt) {
        return new Approval(
                approvalId,
                taskId,
                tenantId,
                requestedBy,
                reason,
                expiresAt,
                status,
                decidedBy,
                decidedAt);
    }

    public void approve(String actor) {
        decide(ApprovalStatus.APPROVED, actor);
    }

    public void reject(String actor) {
        decide(ApprovalStatus.REJECTED, actor);
    }

    public void expire() {
        if (status == ApprovalStatus.PENDING) {
            status = ApprovalStatus.EXPIRED;
        }
    }

    private void decide(ApprovalStatus target, String actor) {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval is not pending: " + status);
        }
        String actorValue = requireText(actor, "actor");
        status = target;
        decidedBy = actorValue;
        decidedAt = Instant.now();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public UUID approvalId() { return approvalId; }
    public UUID taskId() { return taskId; }
    public String tenantId() { return tenantId; }
    public String requestedBy() { return requestedBy; }
    public String reason() { return reason; }
    public Instant expiresAt() { return expiresAt; }
    public ApprovalStatus status() { return status; }
    public String decidedBy() { return decidedBy; }
    public Instant decidedAt() { return decidedAt; }
}
