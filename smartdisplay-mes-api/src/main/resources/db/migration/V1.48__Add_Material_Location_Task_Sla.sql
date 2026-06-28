ALTER TABLE material_location_task
    ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS due_time TIMESTAMP;

UPDATE material_location_task
SET priority = CASE
        WHEN task_type = 'SPLIT' THEN 8
        WHEN task_type = 'PUTAWAY' THEN 6
        WHEN task_type = 'MOVE' THEN 5
        WHEN task_type = 'COUNT' THEN 3
        ELSE COALESCE(priority, 3)
    END,
    due_time = COALESCE(due_time, created_time + INTERVAL '4 hours')
WHERE status IN ('CREATED', 'ASSIGNED', 'EXECUTING');

CREATE INDEX IF NOT EXISTS idx_material_location_task_sla
ON material_location_task(status, due_time, priority DESC, created_time DESC);
