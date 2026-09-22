package com.xr.agent.model.openai;

import com.xr.agent.application.port.out.ModelGatewayPort;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.gateway.ModelGatewayException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI-compatible adapter for a locally deployed CLIProxyAPI instance.
 */
public final class CliProxyApiModelGateway implements ModelGatewayPort {

    private final URI baseUri;
    private final String apiKey;
    private final String defaultModel;
    private final Duration timeout;
    private final HttpTransport transport;

    public CliProxyApiModelGateway(
            URI baseUri,
            String apiKey,
            String defaultModel,
            Duration timeout) {
        this(baseUri, apiKey, defaultModel, timeout, new JdkHttpTransport());
    }

    public CliProxyApiModelGateway(
            URI baseUri,
            String apiKey,
            String defaultModel,
            Duration timeout,
            HttpTransport transport) {
        this.baseUri = requireBaseUri(baseUri);
        this.apiKey = requireText(apiKey, "apiKey");
        this.defaultModel = requireText(defaultModel, "defaultModel");
        this.timeout = requirePositive(timeout, "timeout");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public static CliProxyApiModelGateway fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String baseUrl = environment.getOrDefault("CLIPROXY_API_BASE_URL", "http://127.0.0.1:8317/v1");
        String apiKey = environment.get("CLIPROXY_API_KEY");
        String model = environment.getOrDefault("CLIPROXY_MODEL", "gpt-5.5");
        String timeoutSeconds = environment.getOrDefault("CLIPROXY_TIMEOUT_SECONDS", "30");
        try {
            return new CliProxyApiModelGateway(
                    URI.create(baseUrl),
                    apiKey,
                    model,
                    Duration.ofSeconds(Long.parseLong(timeoutSeconds)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("CLIPROXY_TIMEOUT_SECONDS must be a positive integer", exception);
        }
    }

    @Override
    public ModelResponse complete(AgentTask task, ModelRequest request) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(request, "request");
        String model = request.model() == null || request.model().isBlank()
                ? defaultModel
                : request.model();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        payload.put("user", task.taskId().toString());
        payload.put("stream", false);

        HttpResult result;
        try {
            result = transport.post(
                    baseUri.resolve("chat/completions"),
                    "Bearer " + apiKey,
                    JsonCodec.toJson(payload),
                    timeout);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelGatewayException("MODEL_UPSTREAM_INTERRUPTED", exception);
        } catch (IOException exception) {
            throw new ModelGatewayException("MODEL_UPSTREAM_UNAVAILABLE", exception);
        } catch (RuntimeException exception) {
            throw new ModelGatewayException("MODEL_UPSTREAM_REQUEST_FAILED", exception);
        }
        if (result.statusCode() < 200 || result.statusCode() >= 300) {
            throw new ModelGatewayException("MODEL_UPSTREAM_HTTP_" + result.statusCode());
        }
        return mapResponse(result.body(), model);
    }

    @SuppressWarnings("unchecked")
    private static ModelResponse mapResponse(String responseBody, String requestedModel) {
        try {
            Map<String, Object> root = JsonCodec.parseObject(responseBody);
            List<Object> choices = (List<Object>) root.get("choices");
            if (choices == null || choices.isEmpty() || !(choices.getFirst() instanceof Map<?, ?> choice)) {
                throw new IllegalArgumentException("choices missing");
            }
            Object messageValue = choice.get("message");
            if (!(messageValue instanceof Map<?, ?> message)) {
                throw new IllegalArgumentException("message missing");
            }
            Object contentValue = message.get("content");
            String content = contentValue == null ? "" : String.valueOf(contentValue);
            List<ToolCall> toolCalls = mapToolCalls(message.get("tool_calls"));
            Map<String, Object> usageMap = root.get("usage") instanceof Map<?, ?> usage
                    ? (Map<String, Object>) usage
                    : Map.of();
            return new ModelResponse(
                    text(root.get("model"), requestedModel),
                    content,
                    toolCalls,
                    new Usage(
                            number(usageMap.get("prompt_tokens")),
                            number(usageMap.get("completion_tokens")),
                            number(usageMap.get("total_tokens")),
                            0));
        } catch (RuntimeException exception) {
            throw new ModelGatewayException("MODEL_RESPONSE_INVALID", exception);
        }
    }

    private static List<ToolCall> mapToolCalls(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> call) || !(call.get("function") instanceof Map<?, ?> function)) {
                continue;
            }
            String name = text(function.get("name"), null);
            if (name == null || name.isBlank()) {
                continue;
            }
            String arguments = text(function.get("arguments"), "{}");
            calls.add(new ToolCall(name, JsonCodec.parseObject(arguments)));
        }
        return List.copyOf(calls);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static URI requireBaseUri(URI value) {
        Objects.requireNonNull(value, "baseUri");
        String rendered = value.toString();
        if (!rendered.endsWith("/")) {
            rendered += "/";
        }
        return URI.create(rendered);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    public interface HttpTransport {
        HttpResult post(URI uri, String authorization, String body, Duration timeout)
                throws IOException, InterruptedException;
    }

    public record HttpResult(int statusCode, String body) {
        public HttpResult {
            body = body == null ? "" : body;
        }
    }

    private static final class JdkHttpTransport implements HttpTransport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        @Override
        public HttpResult post(URI uri, String authorization, String body, Duration timeout)
                throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        }
    }
}
