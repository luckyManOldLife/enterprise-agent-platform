package com.xr.agent.application.service;

import com.xr.agent.application.port.in.TaskUseCase;
import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.domain.model.AgentTask;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DefaultTaskService implements TaskUseCase {

    private final TaskPersistencePort taskPersistence;

    public DefaultTaskService(TaskPersistencePort taskPersistence) {
        this.taskPersistence = Objects.requireNonNull(taskPersistence, "taskPersistence");
    }

    @Override
    public AgentTask submit(SubmitTaskCommand command) {
        Objects.requireNonNull(command, "command");
        AgentTask task = AgentTask.create(
                command.tenantId(),
                command.userId(),
                command.traceId(),
                command.conversationId(),
                command.sourceAgent(),
                command.targetAgent(),
                command.input(),
                null);
        Map<String, Object> payload = new HashMap<>();
        if (task.targetAgent() != null) {
            payload.put("targetAgent", task.targetAgent());
        }
        if (task.sourceAgent() != null) {
            payload.put("sourceAgent", task.sourceAgent());
        }
        TaskPersistencePort.TaskOutboxMessage event = new TaskPersistencePort.TaskOutboxMessage(
                UUID.randomUUID(),
                task.taskId(),
                task.tenantId(),
                task.traceId(),
                "TASK_CREATED",
                payload,
                Instant.now());
        return taskPersistence.saveWithOutbox(task, event, command.idempotencyKey());
    }

    @Override
    public AgentTask get(UUID taskId) {
        return taskPersistence.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }
}
