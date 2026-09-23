package com.xr.agent.worker;

import com.xr.agent.application.port.out.ToolExecutorPort;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.ToolDefinition;

import java.util.Map;

/**
 * Fails closed until a concrete MCP/A2A executor is configured.
 */
final class LocalToolExecutor implements ToolExecutorPort {

    @Override
    public ToolExecutionResult execute(AgentTask task, ToolDefinition tool, Map<String, Object> arguments) {
        return new ToolExecutionResult(false, Map.of(), "TOOL_EXECUTOR_UNAVAILABLE");
    }
}
