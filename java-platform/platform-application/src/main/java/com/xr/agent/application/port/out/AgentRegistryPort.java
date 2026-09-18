package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentDefinition;

import java.util.List;
import java.util.Optional;

public interface AgentRegistryPort {

    List<AgentDefinition> findAvailable(String tenantId);

    Optional<AgentDefinition> findById(String agentId);
}
