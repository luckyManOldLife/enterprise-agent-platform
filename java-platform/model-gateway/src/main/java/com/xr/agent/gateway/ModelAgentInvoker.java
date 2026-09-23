package com.xr.agent.gateway;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.ModelGatewayPort;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("content", response.content() == null ? "" : response.content());
            output.put("model", response.model() == null || response.model().isBlank()
                    ? defaultModel
                    : response.model());
            output.put("usage", Map.of(
                    "inputTokens", usage.inputTokens(),
                    "outputTokens", usage.outputTokens(),
                    "totalTokens", usage.totalTokens()));
            List<Map<String, Object>> toolCalls = toolCalls(response.toolCalls());
            if (!toolCalls.isEmpty()) {
                output.put("toolCalls", toolCalls);
            }
            return new AgentInvocationResult(true, output, null);
        } catch (ModelGatewayException exception) {
            return new AgentInvocationResult(false, Map.of(), exception.errorCode());
        } catch (RuntimeException exception) {
            return new AgentInvocationResult(false, Map.of(), "MODEL_INVOCATION_FAILED");
        }
    }

    private static List<Map<String, Object>> toolCalls(List<ModelGatewayPort.ToolCall> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> calls = new ArrayList<>();
        for (ModelGatewayPort.ToolCall value : values) {
            if (value == null || value.name() == null || value.name().isBlank()) {
                continue;
            }
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("name", value.name());
            call.put("arguments", Collections.unmodifiableMap(
                    new LinkedHashMap<>(value.arguments() == null ? Map.of() : value.arguments())));
            calls.add(Collections.unmodifiableMap(call));
        }
        return List.copyOf(calls);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
