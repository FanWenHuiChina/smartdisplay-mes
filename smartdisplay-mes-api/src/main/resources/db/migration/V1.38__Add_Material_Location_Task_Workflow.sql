ALTER TABLE material_location_task
    ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(80),
    ADD COLUMN IF NOT EXISTS assigned_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reviewer VARCHAR(80),
    ADD COLUMN IF NOT EXISTS reviewed_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(80),
    ADD COLUMN IF NOT EXISTS cancelled_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS exception_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS task_source VARCHAR(80) DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS completed_time TIMESTAMP;

UPDATE material_location_task
SET completed_time = COALESCE(completed_time, executed_time),
    task_source = COALESCE(task_source, 'SEED')
WHERE status = 'DONE';

CREATE INDEX IF NOT EXISTS idx_material_location_task_assignee
ON material_location_task(assigned_to, status, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_material_location_task_created
ON material_location_task(status, created_time DESC);
