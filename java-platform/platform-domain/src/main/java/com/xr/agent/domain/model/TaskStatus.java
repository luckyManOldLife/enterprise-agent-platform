package com.xr.agent.domain.model;

public enum TaskStatus {
    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    WAITING_TOOL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
