from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any
import uuid


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


class TaskStatus(str, Enum):
    SUBMITTED = "SUBMITTED"
    DISPATCHING = "DISPATCHING"
    RUNNING = "RUNNING"
    WAITING_SUB_TASK = "WAITING_SUB_TASK"
    WAITING_APPROVAL = "WAITING_APPROVAL"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"
    TIMED_OUT = "TIMED_OUT"


TRANSITIONS = {
    TaskStatus.SUBMITTED: {TaskStatus.DISPATCHING, TaskStatus.CANCELLED},
    TaskStatus.DISPATCHING: {TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED},
    TaskStatus.RUNNING: {TaskStatus.WAITING_SUB_TASK, TaskStatus.WAITING_APPROVAL, TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.TIMED_OUT, TaskStatus.CANCELLED},
    TaskStatus.WAITING_SUB_TASK: {TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.TIMED_OUT, TaskStatus.CANCELLED},
    TaskStatus.WAITING_APPROVAL: {TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED},
    TaskStatus.COMPLETED: set(), TaskStatus.FAILED: set(), TaskStatus.CANCELLED: set(), TaskStatus.TIMED_OUT: set(),
}


@dataclass
class Task:
    tenant_id: str
    user_id: str
    roles: list[str]
    input: str
    task_id: str = field(default_factory=lambda: f"task-{uuid.uuid4().hex[:12]}")
    trace_id: str = field(default_factory=lambda: f"trace-{uuid.uuid4().hex[:12]}")
    status: TaskStatus = TaskStatus.SUBMITTED
    output: dict[str, Any] | None = None
    error: str | None = None
    version: int = 0
    created_at: str = field(default_factory=now)
    updated_at: str = field(default_factory=now)
    steps: list[dict[str, Any]] = field(default_factory=list)

    def transition(self, status: TaskStatus, **fields: Any) -> None:
        if status not in TRANSITIONS[self.status]:
            raise ValueError(f"invalid transition {self.status} -> {status}")
        self.status = status
        self.version += 1
        self.updated_at = now()
        for key, value in fields.items():
            setattr(self, key, value)

    def as_dict(self) -> dict[str, Any]:
        result = self.__dict__.copy()
        result["status"] = self.status.value
        return result


@dataclass(frozen=True)
class AgentDefinition:
    agent_id: str
    name: str
    version: str
    description: str
    capabilities: tuple[str, ...]
    protocol: str = "A2A-1.0"
    status: str = "ACTIVE"
    timeout_seconds: int = 10

    def as_dict(self) -> dict[str, Any]:
        result = self.__dict__.copy()
        result["capabilities"] = list(self.capabilities)
        return result


@dataclass(frozen=True)
class ToolDefinition:
    tool_id: str
    name: str
    description: str
    risk_level: str
    required_permissions: tuple[str, ...]
    idempotent: bool
    enabled: bool = True

    def as_dict(self) -> dict[str, Any]:
        result = self.__dict__.copy()
        result["required_permissions"] = list(self.required_permissions)
        return result
