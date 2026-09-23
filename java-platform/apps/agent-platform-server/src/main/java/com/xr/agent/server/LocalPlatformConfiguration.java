package com.xr.agent.server;

import com.xr.agent.api.PlatformApiFacade;
import com.xr.agent.application.port.in.ApprovalUseCase;
import com.xr.agent.application.port.in.TaskUseCase;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.registry.InMemoryAgentRegistry;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import com.xr.agent.policy.ApprovalWorkflowService;
import com.xr.agent.policy.InMemoryApprovalRepository;
import com.xr.agent.task.InMemoryTaskPersistence;
import com.xr.agent.application.service.DefaultTaskService;

import java.util.Set;

public final class LocalPlatformConfiguration {

    private LocalPlatformConfiguration() {
    }

    public static PlatformApiFacade createApi() {
        InMemoryTaskPersistence taskPersistence = new InMemoryTaskPersistence();
        TaskEventStorePort taskEventStore = new InMemoryTaskEventStore();
        TaskUseCase taskUseCase = new DefaultTaskService(taskPersistence, taskEventStore);
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(new AgentDefinition(
                "supervisor",
                "Supervisor",
                "0.1.0",
                AgentStatus.ACTIVE,
                "local://supervisor",
                Set.of("task.plan"),
                null));
        ApprovalUseCase approvalUseCase = new ApprovalWorkflowService(new InMemoryApprovalRepository());
        return new PlatformApiFacade(taskUseCase, registry, approvalUseCase, taskEventStore);
    }
}
