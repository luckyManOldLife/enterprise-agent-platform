package com.xr.agent.policy;

import com.xr.agent.application.port.in.ApprovalUseCase;
import com.xr.agent.application.port.out.PolicyEnginePort;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.Approval;
import com.xr.agent.domain.model.ApprovalStatus;
import com.xr.agent.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalWorkflowServiceTest {

    @Test
    void createsPendingApprovalAndPausesTask() {
        InMemoryApprovalRepository repository = new InMemoryApprovalRepository();
        ApprovalWorkflowService service = new ApprovalWorkflowService(repository);
        AgentTask task = runningTask();

        Approval approval = service.requestApproval(task, requiresApproval(), "support-agent", Duration.ofMinutes(5));

        assertEquals(TaskStatus.WAITING_APPROVAL, task.status());
        assertEquals(ApprovalStatus.PENDING, approval.status());
        assertEquals(1, repository.findPendingByTenant("tenant-a").size());
    }

    @Test
    void approvesAndRecoversWaitingTask() {
        InMemoryApprovalRepository repository = new InMemoryApprovalRepository();
        ApprovalWorkflowService service = new ApprovalWorkflowService(repository);
        AgentTask task = runningTask();
        Approval approval = service.requestApproval(task, requiresApproval(), "support-agent", Duration.ofMinutes(5));

        Approval decided = service.decide(
                "tenant-a",
                approval.approvalId(),
                "operator-a",
                ApprovalUseCase.ApprovalDecision.APPROVE);
        service.recoverTask(task, decided);

        assertEquals(ApprovalStatus.APPROVED, decided.status());
        assertEquals(TaskStatus.RUNNING, task.status());
    }

    @Test
    void rejectsCrossTenantDecision() {
        InMemoryApprovalRepository repository = new InMemoryApprovalRepository();
        ApprovalWorkflowService service = new ApprovalWorkflowService(repository);
        Approval approval = service.requestApproval(runningTask(), requiresApproval(), "support-agent", Duration.ofMinutes(5));

        assertThrows(IllegalArgumentException.class, () -> {
            service.decide(
                    "tenant-b",
                    approval.approvalId(),
                    "operator-b",
                    ApprovalUseCase.ApprovalDecision.APPROVE);
        });
    }

    @Test
    void expiresPendingApprovalsAndFailsWaitingTask() {
        InMemoryApprovalRepository repository = new InMemoryApprovalRepository();
        ApprovalWorkflowService service = new ApprovalWorkflowService(repository);
        AgentTask task = runningTask();
        Approval approval = service.requestApproval(task, requiresApproval(), "support-agent", Duration.ofMillis(1));

        Approval expired = service.expirePending(Instant.now().plusSeconds(1)).getFirst();
        service.recoverTask(task, expired);

        assertEquals(approval.approvalId(), expired.approvalId());
        assertEquals(ApprovalStatus.EXPIRED, expired.status());
        assertEquals(TaskStatus.FAILED, task.status());
        assertEquals("APPROVAL_EXPIRED", task.errorCode());
    }

    private static AgentTask runningTask() {
        AgentTask task = AgentTask.create(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                "supervisor",
                "support-agent",
                Map.of("request", "create after-sales task"),
                null);
        task.start();
        return task;
    }

    private static PolicyEnginePort.PolicyDecision requiresApproval() {
        return new PolicyEnginePort.PolicyDecision(
                true,
                true,
                "High-risk operation requires recorded human approval",
                "POLICY_HIGH_RISK_APPROVAL_REQUIRED");
    }
}
