package com.xr.agent.server;

import com.xr.agent.api.PlatformApiFacade;
import com.xr.agent.application.service.DefaultTaskService;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import com.xr.agent.persistence.postgres.DriverManagerDataSource;
import com.xr.agent.persistence.postgres.JdbcPostgresPersistenceAdapter;
import com.xr.agent.persistence.postgres.JdkJsonMapCodec;
import com.xr.agent.policy.ApprovalWorkflowService;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class PostgresPlatformConfiguration {

    private PostgresPlatformConfiguration() {
    }

    static PlatformApiFacade createApi(Map<String, String> environment) {
        JdbcPostgresPersistenceAdapter persistence = new JdbcPostgresPersistenceAdapter(
                new DriverManagerDataSource(
                        required(environment, "POSTGRES_URL"),
                        required(environment, "POSTGRES_USER"),
                        required(environment, "POSTGRES_PASSWORD")),
                new JdkJsonMapCodec());
        for (AgentDefinition agent : modelAgentDefinitions(environment.get("MODEL_AGENT_IDS"))) {
            persistence.register(agent);
        }
        return new PlatformApiFacade(
                new DefaultTaskService(persistence, persistence),
                persistence,
                new ApprovalWorkflowService(persistence),
                persistence);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        if ("POSTGRES_URL".equals(name) && !value.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("POSTGRES_URL must use jdbc:postgresql");
        }
        return value;
    }

    static List<AgentDefinition> modelAgentDefinitions(String configuredIds) {
        return modelAgentIds(configuredIds).stream()
                .map(agentId -> new AgentDefinition(
                        agentId,
                        agentId,
                        "1.0.0",
                        AgentStatus.ACTIVE,
                        "model://cliproxyapi",
                        Set.of("task.execute"),
                        null))
                .toList();
    }

    private static List<String> modelAgentIds(String configuredIds) {
        String raw = configuredIds == null || configuredIds.isBlank() ? "supervisor" : configuredIds;
        Set<String> agentIds = new LinkedHashSet<>();
        for (String candidate : raw.split(",")) {
            String agentId = candidate.trim();
            if (!agentId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
                throw new IllegalArgumentException("MODEL_AGENT_IDS contains an invalid agent id");
            }
            if (!agentIds.add(agentId)) {
                throw new IllegalArgumentException("MODEL_AGENT_IDS must not contain duplicates");
            }
        }
        return List.copyOf(new ArrayList<>(agentIds));
    }
}
