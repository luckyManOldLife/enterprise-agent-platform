package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentTask;

import java.util.Map;

public interface AgentInvokerPort {

    AgentInvocationResult invoke(AgentTask task, AgentDefinition agent);

    record AgentInvocationResult(
            boolean successful,
            Map<String, Object> output,
            String errorCode) {

        public AgentInvocationResult {
            output = Map.copyOf(output == null ? Map.of() : output);
        }
    }
}
