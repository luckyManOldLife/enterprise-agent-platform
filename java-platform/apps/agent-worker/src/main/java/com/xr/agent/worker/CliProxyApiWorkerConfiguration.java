package com.xr.agent.worker;

import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.gateway.ModelAgentInvoker;
import com.xr.agent.model.openai.CliProxyApiModelGateway;
import com.xr.agent.task.OutboxStorePort;

import java.util.Map;
import java.util.Objects;

/**
 * Wires a worker to the local OpenAI-compatible CLIProxyAPI through environment configuration.
 */
public final class CliProxyApiWorkerConfiguration {

    private CliProxyApiWorkerConfiguration() {
    }

    public static TaskOutboxWorker create(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            AgentRegistryPort agentRegistry,
            Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        CliProxyApiModelGateway modelGateway = CliProxyApiModelGateway.fromEnvironment(environment);
        String model = environment.getOrDefault("CLIPROXY_MODEL", "gpt-5.5");
        return new TaskOutboxWorker(
                outboxStore,
                taskPersistence,
                agentRegistry,
                new ModelAgentInvoker(modelGateway, model));
    }

    public static TaskOutboxWorker create(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            AgentRegistryPort agentRegistry) {
        return create(outboxStore, taskPersistence, agentRegistry, System.getenv());
    }
}
