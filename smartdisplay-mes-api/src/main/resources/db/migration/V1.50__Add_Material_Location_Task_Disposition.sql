ALTER TABLE material_location_task
    ADD COLUMN IF NOT EXISTS disposition_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS disposition_result VARCHAR(30),
    ADD COLUMN IF NOT EXISTS disposition_conclusion VARCHAR(500),
    ADD COLUMN IF NOT EXISTS disposition_by VARCHAR(80),
    ADD COLUMN IF NOT EXISTS disposition_time TIMESTAMP;

UPDATE material_location_task
SET disposition_status = 'PENDING'
WHERE review_result = 'REJECTED'
  AND disposition_status IS NULL;

UPDATE material_location_task
SET disposition_status = 'CLOSED',
    disposition_result = COALESCE(disposition_result, 'APPROVED'),
    disposition_conclusion = COALESCE(disposition_conclusion, review_conclusion, '库位任务复核通过'),
    disposition_by = COALESCE(disposition_by, reviewer),
    disposition_time = COALESCE(disposition_time, reviewed_time)
WHERE review_result = 'APPROVED'
  AND reviewed_time IS NOT NULL
  AND disposition_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_material_location_task_disposition
ON material_location_task(disposition_status, review_result, disposition_time DESC);
