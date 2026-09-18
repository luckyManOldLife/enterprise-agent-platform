CREATE TABLE agent_definition (
  agent_id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  version VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  endpoint TEXT NOT NULL,
  capabilities JSONB NOT NULL,
  tenant_scope VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent_task (
  task_id UUID PRIMARY KEY,
  parent_task_id UUID,
  conversation_id VARCHAR(128),
  trace_id VARCHAR(128) NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  source_agent VARCHAR(128),
  target_agent VARCHAR(128),
  status VARCHAR(32) NOT NULL,
  input JSONB NOT NULL,
  output JSONB,
  error_code VARCHAR(128),
  retry_count INTEGER NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  deadline TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ
);

CREATE TABLE audit_event (
  event_id UUID PRIMARY KEY,
  task_id UUID NOT NULL,
  trace_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_task_tenant_status ON agent_task(tenant_id, status);
CREATE INDEX idx_audit_event_task_created ON audit_event(task_id, created_at);
