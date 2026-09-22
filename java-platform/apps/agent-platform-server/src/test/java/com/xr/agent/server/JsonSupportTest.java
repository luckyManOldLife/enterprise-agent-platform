package com.xr.agent.server;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonSupportTest {

    @Test
    void parsesNestedRequestValues() {
        Map<String, Object> json = JsonSupport.parseObject("""
                {
                  "tenantId": "tenant-a",
                  "roles": ["operator", "approver"],
                  "enabled": true,
                  "metadata": {"attempt": 2}
                }
                """);

        assertEquals("tenant-a", json.get("tenantId"));
        assertEquals(List.of("operator", "approver"), json.get("roles"));
        assertEquals(Boolean.TRUE, json.get("enabled"));
        assertEquals(2L, ((Map<?, ?>) json.get("metadata")).get("attempt"));
    }

    @Test
    void serializesNullsCollectionsAndInstants() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", "CREATED");
        value.put("items", List.of("a", "b"));
        value.put("createdAt", Instant.parse("2026-09-21T10:00:00Z"));
        value.put("output", null);
        String json = JsonSupport.stringify(value);

        assertEquals(
                "{\"status\":\"CREATED\",\"items\":[\"a\",\"b\"],"
                        + "\"createdAt\":\"2026-09-21T10:00:00Z\",\"output\":null}",
                json);
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> {
            JsonSupport.parseObject("{\"tenantId\":\"tenant-a\"} trailing");
        });
    }
}
