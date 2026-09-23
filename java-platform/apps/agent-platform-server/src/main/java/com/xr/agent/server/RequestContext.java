package com.xr.agent.server;

import com.sun.net.httpserver.Headers;

import java.util.ArrayList;
import java.util.List;

record RequestContext(
        String tenantId,
        String userId,
        String traceId,
        List<String> roles) {

    static RequestContext requireTenant(Headers headers) {
        return from(headers, false);
    }

    static RequestContext requireActor(Headers headers) {
        return from(headers, true);
    }

    private static RequestContext from(Headers headers, boolean requireUser) {
        String tenantId = requireHeader(headers, "X-Tenant-Id");
        String userId = text(headers.getFirst("X-User-Id"));
        if (requireUser && userId == null) {
            throw new IllegalArgumentException("X-User-Id header must not be blank");
        }
        return new RequestContext(
                tenantId,
                userId,
                text(headers.getFirst("X-Trace-Id")),
                roles(headers.getFirst("X-Roles")));
    }

    private static String requireHeader(Headers headers, String name) {
        String value = text(headers.getFirst(name));
        if (value == null) {
            throw new IllegalArgumentException(name + " header must not be blank");
        }
        return value;
    }

    private static List<String> roles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (String role : raw.split(",")) {
            String value = role.trim();
            if (!value.isEmpty()) {
                parsed.add(value);
            }
        }
        return List.copyOf(parsed);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
