package com.xr.agent.worker;

import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.TaskEventStorePort;
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
        return create(
                outboxStore,
                taskPersistence,
                agentRegistry,
                environment,
                positiveInt(environment, "WORKER_MAX_ATTEMPTS", TaskOutboxWorker.DEFAULT_MAX_ATTEMPTS));
    }

    public static TaskOutboxWorker create(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            AgentRegistryPort agentRegistry,
            Map<String, String> environment,
            int maxAttempts) {
        Objects.requireNonNull(environment, "environment");
        CliProxyApiModelGateway modelGateway = CliProxyApiModelGateway.fromEnvironment(environment);
        String model = environment.getOrDefault("CLIPROXY_MODEL", "gpt-5.5");
        return new TaskOutboxWorker(
                outboxStore,
                taskPersistence,
                taskPersistence instanceof TaskEventStorePort taskEvents
                        ? taskEvents
                        : null,
                agentRegistry,
                new ModelAgentInvoker(modelGateway, model),
                java.time.Clock.systemUTC(),
                TaskOutboxWorker.DEFAULT_PROCESSING_LEASE,
                TaskOutboxWorker.DEFAULT_RETRY_DELAY,
                maxAttempts);
    }

    public static TaskOutboxWorker create(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            AgentRegistryPort agentRegistry) {
        return create(outboxStore, taskPersistence, agentRegistry, System.getenv());
    }

    private static int positiveInt(Map<String, String> environment, String name, int defaultValue) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
    }
}
