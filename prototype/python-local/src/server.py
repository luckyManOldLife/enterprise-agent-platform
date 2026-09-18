#!/usr/bin/env python3
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse
from .platform import Platform
from .store import Store

platform = Platform(Store())


class Handler(BaseHTTPRequestHandler):
    def send_json(self, payload, status=200):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status); self.send_header("Content-Type", "application/json; charset=utf-8"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/healthz": return self.send_json({"status": "ok", "storage": "sqlite"})
        if path == "/api/agents": return self.send_json([a.as_dict() for a in platform.agents])
        if path == "/api/tools": return self.send_json([t.as_dict() for t in platform.tools])
        if path == "/api/approvals": return self.send_json(platform.store.approvals("PENDING"))
        if path.startswith("/api/tasks/"):
            parts = path.split("/"); task_id = parts[3] if len(parts) > 3 else ""
            if len(parts) > 4 and parts[4] == "events":
                events = platform.store.events(task_id); body = "".join(f"event: {e['event_type']}\ndata: {json.dumps(e, ensure_ascii=False)}\n\n" for e in events).encode(); self.send_response(200); self.send_header("Content-Type", "text/event-stream; charset=utf-8"); self.send_header("Content-Length", str(len(body))); self.end_headers(); return self.wfile.write(body)
            task = platform.store.get_task(task_id); return self.send_json(task or {"error": "not_found"}, 200 if task else 404)
        return self.send_json({"error": "not_found"}, 404)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0")); data = json.loads(self.rfile.read(length) or b"{}")
        path = urlparse(self.path).path
        if path == "/api/tasks": return self.send_json(platform.create_task(data).as_dict(), 201)
        if path.startswith("/api/approvals/"):
            result = platform.decide(path.rsplit("/", 1)[1], str(data.get("decision", "")).upper(), str(data.get("reviewer", "unknown"))); return self.send_json(result or {"error": "approval_not_found_or_decided"}, 200 if result else 404)
        return self.send_json({"error": "not_found"}, 404)

    def log_message(self, *_): pass


if __name__ == "__main__":
    print("Enterprise Agent Platform listening on http://127.0.0.1:8787")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
