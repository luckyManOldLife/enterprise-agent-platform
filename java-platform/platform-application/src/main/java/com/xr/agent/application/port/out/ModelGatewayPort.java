package com.xr.agent.application.port.out;

import com.xr.agent.domain.model.AgentTask;

import java.util.List;
import java.util.Map;

public interface ModelGatewayPort {

    ModelResponse complete(AgentTask task, ModelRequest request);

    record ModelRequest(
            String model,
            String systemPrompt,
            String userPrompt,
            List<String> tools,
            Map<String, Object> metadata) {
    }

    record ModelResponse(
            String model,
            String content,
            List<ToolCall> toolCalls,
            Usage usage) {
    }

    record ToolCall(String name, Map<String, Object> arguments) {
    }

    record Usage(long inputTokens, long outputTokens, long totalTokens, double estimatedCost) {
    }
}
