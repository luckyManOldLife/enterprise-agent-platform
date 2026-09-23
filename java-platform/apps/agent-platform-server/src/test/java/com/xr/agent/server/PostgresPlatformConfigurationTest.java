package com.xr.agent.server;

import com.xr.agent.domain.model.AgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresPlatformConfigurationTest {

    @Test
    void buildsSharedModelAgentDefinitionsWithoutOpeningADatabaseConnection() {
        var agents = PostgresPlatformConfiguration.modelAgentDefinitions("supervisor,order-agent");

        assertEquals(
                java.util.List.of("supervisor", "order-agent"),
                agents.stream().map(AgentDefinition::agentId).toList());
        assertEquals("model://cliproxyapi", agents.getFirst().endpoint());
    }

    @Test
    void rejectsIncompleteOrInvalidPostgresConfiguration() {
        assertThrows(IllegalArgumentException.class, () ->
                PostgresPlatformConfiguration.createApi(Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                PostgresPlatformConfiguration.createApi(Map.of(
                        "POSTGRES_URL", "jdbc:mysql://database/platform",
                        "POSTGRES_USER", "agent",
                        "POSTGRES_PASSWORD", "test-password")));
        assertThrows(IllegalArgumentException.class, () ->
                PostgresPlatformConfiguration.createApi(Map.of(
                        "POSTGRES_URL", "jdbc:postgresql://database/platform",
                        "POSTGRES_USER", "agent",
                        "POSTGRES_PASSWORD", "test-password",
                        "MODEL_AGENT_IDS", "supervisor,supervisor")));
    }
}
