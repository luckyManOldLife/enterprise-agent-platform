ALTER TABLE agent_task
  ADD COLUMN idempotency_key VARCHAR(256);

CREATE UNIQUE INDEX uq_agent_task_tenant_idempotency
  ON agent_task(tenant_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE TABLE task_outbox (
  event_id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES agent_task(task_id),
  tenant_id VARCHAR(128) NOT NULL,
  trace_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  payload JSONB NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  attempts INTEGER NOT NULL DEFAULT 0,
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  claimed_at TIMESTAMPTZ,
  published_at TIMESTAMPTZ,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_outbox_claim
  ON task_outbox(status, available_at, created_at);

CREATE INDEX idx_task_outbox_task
  ON task_outbox(task_id, created_at);
