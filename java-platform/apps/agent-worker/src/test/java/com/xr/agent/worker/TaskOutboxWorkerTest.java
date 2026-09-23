package com.xr.agent.worker;

import com.xr.agent.application.port.out.AgentInvokerPort;
import com.xr.agent.application.port.out.AgentRegistryPort;
import com.xr.agent.application.port.out.TaskEventStorePort;
import com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage;
import com.xr.agent.domain.model.AgentDefinition;
import com.xr.agent.domain.model.AgentStatus;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.TaskStatus;
import com.xr.agent.task.InMemoryTaskPersistence;
import com.xr.agent.task.OutboxClaimLostException;
import com.xr.agent.task.OutboxRecord;
import com.xr.agent.task.OutboxStorePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskOutboxWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-22T02:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(1);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    @Test
    void startsCreatedTaskAndPublishesItsEvent() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        RecordingTaskEventStore events = new RecordingTaskEventStore();
        AgentTask task = saveTask(persistence, null);

        TaskOutboxWorker.BatchResult result = worker(persistence, events).runOnce(10);

        AgentTask updated = persistence.findById(task.taskId()).orElseThrow();
        assertEquals(new TaskOutboxWorker.BatchResult(1, 1, 0), result);
        assertEquals(TaskStatus.RUNNING, updated.status());
        assertEquals(1, updated.version());
        assertEquals(java.util.List.of("TASK_RUNNING"), events.eventTypes());
        assertTrue(persistence.claim(1, NOW.plusSeconds(1), LEASE).isEmpty());
    }

    @Test
    void treatsAnAlreadyStartedTaskAsAnIdempotentDelivery() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = saveTask(persistence, null);
        AgentTask started = persistence.findById(task.taskId()).orElseThrow();
        started.start();
        persistence.update(started, started.version());

        TaskOutboxWorker.BatchResult result = worker(persistence).runOnce(10);

        AgentTask stored = persistence.findById(task.taskId()).orElseThrow();
        assertEquals(new TaskOutboxWorker.BatchResult(1, 1, 0), result);
        assertEquals(TaskStatus.RUNNING, stored.status());
        assertEquals(1, stored.version());
        assertTrue(persistence.claim(1, NOW.plusSeconds(1), LEASE).isEmpty());
    }

    @Test
    void timesOutAnExpiredCreatedTaskBeforeAgentExecution() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        RecordingTaskEventStore events = new RecordingTaskEventStore();
        AgentTask task = saveTask(persistence, NOW.minusSeconds(1));

        TaskOutboxWorker.BatchResult result = worker(persistence, events).runOnce(10);

        AgentTask stored = persistence.findById(task.taskId()).orElseThrow();
        assertEquals(new TaskOutboxWorker.BatchResult(1, 1, 0), result);
        assertEquals(TaskStatus.TIMED_OUT, stored.status());
        assertEquals(1, stored.version());
        assertEquals(java.util.List.of("TASK_TIMED_OUT"), events.eventTypes());
    }

    @Test
    void retriesMissingTaskWithAStableErrorCodeAndBackoff() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        TaskOutboxMessage event = new TaskOutboxMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tenant-a",
                "trace-a",
                "TASK_CREATED",
                Map.of(),
                NOW);
        persistence.append(event);

        TaskOutboxWorker.BatchResult result = worker(persistence).runOnce(10);

        assertEquals(new TaskOutboxWorker.BatchResult(1, 0, 1), result);
        assertTrue(persistence.claim(1, NOW.plusSeconds(29), LEASE).isEmpty());
        OutboxRecord retry = persistence.claim(1, NOW.plusSeconds(30), LEASE).getFirst();
        assertEquals(2, retry.attempts());
        assertEquals("TASK_NOT_FOUND", retry.lastError());
    }

    @Test
    void rejectsAnEventWithMismatchedTenantOrTraceContext() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = saveTask(persistence, null);
        OutboxRecord createdEvent = persistence.claim(1, NOW, LEASE).getFirst();
        persistence.markPublished(createdEvent.eventId(), createdEvent.claimToken(), NOW);
        persistence.append(new TaskOutboxMessage(
                UUID.randomUUID(),
                task.taskId(),
                "tenant-b",
                task.traceId(),
                "TASK_CREATED",
                Map.of(),
                NOW));

        TaskOutboxWorker.BatchResult result = worker(persistence).runOnce(10);

        assertEquals(new TaskOutboxWorker.BatchResult(1, 0, 1), result);
        assertEquals(TaskStatus.CREATED, persistence.findById(task.taskId()).orElseThrow().status());
        OutboxRecord retry = persistence.claim(1, NOW.plusSeconds(30), LEASE).getFirst();
        assertEquals("TASK_EVENT_CONTEXT_MISMATCH", retry.lastError());
    }

    @Test
    void doesNotRetryAfterLosingTheOutboxClaimDuringCompletion() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = saveTask(persistence, null);
        OutboxStorePort claimLostOnPublish = new ClaimLostOnPublishStore(persistence);
        TaskOutboxWorker worker = new TaskOutboxWorker(
                claimLostOnPublish,
                persistence,
                null,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                LEASE,
                RETRY_DELAY);

        TaskOutboxWorker.BatchResult result = worker.runOnce(10);

        assertEquals(new TaskOutboxWorker.BatchResult(1, 0, 0), result);
        assertEquals(TaskStatus.RUNNING, persistence.findById(task.taskId()).orElseThrow().status());
    }

    @Test
    void persistsTheRealAgentOutputAfterStartingTheTask() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        RecordingTaskEventStore events = new RecordingTaskEventStore();
        AgentTask task = saveTask(persistence, null);
        AgentInvokerPort invoker = (invokedTask, agent) ->
                new AgentInvokerPort.AgentInvocationResult(true, Map.of("content", "order found"), null);

        TaskOutboxWorker.BatchResult result = executingWorker(persistence, events, invoker).runOnce(10);

        AgentTask stored = persistence.findById(task.taskId()).orElseThrow();
        assertEquals(new TaskOutboxWorker.BatchResult(1, 1, 0), result);
        assertEquals(TaskStatus.SUCCEEDED, stored.status());
        assertEquals("order found", stored.output().get("content"));
        assertEquals(2, stored.version());
        assertEquals(java.util.List.of("TASK_RUNNING", "TASK_SUCCEEDED"), events.eventTypes());
    }

    @Test
    void persistsAStableAgentFailureCode() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = saveTask(persistence, null);
        AgentInvokerPort invoker = (invokedTask, agent) ->
                new AgentInvokerPort.AgentInvocationResult(false, Map.of(), "MODEL_UPSTREAM_HTTP_429");

        TaskOutboxWorker.BatchResult result = executingWorker(persistence, invoker).runOnce(10);

        AgentTask stored = persistence.findById(task.taskId()).orElseThrow();
        assertEquals(new TaskOutboxWorker.BatchResult(1, 1, 0), result);
        assertEquals(TaskStatus.FAILED, stored.status());
        assertEquals("MODEL_UPSTREAM_HTTP_429", stored.errorCode());
        assertEquals(2, stored.version());
    }

    @Test
    void doesNotPersistUntrustedAgentFailureText() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = saveTask(persistence, null);
        AgentInvokerPort invoker = (invokedTask, agent) ->
                new AgentInvokerPort.AgentInvocationResult(false, Map.of(), "upstream returned bearer token");

        executingWorker(persistence, invoker).runOnce(10);

        assertEquals(
                "AGENT_INVOCATION_FAILED",
                persistence.findById(task.taskId()).orElseThrow().errorCode());
    }

    @Test
    void retriesRecoveredRunningExecutionUntilAttemptsAreExhausted() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        RecordingTaskEventStore events = new RecordingTaskEventStore();
        AgentTask task = saveTask(persistence, null);
        AgentTask started = persistence.findById(task.taskId()).orElseThrow();
        started.start();
        persistence.update(started, started.version());

        TaskOutboxWorker.BatchResult first = executingWorker(persistence, events, (invokedTask, agent) ->
                new AgentInvokerPort.AgentInvocationResult(true, Map.of(), null), 2).runOnce(10);

        assertEquals(new TaskOutboxWorker.BatchResult(1, 0, 1), first);
        assertEquals(TaskStatus.RUNNING, persistence.findById(task.taskId()).orElseThrow().status());
        OutboxRecord retry = persistence.claim(1, NOW.plusSeconds(30), LEASE).getFirst();
        assertEquals("TASK_EXECUTION_IN_PROGRESS", retry.lastError());
        persistence.markFailed(
                retry.eventId(),
                retry.claimToken(),
                retry.lastError(),
                NOW);

        TaskOutboxWorker.BatchResult finalResult = executingWorker(persistence, events, (invokedTask, agent) ->
                new AgentInvokerPort.AgentInvocationResult(true, Map.of(), null), 2)
                .runOnce(10);

        AgentTask stored = persistence.findById(task.taskId()).orElseThrow();
        assertEquals(new TaskOutboxWorker.BatchResult(1, 1, 0), finalResult);
        assertEquals(TaskStatus.FAILED, stored.status());
        assertEquals("TASK_EXECUTION_ATTEMPTS_EXHAUSTED", stored.errorCode());
        assertEquals(java.util.List.of("TASK_FAILED"), events.eventTypes());
    }

    private static TaskOutboxWorker worker(InMemoryTaskPersistence persistence) {
        return worker(persistence, null);
    }

    private static TaskOutboxWorker worker(
            InMemoryTaskPersistence persistence,
            TaskEventStorePort events) {
        return new TaskOutboxWorker(
                persistence,
                persistence,
                events,
                null,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                LEASE,
                RETRY_DELAY,
                TaskOutboxWorker.DEFAULT_MAX_ATTEMPTS);
    }

    private static TaskOutboxWorker executingWorker(
            InMemoryTaskPersistence persistence,
            AgentInvokerPort invoker) {
        return executingWorker(persistence, null, invoker);
    }

    private static TaskOutboxWorker executingWorker(
            InMemoryTaskPersistence persistence,
            TaskEventStorePort events,
            AgentInvokerPort invoker) {
        return executingWorker(persistence, events, invoker, TaskOutboxWorker.DEFAULT_MAX_ATTEMPTS);
    }

    private static TaskOutboxWorker executingWorker(
            InMemoryTaskPersistence persistence,
            TaskEventStorePort events,
            AgentInvokerPort invoker,
            int maxAttempts) {
        return new TaskOutboxWorker(
                persistence,
                persistence,
                events,
                new SingleAgentRegistry(agent()),
                invoker,
                Clock.fixed(NOW, ZoneOffset.UTC),
                LEASE,
                RETRY_DELAY,
                maxAttempts);
    }

    private static AgentTask saveTask(InMemoryTaskPersistence persistence, Instant deadline) {
        AgentTask task = AgentTask.create(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                "supervisor",
                "order-agent",
                Map.of(),
                deadline);
        TaskOutboxMessage event = new TaskOutboxMessage(
                UUID.randomUUID(),
                task.taskId(),
                task.tenantId(),
                task.traceId(),
                "TASK_CREATED",
                Map.of("targetAgent", task.targetAgent()),
                NOW);
        return persistence.saveWithOutbox(task, event);
    }

    private static final class ClaimLostOnPublishStore implements OutboxStorePort {
        private final InMemoryTaskPersistence delegate;

        private ClaimLostOnPublishStore(InMemoryTaskPersistence delegate) {
            this.delegate = delegate;
        }

        @Override
        public void append(TaskOutboxMessage event) {
            delegate.append(event);
        }

        @Override
        public java.util.List<OutboxRecord> claim(int limit, Instant now, Duration processingLease) {
            return delegate.claim(limit, now, processingLease);
        }

        @Override
        public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
            throw new OutboxClaimLostException(eventId);
        }

        @Override
        public void markFailed(UUID eventId, UUID claimToken, String error, Instant nextAttemptAt) {
            delegate.markFailed(eventId, claimToken, error, nextAttemptAt);
        }
    }

    private static AgentDefinition agent() {
        return new AgentDefinition(
                "order-agent",
                "Order Agent",
                "1.0.0",
                AgentStatus.ACTIVE,
                "model://cliproxyapi",
                Set.of("order.read"),
                null);
    }

    private record SingleAgentRegistry(AgentDefinition agent) implements AgentRegistryPort {

        @Override
        public void register(AgentDefinition agent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<AgentDefinition> findAvailable(String tenantId) {
            return agent.isAvailableFor(tenantId) ? java.util.List.of(agent) : java.util.List.of();
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return agent.agentId().equals(agentId) ? Optional.of(agent) : Optional.empty();
        }
    }

    private static final class RecordingTaskEventStore implements TaskEventStorePort {
        private final CopyOnWriteArrayList<TaskEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void append(TaskEvent event) {
            events.addIfAbsent(event);
        }

        @Override
        public java.util.List<TaskEvent> listByTask(UUID taskId) {
            return events.stream()
                    .filter(event -> event.taskId().equals(taskId))
                    .toList();
        }

        private java.util.List<String> eventTypes() {
            return events.stream().map(TaskEvent::eventType).toList();
        }
    }
}
