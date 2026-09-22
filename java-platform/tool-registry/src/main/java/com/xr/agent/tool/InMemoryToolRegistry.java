package com.xr.agent.tool;

import com.xr.agent.application.port.out.ToolRegistryPort;
import com.xr.agent.domain.model.ToolDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryToolRegistry implements ToolRegistryPort {

    private final ConcurrentMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    @Override
    public void register(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool");
        ToolDefinition existing = tools.putIfAbsent(tool.toolId(), tool);
        if (existing != null) {
            throw new IllegalStateException("Tool already registered: " + tool.toolId());
        }
    }

    @Override
    public List<ToolDefinition> findByCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            return List.of();
        }
        return tools.values().stream()
                .filter(tool -> tool.capabilities().contains(capability))
                .sorted(Comparator.comparing(ToolDefinition::toolId))
                .toList();
    }

    @Override
    public Optional<ToolDefinition> findById(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(toolId));
    }
}
