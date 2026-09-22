package com.xr.agent.registry;

import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryAgentRegistryTest {

    @Test
    void findsOnlyActiveAgentsAvailableForTenant() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(agent("customer-agent", AgentStatus.ACTIVE, "tenant-a"));
        registry.register(agent("order-agent", AgentStatus.ACTIVE, null));
        registry.register(agent("support-agent", AgentStatus.DISABLED, "tenant-a"));
        registry.register(agent("audit-agent", AgentStatus.ACTIVE, "tenant-b"));

        assertEquals(
                java.util.List.of("customer-agent", "order-agent"),
                registry.findAvailable("tenant-a").stream().map(AgentDefinition::agentId).toList());
    }

    @Test
    void rejectsDuplicateAgentId() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(agent("customer-agent", AgentStatus.ACTIVE, null));

        assertThrows(IllegalStateException.class, () -> {
            registry.register(agent("customer-agent", AgentStatus.ACTIVE, null));
        });
    }

    private static AgentDefinition agent(String id, AgentStatus status, String tenantScope) {
        return new AgentDefinition(
                id,
                id,
                "1.0.0",
                status,
                "http://localhost/" + id,
                Set.of("order.read"),
                tenantScope);
    }
}
