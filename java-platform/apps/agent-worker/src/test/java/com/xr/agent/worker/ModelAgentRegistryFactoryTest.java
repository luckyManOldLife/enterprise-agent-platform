package com.xr.agent.worker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelAgentRegistryFactoryTest {

    @Test
    void registersOnlyConfiguredModelAgents() {
        var registry = ModelAgentRegistryFactory.create(List.of("supervisor", "order-agent"));

        assertEquals(
                List.of("order-agent", "supervisor"),
                registry.findAvailable("tenant-a").stream().map(agent -> agent.agentId()).toList());
        assertEquals(
                "model://cliproxyapi",
                registry.findById("supervisor").orElseThrow().endpoint());
    }
}
