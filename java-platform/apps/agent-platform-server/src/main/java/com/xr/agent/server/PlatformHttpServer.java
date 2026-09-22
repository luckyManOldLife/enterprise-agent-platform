package com.xr.agent.server;

import com.xr.agent.api.PlatformApiFacade;
import com.xr.agent.application.port.in.ApprovalUseCase;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlatformHttpServer implements AutoCloseable {

    private static final int MAX_BODY_BYTES = 1_048_576;

    private final HttpServer server;
    private final PlatformApiFacade api;
    private final ExecutorService executor;

    private PlatformHttpServer(HttpServer server, PlatformApiFacade api) {
        this.server = server;
        this.api = api;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.server.createContext("/", this::handle);
        this.server.setExecutor(executor);
    }

    public static PlatformHttpServer create(PlatformApiFacade api, int port) {
        try {
            return new PlatformHttpServer(
                    HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0),
                    api);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to bind HTTP server", exception);
        }
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private void handle(HttpExchange exchange) {
        try {
            route(exchange);
        } catch (IllegalArgumentException exception) {
            sendError(exchange, ApiExceptionHandler.handleBadRequest(exception));
        } catch (IllegalStateException exception) {
            sendError(exchange, ApiExceptionHandler.handleConflict(exception));
        } catch (Exception exception) {
            sendError(exchange, ApiExceptionHandler.handleUnexpected(exception));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("GET".equals(method) && "/healthz".equals(path)) {
            sendJson(exchange, 200, HealthController.health());
            return;
        }
        if ("GET".equals(method) && "/api/agents".equals(path)) {
            String tenantId = requiredQuery(exchange.getRequestURI(), "tenantId");
            sendJson(exchange, 200, api.listAgents(tenantId).stream()
                    .map(PlatformHttpServer::agentJson)
                    .toList());
            return;
        }
        if ("POST".equals(method) && "/api/tasks".equals(path)) {
            PlatformApiFacade.CreateTaskRequest request = parseCreateTask(readBody(exchange));
            sendJson(exchange, 202, taskJson(api.createTask(request)));
            return;
        }
        if ("GET".equals(method) && path.startsWith("/api/tasks/")
                && path.endsWith("/events")) {
            UUID taskId = uuidFromPath(path, "/api/tasks/", "/events");
            sendEvents(exchange, api.streamTaskEvents(taskId));
            return;
        }
        if ("GET".equals(method) && path.startsWith("/api/tasks/")) {
            UUID taskId = uuidFromPath(path, "/api/tasks/", "");
            sendJson(exchange, 200, taskJson(api.getTask(taskId)));
            return;
        }
        if ("GET".equals(method) && "/api/approvals".equals(path)) {
            String tenantId = requiredQuery(exchange.getRequestURI(), "tenantId");
            sendJson(exchange, 200, api.listApprovals(tenantId).stream()
                    .map(PlatformHttpServer::approvalJson)
                    .toList());
            return;
        }
        if ("POST".equals(method) && path.startsWith("/api/approvals/")) {
            UUID approvalId = uuidFromPath(path, "/api/approvals/", "");
            PlatformApiFacade.ApprovalDecisionRequest request = parseApprovalDecision(readBody(exchange));
            sendJson(exchange, 200, approvalJson(api.decideApproval(approvalId, request)));
            return;
        }

        sendJson(exchange, 404, Map.of(
                "code", "NOT_FOUND",
                "message", "Route not found"));
    }

    private void sendEvents(
            HttpExchange exchange,
            List<PlatformApiFacade.TaskEventResponse> events) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-cache");
        StringBuilder body = new StringBuilder();
        for (PlatformApiFacade.TaskEventResponse event : events) {
            body.append("id: ").append(event.eventId()).append('\n');
            body.append("event: ").append(event.eventType()).append('\n');
            body.append("data: ").append(JsonSupport.stringify(eventJson(event))).append("\n\n");
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, ApiExceptionHandler.ApiError error) {
        try {
            sendJson(exchange, error.status(), error.body());
        } catch (IOException ignored) {
            exchange.close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = JsonSupport.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static PlatformApiFacade.CreateTaskRequest parseCreateTask(String body) {
        Map<String, Object> json = JsonSupport.parseObject(body);
        return new PlatformApiFacade.CreateTaskRequest(
                requiredString(json, "tenantId"),
                requiredString(json, "userId"),
                optionalString(json, "traceId"),
                optionalString(json, "conversationId"),
                stringList(json.get("roles")),
                requiredString(json, "input"),
                optionalString(json, "idempotencyKey"));
    }

    private static PlatformApiFacade.ApprovalDecisionRequest parseApprovalDecision(String body) {
        Map<String, Object> json = JsonSupport.parseObject(body);
        return new PlatformApiFacade.ApprovalDecisionRequest(
                requiredString(json, "tenantId"),
                requiredString(json, "actor"),
                ApprovalUseCase.ApprovalDecision.valueOf(requiredString(json, "decision")));
    }

    private static String requiredQuery(URI uri, String key) {
        String value = query(uri).get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 1
                    ? ""
                    : URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private static UUID uuidFromPath(String path, String prefix, String suffix) {
        String raw = path.substring(prefix.length(), path.length() - suffix.length());
        if (raw.isBlank() || raw.contains("/")) {
            throw new IllegalArgumentException("Invalid UUID path");
        }
        return UUID.fromString(raw);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        String contentLengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLengthHeader != null && Long.parseLong(contentLengthHeader) > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Request body is too large");
        }
        try (InputStream input = exchange.getRequestBody()) {
            byte[] bytes = input.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Request body is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String requiredString(Map<String, Object> json, String key) {
        String value = optionalString(json, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private static String optionalString(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return string;
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("roles must be an array");
        }
        List<String> strings = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String string)) {
                throw new IllegalArgumentException("roles must contain strings");
            }
            strings.add(string);
        }
        return List.copyOf(strings);
    }

    private static Map<String, Object> agentJson(PlatformApiFacade.AgentResponse agent) {
        return map(
                "agentId", agent.agentId(),
                "name", agent.name(),
                "version", agent.version(),
                "status", agent.status(),
                "capabilities", agent.capabilities(),
                "tenantScope", agent.tenantScope());
    }

    private static Map<String, Object> taskJson(PlatformApiFacade.TaskResponse task) {
        return map(
                "taskId", task.taskId(),
                "traceId", task.traceId(),
                "tenantId", task.tenantId(),
                "userId", task.userId(),
                "status", task.status(),
                "output", task.output(),
                "errorCode", task.errorCode());
    }

    private static Map<String, Object> eventJson(PlatformApiFacade.TaskEventResponse event) {
        return map(
                "eventId", event.eventId(),
                "taskId", event.taskId(),
                "traceId", event.traceId(),
                "eventType", event.eventType(),
                "payload", event.payload(),
                "createdAt", event.createdAt());
    }

    private static Map<String, Object> approvalJson(PlatformApiFacade.ApprovalResponse approval) {
        return map(
                "approvalId", approval.approvalId(),
                "taskId", approval.taskId(),
                "tenantId", approval.tenantId(),
                "requestedBy", approval.requestedBy(),
                "reason", approval.reason(),
                "status", approval.status(),
                "expiresAt", approval.expiresAt(),
                "decidedBy", approval.decidedBy(),
                "decidedAt", approval.decidedAt());
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}
