package com.xr.agent.task;

import com.xr.agent.application.port.in.TaskUseCase;
import com.xr.agent.application.service.DefaultTaskService;
import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.TaskStatus;
import com.xr.agent.domain.model.TaskVersionConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryTaskPersistenceTest {

    @Test
    void savesTaskAndCreatedEventAsOneOperation() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        DefaultTaskService service = new DefaultTaskService(persistence);

        AgentTask task = service.submit(new TaskUseCase.SubmitTaskCommand(
                "tenant-a", "user-a", "trace-a", "conversation-a",
                "supervisor", "customer-agent", Map.of("customerId", "c-1")));

        List<OutboxRecord> records = persistence.claim(10, Instant.now());
        assertEquals(1, records.size());
        assertEquals(task.taskId(), records.getFirst().message().taskId());
        assertEquals("TASK_CREATED", records.getFirst().message().eventType());
    }

    @Test
    void rejectsDuplicateTaskWithoutLeavingAnExtraEvent() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = AgentTask.create(
                "tenant-a", "user-a", "trace-a", "conversation-a",
                "supervisor", "order-agent", Map.of(), null);
        var event = new com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage(
                java.util.UUID.randomUUID(), task.taskId(), task.tenantId(),
                task.traceId(), "TASK_CREATED", Map.of(), Instant.now());
        persistence.saveWithOutbox(task, event);

        assertThrows(IllegalStateException.class, () -> {
            persistence.saveWithOutbox(task, event);
        });
        assertEquals(1, persistence.claim(10, Instant.now()).size());
    }

    @Test
    void retriesFailedEventWithAttemptCount() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        DefaultTaskService service = new DefaultTaskService(persistence);
        service.submit(new TaskUseCase.SubmitTaskCommand(
                "tenant-a", "user-a", "trace-a", null,
                "supervisor", "order-agent", Map.of()));

        OutboxRecord record = persistence.claim(1, Instant.now()).getFirst();
        persistence.markFailed(record.eventId(), "broker unavailable", Instant.now().minusSeconds(1));
        OutboxRecord retry = persistence.claim(1, Instant.now()).getFirst();

        assertEquals(2, retry.attempts());
    }

    @Test
    void updatesTaskWithTheExpectedVersion() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = saveTask(persistence);
        AgentTask pending = persistence.findById(task.taskId()).orElseThrow();
        pending.start();

        AgentTask updated = persistence.update(pending, pending.version());

        assertEquals(1, updated.version());
        assertEquals(TaskStatus.RUNNING, updated.status());
        assertEquals(1, persistence.findById(task.taskId()).orElseThrow().version());
    }

    @Test
    void rejectsAStaleTaskSnapshot() {
        InMemoryTaskPersistence persistence = new InMemoryTaskPersistence();
        AgentTask task = saveTask(persistence);
        AgentTask firstSnapshot = persistence.findById(task.taskId()).orElseThrow();
        AgentTask staleSnapshot = persistence.findById(task.taskId()).orElseThrow();

        firstSnapshot.start();
        persistence.update(firstSnapshot, firstSnapshot.version());
        staleSnapshot.start();

        assertThrows(TaskVersionConflictException.class, () -> {
            persistence.update(staleSnapshot, staleSnapshot.version());
        });
        assertEquals(TaskStatus.RUNNING, persistence.findById(task.taskId()).orElseThrow().status());
    }

    private static AgentTask saveTask(InMemoryTaskPersistence persistence) {
        AgentTask task = AgentTask.create(
                "tenant-a", "user-a", "trace-a", "conversation-a",
                "supervisor", "order-agent", Map.of(), null);
        var event = new com.xr.agent.application.port.out.TaskPersistencePort.TaskOutboxMessage(
                java.util.UUID.randomUUID(), task.taskId(), task.tenantId(),
                task.traceId(), "TASK_CREATED", Map.of(), Instant.now());
        return persistence.saveWithOutbox(task, event);
    }
}
