package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.Approval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRepositoryPort {

    Approval save(Approval approval);

    Optional<Approval> findApprovalById(UUID approvalId);

    List<Approval> findPendingByTenant(String tenantId);

    List<Approval> findExpiredPending(Instant now);
}
