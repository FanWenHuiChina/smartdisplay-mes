ALTER TABLE IF EXISTS exception_event
    ADD COLUMN IF NOT EXISTS mrb_result VARCHAR(40),
    ADD COLUMN IF NOT EXISTS mrb_opinion VARCHAR(500),
    ADD COLUMN IF NOT EXISTS mrb_reviewer VARCHAR(50),
    ADD COLUMN IF NOT EXISTS mrb_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS disposition_action VARCHAR(40),
    ADD COLUMN IF NOT EXISTS root_cause VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_exception_event_mrb_time ON exception_event(mrb_time);
