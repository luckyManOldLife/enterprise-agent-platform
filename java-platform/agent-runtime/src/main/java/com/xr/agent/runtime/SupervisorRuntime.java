package com.xr.agent.runtime;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SupervisorRuntime {

    private final AgentRegistryPort agentRegistry;
    private final AgentInvokerPort agentInvoker;

    public SupervisorRuntime(AgentRegistryPort agentRegistry, AgentInvokerPort agentInvoker) {
        this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry");
        this.agentInvoker = Objects.requireNonNull(agentInvoker, "agentInvoker");
    }

    public AgentTask run(AgentTask task, List<AgentStep> steps) {
        Objects.requireNonNull(task, "task");
        List<AgentStep> plan = List.copyOf(Objects.requireNonNull(steps, "steps"));
        task.start();

        if (plan.isEmpty()) {
            task.fail("SUPERVISOR_PLAN_EMPTY");
            return task;
        }

        List<Map<String, Object>> stepOutputs = new ArrayList<>();
        for (AgentStep step : plan) {
            AgentDefinition agent = agentRegistry.findById(step.agentId())
                    .filter(candidate -> candidate.isAvailableFor(task.tenantId()))
                    .orElse(null);
            if (agent == null) {
                task.fail("SUPERVISOR_AGENT_UNAVAILABLE");
                return task;
            }

            AgentTask child = AgentTask.childOf(task, agent.agentId(), step.input(), step.deadline());
            child.start();
            AgentInvokerPort.AgentInvocationResult result = invoke(child, agent);
            if (!result.successful()) {
                child.fail(result.errorCode() == null ? "AGENT_INVOCATION_FAILED" : result.errorCode());
                task.fail(child.errorCode());
                return task;
            }

            child.succeed(result.output());
            stepOutputs.add(Map.of(
                    "agentId", agent.agentId(),
                    "taskId", child.taskId().toString(),
                    "output", child.output()));
        }

        task.succeed(Map.of("steps", List.copyOf(stepOutputs)));
        return task;
    }

    private AgentInvokerPort.AgentInvocationResult invoke(AgentTask child, AgentDefinition agent) {
        try {
            AgentInvokerPort.AgentInvocationResult result = agentInvoker.invoke(child, agent);
            if (result == null) {
                return new AgentInvokerPort.AgentInvocationResult(
                        false,
                        Map.of(),
                        "AGENT_INVOCATION_EMPTY_RESULT");
            }
            return result;
        } catch (RuntimeException exception) {
            return new AgentInvokerPort.AgentInvocationResult(
                    false,
                    Map.of(),
                    exception.getClass().getSimpleName());
        }
    }

    public record AgentStep(
            String agentId,
            Map<String, Object> input,
            Instant deadline) {

        public AgentStep {
            agentId = requireText(agentId, "agentId");
            input = Map.copyOf(input == null ? Map.of() : input);
        }

        public AgentStep(String agentId, Map<String, Object> input) {
            this(agentId, input, null);
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }
    }
}
