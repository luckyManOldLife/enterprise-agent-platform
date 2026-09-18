package com.xr.agent.domain;

import com.xr.agent.domain.model.AgentTask;
import com.xr.agent.domain.model.TaskStatus;
import com.xr.agent.domain.model.TaskTransitionException;
import com.xr.agent.domain.service.TaskStateMachine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStateMachineTest {

    @Test
    void allowsApprovalPauseAndResume() {
        AgentTask task = AgentTask.create(
                "tenant-a",
                "user-a",
                "trace-a",
                "conversation-a",
                "supervisor",
                "support-agent",
                Map.of("customerId", "c-1"),
                null);

        task.start();
        task.waitForApproval();
        task.resume();
        task.succeed(Map.of("ticketId", "t-1"));

        assertEquals(TaskStatus.SUCCEEDED, task.status());
        assertEquals("t-1", task.output().get("ticketId"));
    }

    @Test
    void rejectsTerminalStateMutation() {
        AgentTask task = AgentTask.create(
                "tenant-a", "user-a", "trace-a", "conversation-a",
                "supervisor", "order-agent", Map.of(), null);
        task.start();
        task.succeed(Map.of());

        assertThrows(TaskTransitionException.class, task::cancel);
    }

    @Test
    void exposesTransitionRules() {
        assertEquals(true, TaskStateMachine.canTransition(TaskStatus.FAILED, TaskStatus.RUNNING));
        assertEquals(false, TaskStateMachine.canTransition(TaskStatus.SUCCEEDED, TaskStatus.RUNNING));
    }
}
