package com.xr.agent.domain;

import com.xr.agent.domain.model.AgentTask;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTaskVersionTest {

    @Test
    void createsAndCopiesWithExplicitVersions() {
        AgentTask task = AgentTask.create(
                "tenant-a", "user-a", "trace-a", "conversation-a",
                "supervisor", "order-agent", Map.of(), null);

        AgentTask versioned = task.copyWithVersion(3);

        assertEquals(0, task.version());
        assertEquals(3, versioned.version());
        assertEquals(task.taskId(), versioned.taskId());
    }

    @Test
    void rejectsNegativeVersions() {
        AgentTask task = AgentTask.create(
                "tenant-a", "user-a", "trace-a", "conversation-a",
                "supervisor", "order-agent", Map.of(), null);

        assertThrows(IllegalArgumentException.class, () -> task.copyWithVersion(-1));
    }
}
