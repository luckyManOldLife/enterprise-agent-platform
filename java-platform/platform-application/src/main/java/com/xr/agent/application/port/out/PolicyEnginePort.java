package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.ToolDefinition;

import java.util.Set;

public interface PolicyEnginePort {

    PolicyDecision authorize(PolicyRequest request);

    record PolicyRequest(
            AgentTask task,
            ToolDefinition tool,
            Set<String> roles,
            String idempotencyKey) {
        public PolicyRequest {
            roles = Set.copyOf(roles == null ? Set.of() : roles);
        }
    }

    record PolicyDecision(
            boolean allowed,
            boolean requiresApproval,
            String reason,
            String policyCode) {
    }
}
