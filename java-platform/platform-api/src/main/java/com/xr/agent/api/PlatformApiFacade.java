package com.xr.agent.api;

import com.xr.agent.application.port.in.ApprovalUseCase;
import com.xr.agent.application.port.in.TaskUseCase;
import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.Approval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlatformApiFacade {

    private final TaskUseCase tasks;
    private final AgentRegistryPort agents;
    private final ApprovalUseCase approvals;
    private final TaskEventStorePort taskEvents;

    public PlatformApiFacade(
            TaskUseCase tasks,
            AgentRegistryPort agents,
            ApprovalUseCase approvals,
            TaskEventStorePort taskEvents) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.approvals = Objects.requireNonNull(approvals, "approvals");
        this.taskEvents = Objects.requireNonNull(taskEvents, "taskEvents");
    }

    public List<AgentResponse> listAgents(String tenantId) {
        return agents.findAvailable(tenantId).stream()
                .map(PlatformApiFacade::toAgentResponse)
                .toList();
    }

    public TaskResponse createTask(CreateTaskRequest request) {
        Objects.requireNonNull(request, "request");
        request.validate();
        String traceId = request.traceId() == null || request.traceId().isBlank()
                ? UUID.randomUUID().toString()
                : request.traceId();
        AgentTask task = tasks.submit(new TaskUseCase.SubmitTaskCommand(
                request.tenantId(),
                request.userId(),
                traceId,
                request.conversationId(),
                "api",
                "supervisor",
                Map.of(
                        "input", request.input(),
                        "roles", request.roles()),
                request.idempotencyKey()));
        return toTaskResponse(task);
    }

    public TaskResponse getTask(UUID taskId, String tenantId) {
        AgentTask task = tenantTask(taskId, tenantId);
        return toTaskResponse(task);
    }

    public List<TaskEventResponse> streamTaskEvents(UUID taskId, String tenantId) {
        tenantTask(taskId, tenantId);
        return taskEvents.listByTask(taskId).stream()
                .map(event -> new TaskEventResponse(
                        event.eventId(),
                        event.taskId(),
                        event.traceId(),
                        event.eventType(),
                        event.payload(),
                        event.createdAt()))
                .toList();
    }

    private AgentTask tenantTask(UUID taskId, String tenantId) {
        Objects.requireNonNull(taskId, "taskId");
        requireText(tenantId, "tenantId");
        AgentTask task;
        try {
            task = tasks.get(taskId);
        } catch (IllegalArgumentException exception) {
            throw new TenantResourceNotFoundException();
        }
        if (!tenantId.equals(task.tenantId())) {
            throw new TenantResourceNotFoundException();
        }
        return task;
    }

    public List<ApprovalResponse> listApprovals(String tenantId) {
        return approvals.listPending(tenantId).stream()
                .map(PlatformApiFacade::toApprovalResponse)
                .toList();
    }

    public ApprovalResponse decideApproval(UUID approvalId, ApprovalDecisionRequest request) {
        Objects.requireNonNull(request, "request");
        request.validate();
        Approval approval = approvals.decide(new ApprovalUseCase.DecideApprovalCommand(
                request.tenantId(),
                Objects.requireNonNull(approvalId, "approvalId"),
                request.actor(),
                request.decision()));
        return toApprovalResponse(approval);
    }

    private static AgentResponse toAgentResponse(AgentDefinition agent) {
        return new AgentResponse(
                agent.agentId(),
                agent.name(),
                agent.version(),
                agent.status().name(),
                agent.capabilities(),
                agent.tenantScope());
    }

    private static TaskResponse toTaskResponse(AgentTask task) {
        return new TaskResponse(
                task.taskId(),
                task.traceId(),
                task.tenantId(),
                task.userId(),
                task.status().name(),
                task.output(),
                task.errorCode());
    }

    private static ApprovalResponse toApprovalResponse(Approval approval) {
        return new ApprovalResponse(
                approval.approvalId(),
                approval.taskId(),
                approval.tenantId(),
                approval.requestedBy(),
                approval.reason(),
                approval.status().name(),
                approval.expiresAt(),
                approval.decidedBy(),
                approval.decidedAt());
    }

    public record CreateTaskRequest(
            String tenantId,
            String userId,
            String traceId,
            String conversationId,
            List<String> roles,
            String input,
            String idempotencyKey) {

        public CreateTaskRequest {
            roles = List.copyOf(roles == null ? List.of() : roles);
        }

        private void validate() {
            requireText(tenantId, "tenantId");
            requireText(userId, "userId");
            requireText(input, "input");
        }
    }

    public record TaskResponse(
            UUID taskId,
            String traceId,
            String tenantId,
            String userId,
            String status,
            Map<String, Object> output,
            String errorCode) {
    }

    public record TaskEventResponse(
            UUID eventId,
            UUID taskId,
            String traceId,
            String eventType,
            Map<String, Object> payload,
            Instant createdAt) {
    }

    public record AgentResponse(
            String agentId,
            String name,
            String version,
            String status,
            java.util.Set<String> capabilities,
            String tenantScope) {
    }

    public record ApprovalResponse(
            UUID approvalId,
            UUID taskId,
            String tenantId,
            String requestedBy,
            String reason,
            String status,
            Instant expiresAt,
            String decidedBy,
            Instant decidedAt) {
    }

    public record ApprovalDecisionRequest(
            String tenantId,
            String actor,
            ApprovalUseCase.ApprovalDecision decision) {

        private void validate() {
            requireText(tenantId, "tenantId");
            requireText(actor, "actor");
            Objects.requireNonNull(decision, "decision");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public static final class TenantResourceNotFoundException extends RuntimeException {

        public TenantResourceNotFoundException() {
            super("Requested resource was not found");
        }
    }
}
