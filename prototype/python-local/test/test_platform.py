import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parents[1]))
from src.platform import Platform
from src.store import Store


def test_end_to_end_requires_approval(tmp_path):
    p = Platform(Store(str(tmp_path / "test.db")))
    task = p.create_task({"tenant_id": "t1", "user_id": "u1", "roles": ["support_operator"], "input": "查询 CUST-1001 最近订单并创建售后任务"})
    assert task.status.value == "WAITING_APPROVAL"
    approvals = p.store.approvals("PENDING")
    assert len(approvals) == 1
    p.decide(approvals[0]["approval_id"], "APPROVED", "admin")
    assert p.store.get_task(task.task_id)["status"] == "COMPLETED"
