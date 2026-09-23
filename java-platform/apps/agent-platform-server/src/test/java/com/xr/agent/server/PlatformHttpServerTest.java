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
        HttpResponse<String> health = request("/healthz").GET().send();
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"status\":\"UP\""));

        HttpResponse<String> agents = request("/api/agents").tenant("tenant-a").GET().send();
        assertEquals(200, agents.statusCode());
        assertTrue(agents.body().contains("\"agentId\":\"supervisor\""));
    }

    @Test
    void acceptsTaskCreationAndReturnsTraceContext() throws Exception {
        HttpResponse<String> response = post("/api/tasks", """
                {
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
                  "input": " "
                }
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"BAD_REQUEST\""));
    }

    @Test
    void rejectsTaskCreationWithoutServerSideIdentityHeaders() throws Exception {
        HttpResponse<String> response = request("/api/tasks")
                .POST("""
                        {"input": "query recent orders"}
                        """)
                .send();

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("X-Tenant-Id header must not be blank"));
    }

    @Test
    void ignoresBodyTenantAndUserWhenHeadersArePresent() throws Exception {
        HttpResponse<String> response = request("/api/tasks")
                .tenant("tenant-a")
                .user("user-a")
                .POST("""
                        {
                          "tenantId": "tenant-b",
                          "userId": "user-b",
                          "input": "query recent orders"
                        }
                        """)
                .send();

        assertEquals(202, response.statusCode());
        assertTrue(response.body().contains("\"tenantId\":\"tenant-a\""));
        assertTrue(response.body().contains("\"userId\":\"user-a\""));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return request(path)
                .tenant("tenant-a")
                .user("user-a")
                .POST(body)
                .send();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private Request request(String path) {
        return new Request(HttpRequest.newBuilder(uri(path)));
    }

    private final class Request {
        private final HttpRequest.Builder builder;

        private Request(HttpRequest.Builder builder) {
            this.builder = builder;
        }

        private Request tenant(String tenantId) {
            builder.header("X-Tenant-Id", tenantId);
            return this;
        }

        private Request user(String userId) {
            builder.header("X-User-Id", userId);
            return this;
        }

        private Request GET() {
            builder.GET();
            return this;
        }

        private Request POST(String body) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            return this;
        }

        private HttpResponse<String> send() throws Exception {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
