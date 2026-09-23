package com.xr.agent.worker;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;
import com.xr.agent.application.service.TaskAuditEvents;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.TaskStatus;
import com.xr.agent.domain.model.TaskVersionConflictException;
import com.xr.agent.task.OutboxClaimLostException;
import com.xr.agent.task.OutboxRecord;
import com.xr.agent.task.OutboxStorePort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Processes task lifecycle Outbox messages without depending on a delivery framework.
 */
public final class TaskOutboxWorker {

    public static final Duration DEFAULT_PROCESSING_LEASE = Duration.ofMinutes(5);
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final OutboxStorePort outboxStore;
    private final TaskPersistencePort taskPersistence;
    private final TaskEventStorePort taskEvents;
    private final AgentRegistryPort agentRegistry;
    private final AgentInvokerPort agentInvoker;
    private final Clock clock;
    private final Duration processingLease;
    private final Duration retryDelay;
    private final int maxAttempts;

    public TaskOutboxWorker(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            TaskEventStorePort taskEvents,
            AgentRegistryPort agentRegistry,
            AgentInvokerPort agentInvoker,
            Clock clock,
            Duration processingLease,
            Duration retryDelay,
            int maxAttempts) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore");
        this.taskPersistence = Objects.requireNonNull(taskPersistence, "taskPersistence");
        this.taskEvents = taskEvents;
        this.agentRegistry = agentRegistry;
        this.agentInvoker = agentInvoker;
        if ((agentRegistry == null) != (agentInvoker == null)) {
            throw new IllegalArgumentException("agentRegistry and agentInvoker must be provided together");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.processingLease = requirePositive(processingLease, "processingLease");
        this.retryDelay = requirePositive(retryDelay, "retryDelay");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
    }

    public TaskOutboxWorker(OutboxStorePort outboxStore, TaskPersistencePort taskPersistence) {
        this(
                outboxStore,
                taskPersistence,
                null,
                null,
                null,
                Clock.systemUTC(),
                DEFAULT_PROCESSING_LEASE,
                DEFAULT_RETRY_DELAY,
                DEFAULT_MAX_ATTEMPTS);
    }

    public TaskOutboxWorker(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            AgentRegistryPort agentRegistry,
            AgentInvokerPort agentInvoker) {
        this(
                outboxStore,
                taskPersistence,
                null,
                agentRegistry,
                agentInvoker,
                Clock.systemUTC(),
                DEFAULT_PROCESSING_LEASE,
                DEFAULT_RETRY_DELAY,
                DEFAULT_MAX_ATTEMPTS);
    }

    public TaskOutboxWorker(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            AgentRegistryPort agentRegistry,
            AgentInvokerPort agentInvoker,
            Clock clock,
            Duration processingLease,
            Duration retryDelay) {
        this(
                outboxStore,
                taskPersistence,
                null,
                agentRegistry,
                agentInvoker,
                clock,
                processingLease,
                retryDelay,
                DEFAULT_MAX_ATTEMPTS);
    }

    public TaskOutboxWorker(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            TaskEventStorePort taskEvents,
            AgentRegistryPort agentRegistry,
            AgentInvokerPort agentInvoker) {
        this(
                outboxStore,
                taskPersistence,
                taskEvents,
                agentRegistry,
                agentInvoker,
                Clock.systemUTC(),
                DEFAULT_PROCESSING_LEASE,
                DEFAULT_RETRY_DELAY,
                DEFAULT_MAX_ATTEMPTS);
    }

    public BatchResult runOnce(int limit) {
        Instant now = clock.instant();
        List<OutboxRecord> records = outboxStore.claim(limit, now, processingLease);
        int published = 0;
        int scheduledRetries = 0;

        for (OutboxRecord record : records) {
            try {
                process(record, now);
            } catch (EventProcessingException exception) {
                if (scheduleRetry(record, exception.code(), now)) {
                    scheduledRetries++;
                }
                continue;
            } catch (RuntimeException exception) {
                if (scheduleRetry(record, "OUTBOX_PROCESSING_FAILED", now)) {
                    scheduledRetries++;
                }
                continue;
            }

            try {
                outboxStore.markPublished(record.eventId(), record.claimToken(), now);
                published++;
            } catch (OutboxClaimLostException ignored) {
                // A recovered worker owns this message now.
            }
        }
        return new BatchResult(records.size(), published, scheduledRetries);
    }

    private void process(OutboxRecord record, Instant now) {
        TaskOutboxMessage message = record.message();
        if (!"TASK_CREATED".equals(message.eventType())) {
            throw new EventProcessingException("OUTBOX_EVENT_UNSUPPORTED");
        }

        AgentTask task = taskPersistence.findById(message.taskId())
                .orElseThrow(() -> new EventProcessingException("TASK_NOT_FOUND"));
        verifyTaskContext(task, message);
        if (task.status() == TaskStatus.RUNNING) {
            if (agentInvoker == null) {
                return;
            }
            handleRecoveredRunningTask(task, record);
            return;
        }
        if (task.status() != TaskStatus.CREATED) {
            return;
        }

        if (task.deadline() != null && !task.deadline().isAfter(now)) {
            task.timeout();
            AgentTask timedOut = persistTransition(task, message, "TASK_VERSION_CONFLICT");
            appendAudit(timedOut == null ? task : timedOut, "TASK_TIMED_OUT", Map.of());
        } else {
            task.start();
            AgentTask running = persistTransition(task, message, "TASK_VERSION_CONFLICT");
            if (running == null) {
                return;
            }
            appendAudit(running, "TASK_RUNNING", Map.of("attempt", record.attempts()));
            if (agentInvoker == null) {
                return;
            }
            invokeAndPersistResult(running, message);
        }
    }

    private void handleRecoveredRunningTask(AgentTask task, OutboxRecord record) {
        if (record.attempts() < maxAttempts) {
            throw new EventProcessingException("TASK_EXECUTION_IN_PROGRESS");
        }
        task.fail("TASK_EXECUTION_ATTEMPTS_EXHAUSTED");
        persistExecutionResult(task, record.message());
    }

    private AgentTask persistTransition(
            AgentTask task,
            TaskOutboxMessage message,
            String conflictCode) {
        try {
            return taskPersistence.update(task, task.version());
        } catch (TaskVersionConflictException exception) {
            AgentTask current = taskPersistence.findById(task.taskId())
                    .orElseThrow(() -> new EventProcessingException("TASK_NOT_FOUND"));
            verifyTaskContext(current, message);
            if (current.status() == TaskStatus.CREATED) {
                throw new EventProcessingException(conflictCode);
            }
            return null;
        }
    }

    private void invokeAndPersistResult(AgentTask task, TaskOutboxMessage message) {
        AgentDefinition agent = agentRegistry.findById(task.targetAgent())
                .filter(candidate -> candidate.isAvailableFor(task.tenantId()))
                .orElse(null);
        if (agent == null) {
            task.fail("AGENT_UNAVAILABLE");
            persistExecutionResult(task, message);
            return;
        }

        AgentInvokerPort.AgentInvocationResult result;
        try {
            result = agentInvoker.invoke(task, agent);
        } catch (RuntimeException exception) {
            result = new AgentInvokerPort.AgentInvocationResult(false, java.util.Map.of(), "AGENT_INVOCATION_FAILED");
        }
        if (result == null || !result.successful()) {
            task.fail(result == null ? "AGENT_INVOCATION_FAILED" : safeErrorCode(result.errorCode()));
        } else {
            task.succeed(result.output());
        }
        persistExecutionResult(task, message);
    }

    private void persistExecutionResult(AgentTask task, TaskOutboxMessage message) {
        TaskStatus terminalStatus = task.status();
        try {
            AgentTask stored = taskPersistence.update(task, task.version());
            appendAudit(stored, eventTypeFor(terminalStatus), Map.of());
        } catch (TaskVersionConflictException exception) {
            AgentTask current = taskPersistence.findById(task.taskId())
                    .orElseThrow(() -> new EventProcessingException("TASK_NOT_FOUND"));
            verifyTaskContext(current, message);
            if (current.status() == TaskStatus.RUNNING) {
                throw new EventProcessingException("TASK_EXECUTION_VERSION_CONFLICT");
            }
        }
    }

    private void appendAudit(AgentTask task, String eventType, Map<String, Object> payload) {
        if (taskEvents == null || task == null) {
            return;
        }
        taskEvents.append(TaskAuditEvents.fromTask(task, eventType, payload, clock.instant()));
    }

    private static String eventTypeFor(TaskStatus status) {
        return switch (status) {
            case SUCCEEDED -> "TASK_SUCCEEDED";
            case FAILED -> "TASK_FAILED";
            case TIMED_OUT -> "TASK_TIMED_OUT";
            default -> "TASK_" + status.name();
        };
    }

    private static void verifyTaskContext(AgentTask task, TaskOutboxMessage message) {
        if (!task.tenantId().equals(message.tenantId())
                || !task.traceId().equals(message.traceId())) {
            throw new EventProcessingException("TASK_EVENT_CONTEXT_MISMATCH");
        }
    }

    private static String safeErrorCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,128}")) {
            return "AGENT_INVOCATION_FAILED";
        }
        return value;
    }

    private boolean scheduleRetry(OutboxRecord record, String errorCode, Instant now) {
        try {
            outboxStore.markFailed(
                    record.eventId(),
                    record.claimToken(),
                    errorCode,
                    now.plus(retryDelay.multipliedBy(retryMultiplier(record.attempts()))));
            return true;
        } catch (OutboxClaimLostException ignored) {
            return false;
        }
    }

    private static long retryMultiplier(int attempts) {
        return 1L << Math.min(Math.max(attempts - 1, 0), 6);
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    public record BatchResult(int claimed, int published, int scheduledRetries) {
    }

    private static final class EventProcessingException extends RuntimeException {
        private final String code;

        private EventProcessingException(String code) {
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
