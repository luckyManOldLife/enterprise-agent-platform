package com.xr.agent.worker;

import com.xr.agent.persistence.postgres.DriverManagerDataSource;
import com.xr.agent.persistence.postgres.JdbcPostgresPersistenceAdapter;
import com.xr.agent.persistence.postgres.JdkJsonMapCodec;

import java.util.Map;
import java.util.Objects;

final class AgentWorkerRuntime implements AutoCloseable {

    private final WorkerPollingLoop pollingLoop;

    private AgentWorkerRuntime(WorkerPollingLoop pollingLoop) {
        this.pollingLoop = Objects.requireNonNull(pollingLoop, "pollingLoop");
    }

    static AgentWorkerRuntime create(Map<String, String> environment) {
        WorkerRuntimeConfiguration configuration = WorkerRuntimeConfiguration.fromEnvironment(environment);
        JdbcPostgresPersistenceAdapter persistence = new JdbcPostgresPersistenceAdapter(
                new DriverManagerDataSource(
                        configuration.postgresUrl(),
                        configuration.postgresUser(),
                        configuration.postgresPassword()),
                new JdkJsonMapCodec());
        TaskOutboxWorker worker = CliProxyApiWorkerConfiguration.create(
                persistence,
                persistence,
                ModelAgentRegistryFactory.create(configuration.agentIds()),
                environment,
                configuration.maxAttempts());
        return new AgentWorkerRuntime(new WorkerPollingLoop(
                worker::runOnce,
                configuration.batchSize(),
                configuration.pollInterval()));
    }

    void runUntilStopped() {
        pollingLoop.runUntilStopped();
    }

    @Override
    public void close() {
        pollingLoop.stop();
    }
}
