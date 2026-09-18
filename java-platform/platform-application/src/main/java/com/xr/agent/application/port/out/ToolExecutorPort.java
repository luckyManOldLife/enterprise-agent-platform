package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.ToolDefinition;

import java.util.Map;

public interface ToolExecutorPort {

    ToolExecutionResult execute(AgentTask task, ToolDefinition tool, Map<String, Object> arguments);

    record ToolExecutionResult(
            boolean successful,
            Map<String, Object> output,
            String errorCode) {
    }
}
