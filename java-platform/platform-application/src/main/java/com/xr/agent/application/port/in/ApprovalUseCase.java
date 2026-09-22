package com.xr.agent.application.port.in;

import com.xr.agent.domain.model.Approval;

import java.util.List;
import java.util.UUID;

public interface ApprovalUseCase {

    List<Approval> listPending(String tenantId);

    Approval decide(DecideApprovalCommand command);

    record DecideApprovalCommand(
            String tenantId,
            UUID approvalId,
            String actor,
            ApprovalDecision decision) {
    }

    enum ApprovalDecision {
        APPROVE,
        REJECT
    }
}
