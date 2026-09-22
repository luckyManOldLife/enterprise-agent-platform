package com.xr.agent.gateway;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.ModelGatewayPort;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentTask;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModelAgentInvoker implements AgentInvokerPort {

    private final ModelGatewayPort modelGateway;
    private final String defaultModel;

    public ModelAgentInvoker(ModelGatewayPort modelGateway, String defaultModel) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
        this.defaultModel = requireText(defaultModel, "defaultModel");
    }

    @Override
    public AgentInvocationResult invoke(AgentTask task, AgentDefinition agent) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(agent, "agent");
        try {
            ModelGatewayPort.ModelResponse response = modelGateway.complete(
                    task,
                    new ModelGatewayPort.ModelRequest(
                            defaultModel,
                            "You are the " + agent.name()
                                    + ". Return a concise, factual result for the task input.",
                            JsonValueCodec.toJson(task.input()),
                            List.of(),
                            Map.of(
                                    "taskId", task.taskId().toString(),
                                    "tenantId", task.tenantId(),
                                    "traceId", task.traceId(),
                                    "agentId", agent.agentId())));
            ModelGatewayPort.Usage usage = response.usage() == null
                    ? new ModelGatewayPort.Usage(0, 0, 0, 0)
                    : response.usage();
            return new AgentInvocationResult(
                    true,
                    Map.of(
                            "content", response.content() == null ? "" : response.content(),
                            "model", response.model() == null || response.model().isBlank()
                                    ? defaultModel
                                    : response.model(),
                            "usage", Map.of(
                                    "inputTokens", usage.inputTokens(),
                                    "outputTokens", usage.outputTokens(),
                                    "totalTokens", usage.totalTokens())),
                    null);
        } catch (ModelGatewayException exception) {
            return new AgentInvocationResult(false, Map.of(), exception.errorCode());
        } catch (RuntimeException exception) {
            return new AgentInvocationResult(false, Map.of(), "MODEL_INVOCATION_FAILED");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
