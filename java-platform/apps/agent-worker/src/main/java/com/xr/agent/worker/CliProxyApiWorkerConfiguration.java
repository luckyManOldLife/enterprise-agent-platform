package com.xr.agent.worker;

import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.ApprovalRepositoryPort;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.domain.model.RiskLevel;
import com.xr.agent.domain.model.ToolDefinition;
import com.xr.agent.gateway.ModelAgentInvoker;
import com.xr.agent.model.openai.CliProxyApiModelGateway;
import com.xr.agent.policy.RuleBasedPolicyEngine;
import com.xr.agent.task.OutboxStorePort;
import com.xr.agent.tool.InMemoryToolRegistry;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        Clock clock = Clock.systemUTC();
        return new TaskOutboxWorker(
                outboxStore,
                taskPersistence,
                taskPersistence instanceof TaskEventStorePort taskEvents
                        ? taskEvents
                        : null,
                agentRegistry,
                new ModelAgentInvoker(modelGateway, model),
                defaultToolRegistry(),
                RuleBasedPolicyEngine.defaults(),
                new LocalToolExecutor(),
                taskPersistence instanceof ApprovalRepositoryPort approvals
                        ? approvals
                        : null,
                clock,
                TaskOutboxWorker.DEFAULT_PROCESSING_LEASE,
                TaskOutboxWorker.DEFAULT_RETRY_DELAY,
                approvalTtl(environment),
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

    private static Duration approvalTtl(Map<String, String> environment) {
        return Duration.ofSeconds(positiveInt(
                environment,
                "WORKER_APPROVAL_TTL_SECONDS",
                Math.toIntExact(TaskOutboxWorker.DEFAULT_APPROVAL_TTL.toSeconds())));
    }

    private static InMemoryToolRegistry defaultToolRegistry() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new ToolDefinition(
                "order.lookup",
                "Order Lookup",
                "1.0.0",
                RiskLevel.LOW,
                Set.of("order.read"),
                true));
        registry.register(new ToolDefinition(
                "customer.lookup",
                "Customer Lookup",
                "1.0.0",
                RiskLevel.LOW,
                Set.of("customer.read"),
                true));
        registry.register(new ToolDefinition(
                "support.create-task",
                "Create Support Task",
                "1.0.0",
                RiskLevel.HIGH,
                Set.of("support.write"),
                true));
        return registry;
    }
}
