package com.xr.agent.policy;

import com.xr.agent.application.port.in.ApprovalUseCase;
import com.xr.agent.application.port.out.ApprovalRepositoryPort;
import com.xr.agent.application.port.out.PolicyEnginePort;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.Approval;
import com.xr.agent.domain.model.ApprovalStatus;
import com.xr.agent.domain.model.TaskStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ApprovalWorkflowService implements ApprovalUseCase {

    private final ApprovalRepositoryPort approvals;

    public ApprovalWorkflowService(ApprovalRepositoryPort approvals) {
        this.approvals = Objects.requireNonNull(approvals, "approvals");
    }

    public Approval requestApproval(
            AgentTask task,
            PolicyEnginePort.PolicyDecision decision,
            String requestedBy,
            Duration ttl) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(ttl, "ttl");

        if (!decision.allowed() || !decision.requiresApproval()) {
            throw new IllegalArgumentException("Policy decision does not require approval: " + decision.policyCode());
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        task.waitForApproval();
        Approval approval = new Approval(
                task.taskId(),
                task.tenantId(),
                requestedBy,
                decision.reason(),
                Instant.now().plus(ttl));
        return approvals.save(approval);
    }

    @Override
    public List<Approval> listPending(String tenantId) {
        return approvals.findPendingByTenant(tenantId);
    }

    @Override
    public Approval decide(DecideApprovalCommand command) {
        Objects.requireNonNull(command, "command");
        return decide(command.tenantId(), command.approvalId(), command.actor(), command.decision());
    }

    public Approval decide(String tenantId, UUID approvalId, String actor, ApprovalUseCase.ApprovalDecision decision) {
        String tenant = requireText(tenantId, "tenantId");
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(decision, "decision");

        Approval approval = approvals.findApprovalById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
        if (!tenant.equals(approval.tenantId())) {
            throw new IllegalArgumentException("Approval does not belong to tenant: " + tenant);
        }

        if (decision == ApprovalUseCase.ApprovalDecision.APPROVE) {
            approval.approve(actor);
        } else {
            approval.reject(actor);
        }
        return approvals.save(approval);
    }

    public List<Approval> expirePending(Instant now) {
        Objects.requireNonNull(now, "now");
        List<Approval> expired = approvals.findExpiredPending(now);
        expired.forEach(approval -> {
            approval.expire();
            approvals.save(approval);
        });
        return List.copyOf(expired);
    }

    public AgentTask recoverTask(AgentTask task, Approval approval) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(approval, "approval");

        if (!task.taskId().equals(approval.taskId())) {
            throw new IllegalArgumentException("Approval task does not match task");
        }
        if (!task.tenantId().equals(approval.tenantId())) {
            throw new IllegalArgumentException("Approval tenant does not match task");
        }
        if (task.status() != TaskStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("Task is not waiting approval: " + task.status());
        }

        if (approval.status() == ApprovalStatus.APPROVED) {
            task.resume();
        } else if (approval.status() == ApprovalStatus.REJECTED) {
            task.fail("APPROVAL_REJECTED");
        } else if (approval.status() == ApprovalStatus.EXPIRED) {
            task.fail("APPROVAL_EXPIRED");
        } else {
            throw new IllegalStateException("Approval is still pending: " + approval.approvalId());
        }
        return task;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
