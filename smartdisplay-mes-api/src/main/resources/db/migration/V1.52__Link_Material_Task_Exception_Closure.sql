ALTER TABLE material_location_task
    ADD COLUMN IF NOT EXISTS linked_exception_event_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS exception_close_action VARCHAR(40),
    ADD COLUMN IF NOT EXISTS exception_close_conclusion VARCHAR(500),
    ADD COLUMN IF NOT EXISTS exception_closed_by VARCHAR(80),
    ADD COLUMN IF NOT EXISTS exception_closed_time TIMESTAMP;

COMMENT ON COLUMN material_location_task.linked_exception_event_no IS 'WMS升级处置生成的异常事件编号';
COMMENT ON COLUMN material_location_task.exception_close_action IS 'MRB关闭WMS异常时使用的处置动作';
COMMENT ON COLUMN material_location_task.exception_close_conclusion IS 'MRB关闭WMS异常时的处置结论';
COMMENT ON COLUMN material_location_task.exception_closed_by IS 'MRB关闭WMS异常的操作人';
COMMENT ON COLUMN material_location_task.exception_closed_time IS 'MRB关闭WMS异常的时间';

UPDATE material_location_task task
SET linked_exception_event_no = ev.event_no
FROM exception_event ev
WHERE ev.source_ref_type = 'MATERIAL_LOCATION_TASK'
  AND ev.source_ref_no = task.task_no
  AND task.linked_exception_event_no IS NULL;

CREATE INDEX IF NOT EXISTS idx_material_location_task_linked_exception
    ON material_location_task (linked_exception_event_no);
