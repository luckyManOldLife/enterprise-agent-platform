package com.xr.agent.gateway;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.ModelGatewayPort;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import com.xr.agent.domain.model.AgentTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelAgentInvokerTest {

    @Test
    void mapsModelContentToAnAgentResultWithTaskContext() {
        RecordingGateway gateway = new RecordingGateway();
        ModelAgentInvoker invoker = new ModelAgentInvoker(gateway, "gpt-5.5");
        AgentTask task = task();

        AgentInvokerPort.AgentInvocationResult result = invoker.invoke(task, agent());

        assertTrue(result.successful());
        assertEquals("completed order lookup", result.output().get("content"));
        assertEquals("gpt-5.5", result.output().get("model"));
        assertEquals(task.taskId().toString(), gateway.request.metadata().get("taskId"));
        assertEquals(task.tenantId(), gateway.request.metadata().get("tenantId"));
        assertTrue(gateway.request.userPrompt().contains("\"orderId\":\"o-1\""));
    }

    @Test
    void returnsTheModelErrorCodeWithoutLeakingTheProviderFailure() {
        ModelGatewayPort failingGateway = (task, request) -> {
            throw new ModelGatewayException("MODEL_UPSTREAM_HTTP_429");
        };
        ModelAgentInvoker invoker = new ModelAgentInvoker(failingGateway, "gpt-5.5");

        AgentInvokerPort.AgentInvocationResult result = invoker.invoke(task(), agent());

        assertFalse(result.successful());
        assertEquals("MODEL_UPSTREAM_HTTP_429", result.errorCode());
        assertTrue(result.output().isEmpty());
    }

    private static AgentTask task() {
        return AgentTask.create(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                "supervisor",
                "order-agent",
                Map.of("orderId", "o-1"),
                null);
    }

    private static AgentDefinition agent() {
        return new AgentDefinition(
                "order-agent",
                "Order Agent",
                "1.0.0",
                AgentStatus.ACTIVE,
                "model://cliproxyapi",
                Set.of("order.read"),
                null);
    }

    private static final class RecordingGateway implements ModelGatewayPort {
        private ModelRequest request;

        @Override
        public ModelResponse complete(AgentTask task, ModelRequest request) {
            this.request = request;
            return new ModelResponse(
                    "gpt-5.5",
                    "completed order lookup",
                    List.of(),
                    new Usage(12, 8, 20, 0));
        }
    }
}
