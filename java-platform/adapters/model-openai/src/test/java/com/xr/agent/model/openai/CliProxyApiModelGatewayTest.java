package com.xr.agent.model.openai;

import com.xr.agent.application.port.out.ModelGatewayPort;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.gateway.ModelGatewayException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliProxyApiModelGatewayTest {

    @Test
    void sendsOpenAiCompatibleChatRequestAndMapsTheResponse() {
        AgentTask task = task();
        RecordingTransport transport = new RecordingTransport(200, """
                {
                  "model":"gpt-5.5",
                  "choices":[{"message":{"content":"agent response"}}],
                  "usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}
                }
                """);
        CliProxyApiModelGateway gateway = new CliProxyApiModelGateway(
                URI.create("http://127.0.0.1:8317/v1/"),
                "test-key",
                "gpt-5.5",
                Duration.ofSeconds(10),
                transport);

        ModelGatewayPort.ModelResponse response = gateway.complete(task, request());

        assertEquals("agent response", response.content());
        assertEquals(18, response.usage().totalTokens());
        assertEquals(URI.create("http://127.0.0.1:8317/v1/chat/completions"), transport.uri);
        assertEquals("Bearer test-key", transport.authorization);
        assertTrue(transport.body.contains("\"model\":\"gpt-5.5\""));
        assertTrue(transport.body.contains("\"user\":\"" + task.taskId() + "\""));
    }

    @Test
    void mapsNonSuccessfulResponsesToStableErrorCodes() {
        CliProxyApiModelGateway gateway = new CliProxyApiModelGateway(
                URI.create("http://127.0.0.1:8317/v1/"),
                "test-key",
                "gpt-5.5",
                Duration.ofSeconds(10),
                new RecordingTransport(429, "{\"error\":{\"message\":\"quota\"}}"));

        ModelGatewayException exception = assertThrows(
                ModelGatewayException.class, () -> gateway.complete(task(), request()));

        assertEquals("MODEL_UPSTREAM_HTTP_429", exception.errorCode());
    }

    private static AgentTask task() {
        return AgentTask.create(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                "supervisor",
                "order-agent",
                Map.of("request", "lookup"),
                null);
    }

    private static ModelGatewayPort.ModelRequest request() {
        return new ModelGatewayPort.ModelRequest(
                "gpt-5.5",
                "system prompt",
                "user prompt",
                List.of(),
                Map.of());
    }

    private static final class RecordingTransport implements CliProxyApiModelGateway.HttpTransport {
        private final int statusCode;
        private final String responseBody;
        private URI uri;
        private String authorization;
        private String body;

        private RecordingTransport(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public CliProxyApiModelGateway.HttpResult post(
                URI uri,
                String authorization,
                String body,
                Duration timeout) {
            this.uri = uri;
            this.authorization = authorization;
            this.body = body;
            return new CliProxyApiModelGateway.HttpResult(statusCode, responseBody);
        }
    }
}
