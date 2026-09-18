package com.xr.agent.domain.model;

public final class TaskTransitionException extends IllegalStateException {

    public TaskTransitionException(TaskStatus from, TaskStatus to) {
        super("Invalid task transition: " + from + " -> " + to);
    }
}
