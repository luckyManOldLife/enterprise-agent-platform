package com.xr.agent.server;

import com.xr.agent.api.PlatformApiFacade;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresPlatformConfigurationTest {

    @Test
    void registersTheSharedModelAgentAllowlistWithoutOpeningADatabaseConnection() {
        PlatformApiFacade api = PostgresPlatformConfiguration.createApi(Map.of(
                "POSTGRES_URL", "jdbc:postgresql://postgres:5432/agent_platform",
                "POSTGRES_USER", "agent",
                "POSTGRES_PASSWORD", "test-password",
                "MODEL_AGENT_IDS", "supervisor,order-agent"));

        assertEquals(
                java.util.List.of("order-agent", "supervisor"),
                api.listAgents("tenant-a").stream().map(PlatformApiFacade.AgentResponse::agentId).toList());
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
