package com.xr.agent.worker;

import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import com.xr.agent.registry.InMemoryAgentRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ModelAgentRegistryFactory {

    private ModelAgentRegistryFactory() {
    }

    static InMemoryAgentRegistry create(List<String> agentIds) {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        for (String agentId : List.copyOf(Objects.requireNonNull(agentIds, "agentIds"))) {
            registry.register(new AgentDefinition(
                    agentId,
                    agentId,
                    "1.0.0",
                    AgentStatus.ACTIVE,
                    "model://cliproxyapi",
                    Set.of("task.execute"),
                    null));
        }
        return registry;
    }
}
