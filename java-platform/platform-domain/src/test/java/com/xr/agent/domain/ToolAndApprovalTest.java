package com.xr.agent.domain;

import com.xr.agent.domain.model.Approval;
import com.xr.agent.domain.model.ApprovalStatus;
import com.xr.agent.domain.model.RiskLevel;
import com.xr.agent.domain.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolAndApprovalTest {

    @Test
    void highRiskToolRequiresApproval() {
        ToolDefinition tool = new ToolDefinition(
                "support.create-task", "Create support task", "1.0.0",
                RiskLevel.HIGH, Map.of("support.write", "enabled").keySet(), true);

        assertTrue(tool.requiresApproval());
    }

    @Test
    void approvalRecordsDecision() {
        Approval approval = new Approval(
                UUID.randomUUID(), "tenant-a", "agent-a",
                "Create after-sales task", Instant.now().plusSeconds(300));
        approval.approve("operator-a");

        assertEquals(ApprovalStatus.APPROVED, approval.status());
        assertEquals("operator-a", approval.decidedBy());
    }

    @Test
    void invalidApprovalActorDoesNotMutateStatus() {
        Approval approval = new Approval(
                UUID.randomUUID(), "tenant-a", "agent-a",
                "Create after-sales task", Instant.now().plusSeconds(300));

        assertThrows(IllegalArgumentException.class, () -> {
            approval.approve(" ");
        });

        assertEquals(ApprovalStatus.PENDING, approval.status());
    }
}
