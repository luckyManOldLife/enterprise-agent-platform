package com.xr.agent.application.port.in;

import com.xr.agent.domain.model.AgentTask;

import java.util.Map;
import java.util.UUID;

public interface TaskUseCase {

    AgentTask submit(SubmitTaskCommand command);

    AgentTask get(UUID taskId);

    record SubmitTaskCommand(
            String tenantId,
            String userId,
            String traceId,
            String conversationId,
            String sourceAgent,
            String targetAgent,
            Map<String, Object> input) {
    }
}
