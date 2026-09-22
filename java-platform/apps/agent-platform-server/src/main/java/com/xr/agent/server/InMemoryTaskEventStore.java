package com.xr.agent.server;

import com.xr.agent.application.port.out.TaskEventStorePort;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class InMemoryTaskEventStore implements TaskEventStorePort {

    private final List<TaskEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(TaskEvent event) {
        events.add(event);
    }

    @Override
    public List<TaskEvent> listByTask(UUID taskId) {
        return events.stream()
                .filter(event -> event.taskId().equals(taskId))
                .sorted(Comparator.comparing(TaskEvent::createdAt))
                .toList();
    }
}
