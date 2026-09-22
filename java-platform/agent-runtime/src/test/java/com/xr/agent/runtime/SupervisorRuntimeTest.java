package com.xr.agent.runtime;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorRuntimeTest {

    @Test
    void runsAgentStepsWithInheritedContext() {
        TestAgentRegistry registry = new TestAgentRegistry();
        registry.register(agent("customer-agent", AgentStatus.ACTIVE, null));
        registry.register(agent("order-agent", AgentStatus.ACTIVE, null));
        RecordingInvoker invoker = new RecordingInvoker();
        SupervisorRuntime runtime = new SupervisorRuntime(registry, invoker);
        AgentTask task = supervisorTask();

        runtime.run(task, List.of(
                new SupervisorRuntime.AgentStep("customer-agent", Map.of("customerId", "c-1")),
                new SupervisorRuntime.AgentStep("order-agent", Map.of("customerId", "c-1"))));

        assertEquals(TaskStatus.SUCCEEDED, task.status());
        assertEquals(2, invoker.calls.size());
        assertEquals(task.taskId(), invoker.calls.getFirst().parentTaskId());
        assertEquals("tenant-a", invoker.calls.getFirst().tenantId());
        assertEquals("trace-a", invoker.calls.getFirst().traceId());
        assertTrue(task.output().containsKey("steps"));
    }

    @Test
    void failsWhenAgentIsUnavailableForTenant() {
        TestAgentRegistry registry = new TestAgentRegistry();
        registry.register(agent("support-agent", AgentStatus.ACTIVE, "tenant-b"));
        SupervisorRuntime runtime = new SupervisorRuntime(registry, new RecordingInvoker());
        AgentTask task = supervisorTask();

        runtime.run(task, List.of(new SupervisorRuntime.AgentStep("support-agent", Map.of())));

        assertEquals(TaskStatus.FAILED, task.status());
        assertEquals("SUPERVISOR_AGENT_UNAVAILABLE", task.errorCode());
    }

    @Test
    void failsParentWhenChildInvocationFails() {
        TestAgentRegistry registry = new TestAgentRegistry();
        registry.register(agent("order-agent", AgentStatus.ACTIVE, null));
        AgentInvokerPort failingInvoker = (task, agent) ->
                new AgentInvokerPort.AgentInvocationResult(false, Map.of(), "ORDER_AGENT_TIMEOUT");
        SupervisorRuntime runtime = new SupervisorRuntime(registry, failingInvoker);
        AgentTask task = supervisorTask();

        runtime.run(task, List.of(new SupervisorRuntime.AgentStep("order-agent", Map.of())));

        assertEquals(TaskStatus.FAILED, task.status());
        assertEquals("ORDER_AGENT_TIMEOUT", task.errorCode());
    }

    private static AgentTask supervisorTask() {
        return AgentTask.create(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                "api",
                "supervisor",
                Map.of("request", "create after-sales task"),
                null);
    }

    private static AgentDefinition agent(String id, AgentStatus status, String tenantScope) {
        return new AgentDefinition(
                id,
                id,
                "1.0.0",
                status,
                "a2a://" + id,
                Set.of("task.handle"),
                tenantScope);
    }

    private static final class RecordingInvoker implements AgentInvokerPort {
        private final List<AgentTask> calls = new ArrayList<>();

        @Override
        public AgentInvocationResult invoke(AgentTask task, AgentDefinition agent) {
            calls.add(task);
            return new AgentInvocationResult(true, Map.of("handledBy", agent.agentId()), null);
        }
    }

    private static final class TestAgentRegistry implements AgentRegistryPort {
        private final Map<String, AgentDefinition> agents = new java.util.HashMap<>();

        @Override
        public void register(AgentDefinition agent) {
            agents.put(agent.agentId(), agent);
        }

        @Override
        public List<AgentDefinition> findAvailable(String tenantId) {
            return agents.values().stream()
                    .filter(agent -> agent.isAvailableFor(tenantId))
                    .toList();
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.ofNullable(agents.get(agentId));
        }
    }
}
