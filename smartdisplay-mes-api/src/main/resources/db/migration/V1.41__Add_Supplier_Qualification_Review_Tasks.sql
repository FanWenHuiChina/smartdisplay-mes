CREATE TABLE IF NOT EXISTS supplier_qualification_review_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(80) NOT NULL UNIQUE,
    supplier_code VARCHAR(60) NOT NULL,
    review_type VARCHAR(40) NOT NULL DEFAULT 'PERIODIC',
    source_no VARCHAR(80),
    trigger_reason VARCHAR(500) NOT NULL,
    qualification_before VARCHAR(30),
    risk_before VARCHAR(20),
    suggested_qualification VARCHAR(30) NOT NULL,
    suggested_risk VARCHAR(20) NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    due_time TIMESTAMP,
    reviewer VARCHAR(80),
    review_time TIMESTAMP,
    decision VARCHAR(30),
    decision_comment VARCHAR(800),
    performance_snapshot TEXT,
    request_snapshot TEXT,
    created_by VARCHAR(80),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supplier_review_supplier_status
ON supplier_qualification_review_task(supplier_code, review_status, due_time);

CREATE INDEX IF NOT EXISTS idx_supplier_review_due
ON supplier_qualification_review_task(review_status, due_time);

INSERT INTO supplier_qualification_review_task (
    task_no, supplier_code, review_type, source_no, trigger_reason,
    qualification_before, risk_before, suggested_qualification, suggested_risk,
    review_status, due_time, performance_snapshot, request_snapshot, created_by
)
VALUES
('SQR-SEED-OLED-001', 'SUP-OLED-02', 'PERIODIC', 'SCA-SEED-OLED-001',
 '供应商存在未关闭8D和批次稳定性风险，需完成月度准入复审',
 'CONDITIONAL', 'MEDIUM', 'CONDITIONAL', 'HIGH',
 'OPEN', CURRENT_TIMESTAMP + INTERVAL '7 days',
 '{"source":"seed","risk":"supplier monthly review"}',
 '{"source":"seed","reason":"supplier qualification review pilot"}',
 'qe1003')
ON CONFLICT (task_no) DO NOTHING;
