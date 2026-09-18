import json
import sqlite3
from pathlib import Path
from typing import Any
from .domain import Task, TaskStatus, now


class Store:
    def __init__(self, path: str = "data/agent-platform.db"):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
        CREATE TABLE IF NOT EXISTS tasks (task_id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL, event_type TEXT NOT NULL, payload TEXT NOT NULL, created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS approvals (approval_id TEXT PRIMARY KEY, task_id TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL);
        """)
        self.db.commit()

    def save_task(self, task: Task) -> None:
        self.db.execute("INSERT OR REPLACE INTO tasks(task_id,payload,updated_at) VALUES(?,?,?)", (task.task_id, json.dumps(task.as_dict(), ensure_ascii=False), task.updated_at))
        self.db.commit()

    def get_task(self, task_id: str) -> dict[str, Any] | None:
        row = self.db.execute("SELECT payload FROM tasks WHERE task_id=?", (task_id,)).fetchone()
        return json.loads(row["payload"]) if row else None

    def add_event(self, task_id: str, event_type: str, payload: dict[str, Any]) -> dict[str, Any]:
        event = {"task_id": task_id, "event_type": event_type, "payload": payload, "created_at": now()}
        self.db.execute("INSERT INTO events(task_id,event_type,payload,created_at) VALUES(?,?,?,?)", (task_id, event_type, json.dumps(payload, ensure_ascii=False), event["created_at"]))
        self.db.commit()
        return event

    def events(self, task_id: str) -> list[dict[str, Any]]:
        rows = self.db.execute("SELECT event_type,payload,created_at FROM events WHERE task_id=? ORDER BY id", (task_id,)).fetchall()
        return [{"task_id": task_id, "event_type": r["event_type"], "payload": json.loads(r["payload"]), "created_at": r["created_at"]} for r in rows]

    def add_approval(self, approval_id: str, task_id: str, payload: dict[str, Any]) -> None:
        self.db.execute("INSERT INTO approvals VALUES(?,?,?,?,?)", (approval_id, task_id, json.dumps(payload, ensure_ascii=False), "PENDING", now()))
        self.db.commit()

    def approvals(self, status: str | None = None) -> list[dict[str, Any]]:
        query = "SELECT approval_id,task_id,payload,status,created_at FROM approvals"
        args: tuple[Any, ...] = ()
        if status:
            query += " WHERE status=?"; args = (status,)
        rows = self.db.execute(query + " ORDER BY created_at DESC", args).fetchall()
        return [{"approval_id": r[0], "task_id": r[1], "payload": json.loads(r[2]), "status": r[3], "created_at": r[4]} for r in rows]

    def decide_approval(self, approval_id: str, decision: str, reviewer: str) -> dict[str, Any] | None:
        row = self.db.execute("SELECT approval_id,task_id,payload,status,created_at FROM approvals WHERE approval_id=?", (approval_id,)).fetchone()
        if not row or row["status"] != "PENDING": return None
        self.db.execute("UPDATE approvals SET status=?, payload=? WHERE approval_id=?", (decision, json.dumps({**json.loads(row["payload"]), "reviewer": reviewer}, ensure_ascii=False), approval_id)); self.db.commit()
        return {"approval_id": approval_id, "task_id": row["task_id"], "status": decision}
