package com.xr.agent.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class WorkerRuntimeConfiguration {

    static final int DEFAULT_BATCH_SIZE = 10;
    static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
    static final List<String> DEFAULT_AGENT_IDS = List.of("supervisor");

    private final String postgresUrl;
    private final String postgresUser;
    private final String postgresPassword;
    private final int batchSize;
    private final Duration pollInterval;
    private final int maxAttempts;
    private final List<String> agentIds;

    private WorkerRuntimeConfiguration(
            String postgresUrl,
            String postgresUser,
            String postgresPassword,
            int batchSize,
            Duration pollInterval,
            int maxAttempts,
            List<String> agentIds) {
        this.postgresUrl = postgresUrl;
        this.postgresUser = postgresUser;
        this.postgresPassword = postgresPassword;
        this.batchSize = batchSize;
        this.pollInterval = pollInterval;
        this.maxAttempts = maxAttempts;
        this.agentIds = agentIds;
    }

    static WorkerRuntimeConfiguration fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String postgresUrl = requireText(environment.get("POSTGRES_URL"), "POSTGRES_URL");
        if (!postgresUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("POSTGRES_URL must use jdbc:postgresql");
        }
        return new WorkerRuntimeConfiguration(
                postgresUrl,
                requireText(environment.get("POSTGRES_USER"), "POSTGRES_USER"),
                requireText(environment.get("POSTGRES_PASSWORD"), "POSTGRES_PASSWORD"),
                positiveInt(environment, "WORKER_BATCH_SIZE", DEFAULT_BATCH_SIZE),
                Duration.ofMillis(positiveInt(
                        environment,
                        "WORKER_POLL_INTERVAL_MILLIS",
                        Math.toIntExact(DEFAULT_POLL_INTERVAL.toMillis()))),
                positiveInt(environment, "WORKER_MAX_ATTEMPTS", TaskOutboxWorker.DEFAULT_MAX_ATTEMPTS),
                agentIds(environment.get("MODEL_AGENT_IDS")));
    }

    String postgresUrl() {
        return postgresUrl;
    }

    String postgresUser() {
        return postgresUser;
    }

    String postgresPassword() {
        return postgresPassword;
    }

    int batchSize() {
        return batchSize;
    }

    Duration pollInterval() {
        return pollInterval;
    }

    int maxAttempts() {
        return maxAttempts;
    }

    List<String> agentIds() {
        return agentIds;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }

    private static int positiveInt(Map<String, String> environment, String name, int defaultValue) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
    }

    private static List<String> agentIds(String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) {
            return DEFAULT_AGENT_IDS;
        }
        Set<String> uniqueIds = new LinkedHashSet<>();
        for (String candidate : configuredIds.split(",")) {
            String agentId = candidate.trim();
            if (!agentId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
                throw new IllegalArgumentException("MODEL_AGENT_IDS contains an invalid agent id");
            }
            if (!uniqueIds.add(agentId)) {
                throw new IllegalArgumentException("MODEL_AGENT_IDS must not contain duplicates");
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("MODEL_AGENT_IDS must contain an agent id");
        }
        return List.copyOf(new ArrayList<>(uniqueIds));
    }
}
