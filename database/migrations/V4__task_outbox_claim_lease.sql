ALTER TABLE task_outbox
  ADD COLUMN claim_token UUID;

CREATE INDEX idx_task_outbox_processing_lease
  ON task_outbox(status, claimed_at)
  WHERE status = 'PROCESSING';
