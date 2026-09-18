package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentTask;

import java.util.Optional;
import java.util.UUID;

public interface TaskRepositoryPort {

    AgentTask save(AgentTask task);

    Optional<AgentTask> findById(UUID taskId);
}
