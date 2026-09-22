package com.xr.agent.api;

import com.xr.agent.application.port.in.ApprovalUseCase;
import com.xr.agent.application.port.in.TaskUseCase;
import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.Approval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformApiFacadeTest {

    @Test
    void createsTaskFromRestRequest() {
        RecordingTaskUseCase tasks = new RecordingTaskUseCase();
        PlatformApiFacade api = newApi(tasks);

        PlatformApiFacade.TaskResponse response = api.createTask(new PlatformApiFacade.CreateTaskRequest(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                List.of("agent.write"),
                "Create after-sales task",
                "idem-1"));

        assertEquals("trace-a", response.traceId());
        assertEquals("api", tasks.lastCommand.sourceAgent());
        assertEquals("supervisor", tasks.lastCommand.targetAgent());
        assertEquals("Create after-sales task", tasks.lastCommand.input().get("input"));
    }

    @Test
    void listsPendingApprovalsAndDecides() {
        PlatformApiFacade api = newApi(new RecordingTaskUseCase());
        UUID approvalId = UUID.randomUUID();

        PlatformApiFacade.ApprovalResponse response = api.decideApproval(
                approvalId,
                new PlatformApiFacade.ApprovalDecisionRequest(
                        "tenant-a",
                        "operator-a",
                        ApprovalUseCase.ApprovalDecision.APPROVE));

        assertEquals(approvalId, response.approvalId());
        assertEquals("APPROVED", response.status());
        assertEquals(1, api.listApprovals("tenant-a").size());
    }

    @Test
    void rejectsBlankTaskInput() {
        PlatformApiFacade api = newApi(new RecordingTaskUseCase());

        assertThrows(IllegalArgumentException.class, () -> {
            api.createTask(new PlatformApiFacade.CreateTaskRequest(
                    "tenant-a", "user-a", null, null, List.of(), " ", null));
        });
    }

    private static PlatformApiFacade newApi(RecordingTaskUseCase tasks) {
        return new PlatformApiFacade(
                tasks,
                new TestAgentRegistry(),
                new TestApprovalUseCase(),
                new TestTaskEventStore());
    }

    private static final class RecordingTaskUseCase implements TaskUseCase {
        private SubmitTaskCommand lastCommand;
        private AgentTask lastTask;

        @Override
        public AgentTask submit(SubmitTaskCommand command) {
            lastCommand = command;
            lastTask = AgentTask.create(
                    command.tenantId(),
                    command.userId(),
                    command.traceId(),
                    command.conversationId(),
                    command.sourceAgent(),
                    command.targetAgent(),
                    command.input(),
                    null);
            return lastTask;
        }

        @Override
        public AgentTask get(UUID taskId) {
            return lastTask;
        }
    }

    private static final class TestAgentRegistry implements AgentRegistryPort {
        @Override
        public void register(AgentDefinition agent) {
        }

        @Override
        public List<AgentDefinition> findAvailable(String tenantId) {
            return List.of(new AgentDefinition(
                    "supervisor",
                    "Supervisor",
                    "1.0.0",
                    AgentStatus.ACTIVE,
                    "a2a://supervisor",
                    Set.of("task.plan"),
                    null));
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.empty();
        }
    }

    private static final class TestApprovalUseCase implements ApprovalUseCase {
        @Override
        public List<Approval> listPending(String tenantId) {
            return List.of(new Approval(
                    UUID.randomUUID(),
                    tenantId,
                    "support-agent",
                    "High-risk operation requires approval",
                    Instant.now().plusSeconds(60)));
        }

        @Override
        public Approval decide(DecideApprovalCommand command) {
            Approval approval = new Approval(
                    command.approvalId(),
                    command.tenantId(),
                    "support-agent",
                    "High-risk operation requires approval",
                    Instant.now().plusSeconds(60));
            if (command.decision() == ApprovalUseCase.ApprovalDecision.APPROVE) {
                approval.approve(command.actor());
            } else {
                approval.reject(command.actor());
            }
            return Approval.restore(
                    command.approvalId(),
                    approval.taskId(),
                    approval.tenantId(),
                    approval.requestedBy(),
                    approval.reason(),
                    approval.expiresAt(),
                    approval.status(),
                    approval.decidedBy(),
                    approval.decidedAt());
        }
    }

    private static final class TestTaskEventStore implements TaskEventStorePort {
        @Override
        public void append(TaskEvent event) {
        }

        @Override
        public List<TaskEvent> listByTask(UUID taskId) {
            return List.of(new TaskEvent(
                    UUID.randomUUID(),
                    taskId,
                    "trace-a",
                    "TASK_CREATED",
                    Map.of("source", "api"),
                    Instant.now()));
        }
    }
}
