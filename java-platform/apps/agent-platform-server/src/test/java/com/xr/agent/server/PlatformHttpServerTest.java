package com.xr.agent.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.SocketException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformHttpServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private PlatformHttpServer server;

    @BeforeEach
    void setUp() {
        try {
            server = PlatformHttpServer.create(LocalPlatformConfiguration.createApi(), 0);
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof SocketException) {
                Assumptions.abort("Environment does not permit local HTTP sockets");
            }
            throw exception;
        }
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesHealthAndTenantScopedAgents() throws Exception {
        HttpResponse<String> health = get("/healthz");
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\":\"UP\""));

        HttpResponse<String> agents = get("/api/agents?tenantId=tenant-a");
        assertEquals(200, agents.statusCode());
        assertTrue(agents.body().contains("\"agentId\":\"supervisor\""));
    }

    @Test
    void acceptsTaskCreationAndReturnsTraceContext() throws Exception {
        HttpResponse<String> response = post("/api/tasks", """
                {
                  "tenantId": "tenant-a",
                  "userId": "user-a",
                  "roles": ["support_operator"],
                  "input": "query recent orders"
                }
                """);

        assertEquals(202, response.statusCode());
        assertTrue(response.body().contains("\"tenantId\":\"tenant-a\""));
        assertTrue(response.body().contains("\"status\":\"CREATED\""));
        assertTrue(response.body().contains("\"traceId\""));
    }

    @Test
    void rejectsMalformedTaskRequest() throws Exception {
        HttpResponse<String> response = post("/api/tasks", """
                {
                  "tenantId": "tenant-a",
                  "userId": "user-a",
                  "input": " "
                }
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"BAD_REQUEST\""));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }
}
