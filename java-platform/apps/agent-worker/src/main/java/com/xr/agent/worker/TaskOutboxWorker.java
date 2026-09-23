package com.xr.agent.worker;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.ApprovalRepositoryPort;
import com.xr.agent.application.port.out.PolicyEnginePort;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.application.port.out.TaskPersistencePort;
import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;
import com.xr.agent.application.port.out.ToolExecutorPort;
import com.xr.agent.application.port.out.ToolRegistryPort;
import com.xr.agent.application.service.TaskAuditEvents;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.Approval;
import com.xr.agent.domain.model.TaskStatus;
import com.xr.agent.domain.model.TaskVersionConflictException;
import com.xr.agent.domain.model.ToolDefinition;
import com.xr.agent.task.OutboxClaimLostException;
import com.xr.agent.task.OutboxRecord;
import com.xr.agent.task.OutboxStorePort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Processes task lifecycle Outbox messages without depending on a delivery framework.
 */
public final class TaskOutboxWorker {

    public static final Duration DEFAULT_PROCESSING_LEASE = Duration.ofMinutes(5);
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(30);
    public static final Duration DEFAULT_APPROVAL_TTL = Duration.ofHours(4);
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final OutboxStorePort outboxStore;
    private final TaskPersistencePort taskPersistence;
    private final TaskEventStorePort taskEvents;
    private final AgentRegistryPort agentRegistry;
    private final AgentInvokerPort agentInvoker;
    private final ToolRegistryPort toolRegistry;
    private final PolicyEnginePort policyEngine;
    private final ToolExecutorPort toolExecutor;
    private final ApprovalRepositoryPort approvalRepository;
    private final Clock clock;
    private final Duration processingLease;
    private final Duration retryDelay;
    private final Duration approvalTtl;
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
        this(
                outboxStore,
                taskPersistence,
                taskEvents,
                agentRegistry,
                agentInvoker,
                null,
                null,
                null,
                null,
                clock,
                processingLease,
                retryDelay,
                DEFAULT_APPROVAL_TTL,
                maxAttempts);
    }

    public TaskOutboxWorker(
            OutboxStorePort outboxStore,
            TaskPersistencePort taskPersistence,
            TaskEventStorePort taskEvents,
            AgentRegistryPort agentRegistry,
            AgentInvokerPort agentInvoker,
            ToolRegistryPort toolRegistry,
            PolicyEnginePort policyEngine,
            ToolExecutorPort toolExecutor,
            ApprovalRepositoryPort approvalRepository,
            Clock clock,
            Duration processingLease,
            Duration retryDelay,
            Duration approvalTtl,
            int maxAttempts) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore");
        this.taskPersistence = Objects.requireNonNull(taskPersistence, "taskPersistence");
        this.taskEvents = taskEvents;
        this.agentRegistry = agentRegistry;
        this.agentInvoker = agentInvoker;
        if ((agentRegistry == null) != (agentInvoker == null)) {
            throw new IllegalArgumentException("agentRegistry and agentInvoker must be provided together");
        }
        this.toolRegistry = toolRegistry;
        this.policyEngine = policyEngine;
        this.toolExecutor = toolExecutor;
        this.approvalRepository = approvalRepository;
        int configuredToolDependencies = (toolRegistry == null ? 0 : 1)
                + (policyEngine == null ? 0 : 1)
                + (toolExecutor == null ? 0 : 1)
                + (approvalRepository == null ? 0 : 1);
        if (configuredToolDependencies != 0 && configuredToolDependencies != 4) {
            throw new IllegalArgumentException("Tool governance dependencies must be provided together");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.processingLease = requirePositive(processingLease, "processingLease");
        this.retryDelay = requirePositive(retryDelay, "retryDelay");
        this.approvalTtl = requirePositive(approvalTtl, "approvalTtl");
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
        } else if (toolCalls(result.output()).isEmpty()) {
            task.succeed(result.output());
        } else {
            handleToolCalls(task, message, result.output());
            return;
        }
        persistExecutionResult(task, message);
    }

    private void handleToolCalls(AgentTask task, TaskOutboxMessage message, Map<String, Object> modelOutput) {
        if (toolRegistry == null || policyEngine == null || toolExecutor == null || approvalRepository == null) {
            task.fail("TOOL_EXECUTION_UNCONFIGURED");
            persistExecutionResult(task, message);
            return;
        }

        List<ToolCall> calls = toolCalls(modelOutput);
        if (calls.size() != 1) {
            task.fail("TOOL_CALL_UNSUPPORTED");
            persistExecutionResult(task, message);
            return;
        }

        ToolCall call = calls.getFirst();
        ToolDefinition tool = toolRegistry.findById(call.name()).orElse(null);
        if (tool == null) {
            task.fail("TOOL_UNAVAILABLE");
            AgentTask stored = persistExecutionResult(task, message);
            appendAudit(stored, "TOOL_FAILED", Map.of(
                    "toolId", call.name(),
                    "errorCode", "TOOL_UNAVAILABLE"));
            return;
        }

        PolicyEnginePort.PolicyDecision decision = policyEngine.authorize(new PolicyEnginePort.PolicyRequest(
                task,
                tool,
                roles(task.input().get("roles")),
                idempotencyKey(task, tool)));
        if (!decision.allowed()) {
            task.fail(safePolicyCode(decision.policyCode()));
            AgentTask stored = persistExecutionResult(task, message);
            appendAudit(stored, "TOOL_POLICY_DENIED", Map.of(
                    "toolId", tool.toolId(),
                    "policyCode", safePolicyCode(decision.policyCode())));
            return;
        }

        appendAudit(task, "TOOL_POLICY_ALLOWED", Map.of(
                "toolId", tool.toolId(),
                "policyCode", safePolicyCode(decision.policyCode())));
        if (decision.requiresApproval()) {
            Approval approval = new Approval(
                    task.taskId(),
                    task.tenantId(),
                    task.userId(),
                    decision.reason(),
                    clock.instant().plus(approvalTtl));
            approvalRepository.save(approval);
            task.waitForApproval();
            AgentTask stored = persistExecutionResult(task, message);
            appendAudit(stored, "TOOL_APPROVAL_REQUESTED", Map.of(
                    "toolId", tool.toolId(),
                    "approvalId", approval.approvalId().toString(),
                    "policyCode", safePolicyCode(decision.policyCode())));
            return;
        }

        ToolExecutorPort.ToolExecutionResult toolResult;
        try {
            Map<String, Object> executionArguments = new LinkedHashMap<>(call.arguments());
            executionArguments.put("idempotencyKey", idempotencyKey(task, tool));
            toolResult = toolExecutor.execute(
                    task,
                    tool,
                    Collections.unmodifiableMap(executionArguments));
        } catch (RuntimeException exception) {
            toolResult = new ToolExecutorPort.ToolExecutionResult(false, Map.of(), "TOOL_EXECUTION_FAILED");
        }
        if (toolResult == null || !toolResult.successful()) {
            String errorCode = toolResult == null ? "TOOL_EXECUTION_FAILED" : safeToolErrorCode(toolResult.errorCode());
            task.fail(errorCode);
            AgentTask stored = persistExecutionResult(task, message);
            appendAudit(stored, "TOOL_FAILED", Map.of(
                    "toolId", tool.toolId(),
                    "errorCode", errorCode));
            return;
        }

        task.succeed(toolOutput(modelOutput, tool, toolResult.output()));
        AgentTask stored = persistExecutionResult(task, message);
        appendAudit(stored, "TOOL_EXECUTED", Map.of("toolId", tool.toolId()));
    }

    private AgentTask persistExecutionResult(AgentTask task, TaskOutboxMessage message) {
        TaskStatus status = task.status();
        try {
            AgentTask stored = taskPersistence.update(task, task.version());
            appendAudit(stored, eventTypeFor(status), Map.of());
            return stored;
        } catch (TaskVersionConflictException exception) {
            AgentTask current = taskPersistence.findById(task.taskId())
                    .orElseThrow(() -> new EventProcessingException("TASK_NOT_FOUND"));
            verifyTaskContext(current, message);
            if (current.status() == TaskStatus.RUNNING) {
                throw new EventProcessingException("TASK_EXECUTION_VERSION_CONFLICT");
            }
            return current;
        }
    }

    private static Map<String, Object> toolOutput(
            Map<String, Object> modelOutput,
            ToolDefinition tool,
            Map<String, Object> toolResult) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (modelOutput != null) {
            modelOutput.forEach((key, value) -> {
                if (!"toolCalls".equals(key)) {
                    output.put(key, value);
                }
            });
        }
        output.put("tool", Map.of(
                "toolId", tool.toolId(),
                "name", tool.name(),
                "version", tool.version()));
        output.put("toolResult", Collections.unmodifiableMap(
                new LinkedHashMap<>(toolResult == null ? Map.of() : toolResult)));
        return Map.copyOf(output);
    }

    private static List<ToolCall> toolCalls(Map<String, Object> output) {
        if (output == null || !(output.get("toolCalls") instanceof List<?> values)) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                String name = text(map.get("name"));
                if (name == null) {
                    name = text(map.get("toolId"));
                }
                if (name == null || name.isBlank()) {
                    continue;
                }
                calls.add(new ToolCall(name, arguments(map.get("arguments"))));
            }
        }
        return List.copyOf(calls);
    }

    private static Map<String, Object> arguments(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                arguments.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(arguments);
    }

    private static Set<String> roles(Object value) {
        if (value instanceof Iterable<?> values) {
            java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>();
            for (Object item : values) {
                String role = text(item);
                if (role != null && !role.isBlank()) {
                    roles.add(role);
                }
            }
            return Set.copyOf(roles);
        }
        String role = text(value);
        return role == null || role.isBlank() ? Set.of() : Set.of(role);
    }

    private static String idempotencyKey(AgentTask task, ToolDefinition tool) {
        return task.taskId() + ":" + tool.toolId();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private static String safeToolErrorCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,128}")) {
            return "TOOL_EXECUTION_FAILED";
        }
        return value;
    }

    private static String safePolicyCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,128}")) {
            return "POLICY_DENIED";
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

    private record ToolCall(String name, Map<String, Object> arguments) {
    }
}
