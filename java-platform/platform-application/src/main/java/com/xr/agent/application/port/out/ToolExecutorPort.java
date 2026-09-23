package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.ToolDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public interface ToolExecutorPort {

    ToolExecutionResult execute(AgentTask task, ToolDefinition tool, Map<String, Object> arguments);

    record ToolExecutionResult(
            boolean successful,
            Map<String, Object> output,
            String errorCode) {
        public ToolExecutionResult {
            output = Collections.unmodifiableMap(new LinkedHashMap<>(output == null ? Map.of() : output));
        }
    }
}
