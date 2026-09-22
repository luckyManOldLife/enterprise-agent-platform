package com.xr.agent.server;

import java.util.Map;

final class HealthController {

    private HealthController() {
    }

    static Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
