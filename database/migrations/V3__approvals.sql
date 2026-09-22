CREATE TABLE approval_request (
  approval_id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES agent_task(task_id),
  tenant_id VARCHAR(128) NOT NULL,
  requested_by VARCHAR(128) NOT NULL,
  reason TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  decided_by VARCHAR(128),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_approval_tenant_status_expires
  ON approval_request(tenant_id, status, expires_at);

CREATE INDEX idx_approval_task_status
  ON approval_request(task_id, status);
