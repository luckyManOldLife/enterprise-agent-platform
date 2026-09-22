package com.xr.agent.persistence.postgres;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdkJsonMapCodecTest {

    private final JdkJsonMapCodec codec = new JdkJsonMapCodec();

    @Test
    void roundTripsNestedJsonCompatibleMaps() {
        Map<String, Object> value = Map.of(
                "content", "order found",
                "usage", Map.of("totalTokens", 18),
                "toolCalls", List.of(Map.of("name", "order.lookup")),
                "completedAt", Instant.parse("2026-09-22T02:00:00Z"));

        Map<String, Object> parsed = codec.fromJson(codec.toJson(value));

        assertEquals("order found", parsed.get("content"));
        assertEquals(18L, ((Map<?, ?>) parsed.get("usage")).get("totalTokens"));
        assertEquals("order.lookup", ((Map<?, ?>) ((List<?>) parsed.get("toolCalls")).getFirst()).get("name"));
        assertEquals("2026-09-22T02:00:00Z", parsed.get("completedAt"));
    }

    @Test
    void rejectsNonObjectDatabasePayloads() {
        assertThrows(IllegalArgumentException.class, () -> codec.fromJson("[]"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> codec.fromJson("{\"content\":}"));
    }
}
