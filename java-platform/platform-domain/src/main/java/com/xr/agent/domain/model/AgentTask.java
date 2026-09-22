package com.xr.agent.domain.model;

import com.xr.agent.domain.service.TaskStateMachine;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AgentTask {

    private final UUID taskId;
    private final UUID parentTaskId;
    private final String tenantId;
    private final String userId;
    private final String traceId;
    private final String conversationId;
    private final String sourceAgent;
    private final String targetAgent;
    private final Map<String, Object> input;
    private final Instant deadline;
    private TaskStatus status;
    private Map<String, Object> output;
    private String errorCode;
    private int retryCount;
    private Instant startedAt;
    private Instant completedAt;

    private AgentTask(
            UUID taskId,
            UUID parentTaskId,
            String tenantId,
            String userId,
            String traceId,
            String conversationId,
            String sourceAgent,
            String targetAgent,
            Map<String, Object> input,
            Instant deadline) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.parentTaskId = parentTaskId;
        this.tenantId = requireText(tenantId, "tenantId");
        this.userId = requireText(userId, "userId");
        this.traceId = requireText(traceId, "traceId");
        this.conversationId = conversationId;
        this.sourceAgent = sourceAgent;
        this.targetAgent = targetAgent;
        this.input = Map.copyOf(Objects.requireNonNull(input, "input"));
        this.deadline = deadline;
        this.status = TaskStatus.CREATED;
    }

    public static AgentTask create(
            String tenantId,
            String userId,
            String traceId,
            String conversationId,
            String sourceAgent,
            String targetAgent,
            Map<String, Object> input,
            Instant deadline) {
        return new AgentTask(
                UUID.randomUUID(),
                null,
                tenantId,
                userId,
                traceId,
                conversationId,
                sourceAgent,
                targetAgent,
                input,
                deadline);
    }

    public static AgentTask childOf(
            AgentTask parent,
            String targetAgent,
            Map<String, Object> input,
            Instant deadline) {
        Objects.requireNonNull(parent, "parent");
        return new AgentTask(
                UUID.randomUUID(),
                parent.taskId,
                parent.tenantId,
                parent.userId,
                parent.traceId,
                parent.conversationId,
                parent.targetAgent,
                targetAgent,
                input,
                deadline);
    }

    public static AgentTask restore(
            UUID taskId,
            UUID parentTaskId,
            String tenantId,
            String userId,
            String traceId,
            String conversationId,
            String sourceAgent,
            String targetAgent,
            Map<String, Object> input,
            Instant deadline,
            TaskStatus status,
            Map<String, Object> output,
            String errorCode,
            int retryCount,
            Instant startedAt,
            Instant completedAt) {
        AgentTask task = new AgentTask(
                taskId,
                parentTaskId,
                tenantId,
                userId,
                traceId,
                conversationId,
                sourceAgent,
                targetAgent,
                input,
                deadline);
        task.status = Objects.requireNonNull(status, "status");
        task.output = output == null ? null : Map.copyOf(output);
        task.errorCode = errorCode;
        task.retryCount = retryCount;
        task.startedAt = startedAt;
        task.completedAt = completedAt;
        return task;
    }

    public void start() {
        transitionTo(TaskStatus.RUNNING);
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void waitForApproval() {
        transitionTo(TaskStatus.WAITING_APPROVAL);
    }

    public void waitForTool() {
        transitionTo(TaskStatus.WAITING_TOOL);
    }

    public void resume() {
        transitionTo(TaskStatus.RUNNING);
    }

    public void succeed(Map<String, Object> result) {
        Map<String, Object> resultCopy = Map.copyOf(Objects.requireNonNull(result, "result"));
        transitionTo(TaskStatus.SUCCEEDED);
        this.output = resultCopy;
        completedAt = Instant.now();
    }

    public void fail(String code) {
        String codeValue = requireText(code, "errorCode");
        transitionTo(TaskStatus.FAILED);
        this.errorCode = codeValue;
        completedAt = Instant.now();
    }

    public void cancel() {
        transitionTo(TaskStatus.CANCELLED);
        completedAt = Instant.now();
    }

    public void timeout() {
        transitionTo(TaskStatus.TIMED_OUT);
        completedAt = Instant.now();
    }

    public void retry() {
        transitionTo(TaskStatus.RUNNING);
        retryCount++;
        this.errorCode = null;
    }

    private void transitionTo(TaskStatus target) {
        TaskStateMachine.requireTransition(status, target);
        status = target;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public UUID taskId() { return taskId; }
    public UUID parentTaskId() { return parentTaskId; }
    public String tenantId() { return tenantId; }
    public String userId() { return userId; }
    public String traceId() { return traceId; }
    public String conversationId() { return conversationId; }
    public String sourceAgent() { return sourceAgent; }
    public String targetAgent() { return targetAgent; }
    public TaskStatus status() { return status; }
    public Map<String, Object> input() { return input; }
    public Map<String, Object> output() { return output; }
    public String errorCode() { return errorCode; }
    public int retryCount() { return retryCount; }
    public Instant deadline() { return deadline; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
}
