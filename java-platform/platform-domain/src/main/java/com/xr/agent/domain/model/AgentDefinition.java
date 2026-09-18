package com.xr.agent.domain.model;

import java.util.Objects;
import java.util.Set;

public record AgentDefinition(
        String agentId,
        String name,
        String version,
        AgentStatus status,
        String endpoint,
        Set<String> capabilities,
        String tenantScope) {

    public AgentDefinition {
        requireText(agentId, "agentId");
        requireText(name, "name");
        requireText(version, "version");
        requireText(endpoint, "endpoint");
        status = Objects.requireNonNull(status, "status");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    public boolean isAvailableFor(String tenantId) {
        return status == AgentStatus.ACTIVE
                && (tenantScope == null || tenantScope.isBlank() || tenantScope.equals(tenantId));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
