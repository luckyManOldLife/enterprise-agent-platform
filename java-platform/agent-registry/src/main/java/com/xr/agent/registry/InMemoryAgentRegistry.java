package com.xr.agent.registry;

import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.domain.model.AgentDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryAgentRegistry implements AgentRegistryPort {

    private final ConcurrentMap<String, AgentDefinition> agents = new ConcurrentHashMap<>();

    @Override
    public void register(AgentDefinition agent) {
        Objects.requireNonNull(agent, "agent");
        AgentDefinition existing = agents.putIfAbsent(agent.agentId(), agent);
        if (existing != null) {
            throw new IllegalStateException("Agent already registered: " + agent.agentId());
        }
    }

    @Override
    public List<AgentDefinition> findAvailable(String tenantId) {
        return agents.values().stream()
                .filter(agent -> agent.isAvailableFor(tenantId))
                .sorted(Comparator.comparing(AgentDefinition::agentId))
                .toList();
    }

    @Override
    public Optional<AgentDefinition> findById(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(agentId));
    }
}
