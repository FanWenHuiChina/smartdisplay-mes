CREATE TABLE IF NOT EXISTS ai_report_record (
    id BIGSERIAL PRIMARY KEY,
    report_no VARCHAR(80) NOT NULL UNIQUE,
    report_type VARCHAR(50) NOT NULL,
    biz_no VARCHAR(120),
    biz_type VARCHAR(50),
    prompt_template_version VARCHAR(50) NOT NULL,
    model_name VARCHAR(80) NOT NULL,
    input_snapshot TEXT NOT NULL,
    output_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    created_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_report_record IS 'AI分析结果留痕表';
COMMENT ON COLUMN ai_report_record.report_no IS 'AI报告编号';
COMMENT ON COLUMN ai_report_record.report_type IS '报告类型：良率日报/设备分析/SOP问答';
COMMENT ON COLUMN ai_report_record.biz_no IS '关联业务对象编号';
COMMENT ON COLUMN ai_report_record.biz_type IS '关联业务对象类型';
COMMENT ON COLUMN ai_report_record.prompt_template_version IS 'Prompt模板版本';
COMMENT ON COLUMN ai_report_record.model_name IS '模型名称';
COMMENT ON COLUMN ai_report_record.input_snapshot IS '输入快照JSON';
COMMENT ON COLUMN ai_report_record.output_json IS '输出JSON';
COMMENT ON COLUMN ai_report_record.status IS '生成状态';
COMMENT ON COLUMN ai_report_record.created_by IS '创建人';

CREATE INDEX IF NOT EXISTS idx_ai_report_type ON ai_report_record(report_type);
CREATE INDEX IF NOT EXISTS idx_ai_report_biz ON ai_report_record(biz_type, biz_no);
CREATE INDEX IF NOT EXISTS idx_ai_report_created_time ON ai_report_record(created_time);
