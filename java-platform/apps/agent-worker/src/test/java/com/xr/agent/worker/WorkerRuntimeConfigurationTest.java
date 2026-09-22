package com.xr.agent.worker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerRuntimeConfigurationTest {

    @Test
    void readsPostgresAndPollingConfigurationWithoutExposingPassword() {
        WorkerRuntimeConfiguration configuration = WorkerRuntimeConfiguration.fromEnvironment(Map.of(
                "POSTGRES_URL", "jdbc:postgresql://postgres:5432/agent_platform",
                "POSTGRES_USER", "agent_worker",
                "POSTGRES_PASSWORD", "secret-value",
                "WORKER_BATCH_SIZE", "25",
                "WORKER_POLL_INTERVAL_MILLIS", "500",
                "WORKER_MODEL_AGENT_IDS", "supervisor,order-agent"));

        assertEquals("jdbc:postgresql://postgres:5432/agent_platform", configuration.postgresUrl());
        assertEquals("agent_worker", configuration.postgresUser());
        assertEquals(25, configuration.batchSize());
        assertEquals(Duration.ofMillis(500), configuration.pollInterval());
        assertEquals(java.util.List.of("supervisor", "order-agent"), configuration.agentIds());
    }

    @Test
    void defaultsToSupervisorAndConservativePollingValues() {
        WorkerRuntimeConfiguration configuration = WorkerRuntimeConfiguration.fromEnvironment(requiredEnvironment());

        assertEquals(WorkerRuntimeConfiguration.DEFAULT_BATCH_SIZE, configuration.batchSize());
        assertEquals(WorkerRuntimeConfiguration.DEFAULT_POLL_INTERVAL, configuration.pollInterval());
        assertEquals(java.util.List.of("supervisor"), configuration.agentIds());
    }

    @Test
    void rejectsInvalidDatabaseAndWorkerSettings() {
        assertThrows(IllegalArgumentException.class, () ->
                WorkerRuntimeConfiguration.fromEnvironment(Map.of(
                        "POSTGRES_URL", "jdbc:mysql://database/platform",
                        "POSTGRES_USER", "agent",
                        "POSTGRES_PASSWORD", "secret")));
        assertThrows(IllegalArgumentException.class, () ->
                WorkerRuntimeConfiguration.fromEnvironment(Map.of(
                        "POSTGRES_URL", "jdbc:postgresql://database/platform",
                        "POSTGRES_USER", "agent",
                        "POSTGRES_PASSWORD", "secret",
                        "WORKER_BATCH_SIZE", "0")));
        assertThrows(IllegalArgumentException.class, () ->
                WorkerRuntimeConfiguration.fromEnvironment(Map.of(
                        "POSTGRES_URL", "jdbc:postgresql://database/platform",
                        "POSTGRES_USER", "agent",
                        "POSTGRES_PASSWORD", "secret",
                        "WORKER_MODEL_AGENT_IDS", "supervisor,supervisor")));
    }

    private static Map<String, String> requiredEnvironment() {
        return Map.of(
                "POSTGRES_URL", "jdbc:postgresql://database/platform",
                "POSTGRES_USER", "agent",
                "POSTGRES_PASSWORD", "secret");
    }
}
