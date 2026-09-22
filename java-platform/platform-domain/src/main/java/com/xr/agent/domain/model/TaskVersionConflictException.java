package com.xr.agent.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class TaskVersionConflictException extends IllegalStateException {

    public TaskVersionConflictException(UUID taskId, long expectedVersion) {
        super("Task version conflict: " + Objects.requireNonNull(taskId, "taskId")
                + " expected version " + expectedVersion);
    }
}
