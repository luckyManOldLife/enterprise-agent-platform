import re
import uuid
from typing import Any
from .domain import AgentDefinition, ToolDefinition, Task, TaskStatus
from .store import Store


class PolicyEngine:
    def authorize(self, task: Task, agent: AgentDefinition, tool: ToolDefinition) -> tuple[bool, str]:
        if not tool.enabled: return False, "tool_disabled"
        if tool.risk_level == "HIGH": return False, "human_approval_required"
        missing = [p for p in tool.required_permissions if p not in task.roles]
        return (False, f"missing_permissions:{','.join(missing)}") if missing else (True, "allowed")


class Platform:
    def __init__(self, store: Store):
        self.store = store
        self.policy = PolicyEngine()
        self.agents = [
            AgentDefinition("customer-agent", "Customer Agent", "1.0.0", "客户查询", ("customer.lookup",)),
            AgentDefinition("order-agent", "Order Agent", "1.0.0", "订单查询", ("order.lookup",)),
            AgentDefinition("support-agent", "Support Agent", "1.0.0", "售后任务", ("support.create",)),
        ]
        self.tools = [
            ToolDefinition("customer.lookup", "查询客户", "按客户 ID 查询客户", "LOW", ("support_operator",), True),
            ToolDefinition("order.lookup", "查询订单", "查询客户最近订单", "LOW", ("support_operator",), True),
            ToolDefinition("support.create", "创建售后任务", "创建售后工单", "HIGH", ("support_operator",), True),
        ]

    def audit(self, task: Task, kind: str, payload: dict[str, Any]) -> None:
        self.store.add_event(task.task_id, kind, {"trace_id": task.trace_id, **payload})

    def create_task(self, data: dict[str, Any]) -> Task:
        task = Task(str(data.get("tenant_id", "demo")), str(data.get("user_id", "anonymous")), list(data.get("roles", [])), str(data.get("input", "")))
        self.store.save_task(task); self.audit(task, "TASK_SUBMITTED", {"input": task.input}); self.run(task)
        return task

    def run(self, task: Task) -> None:
        task.transition(TaskStatus.DISPATCHING); self.store.save_task(task); self.audit(task, "TASK_DISPATCHING", {})
        task.transition(TaskStatus.RUNNING); self.store.save_task(task); self.audit(task, "TASK_RUNNING", {})
        customer_id = (re.search(r"CUST[-_]?\d+", task.input, re.I) or ["CUST-1001"])[0].upper().replace("_", "-")
        customer = {"customer_id": customer_id, "name": "示例客户", "tier": "VIP"}
        order = {"order_id": "ORD-20260918-001", "customer_id": customer_id, "status": "DELIVERED", "amount": 299.0}
        task.steps.extend([{"agent": "customer-agent", "capability": "customer.lookup", "result": customer}, {"agent": "order-agent", "capability": "order.lookup", "result": order}])
        task.transition(TaskStatus.WAITING_SUB_TASK); self.store.save_task(task); self.audit(task, "SUBTASKS_COMPLETED", {"customer": customer, "order": order})
        task.transition(TaskStatus.WAITING_APPROVAL); self.store.save_task(task)
        approval_id = f"approval-{uuid.uuid4().hex[:10]}"; self.store.add_approval(approval_id, task.task_id, {"tool_id": "support.create", "order": order, "customer": customer}); self.audit(task, "APPROVAL_REQUIRED", {"approval_id": approval_id, "tool_id": "support.create"})

    def decide(self, approval_id: str, decision: str, reviewer: str) -> dict[str, Any] | None:
        result = self.store.decide_approval(approval_id, decision, reviewer)
        if not result: return None
        task_data = self.store.get_task(result["task_id"]); task = Task(task_data["tenant_id"], task_data["user_id"], task_data["roles"], task_data["input"], task_id=task_data["task_id"], trace_id=task_data["trace_id"], status=TaskStatus(task_data["status"]), output=task_data.get("output"), error=task_data.get("error"), version=task_data["version"], created_at=task_data["created_at"], updated_at=task_data["updated_at"], steps=task_data["steps"])
        if decision == "APPROVED":
            task.transition(TaskStatus.RUNNING); self.audit(task, "APPROVAL_APPROVED", {"approval_id": approval_id, "reviewer": reviewer}); task.output = {"customer": task.steps[0]["result"], "order": task.steps[1]["result"], "support_task": {"ticket_id": f"CASE-{uuid.uuid4().hex[:8].upper()}", "status": "OPEN"}}; task.transition(TaskStatus.COMPLETED); self.audit(task, "TASK_COMPLETED", {"output": task.output})
        else:
            task.error = "approval_rejected"; task.transition(TaskStatus.FAILED); self.audit(task, "APPROVAL_REJECTED", {"approval_id": approval_id, "reviewer": reviewer})
        self.store.save_task(task); return result
