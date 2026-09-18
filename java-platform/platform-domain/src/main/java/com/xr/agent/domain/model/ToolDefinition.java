package com.xr.agent.domain.model;

import java.util.Objects;
import java.util.Set;

public record ToolDefinition(
        String toolId,
        String name,
        String version,
        RiskLevel riskLevel,
        Set<String> capabilities,
        boolean idempotent) {

    public ToolDefinition {
        requireText(toolId, "toolId");
        requireText(name, "name");
        requireText(version, "version");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    public boolean requiresApproval() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
