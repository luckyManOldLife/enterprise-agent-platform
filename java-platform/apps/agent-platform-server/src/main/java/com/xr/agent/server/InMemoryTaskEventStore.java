package com.xr.agent.server;

import com.xr.agent.application.port.out.TaskEventStorePort;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryTaskEventStore implements TaskEventStorePort {

    private final Map<UUID, TaskEvent> events = new ConcurrentHashMap<>();

    @Override
    public void append(TaskEvent event) {
        events.putIfAbsent(event.eventId(), event);
    }

    @Override
    public List<TaskEvent> listByTask(UUID taskId) {
        return events.values().stream()
                .filter(event -> event.taskId().equals(taskId))
                .sorted(Comparator.comparing(TaskEvent::createdAt))
                .toList();
    }
}
