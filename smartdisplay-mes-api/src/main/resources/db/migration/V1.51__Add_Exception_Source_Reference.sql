ALTER TABLE exception_event
    ADD COLUMN IF NOT EXISTS source_ref_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_ref_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_payload TEXT;

COMMENT ON COLUMN exception_event.source_ref_type IS '异常来源对象类型，例如 MATERIAL_LOCATION_TASK';
COMMENT ON COLUMN exception_event.source_ref_no IS '异常来源对象编号，例如库位任务号';
COMMENT ON COLUMN exception_event.source_payload IS '异常来源对象结构化快照 JSON';

CREATE INDEX IF NOT EXISTS idx_exception_event_source_ref
    ON exception_event (source_module, source_ref_type, source_ref_no);
