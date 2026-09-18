package com.xr.agent.domain.service;

import com.xr.agent.domain.model.TaskStatus;
import com.xr.agent.domain.model.TaskTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = Map.of(
            TaskStatus.CREATED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.TIMED_OUT),
            TaskStatus.RUNNING, EnumSet.of(
                    TaskStatus.WAITING_APPROVAL,
                    TaskStatus.WAITING_TOOL,
                    TaskStatus.SUCCEEDED,
                    TaskStatus.FAILED,
                    TaskStatus.CANCELLED,
                    TaskStatus.TIMED_OUT),
            TaskStatus.WAITING_APPROVAL, EnumSet.of(
                    TaskStatus.RUNNING,
                    TaskStatus.CANCELLED,
                    TaskStatus.TIMED_OUT),
            TaskStatus.WAITING_TOOL, EnumSet.of(
                    TaskStatus.RUNNING,
                    TaskStatus.FAILED,
                    TaskStatus.CANCELLED,
                    TaskStatus.TIMED_OUT),
            TaskStatus.FAILED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED),
            TaskStatus.SUCCEEDED, Set.of(),
            TaskStatus.CANCELLED, Set.of(),
            TaskStatus.TIMED_OUT, Set.of());

    private TaskStateMachine() {
    }

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(TaskStatus from, TaskStatus to) {
        if (!canTransition(from, to)) {
            throw new TaskTransitionException(from, to);
        }
    }
}
