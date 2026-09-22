package com.xr.agent.policy;

import com.xr.agent.application.port.out.ApprovalRepositoryPort;
import com.xr.agent.domain.model.Approval;
import com.xr.agent.domain.model.ApprovalStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryApprovalRepository implements ApprovalRepositoryPort {

    private final ConcurrentMap<UUID, Approval> approvals = new ConcurrentHashMap<>();

    @Override
    public Approval save(Approval approval) {
        Objects.requireNonNull(approval, "approval");
        approvals.put(approval.approvalId(), approval);
        return approval;
    }

    @Override
    public Optional<Approval> findApprovalById(UUID approvalId) {
        if (approvalId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(approvals.get(approvalId));
    }

    @Override
    public List<Approval> findPendingByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return approvals.values().stream()
                .filter(approval -> approval.status() == ApprovalStatus.PENDING)
                .filter(approval -> tenantId.equals(approval.tenantId()))
                .sorted(Comparator.comparing(Approval::expiresAt))
                .toList();
    }

    @Override
    public List<Approval> findExpiredPending(Instant now) {
        Objects.requireNonNull(now, "now");
        return approvals.values().stream()
                .filter(approval -> approval.status() == ApprovalStatus.PENDING)
                .filter(approval -> !approval.expiresAt().isAfter(now))
                .sorted(Comparator.comparing(Approval::expiresAt))
                .toList();
    }
}
