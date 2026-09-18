package com.xr.agent.application.service;

import com.xr.agent.application.port.in.TaskUseCase;
import com.xr.agent.application.port.out.TaskRepositoryPort;
import com.xr.agent.domain.model.AgentTask;

import java.util.Objects;
import java.util.UUID;

public final class DefaultTaskService implements TaskUseCase {

    private final TaskRepositoryPort taskRepository;

    public DefaultTaskService(TaskRepositoryPort taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
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
        return taskRepository.save(task);
    }

    @Override
    public AgentTask get(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }
}
