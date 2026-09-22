package com.xr.agent.tool;

import com.xr.agent.domain.model.RiskLevel;
import com.xr.agent.domain.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryToolRegistryTest {

    @Test
    void findsToolsByCapabilityInStableOrder() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(tool("support.create-task", RiskLevel.HIGH, "support.write"));
        registry.register(tool("order.lookup", RiskLevel.LOW, "order.read"));
        registry.register(tool("customer.lookup", RiskLevel.LOW, "customer.read", "order.read"));

        assertEquals(
                java.util.List.of("customer.lookup", "order.lookup"),
                registry.findByCapability("order.read").stream().map(ToolDefinition::toolId).toList());
    }

    @Test
    void rejectsDuplicateToolId() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(tool("support.create-task", RiskLevel.HIGH, "support.write"));

        assertThrows(IllegalStateException.class, () -> {
            registry.register(tool("support.create-task", RiskLevel.HIGH, "support.write"));
        });
    }

    @Test
    void returnsEmptyForBlankCapability() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();

        assertTrue(registry.findByCapability(" ").isEmpty());
    }

    private static ToolDefinition tool(String id, RiskLevel riskLevel, String... capabilities) {
        return new ToolDefinition(
                id,
                id,
                "1.0.0",
                riskLevel,
                Set.of(capabilities),
                true);
    }
}
