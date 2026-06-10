ALTER TABLE material_location_task
    ADD COLUMN IF NOT EXISTS review_result VARCHAR(20),
    ADD COLUMN IF NOT EXISTS review_conclusion VARCHAR(500);

UPDATE material_location_task
SET review_result = COALESCE(review_result, 'APPROVED'),
    review_conclusion = COALESCE(review_conclusion, '历史库位任务复核通过')
WHERE reviewed_time IS NOT NULL
  AND (review_result IS NULL OR review_result = '');

CREATE INDEX IF NOT EXISTS idx_material_location_task_review_result
ON material_location_task(review_result, reviewed_time DESC);
