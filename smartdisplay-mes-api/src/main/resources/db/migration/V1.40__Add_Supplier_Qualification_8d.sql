CREATE TABLE IF NOT EXISTS md_supplier (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(60) NOT NULL UNIQUE,
    supplier_name VARCHAR(160) NOT NULL,
    supplier_type VARCHAR(40) NOT NULL DEFAULT 'MATERIAL',
    material_class VARCHAR(60) NOT NULL DEFAULT 'GENERAL',
    qualification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    score NUMERIC(8, 2) NOT NULL DEFAULT 0,
    pass_rate NUMERIC(8, 2) NOT NULL DEFAULT 0,
    owner VARCHAR(80),
    last_audit_time TIMESTAMP,
    next_audit_due TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(500),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supplier_qualification
ON md_supplier(qualification_status, risk_level, status);

CREATE TABLE IF NOT EXISTS supplier_corrective_action (
    id BIGSERIAL PRIMARY KEY,
    action_no VARCHAR(80) NOT NULL UNIQUE,
    supplier_code VARCHAR(60) NOT NULL,
    source_type VARCHAR(40) NOT NULL DEFAULT 'IQC',
    source_no VARCHAR(80),
    issue_summary VARCHAR(500) NOT NULL,
    root_cause VARCHAR(800),
    containment_action VARCHAR(800),
    corrective_action VARCHAR(800),
    preventive_action VARCHAR(800),
    owner VARCHAR(80),
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    due_time TIMESTAMP,
    closed_time TIMESTAMP,
    verification_result VARCHAR(800),
    request_snapshot TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supplier_8d_supplier_status
ON supplier_corrective_action(supplier_code, status, due_time);

CREATE INDEX IF NOT EXISTS idx_supplier_8d_source
ON supplier_corrective_action(source_type, source_no);

INSERT INTO md_supplier (
    supplier_code, supplier_name, supplier_type, material_class, qualification_status,
    risk_level, score, pass_rate, owner, last_audit_time, next_audit_due, status, remark
)
VALUES
('SUP-PI-01', 'PI材料供应商A', 'MATERIAL', 'CHEMICAL', 'QUALIFIED',
 'LOW', 96.00, 100.00, 'qe1003', CURRENT_TIMESTAMP - INTERVAL '14 days',
 CURRENT_TIMESTAMP + INTERVAL '76 days', 'ACTIVE', 'PI胶主供应商，试点准入有效'),
('SUP-OLED-02', 'OLED有机材料供应商B', 'MATERIAL', 'ORGANIC', 'CONDITIONAL',
 'MEDIUM', 84.00, 92.00, 'qe1003', CURRENT_TIMESTAMP - INTERVAL '25 days',
 CURRENT_TIMESTAMP + INTERVAL '35 days', 'ACTIVE', '低温有机材料供应商，需持续跟踪批次稳定性'),
('SUP-ENCAP-01', '封装材料供应商C', 'MATERIAL', 'CHEMICAL', 'PENDING',
 'MEDIUM', 0.00, 0.00, 'qe1003', NULL,
 CURRENT_TIMESTAMP + INTERVAL '30 days', 'ACTIVE', '首版试点待准入评估')
ON CONFLICT (supplier_code) DO UPDATE
SET supplier_name = EXCLUDED.supplier_name,
    supplier_type = EXCLUDED.supplier_type,
    material_class = EXCLUDED.material_class,
    owner = EXCLUDED.owner,
    status = EXCLUDED.status,
    remark = EXCLUDED.remark,
    updated_time = CURRENT_TIMESTAMP;

INSERT INTO supplier_corrective_action (
    action_no, supplier_code, source_type, source_no, issue_summary,
    root_cause, containment_action, corrective_action, preventive_action,
    owner, severity, status, due_time, request_snapshot
)
VALUES
('SCA-SEED-OLED-001', 'SUP-OLED-02', 'IQC', 'MIQC-SEED-OLED-R-260605-B',
 '有机材料批次稳定性需持续确认',
 '试点阶段需补充供应商批间波动分析',
 '当前批次按低温库位隔离复核后使用',
 '要求供应商补充近三批 COA 与关键参数趋势',
 '新增供应商批次趋势月度复核',
 'qe1003', 'MEDIUM', 'OPEN', CURRENT_TIMESTAMP + INTERVAL '5 days',
 '{"source":"seed","reason":"supplier qualification pilot"}')
ON CONFLICT (action_no) DO NOTHING;
