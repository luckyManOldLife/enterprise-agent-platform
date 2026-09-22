package com.xr.agent.task;

import java.util.Objects;
import java.util.UUID;

public final class OutboxClaimLostException extends IllegalStateException {

    public OutboxClaimLostException(UUID eventId) {
        super("Outbox claim is no longer current: " + Objects.requireNonNull(eventId, "eventId"));
    }
}
