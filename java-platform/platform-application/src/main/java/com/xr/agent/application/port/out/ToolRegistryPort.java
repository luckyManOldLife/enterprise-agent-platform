package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.ToolDefinition;

import java.util.List;
import java.util.Optional;

public interface ToolRegistryPort {

    void register(ToolDefinition tool);

    List<ToolDefinition> findByCapability(String capability);

    Optional<ToolDefinition> findById(String toolId);
}
